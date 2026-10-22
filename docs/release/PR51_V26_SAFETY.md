# V26 scoped safety approval — PR51

Original release412443e V26 SHA-256: 9de2191043ff6a1da3326ff5bb774ddfc54cb12b7e604e262f8b91f8adce0baf.

Policy correctly rejects conditional DROP COLUMN and DROP INDEX, even if a particular database takes DO0 branches. The affected objects are import_batches.active_marker and import_batches.uk_import_batch_active only. No patient/billing table, import business row, status, filename or source file is intentionally deleted/renumbered. An ordinary legacy active_marker column would lose its stored values and become derived from status; this is a real destructive schema conversion requiring approval, not a harmless comment change.

Read-only staging metadata on2026-10-22: V26/V27 NOT applied; V11–V25 successful. active_marker is tinyint VIRTUAL GENERATED with expected CASE status membership; unique index is(hospital_id,file_sha256,active_marker). No duplicate active imports or duplicate patient/billing custom-ID groups. Current staging takes no DROP branches. No production metadata was accessed, and no claim is made that V26 is unapplied in other environments.

Approved correction: strengthen the complete normalized expression comparison, preserve required unique-column ordering/mode/type, and add the real reviewed flyway:safety-ack reason to this unapplied staging candidate. Do not weaken the validator or grant a generic exemption. New file bytes change Flyway checksum; any environment already containing V26 must STOP for explicit compatibility review, never repair/history editing.

Preconditions before applying: staging only, writers stopped, fresh verified/quiesced backup plus schema definitions and row-preservation baselines, no failed/checksum-mismatched history, duplicate-active check passes before DDL, source and migration checksums match final artifact. Validate ordinary/stored/wrong/inverted-expression and wrong-index cases in disposable MySQL8; prove no business rows change and duplicates are rejected.

Failure/recovery: MySQLDDL autocommits; interruption can leave the index or column absent. Keep application stopped, preserve schema/history/diagnostics, do not rerun/repair blindly. Derived marker/index can be rebuilt from preserved status only with a separately reviewed recovery operation. An old ordinary column's former values require the preserved backup/schema snapshot. Never automatically restore a whole old database over newer records. Previous binary rollback needs schema compatibility; do not down-migrate or delete business data.

Release owner explicitly approved this scope in the Codex review session on2026-10-22: “Approve this scoped V26 correction”. The safety acknowledgment records that approval; it is not a generic validator exemption. No live migration was executed. Required disposable regression results and final migration hashes are recorded with the rebuilt release artifacts.

Canonical expression validation compares complete MySQL8 metadata forms, including charset introducers and escaped versus unescaped literal quotes. It does not erase content inside status literals. Index checks require full, ascending, nonfunctional columns with no prefix lengths.
