# 03 — GLOBAL AUTHENTICATION & SECURITY

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** 28 (`TC-AUTH-001` … `TC-AUTH-028`)

## Facts this section is built on (verified in code)

| Fact                                                                                                       | Evidence                                                                                                   |
| ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Token lifetime is **12 hours**                                                                             | `application.properties:42` → `jwt.expiration=${JWT_EXPIRATION:43200000}` (43,200,000 ms)                  |
| Token is stored in **`sessionStorage`**                                                                    | Axios interceptor, `services/apiService.js` — tab-isolated                                                 |
| A **401 on any call** clears the token and redirects to login                                              | Axios response interceptor                                                                                 |
| Login endpoints: `POST /login` (tenants), `POST /platform/login` (Super Admin)                             | `HospitalAuthController:41`, `PlatformAuthController:21`                                                   |
| **Changing a password or a role invalidates that user's existing sessions**                                | `User.java:178-192` — `@PreUpdate` bumps `tokenVersion`; `JwtAuthenticationFilter` rejects a stale version |
| `/platform/**` is `SUPER_ADMIN` only; `/hospital/**` excludes `SUPER_ADMIN`                                | `SecurityConfig:92-105`                                                                                    |
| Unauthenticated requests get **401**, not 403                                                              | `SecurityConfig` custom `authenticationEntryPoint`                                                         |
| Public endpoints: `/login`, `/platform/login`, `/api/public/health`, `/actuator/health*`, `/actuator/info` | `SecurityConfig:82-89`                                                                                     |

---

### TC-AUTH-001 — Hospital user logs in successfully

| Tenant     | Role         | Module         | Priority     | Screen            |
| ---------- | ------------ | -------------- | ------------ | ----------------- |
| HOSPITAL_A | RECEPTIONIST | Authentication | **Critical** | `/login/hospital` |

**Preconditions:** `rec.hospa@qa.test` exists and is active.
**Test data:** `rec.hospa@qa.test` / `QaPass#2026`
**Navigation:** Open `http://localhost:5173/login/hospital`

**Steps**

1. Enter the email and password.
2. Click **Login**.
3. Open DevTools → Application → Session Storage → `http://localhost:5173`.

**Expected**

- Redirected to **`/hospital/receptionist`**.
- A success toast appears; the top bar shows the tenant name **QA Hospital A**.
- Session Storage contains a token entry. **Do not copy the value into any report.**
- DevTools → Network shows `POST /login` returned **200** with a body containing `token`, `role`, `hospitalId`, `hospitalType`, `modules`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-002 — Every role lands on its own dashboard

| Tenant     | Role | Module         | Priority | Screen            |
| ---------- | ---- | -------------- | -------- | ----------------- |
| HOSPITAL_A | all  | Authentication | **High** | `/login/hospital` |

**Preconditions:** all HOSPITAL_A staff from `02-TEST-DATA-SETUP.md` exist. _Separate Nurse Login_ is **ON** for HOSPITAL_A.
**Navigation:** `/login/hospital`, once per row.

**Steps** — log in as each account, record the landing URL, then log out.

| Account               | Expected landing URL       |
| --------------------- | -------------------------- |
| `admin.hospa@qa.test` | `/hospital/admin`          |
| `doc1.hospa@qa.test`  | `/hospital/doctor`         |
| `rec.hospa@qa.test`   | `/hospital/receptionist`   |
| `pharm.hospa@qa.test` | `/hospital/pharmacy`       |
| `nurse.hospa@qa.test` | `/hospital/nurse`          |
| `ni.hospa@qa.test`    | `/hospital/nurse-incharge` |
| `ot.hospa@qa.test`    | `/hospital/ot-incharge`    |

**Expected**

- Each lands exactly as tabled. No role is bounced back to the login page.
- Each dashboard's sidebar shows only that role's tabs.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-003 — Clinic user lands on the Hospital URL namespace (expected, not a bug)

| Tenant   | Role           | Module         | Priority   | Screen          |
| -------- | -------------- | -------------- | ---------- | --------------- |
| CLINIC_A | HOSPITAL_ADMIN | Authentication | **Medium** | `/login/clinic` |

**Steps**

1. Open `/login/clinic`.
2. Log in as `admin.clina@qa.test`.

**Expected**

