# 05 — API TEST TRACK

**Baseline:** `origin/staging` @ `aa143a7` · **Cases:** 12 (`TC-API-001` … `TC-API-012`)

> **This document is mandatory, not optional.**
>
> HMS has only 18 routes; roughly ninety screens are `activeTab` values with no URL of their own.
> That means **most authorization cannot be tested from the browser address bar**. A button you
> cannot see proves nothing about whether the server would refuse the request. This track is how
> you prove it.
>
> You do **not** need to be a developer to run it. Follow the steps literally.

---

## 0. Safety rules — read before anything else

1. **Only ever use the QA environment** (`http://localhost:8080`). Never point these commands at a
   staging or production host.
2. **Only ever use the synthetic accounts** from `02-TEST-DATA-SETUP.md`.
3. **Never paste a real JWT into a bug report, screenshot, chat or ticket.** Write
   `Authorization: Bearer <redacted>`.
4. A JWT is a **live credential** for 12 hours. Treat it like a password: don't email it, don't
   commit it, don't put it in a shared doc.
5. If a test unexpectedly **succeeds** in reading or changing another tenant's data, **stop
   testing that area immediately**, capture evidence, and raise a **Critical** bug. Do not keep
   poking — you may be modifying data other testers depend on.

---

## 1. The two things every API call needs

**The URL.** Always `http://localhost:8080` + the endpoint path, e.g.
`http://localhost:8080/hospital/patients`.

**The token.** Every endpoint except `/login`, `/platform/login`, `/api/public/health` and
`/actuator/health` requires a header:

```
Authorization: Bearer <your token>
```

---

## 2. Getting a token — Method A: log in via the API (recommended)

This is the cleanest way; it never touches the browser.

**curl** (macOS / Linux / Git Bash on Windows):

```bash
curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"email":"rec.hospa@qa.test","password":"QaPass#2026"}'
```

**PowerShell** (Windows):

```powershell
curl.exe -s -X POST http://localhost:8080/login `
  -H "Content-Type: application/json" `
  -d '{\"email\":\"rec.hospa@qa.test\",\"password\":\"QaPass#2026\"}'
```

The response is JSON like:

```json
{
  "token": "eyJhbGciOi...",
  "role": "RECEPTIONIST",
  "hospitalId": 12,
  "hospitalType": "HOSPITAL",
  "modules": ["OPD", "IPD", "..."]
}
```

Copy the `token` value (without the quotes). To make the rest of this document copy-pasteable,
save it in a shell variable:

```bash
TOKEN_A="eyJhbGciOi..."        # Hospital A receptionist
TOKEN_B="eyJhbGciOi..."        # Hospital B receptionist
TOKEN_SA="eyJhbGciOi..."       # Super Admin  (from POST /platform/login)
```

> Super Admin uses a **different** login path: `POST /platform/login`.

---

## 3. Getting a token — Method B: from the browser

Use this when you want the exact token the UI is using.

1. Log in normally at `http://localhost:5173`.
2. Press **F12** → **Application** tab → **Session Storage** → `http://localhost:5173`.
3. Find the token entry and copy its value.

**Or** capture it from a live request:

1. **F12** → **Network** tab.
2. Click any sidebar tab in the app to trigger a request.
3. Click the request in the list → **Headers** → **Request Headers** → copy the value after
   `Authorization: Bearer `.

---

## 4. Reading a request in DevTools (do this once, it pays for itself)

With **F12 → Network** open, click any screen in the app. For the request you care about:

| DevTools panel                                | What you learn                          | Why you need it                    |
| --------------------------------------------- | --------------------------------------- | ---------------------------------- |
| **Headers → General → Request URL**           | the exact endpoint                      | so you can replay it               |
| **Headers → General → Request Method**        | GET / POST / PUT / DELETE               | the verb to use                    |
| **Headers → General → Status Code**           | 200 / 400 / 401 / 403 / 404 / 409 / 500 | the pass/fail signal               |
| **Headers → Request Headers → Authorization** | the token being used                    | to copy or swap                    |
| **Payload** (or **Request** tab)              | the JSON the UI sent                    | the template for your own call     |
| **Response**                                  | the JSON the server returned            | **where you look for leaked data** |

**Shortcut:** right-click the request → **Copy → Copy as cURL**. That gives you a ready-made
command. Replace the token to test as a different user. _(Redact it before pasting anywhere.)_

---

## 5. Using Postman instead

