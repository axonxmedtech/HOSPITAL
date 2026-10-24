# 11 — STAFF LIFECYCLE JOURNEY

**Cases:** `TC-E2E-087` … `TC-E2E-092` · Tenants HOSPITAL_A, CLINIC_A, PHARMACY_A

From "this person does not exist" to "this person can never log in again" — and the proof that
their historical records survive intact.

## Lifecycle state table (repeat per role)

| #   | Action                                    | Login works?       | Appears in lists? | Old records | Evidence        |
| --- | ----------------------------------------- | ------------------ | ----------------- | ----------- | --------------- |
| 1   | create staff user                         | ✅                 | ✅                | —           | screenshot      |
| 2   | first login, forced/optional password set | ✅                 | ✅                | —           | screenshot      |
| 3   | does work (creates records)               | ✅                 | ✅                | created     | record ids ____ |
| 4   | admin changes their **role**              | **old token dead** | ✅                | intact      | screenshot      |
| 5   | admin resets their **password**           | **old token dead** | ✅                | intact      | screenshot      |
| 6   | deactivate                                | ❌                 | per design ____   | **intact**  | screenshot      |
| 7   | reactivate (if supported)                 | ✅                 | ✅                | intact      | screenshot      |

> `User.java` bumps **`tokenVersion`** on a **password or role change** (`@PreUpdate`), so every
> existing session for that user is revoked. That is the mechanism steps 4 and 5 test.

---

### TC-E2E-087 — ⭐ Create a staff member of every role and prove each can do exactly their job

`HOSPITAL_A · HOSPITAL_ADMIN · Staff · Critical`
**Steps:** `TC-HA-006` … `TC-HA-014` · `TC-PERM-001` … `TC-PERM-010`.
**E2E assertion:** create one `DOCTOR`, `RECEPTIONIST`, `PHARMACIST`, `NURSE`, `NURSE_INCHARGE`, `OT_INCHARGE`. Log in as **each**, land on the **correct dashboard**, and perform **one real task** from their own journey. Then attempt **one task belonging to another role** and confirm it is refused **in the API, not only hidden in the UI**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-088 — ⭐ Role change revokes the live session immediately

`HOSPITAL_A · ADM + staff · Security · Critical`
**Steps:** `TC-AUTH-020` · `TC-PERM-032` · `TC-HA-020`.
**E2E assertion:** with the staff member **logged in in another browser**, change their role. Their **next request** must fail with 401 and land them on `/login` — no waiting for the 12-hour expiry. After re-login the new role's dashboard and permissions apply, and the **old role's endpoints are now refused**. A still-working old token is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-089 — Password reset, forced change, and session hygiene

`HOSPITAL_A · ADM + staff · Security · Critical`
**Steps:** `TC-AUTH-019` · `TC-AUTH-012` · `TC-AUTH-014` · `TC-AUTH-028`.
**E2E assertion:** an admin password reset kills the user's live sessions; the **old password is refused** and the new one works. Confirm the JWT lives in **`sessionStorage`** and is therefore **tab-isolated** — a second tab is a separate session, and closing the tab ends it. Logging out in one tab must not leave a usable token behind in that tab. **No password or token may appear in any response body or log line you can see.**
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-090 — Deactivation is a lock, not a delete

`HOSPITAL_A · ADM · Staff · Critical`
**Steps:** `TC-HA-022` · `TC-HA-023` · `TC-HA-024`.
**E2E assertion:** deactivate a doctor who has OPD cases, prescriptions and bills. They can **no longer log in**; their **existing records remain readable, printable and correctly attributed to them**; historical reports still count their work. Record whether they remain selectable for **new** appointments — if a deactivated doctor can still be booked, that is **High**. Reactivate and confirm login works again with no data loss.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-091 — Duplicate identity and self-harm guards

`HOSPITAL_A · ADM · Staff · High`
**Steps:** `TC-HA-016` · `TC-HA-017` · `TC-HA-025` · `TC-HA-026`.
**E2E assertion:** a second user with the **same email** is refused (state whether the scope is global or per-tenant — record it, do not assume); an admin **cannot deactivate or demote themselves** into locking the tenant out; the **last remaining admin** cannot be removed. If any of these succeeds and locks the tenant out, that is **Critical**.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____

### TC-E2E-092 — Staff lifecycle in clinic and pharmacy, including single-doctor mode

`CLINIC_A + PHARMACY_A · ADM · Staff · High`
**Steps:** `TC-CA-011` · `TC-PA-008` · `TC-MODE-001` … `TC-MODE-009` · `TC-ISO-012`.
**E2E assertion:** `DOCTOR` and `RECEPTIONIST` are **not supported staff roles on a PHARMACY tenant** — confirm they cannot be created, or if they can, that they are unusable (record which, as `NEEDS_PRODUCT_CONFIRMATION`). In CLINIC_A enable **single-doctor mode** (`isSingleDoctor` on the admin): the admin also acts as the sole doctor, consultations auto-assign to them, and **turning the flag off restores the normal doctor selector without orphaning the cases created while it was on**. Finally confirm no tenant can see or edit another tenant's staff.
**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · Actual: ____ · Bug: ____ · Tester: ____ · Date: ____