- Lands on **`/hospital/admin`** — the URL says _hospital_ for a clinic user.
- **This is correct by design.** Clinic shares the Hospital frontend; only the API namespace (`/clinic/**`) differs.
- The tenant name in the header reads **QA Clinic A**, and clinic-only modules apply (no IPD, OT, Nursing, ICU tabs).

**Notes:** Do **not** raise a bug for the URL. Do raise one if the header shows the wrong tenant.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-004 — Pharmacy user lands on the Pharmacy URL namespace

| Tenant     | Role                        | Module         | Priority | Screen            |
| ---------- | --------------------------- | -------------- | -------- | ----------------- |
| PHARMACY_A | HOSPITAL_ADMIN / PHARMACIST | Authentication | **High** | `/login/pharmacy` |

**Steps**

1. Log in at `/login/pharmacy` as `admin.pharma@qa.test`. Record the URL. Log out.
2. Log in as `pharm.pharma@qa.test`. Record the URL.

**Expected**

- Admin → **`/pharmacy/admin`** (not `/hospital/admin`).
- Pharmacist → **`/pharmacy/pharmacy`** (not `/hospital/pharmacy`).
- Both render the same components as the hospital equivalents, under a pharmacy-branded URL.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-005 — Super Admin cannot log in at a tenant portal

| Tenant | Role        | Module         | Priority     | Screen            |
| ------ | ----------- | -------------- | ------------ | ----------------- |
| —      | SUPER_ADMIN | Authentication | **Critical** | `/login/hospital` |

**Steps**

1. Open `/login/hospital`.
2. Enter the Super Admin email and password. Click Login.

**Expected**

- Login is **rejected**. An error message appears.
- No token is written to Session Storage.
- Even if a token were issued, `SecurityConfig:102` excludes `SUPER_ADMIN` from `/hospital/**`, so every subsequent call would 403.

**Negative/security note:** if this _succeeds_, raise a **Critical** bug — a platform account inside a tenant session is a privilege-boundary failure.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-006 — Tenant user cannot log in at the platform portal

| Tenant     | Role           | Module         | Priority     | Screen            |
| ---------- | -------------- | -------------- | ------------ | ----------------- |
| HOSPITAL_A | HOSPITAL_ADMIN | Authentication | **Critical** | `/platform/login` |

**Steps**

1. Open `/platform/login`.
2. Enter `admin.hospa@qa.test` / `QaPass#2026`. Click Login.

**Expected**

- Rejected. No platform session is created. No tenant data or tenant list is shown.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-007 — Wrong password is rejected

| Tenant     | Role         | Module         | Priority     |
| ---------- | ------------ | -------------- | ------------ |
| HOSPITAL_A | RECEPTIONIST | Authentication | **Critical** |

**Steps**

1. Log in with `rec.hospa@qa.test` and password `WrongPass#1`.

**Expected**

- Error shown; no redirect; Session Storage still has no token.
- The message must **not** reveal whether the email exists (no "user not found" vs "wrong password" distinction).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-008 — Unknown email is rejected identically

| Tenant     | Role | Module         | Priority |
| ---------- | ---- | -------------- | -------- |
| HOSPITAL_A | —    | Authentication | **High** |

**Steps**

1. Log in with `doesnotexist@qa.test` / `QaPass#2026`.
2. Compare the error text **and** the response time against `TC-AUTH-007`.

**Expected**

- Same error message as a wrong password (no user enumeration).
- No obviously different response time.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-009 — Empty-field validation

| Tenant     | Role | Module         | Priority   |
| ---------- | ---- | -------------- | ---------- |
| HOSPITAL_A | —    | Authentication | **Medium** |

**Steps**

1. Click **Login** with both fields empty.
2. Enter only an email, click Login.
3. Enter only a password, click Login.

**Expected**

- Client-side validation blocks each attempt with a field-level message.
- **No network request is sent** (check DevTools → Network stays empty).

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-010 — Deactivated user cannot log in

| Tenant     | Role   | Module         | Priority     |
| ---------- | ------ | -------------- | ------------ |
| HOSPITAL_A | DOCTOR | Authentication | **Critical** |

**Preconditions:** `docoff.hospa@qa.test` was created and then deactivated.

**Steps**

1. Attempt login as `docoff.hospa@qa.test`.

**Expected**

- Rejected. No token issued.
- The doctor's historical records (consultations, prescriptions) remain visible to others — deactivation must not delete data.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-011 — Deactivated tenant blocks all its users