1. **New → HTTP Request.**
2. Set the method and URL.
3. **Authorization** tab → Type **Bearer Token** → paste the token.
4. For POST/PUT: **Body** → **raw** → **JSON**.
5. **Send.** Read the status code (top right of the response pane) and the response body.

Create one Postman **Environment** per tenant (`QA-HOSP-A`, `QA-HOSP-B`, …) with a `token`
variable, so you can switch identity from a dropdown instead of re-pasting.

---

## 6. Reusable request templates

Replace `{TOKEN}`, `{ID}` and `{PUBLIC_ID}` with your captured values.

### GET a collection

```bash
curl -i -X GET "http://localhost:8080/hospital/patients" \
  -H "Authorization: Bearer {TOKEN}"
```

### GET one record by **numeric id**

```bash
curl -i -X GET "http://localhost:8080/hospital/patients/{ID}" \
  -H "Authorization: Bearer {TOKEN}"
```

### GET one record by **publicId**

```bash
curl -i -X GET "http://localhost:8080/hospital/patients/{PUBLIC_ID}" \
  -H "Authorization: Bearer {TOKEN}"
```

### POST (create)

```bash
curl -i -X POST "http://localhost:8080/hospital/patients" \
  -H "Authorization: Bearer {TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"API Test Patient","phone":"9900099999","gender":"Male","dateOfBirth":"1990-01-01"}'
```

### PUT (update)

```bash
curl -i -X PUT "http://localhost:8080/hospital/patients/{ID}" \
  -H "Authorization: Bearer {TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"name":"API Test Patient EDITED","phone":"9900099999","gender":"Male","dateOfBirth":"1990-01-01"}'
```

### DELETE (soft delete)

```bash
curl -i -X DELETE "http://localhost:8080/hospital/patients/{PUBLIC_ID}?reason=QA%20test" \
  -H "Authorization: Bearer {TOKEN}"
```

> `-i` prints the status line and headers. **Always use `-i`** — the status code is usually the
> whole answer.

---

## 7. What each status code means here

| Status  | Meaning in HMS                                                                       | Typical verdict                                              |
| ------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------ |
| **200** | request succeeded                                                                    | expected for your _own_ tenant                               |
| **400** | validation failure (`IllegalArgumentException`)                                      | expected for bad input                                       |
| **401** | not authenticated — missing/expired/tampered/revoked token                           | expected without a token                                     |
| **403** | authenticated but **not authorised** (wrong role, wrong tenant type, missing module) | **the correct answer for a denied action**                   |
| **404** | not found — _including_ another tenant's record, which must look absent              | **the correct answer for a cross-tenant fetch**              |
| **409** | conflict — e.g. duplicate phone, bed already taken                                   | expected in those workflows                                  |
| **500** | server error                                                                         | **almost always a bug.** Capture the backend console output. |

> **Key rule for isolation tests:** a cross-tenant fetch should return **403 or 404**. Either is
> acceptable. What is **never** acceptable is **200 with the other tenant's data**.

---

## 8. Recording evidence

For every API case record:

```
Endpoint      : GET /hospital/patients/41
Method        : GET
Auth used     : HOSPITAL_B receptionist token  (Bearer <redacted>)
Status         : 404
Response body : {"success":false,"code":"RESOURCE_NOT_FOUND","error":"Patient not found"}
Leak check     : no name / no phone / no DOB / no address present  ✔
```

---

# TEST CASES

### TC-API-001 — Obtain a tenant token and call an authenticated endpoint

| Tenant     | Role         | Module         | Priority | Endpoint                       | Method    | Auth             | Expected status  |
| ---------- | ------------ | -------------- | -------- | ------------------------------ | --------- | ---------------- | ---------------- |
| HOSPITAL_A | RECEPTIONIST | API foundation | **High** | `/login`, `/hospital/patients` | POST, GET | none → own token | **200**, **200** |

**Steps**

1. Run the `POST /login` command from §2 with `rec.hospa@qa.test`.
2. Confirm the response contains `token`, `role`, `hospitalId`, `hospitalType`, `modules`.
3. Save the token as `TOKEN_A`.
4. Run the GET-collection template against `/hospital/patients`.

**Expected**

- Login **200**; `role` is `RECEPTIONIST`, `hospitalType` is `HOSPITAL`.
- Patients call **200**, returning only HOSPITAL_A patients.
- **Information disclosure:** the login response must **not** contain a password or password hash.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-002 — Obtain a Super Admin token

