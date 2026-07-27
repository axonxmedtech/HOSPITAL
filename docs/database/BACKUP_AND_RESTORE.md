# Backup & Restore

Backups exist so patient data survives a bad migration, a bad deploy, or a failure. A backup is
**not real until a restore has been proven** — so this doc covers both, plus the drills that keep
them trustworthy.

> **PHI stays on the server.** Dumps contain patient data. They are written and retained **on the
> VPS** and are **never** downloaded to a CI runner or uploaded as a GitHub artifact. Only metadata
> (filename, size, checksum, verify result) is surfaced.

---

## Backup strategy

| Trigger | How | Notes |
|---|---|---|
| **Scheduled** | `.github/workflows/db-backup.yml` (daily cron) | full logical backup |
| **Pre-production-deploy** | `_deploy.yml` step (production) | **before** any schema change; **gates** the deploy |
| **Manual** | `db-backup.yml` → *Run workflow* (choose env) | ad-hoc, e.g. before risky ops |
| **On the box** | `scripts/db/backup.sh` | what all of the above call |

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
   /var/backups/hms/‹db›-‹env›-‹ts›.sql.gz  (+ .meta.json)
        │  gzip -t · size · footer · sha256
        ▼
   verified ✔  → retention prune (keep RETENTION_DAYS, never below KEEP_MIN newest)
   metadata (no PHI) → GitHub step summary / log artifact
```

### Retention policy
- Keep backups for **`RETENTION_DAYS`** (default **30**, via `DB_BACKUP_RETENTION_DAYS`).
- **Never** prune below **`KEEP_MIN`** newest (default **7**) regardless of age.
- The newest backup is never deleted. Pre-deploy backups are kept alongside scheduled ones.
- Healthcare retention/compliance may require longer archival — copy periodic dumps to secure
  offsite/object storage out-of-band (documented as an operational step; not automated here to avoid
  moving PHI through CI).

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
```bash
# On the VPS, with app env sourced:
cd <repo> && set -a && . ./.env && set +a
ls -1t /var/backups/hms/*.sql.gz | head             # pick the latest
bash scripts/db/restore.sh /var/backups/hms/<file>.sql.gz     # → scratch DB + validation
# When satisfied:  mysql -e "DROP DATABASE \`hospital_management_restore_check\`;"
```

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
- Schedule the restore **drill** and periodic **offsite** copy of dumps.
- Confirm the daily backup cron window in `db-backup.yml` matches your low-traffic hours.

## Future (out of scope for this phase)
Point-in-time recovery via binlog, cross-region backup replication, and automated offsite archival
— these need infrastructure/DR work explicitly excluded here.