| Tenant     | Role | Module                            | Priority     |
| ---------- | ---- | --------------------------------- | ------------ |
| HOSPITAL_B | all  | Authentication / Tenant lifecycle | **Critical** |

**Steps**

1. As Super Admin, set **QA Hospital B** to _Inactive_.
2. In a **separate browser tab**, attempt login as `admin.hospb@qa.test`, then as `rec.hospb@qa.test`.
3. Re-activate QA Hospital B and log in again.

**Expected**

- Step 2: **both** logins rejected while the tenant is inactive.
- Step 3: login succeeds again and **all previously created HOSPITAL_B data is still present** (patients, appointments, OPD). Deactivation must never destroy data.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-012 — Logout clears the session

| Tenant     | Role         | Module         | Priority |
| ---------- | ------------ | -------------- | -------- |
| HOSPITAL_A | RECEPTIONIST | Authentication | **High** |

**Steps**

1. Log in as `rec.hospa@qa.test`.
2. Click the profile menu → **Logout**.
3. Inspect Session Storage.
4. Press the browser **Back** button.

**Expected**

- Redirected to `/login/hospital`.
- Session Storage token is **gone**.
- Back does **not** restore the dashboard — `ProtectedRoute` bounces to login.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-013 — Two tenants in two browser tabs simultaneously

| Tenant                  | Role         | Module         | Priority |
| ----------------------- | ------------ | -------------- | -------- |
| HOSPITAL_A + HOSPITAL_B | RECEPTIONIST | Authentication | **High** |

**Why this matters:** the token is in `sessionStorage`, so tabs do not share sessions. This is the
technique used throughout `cross-tenant/TENANT-ISOLATION.md`.

**Steps**

1. Tab 1: log in as `rec.hospa@qa.test`. Open **Patients**.
2. Tab 2 (**new tab**, same browser): log in as `rec.hospb@qa.test`. Open **Patients**.
3. Return to Tab 1 and refresh the patient list.

**Expected**

- Tab 1 shows **only** HOSPITAL_A patients (P1–P5, P7). Tab 2 shows **only** HOSPITAL_B patients (P6).
- Neither tab logs the other out.
- Tab 1 after refresh still shows HOSPITAL_A data — **not** HOSPITAL_B's.

**Failure here is a Critical cross-tenant leak.**

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-014 — Closing the tab ends the session

| Tenant     | Role         | Module         | Priority   |
| ---------- | ------------ | -------------- | ---------- |
| HOSPITAL_A | RECEPTIONIST | Authentication | **Medium** |

**Steps**

1. Log in, then close the tab (not the browser).
2. Open a new tab to `http://localhost:5173/hospital/receptionist`.

**Expected**

- Redirected to the login page. The session did not survive.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-015 — Refresh keeps you logged in but resets the active tab

| Tenant     | Role           | Module                      | Priority   |
| ---------- | -------------- | --------------------------- | ---------- |
| HOSPITAL_A | HOSPITAL_ADMIN | Authentication / Navigation | **Medium** |

**Steps**

1. Log in as `admin.hospa@qa.test`.
2. Click the **Billing** tab.
3. Press **F5**.

**Expected**

- You remain logged in.
- You land back on the dashboard's **default tab (Overview)**, not Billing.
- ⚠️ **This is expected behaviour**, because tab state is React state and not part of the URL. **Do not file a bug.** Record it once here.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-016 — Role route guard blocks a cross-role URL

| Tenant     | Role         | Module        | Priority     |
| ---------- | ------------ | ------------- | ------------ |
| HOSPITAL_A | RECEPTIONIST | Authorization | **Critical** |

**Steps**

1. Log in as `rec.hospa@qa.test`.
2. Type `http://localhost:5173/hospital/admin` into the address bar and press Enter.
3. Repeat for `/hospital/doctor`, `/hospital/nurse`, `/hospital/nurse-incharge`, `/hospital/ot-incharge`, `/platform/dashboard`.

**Expected**

- Every one redirects to `/` and then back to `/hospital/receptionist`. No admin or clinical screen renders, even briefly.

**Notes:** this proves the **UI** guard only. The matching API proof is `TC-PERM-001`…`TC-PERM-012`. A hidden screen is not proof.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-017 — Nurse cannot reach the platform dashboard

| Tenant     | Role  | Module        | Priority     |
| ---------- | ----- | ------------- | ------------ |
| HOSPITAL_A | NURSE | Authorization | **Critical** |