| Tenant   | Role        | Module         | Priority | Endpoint                                 | Method    | Auth            | Expected status  |
| -------- | ----------- | -------------- | -------- | ---------------------------------------- | --------- | --------------- | ---------------- |
| PLATFORM | SUPER_ADMIN | API foundation | **High** | `/platform/login`, `/platform/hospitals` | POST, GET | none → SA token | **200**, **200** |

**Steps**

1. `POST /platform/login` with the Super Admin credentials.
2. Save as `TOKEN_SA`.
3. `GET /platform/hospitals` with `TOKEN_SA`.

**Expected**

- Both **200**. The tenant list includes all nine QA tenants.
- **Information disclosure:** the tenant list may show names, types, plans and status. It must **not** contain tenant _patient_ data or admin password hashes.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-003 — Super Admin token is refused on tenant endpoints

| Tenant | Role        | Module        | Priority     | Endpoint             | Method | Auth       | Expected status |
| ------ | ----------- | ------------- | ------------ | -------------------- | ------ | ---------- | --------------- |
| —      | SUPER_ADMIN | Authorization | **Critical** | `/hospital/patients` | GET    | `TOKEN_SA` | **403**         |

**Steps**

1. `GET /hospital/patients` with `TOKEN_SA`.
2. Repeat for `/clinic/patients`, `/pharmacy/sales`, `/hospital/billing`.

**Expected**

- **403** for all four (`SecurityConfig:102-105` excludes `SUPER_ADMIN` from those namespaces).
- **Information disclosure: none.** The platform owner must not be able to read tenant clinical data through the API.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-004 — Tenant token is refused on platform endpoints

| Tenant     | Role           | Module        | Priority     | Endpoint              | Method | Auth                 | Expected status |
| ---------- | -------------- | ------------- | ------------ | --------------------- | ------ | -------------------- | --------------- |
| HOSPITAL_A | HOSPITAL_ADMIN | Authorization | **Critical** | `/platform/hospitals` | GET    | hospital admin token | **403**         |

**Steps**

1. `GET /platform/hospitals` with a HOSPITAL_A **admin** token.
2. Repeat for `/platform/plans`, `/platform/users`, `/platform/audit-logs`.
3. Try `POST /platform/hospitals` with a valid-looking creation body.

**Expected**

- **403** every time. No tenant list is disclosed; no tenant is created.
- A tenant admin must never learn that other tenants exist.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-005 — The UI hides a screen; prove the API also refuses it

| Tenant     | Role         | Module        | Priority     | Endpoint            | Method | Auth               | Expected status |
| ---------- | ------------ | ------------- | ------------ | ------------------- | ------ | ------------------ | --------------- |
| HOSPITAL_A | RECEPTIONIST | Authorization | **Critical** | `/hospital/doctors` | POST   | receptionist token | **403**         |

**Why:** `DoctorController:43-44` restricts creation to `HOSPITAL_ADMIN`. The Receptionist UI has
no "Add Doctor" button — this proves the server agrees.

**Steps**

1. Log in as admin, open **Doctors → Add**, and capture the exact `POST /hospital/doctors` payload from DevTools (§4).
2. Replay that same POST using `TOKEN_A` (the **receptionist** token).
3. Log back in as admin and check the doctor list.

**Expected**

- Step 2: **403**.
- Step 3: **no new doctor exists**. The hidden button was backed by a real server check.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-006 — ⚠️ Non-admin access to tenant **settings** endpoints

| Tenant     | Role                            | Module   | Priority     | Endpoint               | Method   | Auth            | Expected status |
| ---------- | ------------------------------- | -------- | ------------ | ---------------------- | -------- | --------------- | --------------- |
| HOSPITAL_A | RECEPTIONIST, NURSE, PHARMACIST | Settings | **Critical** | `/hospital/settings/*` | GET, PUT | non-admin token | see below       |

> **Context — this is a suspected defect, not a confirmed one.** `HospitalAuthController` carries
> **no `@PreAuthorize`** on ten settings endpoints, so authorization falls back to the
> `SecurityConfig` URL rule, which admits **all seven** tenant roles. Classified
> `IMPLEMENTATION_DRIFT / POTENTIAL_AUTHORIZATION_DEFECT` in
> `status/IMPLEMENTATION-STATUS.md`. **This case determines which it is.**

**Steps** — using the **receptionist** token `TOKEN_A`:

