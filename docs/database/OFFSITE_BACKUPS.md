# Off-site backups

Every night the VPS takes a verified backup (`scripts/db/backup.sh`), encrypts it, and copies it to
object storage at a **different provider** (`scripts/db/offsite-copy.sh`). If the Hostinger VPS is
lost, wiped or hacked, the patient data can still be restored.

| What                                               | Where                  | Kept                         |
| -------------------------------------------------- | ---------------------- | ---------------------------- |
| Local backup (plain `.sql.gz`)                     | VPS `/var/backups/hms` | 30 days (never fewer than 7) |
| Daily off-site copy (encrypted `.sql.gz.age`)      | bucket `…/daily/`      | 35 days (lifecycle rule)     |
| Monthly off-site copy (first backup of each month) | bucket `…/monthly/`    | 400 days (lifecycle rule)    |

**Recommended storage: AWS S3, region `ap-south-1` (Mumbai)**, so the data stays in India. Any
S3-compatible service also works (Cloudflare R2, Backblaze B2, DigitalOcean Spaces); set
`OFFSITE_S3_ENDPOINT` for those.

## How the copy is protected

- **Encrypted before it leaves the VPS**, with [age](https://age-encryption.org) public-key
  encryption. The VPS holds only the **public** key. The **private** key never goes on the server,
  so neither a stolen server nor a leaked bucket can read a backup.
- **The VPS cannot delete off-site copies.** Its access key may only upload and read. With bucket
  versioning on, an overwrite keeps the old version too.
- **The bucket is private**: public access blocked, server-side encryption on.
- **GitHub never sees the data or the key.** The workflow only starts the job over SSH and shows
  file names, sizes and checksums.
- **A missing or failed copy fails the nightly job**, and GitHub emails the failure. The local
  backup is kept either way.

> **The private key is the only way to restore an off-site copy.** Lose it and every off-site copy
> is useless. Keep it in the company password manager **and** one printed copy in a safe place.

## One-time setup (about 30 minutes)

### 1. Make the key pair (on your own laptop, not the VPS)

```bash
brew install age            # macOS;  Ubuntu: sudo apt install age
age-keygen -o hms-backup-identity.txt
# prints:  Public key: age1....   ← goes on the VPS (step 4)
```

Save `hms-backup-identity.txt` (it starts `AGE-SECRET-KEY-1…`) in the password manager, print it,
then delete it from the laptop.

### 2. Create the bucket (AWS console → S3 → Create bucket)

- Name: e.g. `axonx-hms-backups` · Region: **Asia Pacific (Mumbai) ap-south-1**
- **Block all public access**: on (the default)
- **Bucket versioning**: enable
- **Default encryption**: SSE-S3 (the default)
- Management → **Lifecycle rules**:
  - `daily-expiry`: prefix `hms/production/daily/` → expire current versions after **35** days,
    permanently delete noncurrent versions after **7** days
  - `monthly-expiry`: prefix `hms/production/monthly/` → expire after **400** days, noncurrent after **7**
  - (the same two for `hms/staging/` if staging is backed up off-site too)

### 3. Create an upload-only key (IAM → Users → Create user `hms-backup-writer`)

Attach this inline policy (replace the bucket name). It has **no delete permission**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject"],
      "Resource": "arn:aws:s3:::axonx-hms-backups/hms/*"
    },
    { "Effect": "Allow", "Action": "s3:ListBucket", "Resource": "arn:aws:s3:::axonx-hms-backups" }
  ]
}
```

Then Security credentials → **Create access key** (use case: "Application running outside AWS").

### 4. Configure the VPS (as root)

```bash
sudo apt update && sudo apt install -y age awscli
sudo mkdir -p /etc/hms
sudo tee /etc/hms/offsite.env > /dev/null <<'EOF'
OFFSITE_S3_BUCKET=axonx-hms-backups
OFFSITE_S3_PREFIX=hms/production
OFFSITE_S3_REGION=ap-south-1
OFFSITE_AGE_RECIPIENT=age1...the public key from step 1...
AWS_ACCESS_KEY_ID=...from step 3...
AWS_SECRET_ACCESS_KEY=...from step 3...
EOF
sudo chown deploy:deploy /etc/hms/offsite.env   # the user the backup job SSHes in as
sudo chmod 600 /etc/hms/offsite.env             # the script refuses a file others can read
```

If staging runs on the same VPS, give it its own prefix: `OFFSITE_CONFIG=/etc/hms/offsite-staging.env`
with `OFFSITE_S3_PREFIX=hms/staging`.

### 5. Test it

GitHub → Actions → **Database Backup** → Run workflow → Production. The summary must show two
`[offsite] OK — s3://…` lines, `daily/` and (the first time each month) `monthly/`. Then check the
bucket in the AWS console.

Until steps 1–4 are done, the nightly job fails on purpose ("off-site copy required but
/etc/hms/offsite.env does not exist"). While setting up you can set the repository variable
`DB_BACKUP_OFFSITE=auto` to make it a warning, but **remove it afterwards**.

## Restoring from an off-site copy

```bash
# on the VPS (or a fresh server with this repo, age and awscli installed)
bash scripts/db/offsite-fetch.sh list daily          # or: list monthly
# put the private key on the machine only for this
nano /root/identity.txt && chmod 600 /root/identity.txt
bash scripts/db/offsite-fetch.sh get hms_production-production-20261121T183000Z.sql.gz.age /root/identity.txt
shred -u /root/identity.txt
bash scripts/db/restore.sh /var/backups/hms/restore/hms_production-production-20261121T183000Z.sql.gz
# → restores into a scratch database and validates; see BACKUP_AND_RESTORE.md for a live restore
```

Restoring from a **new** server needs a key that can read: the upload key above can (`s3:GetObject`).
Keep its values in the password manager next to the private key.

## Drill (every 3 months)

1. Fetch the newest **monthly** copy and restore it into the scratch database (above).
2. Check the patient count and the latest OPD visit date look right.
3. Drop the scratch database and write down the date, the file and who did it.
