# Backup & Restore

Backups exist so patient data survives a bad migration, a bad deploy, or a failure. A backup is
**not real until a restore has been proven** — so this doc covers both, plus the drills that keep
them trustworthy.

> **PHI never passes through CI.** Dumps contain patient data. They are written **on the VPS**,
> and the VPS itself sends an **encrypted** copy off-site ([OFFSITE_BACKUPS.md](OFFSITE_BACKUPS.md)).
> They are **never** downloaded to a CI runner or uploaded as a GitHub artifact. Only metadata
> (filename, size, checksum, verify result) is surfaced.

---

## Backup strategy

| Trigger                   | How                                                        | Notes                                                                         |
| ------------------------- | ---------------------------------------------------------- | ----------------------------------------------------------------------------- |
| **Scheduled**             | `.github/workflows/db-backup.yml` (daily cron — 00:00 IST) | full logical backup + **required** off-site copy                              |
| **Pre-production-deploy** | `_deploy.yml` step (production)                            | **before** any schema change; **gates** the deploy; off-site copy best-effort |
| **Manual**                | `db-backup.yml` → _Run workflow_ (choose env)              | ad-hoc, e.g. before risky ops                                                 |
| **On the box**            | `scripts/db/backup.sh`                                     | what all of the above call                                                    |

`scripts/db/backup.sh` produces a **consistent** (`--single-transaction`, no locks), gzipped,
**timestamped** dump: `‹db›-‹env›-‹UTC-timestamp›.sql.gz` in `BACKUP_DIR` (default
`/var/backups/hms`), plus a `.meta.json` sidecar (size + SHA-256).

- **Never overwrites** — a unique timestamp per run; refuses if the target exists.
- **Self-verifying** — gzip integrity + minimum-size + "Dump completed" footer; a bad dump is
  removed and the job fails.
- **Full backups** of the logical database (schema + data + routines/triggers/events). MySQL logical
  dumps are inherently full; **incremental** backups (binlog shipping / PITR) are noted under
  "future" — they need binlog + infra beyond this phase.

### Backup workflow

```
 cron / manual / pre-deploy
        │  SSH (key)
        ▼
   VPS: source app .env (SPRING_DATASOURCE_*) ─► backup.sh
        │  mysqldump --single-transaction | gzip
        ▼
   /var/backups/hms/‹db›-‹env›-‹ts›.sql.gz  (mode 600, root/mysql only)
        │  gzip -t · size · footer · sha256
        ▼
   verified ✔
        │  OpenSSL AES-256-CBC PBKDF2 (100k iter) using BACKUP_SECRET_KEY (/etc/hms/backup.env)
        ▼
   /var/backups/hms/‹db›-‹env›-‹ts›.sql.gz.enc  (mode 640, app group readable) + .meta.json
        │  retention prune (keep RETENTION_DAYS, never below KEEP_MIN newest)
        ▼
   Super Admin Portal (GET /platform/backups) ─► offline copy on admin laptop & external drive
        │  (Optional S3 offsite copy when enabled: OFFSITE=s3)
        ▼
   off-site copy verified ✔   metadata (no PHI) → audit log & monitoring
```

---

## Strong Master Key & Server File Permissions

Every backup archive is encrypted directly on the server before storage or download.

### 1. Master Secret Key Setup

The server uses AES-256-CBC PBKDF2 (100,000 iterations) with SHA-256 key derivation.
Configure the key in `/etc/hms/backup.env`:

```bash
sudo mkdir -p /etc/hms
sudo bash -c 'cat > /etc/hms/backup.env <<EOF
BACKUP_SECRET_KEY="<generate-64-char-random-key>"
EOF'
sudo chmod 600 /etc/hms/backup.env
sudo chown root:root /etc/hms/backup.env
```

### 2. File Ownership and Permissions

The app user (`hms-app`) must be able to read `.enc` and `.meta.json` files for Super Admin downloads, but must **never** have read access to the plain `.sql.gz` dumps:

```bash
# Plain database dumps: accessible ONLY to root / mysql
sudo chown root:root /var/backups/hms/*.sql.gz
sudo chmod 600 /var/backups/hms/*.sql.gz

# Encrypted archives & metadata: group-readable by app user
sudo chgrp hms-app /var/backups/hms/*.sql.gz.enc /var/backups/hms/*.meta.json
sudo chmod 640 /var/backups/hms/*.sql.gz.enc /var/backups/hms/*.meta.json
```

---

## Super Admin Weekly Routine & Offline Storage

All hospital patient data sits on the server. To protect against total server loss, ransomware, or host failure:

1. **Weekly Download**:
   - Super Admin opens **Platform Portal** → **Backups** tab (`/platform/dashboard?tab=backups`).
   - Downloads the latest encrypted `.sql.gz.enc` file.
   - Rate limit: max 5 downloads/hour per user.
   - Audit trail: each download writes an immutable `BACKUP_DOWNLOADED` entry in `audit_logs` (user, IP, filename, timestamp).
2. **Two-Location Offline Rule**:
   - Save Copy 1 to the Super Admin laptop.
   - Save Copy 2 to a detached external drive.
   - Without the master key, the file cannot be decrypted even if the drive or laptop is lost.
3. **7-Day Overdue Warning**:
   - The Super Admin Dashboard displays a prominent warning banner if no backup has been downloaded in the last 7 days.

---

### Retention policy

- Keep backups for **`RETENTION_DAYS`** (default **30**, via `DB_BACKUP_RETENTION_DAYS`).
- **Never** prune below **`KEEP_MIN`** newest (default **7**) regardless of age.
- The newest backup is never deleted. Pre-deploy backups are kept alongside scheduled ones.
- Off-site copies are kept longer by the bucket's lifecycle rules: daily copies 35 days, monthly
  copies 400 days. See [OFFSITE_BACKUPS.md](OFFSITE_BACKUPS.md).