1. `GET  http://localhost:8080/hospital/settings/fees`
2. `GET  http://localhost:8080/hospital/settings/operations`
3. `GET  http://localhost:8080/hospital/subscription`
4. `PUT  http://localhost:8080/hospital/settings/fees` with the body the **admin** UI sends (capture it first from the admin's Fees screen), changing a fee value.
5. `PUT  http://localhost:8080/hospital/settings/nurse-login` toggling the setting.
6. Log in as `admin.hospa@qa.test` and check Settings → Fees and Settings → Operations.
7. Repeat steps 4–5 with the **nurse** and **pharmacist** tokens.

**Expected (product intent)**

- All of 1–5 return **403**. Settings are administrative.

**If instead they return 200 and step 6 shows the values changed:** raise a **Critical** bug —
a receptionist, nurse or pharmacist can silently reconfigure hospital fees, operations, barcode,
nurse-login mode, OT-incharge mode and read the subscription. Reference this case ID and
`status/IMPLEMENTATION-STATUS.md` § _Authorization drift_.

**Restore:** if any value changed, set it back as admin before continuing.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-007 — ⚠️ Prescription PDF download without a role check

| Tenant     | Role              | Module             | Priority | Endpoint                                             | Method | Auth               | Expected status |
| ---------- | ----------------- | ------------------ | -------- | ---------------------------------------------------- | ------ | ------------------ | --------------- |
| HOSPITAL_A | NURSE, PHARMACIST | Clinical documents | **High** | `/hospital/doctors/prescription/{appointmentId}/pdf` | GET    | non-clinical token | see below       |

> **Context:** `DoctorController` lines 228 and 276 (the two prescription PDF endpoints) carry no
> `@PreAuthorize`, unlike every neighbouring method. Suspected drift.

**Steps**

1. As `doc1.hospa@qa.test`, complete a consultation for P1 and note the appointment id (and the OPD id).
2. With the **pharmacist** token: `GET /hospital/doctors/prescription/{appointmentId}/pdf`.
3. With the **nurse** token: same.
4. With the **HOSPITAL_B receptionist** token: same id.

**Expected**

- Steps 2–3: a **deliberate product decision** is required — a pharmacist arguably _should_ read a prescription. Record the actual status and mark `NEEDS_PRODUCT_CONFIRMATION` if it is 200.
- **Step 4 must be 403 or 404.** A different tenant downloading a prescription PDF is a **Critical** cross-tenant leak regardless of role.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-008 — Nurse role is refused on the clinic and pharmacy namespaces

| Tenant     | Role  | Module        | Priority | Endpoint                                 | Method | Auth        | Expected status |
| ---------- | ----- | ------------- | -------- | ---------------------------------------- | ------ | ----------- | --------------- |
| HOSPITAL_A | NURSE | Authorization | **High** | `/clinic/patients`, `/pharmacy/patients` | GET    | nurse token | **403**         |

**Why:** `SecurityConfig:104-105` deliberately omits `NURSE`, `NURSE_INCHARGE` and `OT_INCHARGE` from `/clinic/**` and `/pharmacy/**`.

**Steps**

1. With the nurse token: `GET /clinic/patients`, then `GET /pharmacy/patients`.
2. Repeat with the nurse-incharge token and the OT-incharge token.

**Expected** — **403** for all six calls.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-009 — ⚠️ Clinic reaching IPD endpoints (unsupported)

| Tenant   | Role                         | Module | Priority | Endpoint      | Method    | Auth         | Expected status |
| -------- | ---------------------------- | ------ | -------- | ------------- | --------- | ------------ | --------------- |
| CLINIC_A | HOSPITAL_ADMIN, RECEPTIONIST | IPD    | **High** | `/clinic/ipd` | GET, POST | clinic token | see below       |

> **Product decision: IPD is NOT supported for Clinic.** However `IpdAdmissionController` is
> aliased onto `/clinic/ipd` with **13 endpoints carrying neither `@TenantType` nor
> `@RequireModule`**. Nothing currently blocks it.

**Steps**

1. Clinic admin token: `GET http://localhost:8080/clinic/ipd`
2. Clinic receptionist token: same.
3. Attempt a create: `POST /clinic/ipd` with an admission body captured from HOSPITAL_A.

**Expected (product intent)** — **403** on all three.

**If any returns 200:** record `IMPLEMENTATION_DRIFT` and raise a **High** bug — a clinic can
operate an unsold, unsupported module. Note whether step 3 actually **created** a record; if so
raise it as **Critical** (unsupported data being written) and delete the record.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-010 — ⚠️ Pharmacy reaching OPD / IPD / Beds / Wards (unsupported)

| Tenant     | Role           | Module             | Priority | Endpoint                                                              | Method | Auth           | Expected status |
| ---------- | -------------- | ------------------ | -------- | --------------------------------------------------------------------- | ------ | -------------- | --------------- |
| PHARMACY_A | HOSPITAL_ADMIN | OPD/IPD/Wards/Beds | **High** | `/pharmacy/opd`, `/pharmacy/ipd`, `/pharmacy/beds`, `/pharmacy/wards` | GET    | pharmacy token | see below       |

> **Product decision: OPD, IPD, Beds and Wards are NOT supported for standalone Pharmacy.**
> Verified in code: 11 `/pharmacy/opd`, 13 `/pharmacy/ipd`, 5 `/pharmacy/beds`, 7 `/pharmacy/wards`
> endpoints exist, **none** carrying `@TenantType` or `@RequireModule`.

**Steps** — with the PHARMACY_A admin token, GET each of the four paths and record the status.

**Expected (product intent)** — **403** for all four.

**If 200:** `IMPLEMENTATION_DRIFT`, **High** bug. Note especially whether the response contains any
**data** (rather than an empty list) — data would raise it to **Critical**.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-011 — ⚠️ Doctor / Receptionist on a standalone Pharmacy tenant

| Tenant     | Role                 | Module        | Priority | Endpoint       | Method | Auth        | Expected status |
| ---------- | -------------------- | ------------- | -------- | -------------- | ------ | ----------- | --------------- |
| PHARMACY_A | DOCTOR, RECEPTIONIST | Authorization | **High** | `/pharmacy/**` | GET    | those roles | see below       |

> **Product decision: DOCTOR and RECEPTIONIST are NOT supported operational roles on a standalone
> Pharmacy tenant.** But `SecurityConfig:104-105` admits both to `/pharmacy/**`.

**Steps**

1. As `admin.pharma@qa.test`, try to create a DOCTOR and a RECEPTIONIST.
   - If the UI **does not offer** these roles, record that and mark steps 2–3 **N/A**.
   - If it does, create them and note this as the first finding.
2. Log in as the created doctor. Record the landing URL.
3. With that token: `GET /pharmacy/patients`, `GET /pharmacy/appointments`.

**Expected (product intent)** — the roles should not be creatable in a pharmacy tenant at all.

**Observed behaviour to capture:** `LandingRedirect` sends any DOCTOR to `/hospital/doctor`
regardless of tenant type — so a pharmacy doctor would land on a **hospital** URL. Record this
verbatim; it is `IMPLEMENTATION_DRIFT`.

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______

---

### TC-API-012 — Malformed and hostile input is rejected cleanly

| Tenant     | Role         | Module           | Priority | Endpoint             | Method    | Auth      | Expected status      |
| ---------- | ------------ | ---------------- | -------- | -------------------- | --------- | --------- | -------------------- |
| HOSPITAL_A | RECEPTIONIST | Input validation | **High** | `/hospital/patients` | GET, POST | own token | 400 / 404, never 500 |

**Steps** — with `TOKEN_A`:

1. `GET /hospital/patients/abc` (non-numeric id)
2. `GET /hospital/patients/-1`
3. `GET /hospital/patients/99999999`
4. `POST /hospital/patients` with `{}` (empty body)
5. `POST /hospital/patients` with `{"name":"<script>alert(1)</script>","phone":"9900012345","gender":"Male","dateOfBirth":"1990-01-01"}`
6. `POST /hospital/patients` with `{"name":"Test' OR '1'='1","phone":"9900012346","gender":"Male","dateOfBirth":"1990-01-01"}`
7. If 5 or 6 created a patient, open the Patients list in the UI.

**Expected**

- 1–3: **400** or **404**. Never 500.
- 4: **400** with field-level messages.
- 5–6: either rejected by validation, or stored **as literal text**. When displayed in the UI the
  script must render as **text**, never execute (no alert box).
- **No response may contain a Java stack trace, class name, SQL fragment, or the database name.**

**Result:** `[ ] PASS [ ] FAIL [ ] BLOCKED [ ] N/A` · **Actual:** ______ · **Bug:** ______ · **Tester:** ______ · **Date:** ______
