# Encrypting data at rest (Hostinger VPS)

Patient data sits in three places on the server: the MySQL data files, the local backups in
`/var/backups/hms`, and uploaded documents. This page covers how each one is protected when the
server is switched off, a disk is replaced, or a snapshot is copied.

| Where                            | Today                                                                                 | After this runbook                                                                                     |
| -------------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Off-site backups                 | Encrypted (age, public key). See [OFFSITE_BACKUPS.md](../database/OFFSITE_BACKUPS.md) | Same                                                                                                   |
| MySQL data files                 | Depends on Hostinger, not yet confirmed                                               | InnoDB encryption on                                                                                   |
| Local backups `/var/backups/hms` | Plain `.sql.gz`, readable by root and the deploy user only                            | Same, or kept on the encrypted volume (step 3)                                                         |
| Data on its way to the database  | On the same server: does not cross a network                                          | Same. The app logs a SECURITY error if the DB ever moves to another machine without `sslMode=REQUIRED` |

What this does and does not protect against: encryption at rest protects data on disks and
snapshots that leave the server. It does **not** protect against someone who is logged in to the
running server (root can read the live database). That is covered by SSH keys only, the firewall,
staff permissions in the app, and the access log.

## Step 1: Ask Hostinger (do this first, 5 minutes)

Open a support chat and ask for the answer **in writing**:

> Is the storage of my KVM VPS encrypted at rest? If yes, which standard (e.g. AES-256), and does
> it also cover snapshots and backups you keep of my VPS?

Save the reply. Hospitals and auditors may ask for it. If the answer is yes, steps 2 and 3 are
still worth doing, but they are no longer urgent.

## Step 2: Turn on MySQL InnoDB encryption (recommended; no app change)

MySQL 8 can encrypt its own data files. Searching and every screen keep working exactly as
before, because the data is decrypted as MySQL reads it. Plan a quiet window of about 30 minutes,
and take a backup first.

```bash
# 0. Fresh backup first
OFFSITE=required bash scripts/db/backup.sh

# 1. Keyring (holds the master key). Native MySQL 8.0 example with the keyring_file component:
sudo mkdir -p /var/lib/mysql-keyring && sudo chown mysql:mysql /var/lib/mysql-keyring && sudo chmod 750 /var/lib/mysql-keyring
#    Follow the MySQL manual for your exact version ("Installing a Keyring Component").
#    For MySQL running in Docker, mount the keyring directory as its own volume.

# 2. Restart MySQL, then check the keyring is active:
mysql -e "SELECT * FROM performance_schema.keyring_component_status;"

# 3. Make new tables encrypted by default and encrypt the existing ones:
mysql -e "SET PERSIST default_table_encryption = ON;"
mysql -e "ALTER DATABASE hms_production DEFAULT ENCRYPTION = 'Y';"
mysql -N -e "SELECT CONCAT('ALTER TABLE \`', table_name, '\` ENCRYPTION = ''Y'';') FROM information_schema.tables \
  WHERE table_schema = 'hms_production' AND table_type = 'BASE TABLE';" | mysql hms_production

# 4. Also encrypt the redo and undo logs and the binary log:
mysql -e "SET PERSIST innodb_redo_log_encrypt = ON; SET PERSIST innodb_undo_log_encrypt = ON; SET PERSIST binlog_encryption = ON;"

# 5. Check: every table should say Y
mysql -e "SELECT table_name, create_options FROM information_schema.tables WHERE table_schema='hms_production';"
```

**Back up the keyring file** (password manager, next to the backup private key). Without it MySQL
cannot open its own data. A `mysqldump` backup is a plain export, so it still restores on a new
server without this key. That is why the backups are encrypted separately.

## Step 3 (optional, stronger): an encrypted volume for the data and backup folders

A LUKS-encrypted volume protects the MySQL folder, the local backups and the keyring file together.
The catch is that someone has to unlock it after every reboot, either by typing the passphrase
through the Hostinger browser console, or with a key stored elsewhere. Do this only with a planned
maintenance window. It needs MySQL stopped while its data is moved. We can do it together when
you're ready.

## Checklist for the client security note

- [ ] Hostinger's written answer saved (step 1)
- [ ] InnoDB encryption on, all tables `ENCRYPTION='Y'` (step 2)
- [ ] Keyring file and backup private key stored in the password manager
- [ ] Off-site backups running nightly and a restore drill done ([OFFSITE_BACKUPS.md](../database/OFFSITE_BACKUPS.md))
