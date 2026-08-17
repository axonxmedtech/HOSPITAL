# Defects found during review

A running log of bugs caught before they shipped. Kept because the _pattern_ is more useful than
any single entry: most of these were found by executing a plan against real code, not by re-reading
the plan, and several were invisible to the test suite as written.

**Severity:** 🔴 data loss / corruption / security · 🟠 real bug · 🟡 quality, drift, or usability

---

## Legacy patient import

| #   | Sev | Defect                                                                                                                                                           | How it would have shown up                                                                                |
| --- | --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| 1   | 🔴  | `DateTimeFormatter` used the default SMART resolver, which **silently clamps** out-of-range days. `31-02-2020` became `2020-02-29`, `31-04-2020` became the 30th | A wrong **date of birth** written into a patient record with no error. DOB drives identity matching       |
| 2   | 🔴  | `\| tee` masked the SSH exit code in the backup gate (`rc=$?` reads tee's status; no `pipefail`)                                                                 | A failed pre-deploy backup reported success and let migrations run against an unbacked database           |
| 3   | 🔴  | Re-import overwrote corrections staff had made since the last run                                                                                                | Data silently regressed to legacy values, with no warning and no trace                                    |
| 4   | 🔴  | `setCustomFields` assigned unconditionally, so a file without the extra columns wrote `null`                                                                     | Re-uploading the corrected `errors.csv` erased every preserved field for the rows it touched              |
| 5   | 🔴  | Commit crash discarded accumulated row errors and left counts at zero                                                                                            | An import that had already written thousands of rows reported nothing happened, with no error report      |
| 6   | 🟠  | `ROW_NUMBER` is reserved in MySQL 8.0+                                                                                                                           | The migration failed on a real boot; caught only because it was run against a live database               |
| 7   | 🟠  | Dry-run test expected errors at rows `(2,3)`; correct answer was `(3,4)`                                                                                         | Every error would have pointed **one row above** the real problem — usually a valid row                   |
| 8   | 🟠  | JPQL used `o.patientId`, but `Opd` holds a `Patient` relation, not a `Long`                                                                                      | Invalid JPQL compiles fine and fails when Spring builds the repository — the **whole app** would not boot |
| 9   | 🟠  | Imported patients never got a `customId` (assigned post-save by `PatientService`, which the importer bypasses)                                                   | 8,000 patients with a blank patient number, shown in 9 places in reception's UI                           |
| 10  | 🟠  | `preview` and `commit` filtered the request param map differently                                                                                                | The dry-run would not have predicted the commit — the entire basis for trusting it                        |
| 11  | 🟠  | Hand-rolled JSON escaping iterated `char`, breaking on unpaired surrogates                                                                                       | A mis-encoded export produced invalid JSON in `custom_fields`                                             |
| 12  | 🟡  | Plan claimed hand-rolled accessors; 76 of 89 entities use Lombok                                                                                                 | 90 lines of boilerplate against house style                                                               |
| 13  | 🟡  | Plan had responses wrapped in `ApiResponse.ok(...)` where the endpoint returns bare objects                                                                      | Would have changed the response shape and broken the frontend                                             |
| 14  | 🟡  | Header synonyms too thin for the target market (no `uhid`, `ipd no`, `ph no`); no punctuation tolerance                                                          | Admins hand-mapping columns that should have auto-matched                                                 |
| 15  | 🟡  | Plan described the parser as streaming; it used DOM-based `XSSFWorkbook`                                                                                         | A comment asserting behaviour the code did not have                                                       |

---

## Wards, ICU and OT

| #   | Sev | Defect                                                                                                                                         | How it would have shown up                                                                                                                                              |
| --- | --- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 16  | 🔴  | `AppointmentService` reused an existing patient on a **phone match alone**                                                                     | Booking for a child on the family number attached the appointment — and every clinical note after it — to the **parent's record**. Pre-existing, unrelated to this work |
| 17  | 🔴  | No duplicate check existed on patient creation at all                                                                                          | The same patient could be created any number of times; importing thousands made it far likelier                                                                         |
| 18  | 🟠  | Ward type was invisible in the UI and **could not be changed after creation** — the edit payload omitted it                                    | A ward filed under the wrong type was undetectable and unfixable from any screen. Found because a General Ward showed under Intensive care                              |
| 19  | 🟠  | Two `WardModal` instances; only the edit one received `wardType`                                                                               | A ward created from the ICU screen was created as IPD, saved fine, then filtered out of the list the admin was looking at                                               |
| 20  | 🟠  | `WardsAndBeds` owns its ward list, which the dashboard's `loadData()` cannot reach                                                             | The list never refreshed after a save                                                                                                                                   |
| 21  | 🟠  | Hiding the bed-count field for theatres left no way to add a bed                                                                               | An OT ward with zero beds could never get one; beds come only from the ward's bed count                                                                                 |
| 22  | 🟠  | `handleAdd` uses the tab id as the modal type                                                                                                  | The Add button on the new ICU tab would have opened nothing and failed silently                                                                                         |
| 23  | 🟡  | The admin sidebar was built twice and had drifted — grouped on the dashboard, flat on the IPD screen, missing OT Theatres, nursing and presets | Navigation rearranged itself when a case was opened                                                                                                                     |

---

## Backups and infrastructure

| #   | Sev | Defect                                                                                                                                  | How it would have shown up                                                                                                                          |
| --- | --- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| 24  | 🔴  | `/var/backups` was root-owned; the `deploy` user could not create `hms/`                                                                | **Every scheduled backup had failed since it went live.** Zero database backups existed                                                             |
| 25  | 🔴  | `backend/.env` holds an **unquoted** JDBC URL containing `&`; `set -a; . ./backend/.env` backgrounds the assignment and leaves it empty | `backup.sh` fell back to a hardcoded database name and dumped the wrong schema. Spring reads the file with its own parser, so the app never noticed |
| 26  | 🟠  | `PRODUCTION_SSH_HOST` did not exist; the backup job read it anyway                                                                      | The scheduled backup failed its config gate before ever reaching the server                                                                         |
| 27  | 🟠  | `deploy-rollback.yml` had the same host bug                                                                                             | Rollback would have failed **at the moment it was needed**                                                                                          |
| 28  | 🟠  | `undici` 7.28.0 carried 5 advisories incl. CVSS 7.4                                                                                     | Blocked CI; transitive via `jsdom`, fixed by a lockfile bump                                                                                        |

---

## Patient document upload

| #   | Sev | Defect                                                                                                                       | How it would have shown up                                                                                                                                                 |
| --- | --- | ---------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 29  | 🔴  | `"."` passed every path check, then normalised to the hospital's own directory                                               | `delete(id, ".")` would have **deleted an entire hospital's document folder**                                                                                              |
| 30  | 🔴  | HEIC detection checked only `ftyp` at offset 4 — shared by MP4, MOV, M4A, 3GP and AVIF                                       | A video renamed `scan.heic` passed the one gate the validator exists to enforce, and would be stored and served back as a clinical image                                   |
| 31  | 🟠  | `InputStream.read(byte[])` is not guaranteed to fill the buffer; bound checks used `head.length`, not bytes actually read    | A short read compared real signature bytes against **zero padding**, rejecting valid uploads. **Structurally invisible to the test suite**, which used `MockMultipartFile` |
| 32  | 🟠  | `store()`/`read()` echoed `IOException` messages, which embed absolute on-disk paths                                         | Server filesystem layout shown to a receptionist. Same pattern fixed twice in one feature                                                                                  |
| 33  | 🟠  | A failed `Files.copy` left a truncated file at the final path; `store()` throws before returning, so no row is created       | An orphaned fragment of patient data referenced by nothing and removed by nothing — there is no cleanup job                                                                |
| 34  | 🟠  | Malformed `hms.documents.max-file-size` parsed lazily                                                                        | A typo passed startup and health checks, then failed **every upload** mid-shift                                                                                            |
| 35  | 🟡  | `patient_documents` omitted `ENGINE`/`CHARSET`/`COLLATE` that ~80 other tables specify                                       | On a server with a different `collation_server`, later joins hit "Illegal mix of collations"                                                                               |
| 36  | 🟡  | `stored_filename` had no uniqueness constraint despite being the only guarantee one patient's file is not served for another | A collision would alias two patients' documents silently instead of failing loudly                                                                                         |
| 37  | 🟡  | `restrictPermissions` ignored `setReadable`/`setWritable` return values, which are false-on-failure rather than throwing     | A filesystem refusing them left a patient's file world-readable, silently                                                                                                  |

---

## What actually found these

- **Executing the plan against real code and a real database**, not re-reading the plan. #6 and #24 only surfaced on a live run.
- **Two-stage review** — spec compliance first, then quality. #29, #31 and #33 all came from the quality stage after spec had already passed.
- **Refusing to edit a test to make it pass.** #1 and #7 were both found because a plan's test disagreed with the plan's own implementation and the implementer stopped instead of "fixing" the test.
- **Reviewers verifying empirically.** #31's reviewer built a JDK `Path` harness and _disproved_ two suspected issues while finding a real one.
- **Asking what a word meant.** #21 surfaced only because the report said "add the _bed_", which prompted checking how beds are actually created.

## The recurring shape

Three of the worst (#3, #18, #23) are the same failure: **one thing defined in two places**, drifting apart silently. When a defect looks like this, it is worth checking whether the duplicate is the real bug rather than the symptom.