**Steps**

1. Log in as `nurse.hospa@qa.test`.
2. Navigate to `/platform/dashboard`.

**Expected** — redirected away; no tenant list, no platform data.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-018 — Expired token is rejected (12-hour lifetime)

| Tenant     | Role         | Module         | Priority | Endpoint             | Method | Auth        | Expected status |
| ---------- | ------------ | -------------- | -------- | -------------------- | ------ | ----------- | --------------- |
| HOSPITAL_A | RECEPTIONIST | Authentication | **High** | `/hospital/patients` | GET    | expired JWT | **401**         |

**Preconditions:** you can restart the backend with a short expiry.

**Steps**

1. Restart the backend with `JWT_EXPIRATION=60000` (60 seconds) in `backend/.env`.
2. Log in as `rec.hospa@qa.test`. Note the time.
3. Wait 70 seconds without clicking anything.
4. Click the **Patients** tab.
5. Restore `JWT_EXPIRATION` to `43200000` and restart.

**Expected**

- Step 4: the API returns **401**; the Axios interceptor clears the token and redirects to `/login/hospital`.
- The user is not left on a broken screen showing stale data.
- **Expected information disclosure:** none — the 401 body carries no patient data.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-019 — ⭐ Changing a user's password revokes their live session

| Tenant     | Role                    | Module         | Priority     | Endpoint             | Method | Auth          | Expected status |
| ---------- | ----------------------- | -------------- | ------------ | -------------------- | ------ | ------------- | --------------- |
| HOSPITAL_A | HOSPITAL_ADMIN → DOCTOR | Authentication | **Critical** | `/hospital/patients` | GET    | pre-reset JWT | **401**         |

**Why:** `User.java:178-192` bumps `tokenVersion` on any password change; the JWT filter rejects a token carrying an older version.

**Steps**

1. Tab 1: log in as `doc1.hospa@qa.test`. Leave it open on the Patients tab.
2. Tab 2: log in as `admin.hospa@qa.test` → **Doctors** → Dr Meera Kulkarni → **Reset Password**. Set `QaPass#2026New`.
3. Tab 1: click any tab to trigger a fresh API call.

**Expected**

- Tab 1's next request returns **401** and the user is redirected to login. The old session does **not** keep working.
- Logging in with the **old** password fails; the **new** password works.

**Failure here is a Critical security bug** — it means a compromised credential cannot be revoked.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-020 — Changing a user's role revokes their live session

| Tenant     | Role                   | Module         | Priority     | Expected status |
| ---------- | ---------------------- | -------------- | ------------ | --------------- |
| HOSPITAL_A | HOSPITAL_ADMIN → NURSE | Authentication | **Critical** | **401**         |

**Steps**

1. Tab 1: log in as `nurse.hospa@qa.test`.
2. Tab 2: as `ni.hospa@qa.test` or admin, **promote** that nurse to Nurse Incharge (Nurses → Promote).
3. Tab 1: click any tab.

**Expected**

- Tab 1's session is rejected (401 → login). The user must log in again to receive a token carrying the new role.
- After re-login the user lands on `/hospital/nurse-incharge`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-021 — Tampered JWT is rejected

| Tenant     | Role         | Module         | Priority     | Endpoint             | Method | Auth         | Expected status |
| ---------- | ------------ | -------------- | ------------ | -------------------- | ------ | ------------ | --------------- |
| HOSPITAL_A | RECEPTIONIST | Authentication | **Critical** | `/hospital/patients` | GET    | modified JWT | **401**         |

**Steps** (see `05-API-TEST-TRACK.md` §3 for how to capture a token)

1. Capture your own token.
2. Change **one character** in the middle section of the token.
3. Send `GET /hospital/patients` with the modified token.
4. Repeat with the signature (third) section altered.

**Expected**

- **401** both times.
- **Expected information disclosure:** the response contains **no** patient data and **no** stack trace or Java class name.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-022 — No token at all

| Tenant | Role | Module         | Priority     | Endpoint             | Method | Auth | Expected status |
| ------ | ---- | -------------- | ------------ | -------------------- | ------ | ---- | --------------- |
| —      | —    | Authentication | **Critical** | `/hospital/patients` | GET    | none | **401**         |

**Steps**

1. `curl -i http://localhost:8080/hospital/patients` with **no** `Authorization` header.
2. Repeat for `/platform/hospitals`, `/pharmacy/sales`, `/clinic/patients`.