---

## Restore & restore validation

`scripts/db/restore.sh` restores a backup **and validates it**. By default it restores into a
**scratch database** (`‹db›_restore_check`) so you can prove restorability **without touching live
data** — this is the drill that makes backups trustworthy.

```
 pick backup.sql.gz ─► verify-backup.sh (gzip + checksum + content)
        │
        ▼ restore into scratch DB (‹db›_restore_check)
   zcat | mysql   (CREATE DATABASE / USE stripped)
        │
        ▼ VALIDATE: table count ≥ 1 · core tables (patients/users) present
   PASS ✔  → drop scratch DB when done
```

### Restore procedure (drill — do this regularly)

#### Method 1: Super Admin UI (Recommended for drills and disaster recovery)

1. Navigate to **Platform Dashboard** → **Backups** tab.
2. Under **Disaster Recovery (Upload & Restore)**:
   - If the server has existing backups, select the archive from the dropdown.
   - If recovering after a server wipe, select **Upload Backup from Device** and upload your offline `.sql.gz.enc` file.
3. Enter your **Strong Master Key**.
4. Click **1. Test in Scratch DB**:
   - The server streams the decryptor directly into a temporary scratch database (`hms_restore_check`).
   - Verifies all tables and records, checks core tables (`patients`, `users`), and counts restored rows.
   - Leaves the live database completely untouched.
5. If an emergency live restore is needed:
   - Click **2. Restore to Live DB**.
   - Type the required safety confirmation phrase: `RESTORE-LIVE-DATABASE`.
   - The server performs the live database restore and logs an audit trail.

#### Method 2: On the VPS via CLI

```bash
# On the VPS. Do NOT `. ./backend/.env` by hand — see the warning below; the scripts load it.
cd <repo>

# Option A: Restore directly from encrypted archive (.enc)
# restore.sh automatically reads BACKUP_SECRET_KEY from /etc/hms/backup.env or env
bash scripts/db/restore.sh /var/backups/hms/<file>.sql.gz.enc

# Option B: Manual decryption via OpenSSL CLI if needed
openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
  -pass pass:"$BACKUP_SECRET_KEY" \
  -in /var/backups/hms/<file>.sql.gz.enc \
  -out /var/backups/hms/<file>.sql.gz

bash scripts/db/restore.sh /var/backups/hms/<file>.sql.gz     # → scratch DB + validation
# When satisfied:  mysql -e "DROP DATABASE \`<db>_restore_check\`;"
```

> **Never source the app env with `.` / `source`.** `SPRING_DATASOURCE_URL` is an unquoted JDBC URL
> containing `&`, which bash reads as the background operator: `set -a; . ./backend/.env` runs the
> assignment in a subshell and leaves the variable **empty** in your shell. Spring parses the file
> with its own reader, so the app is fine and the breakage is invisible. `backup.sh` and
> `restore.sh` load it safely via `scripts/db/load-env.sh` — let them. To point at a different
> file, set `ENV_FILE=/path/to/env`.

### Emergency restore (over the live DB — last resort)

Restoring over live data is destructive of anything since the backup. Only when the DB is
unrecoverable and after sign-off:

1. **Take a fresh backup first** (`backup.sh`) — even of the broken state.
2. Stop the app: `sudo systemctl stop hms-production`.
3. `FORCE_RESTORE=yes bash scripts/db/restore.sh <backup>.sql.gz hospital_management`
   (the script refuses the live DB without `FORCE_RESTORE=yes`).
4. Start the app; verify `/actuator/health` UP and spot-check key records.
5. Record the incident + data-loss window (any writes after the backup timestamp are lost).

---

## Recovery checklist

- [ ] Backup exists and **verified** (`verify-backup.sh` passes; checksum matches sidecar).
- [ ] Restore into scratch DB **succeeds** and validation passes (tables + core tables present).
- [ ] Restored app boots against the scratch DB (optional deeper drill).
- [ ] Restore drill run **regularly** (e.g. monthly) and the date recorded.
- [ ] Retention healthy: recent scheduled backups present; pre-deploy backups retained.
- [ ] Offsite copy of periodic backups exists (compliance).

## Relationship to deployment rollback

Phase-8 rollback restores the **application/artifact**, not the database. The **pre-deploy backup**
is the database safety net: if a migration corrupts data, roll back the app **and** restore the DB
from the pre-deploy backup. Because migrations are backward-compatible, the common case is
forward-fix (a new `V(n+1)` migration) rather than a DB restore.

## Manual operational steps required

- Ensure the deploy user can write `BACKUP_DIR` (`/var/backups/hms`) and run `mysqldump`/`mysql`
  with the app's DB credentials (they come from the VPS `.env`).
- Set `DB_BACKUP_RETENTION_DAYS` (variable) if 30 days isn't right for your compliance needs.
- Set up the off-site bucket, keys and `/etc/hms/offsite.env` once: [OFFSITE_BACKUPS.md](OFFSITE_BACKUPS.md).
  Until then the nightly job fails on purpose.
- Schedule the restore **drill** (every 3 months, from an off-site monthly copy).
- Confirm the daily backup cron window in `db-backup.yml` matches your low-traffic hours.
- **Required:** set a repository-level variable `PRODUCTION_SSH_HOST` (and `STAGING_SSH_HOST`).
  The scheduled backup job runs without an `environment:` scope on purpose — an environment with
  required reviewers would stall every unattended backup waiting for approval — so it cannot read
  the environment-scoped `vars.HOST` that `_deploy.yml` uses.

## Future (out of scope for this phase)

Point-in-time recovery via binlog (would shrink the worst-case loss below one day) — needs
infrastructure work beyond this phase.
