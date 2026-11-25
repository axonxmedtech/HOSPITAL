# Point-in-time recovery (PITR)

**Goal:** if the server is lost or data is damaged, restore the database to any moment — losing
at most **~5 minutes**, not a whole day. Decided before launch (Nov 2026).

**How:** the nightly dump (`backup.sh`, with `PITR=1`) is the base, and records exactly where in
MySQL's binary log its snapshot sits. Every change after that is in the binary logs, which
`binlog-ship.sh` encrypts and copies off-site every 5 minutes. A restore = last dump + binlogs
replayed up to the chosen second (`pitr-restore.sh`).

```
 nightly 02:00 ── dump (+ binlog position) ──► S3  <prefix>/daily/
 every 5 min   ── closed binlogs ────────────► S3  <prefix>/binlog/
 restore       ── dump → scratch DB, then replay binlogs up to "YYYY-MM-DD HH:MM:SS"
```

Encryption and storage are the same as the nightly off-site copy (`age` public key on the server,
private key kept off it): [OFFSITE_BACKUPS.md](OFFSITE_BACKUPS.md).

## One-time setup (on the VPS)

**Shortcut:** `scripts/ops/server-setup.sh` does steps 1–3 and 5 (plus the Prometheus scrape token)
safely — `check` reports, `apply --dry-run` previews, and it only restarts MySQL or the backend when
passed `--restart-mysql` / `--restart-backend`. Steps 4 (bucket rule) and 6 (alert) stay manual.

1. **Binary logging** — `/etc/mysql/mysql.conf.d/hms-binlog.cnf`:
   ```ini
   [mysqld]
   server_id                  = 1
   log_bin                    = mysql-bin
   binlog_format              = ROW
   sync_binlog                = 1          # every commit reaches the binlog on disk
   binlog_expire_logs_seconds = 1209600    # keep 14 days locally
   ```
   `sudo systemctl restart mysql`, then `mysql -e "SELECT @@log_bin, @@binlog_format"` → `1  ROW`.
2. **Privileges** for the backup login (`dump` needs them for the position, `ship` for FLUSH):
   ```sql
   GRANT RELOAD, REPLICATION CLIENT ON *.* TO '<backup_user>'@'localhost';
   ```
3. **Turn on PITR dumps** — add `PITR=1` to the backup job's environment (the nightly workflow's
   SSH command, or `/etc/hms/backup.env` if used). The next dump's `.meta.json` shows
   `binlogFile` / `binlogPos`; if they are empty the dump **fails on purpose** (exit 9).
4. **Bucket lifecycle** — add a rule for `<prefix>/binlog/`: expire after **35 days** (same as
   `daily/`). The shipping key stays write-only.
5. **Shipping timer**:
   ```bash
   sudo cp scripts/db/systemd/hms-binlog-ship.{service,timer} /etc/systemd/system/
   sudo systemctl daemon-reload && sudo systemctl enable --now hms-binlog-ship.timer
   sudo systemctl start hms-binlog-ship.service && journalctl -u hms-binlog-ship -n 20
   ```
   Expect `[binlog] OK — shipped N closed binlog(s)`. Check `/var/lib/hms/binlog-shipped` grows.
6. **Alert** — the timer failing means PITR is silently off. Add it to monitoring: alert if
   `systemctl is-failed hms-binlog-ship.service` or the newest object in `<prefix>/binlog/` is
   older than 15 minutes.

## Restore to a point in time

1. **Pick the moment** just before the damage (server time zone), e.g. `2026-12-01 14:29:00`.
2. **Fetch** the last nightly dump _before_ that moment and every binlog after it
   (`offsite-fetch.sh`, then `age --decrypt -i <identity>`), into e.g. `/root/pitr/dump.sql.gz` and
   `/root/pitr/binlogs/`. Keep the decrypted files only for the restore, then `shred -u`.
3. **Restore into a scratch database** (never the live one first):
   ```bash
   bash scripts/db/pitr-restore.sh /root/pitr/dump.sql.gz /root/pitr/binlogs "2026-12-01 14:29:00"
   ```
   It refuses a dump without a binlog position, a missing start file, or a gap in the binlog
   sequence. It ends with the table count and the newest audit entry — check that entry is just
   before your chosen moment.
4. **Verify** in the scratch DB (`<db>_pitr_check`): the records you care about, recent bills/admissions.
5. **Switch over** (emergency, with a fresh backup of the current live DB first): stop the backend,
   re-run with the live database as target and `FORCE_RESTORE=yes`, start the backend, check
   `/actuator/health/readiness`. Record the incident.

## Drill

Run a PITR restore into the scratch DB **monthly** and record the date below (the automated
weekly restore drill covers the nightly dump; this covers the binlog replay).

| Date | Restored to | Result | By  |
| ---- | ----------- | ------ | --- |
|      |             |        |     |