**Expected**

- **401** for all four (not 403, not 500, not an HTML error page).
- Body is the canonical JSON error shape with an `error` field. No patient or tenant data.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-023 — Public endpoints need no token

| Tenant | Role | Module         | Priority   | Endpoint             | Method | Auth | Expected status |
| ------ | ---- | -------------- | ---------- | -------------------- | ------ | ---- | --------------- |
| —      | —    | Authentication | **Medium** | `/api/public/health` | GET    | none | **200**         |

**Steps**

1. `curl -i http://localhost:8080/api/public/health`
2. `curl -i http://localhost:8080/actuator/health`

**Expected**

- **200** for both.
- **Expected information disclosure:** status only. The body must not reveal the database URL, credentials, internal hostnames or version-specific stack details.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-024 — View and update own profile

| Tenant     | Role         | Module  | Priority   | Endpoint                    | Method   |
| ---------- | ------------ | ------- | ---------- | --------------------------- | -------- |
| HOSPITAL_A | RECEPTIONIST | Profile | **Medium** | `/auth/me`, `/auth/profile` | GET, PUT |

**Steps**

1. Log in as `rec.hospa@qa.test`. Open the profile screen.
2. Confirm name, email, role and tenant are correct.
3. Change the display name to `Priya S Salunke`. Save.
4. Log out, log back in.

**Expected**

- `GET /auth/me` returns this user only — never another user, never a list.
- The changed name persists across re-login.
- The role and tenant fields are **not editable** by the user themselves.

**Negative:** try to change your own `role` or `hospitalId` via the request body (see `05` §6). Expected: ignored or rejected — never applied.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-025 — Session-expiry redirect returns to the correct portal

| Tenant                | Role | Module         | Priority   |
| --------------------- | ---- | -------------- | ---------- |
| CLINIC_A / PHARMACY_A | any  | Authentication | **Medium** |

**Steps**

1. Log in at `/login/clinic` as `admin.clina@qa.test`.
2. Force a 401 (use the short-expiry method from `TC-AUTH-018`, or clear the token from Session Storage and click a tab).
3. Repeat starting from `/login/pharmacy` with `admin.pharma@qa.test`.

**Expected**

- The clinic session returns to **`/login/clinic`**, not `/login/hospital`.
- The pharmacy session returns to **`/login/pharmacy`**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-026 — Double-click on Login does not create two sessions

| Tenant     | Role         | Module         | Priority   |
| ---------- | ------------ | -------------- | ---------- |
| HOSPITAL_A | RECEPTIONIST | Authentication | **Medium** |

**Steps**

1. Enter valid credentials.
2. Double-click **Login** rapidly.
3. Watch DevTools → Network.

**Expected**

- The button disables after the first click, or at most one `POST /login` is sent.
- Exactly one redirect; no duplicate toasts; no console error.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-027 — Backend unavailable during login

| Tenant     | Role         | Module                          | Priority   |
| ---------- | ------------ | ------------------------------- | ---------- |
| HOSPITAL_A | RECEPTIONIST | Authentication / Error handling | **Medium** |

**Steps**

1. Stop the backend (`Ctrl+C` in the `mvn spring-boot:run` terminal).
2. Attempt to log in.
3. Restart the backend and retry.

**Expected**

- A readable error message ("unable to reach the server" or similar) — **not** a blank screen, not a raw `Network Error` object, not a React crash.
- After restart, login succeeds with no page reload required.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-AUTH-028 — Credentials never appear in logs, URLs or storage in clear text

| Tenant     | Role | Module   | Priority     |
| ---------- | ---- | -------- | ------------ |
| HOSPITAL_A | any  | Security | **Critical** |

**Steps**

1. Log in as `rec.hospa@qa.test` with DevTools → Network open, **Preserve log** ticked.
2. Inspect the `POST /login` **request**: confirm it is a POST **body**, not a query string.
3. Check the browser address bar — no email or password in the URL.
4. Inspect the backend console output for the same login.
5. Inspect Session Storage and Local Storage.

**Expected**

- Password appears only in the POST body, never in a URL, never in `sessionStorage`, never in the backend log.
- Backend log may record the **email** for an attempted login (it does, sanitised) but must **never** log the password or the issued token.

**Expected information disclosure:** none beyond the email in the server log.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______
