# 02 — TEST DATA SETUP

**Baseline:** `origin/staging` @ `aa143a7`

> **Every value in this document is synthetic.** Names are invented, phone numbers use the
> reserved-looking `99000xxxxx` range, emails use `@qa.test` (a non-routable TLD). **Never copy
> a real patient, staff member, phone number or email into a QA environment.**

Build this dataset **once** per environment reset, in the order given. Almost every test case in
the pack references these identifiers by name.

---

## 1. Naming convention

| Token                       | Meaning                                                          |
| --------------------------- | ---------------------------------------------------------------- |
| `HOSPITAL_A` / `HOSPITAL_B` | Two HOSPITAL tenants — B exists solely to prove isolation from A |
| `CLINIC_A` / `CLINIC_B`     | Two CLINIC tenants                                               |
| `PHARMACY_A` / `PHARMACY_B` | Two PHARMACY tenants                                             |

Password for **every** synthetic account: `QaPass#2026`
(the backend enforces a minimum length only; this value is deliberately identical everywhere so
testers never get blocked on credentials).

---

## 2. Step 1 — Plans (Super Admin)

A tenant **cannot be created without a plan** — `CreateHospitalRequest.planPublicId` is
`@NotBlank`. Create these first.

Login at `/platform/login` as the seeded Super Admin, open **Hospital → Plans**.

| Plan name         | Tenant type | Modules to tick                                                                                             | Purpose                      |
| ----------------- | ----------- | ----------------------------------------------------------------------------------------------------------- | ---------------------------- |
| `QA-HOSP-FULL`    | HOSPITAL    | OPD, IPD, APPOINTMENTS, BILLING, PHARMACY, MEDICAL_INVENTORY, HOSPITAL_INVENTORY, REPORTS, OT, NURSING, ICU | everything on                |
| `QA-HOSP-MINIMAL` | HOSPITAL    | OPD only                                                                                                    | module-gating negative tests |
| `QA-CLINIC-FULL`  | CLINIC      | OPD, APPOINTMENTS, BILLING, PHARMACY, MEDICAL_INVENTORY, REPORTS                                            | clinic's full sellable set   |
| `QA-PHARM-SINGLE` | PHARMACY    | SINGLE_PHARMACY                                                                                             | standard pharmacy            |
| `QA-PHARM-SOLO`   | PHARMACY    | SINGLE_PHARMACIST_ADMIN                                                                                     | dual-role special mode       |
| `QA-PHARM-MULTI`  | PHARMACY    | MULTI_PHARMACY                                                                                              | branch support               |

> The module checkboxes are **driven by the server** (`GET /platform/plans/capabilities`), so the
> list you see is filtered to what that tenant type may buy. If you see IPD offered on a CLINIC
> plan, that is a bug — raise it against `TC-MOD-007`.

---

## 3. Step 2 — Tenants (Super Admin)

Open the relevant tenant group → **Hospitals / Clinics / Pharmacies** → **Create**.

| Tenant             | Type     | Plan            | Admin name      | Admin email               | Single doctor? |
| ------------------ | -------- | --------------- | --------------- | ------------------------- | -------------- |
| `QA Hospital A`    | HOSPITAL | QA-HOSP-FULL    | Anita Deshpande | `admin.hospa@qa.test`     | No             |
| `QA Hospital B`    | HOSPITAL | QA-HOSP-FULL    | Rohit Bane      | `admin.hospb@qa.test`     | No             |
| `QA Hospital M`    | HOSPITAL | QA-HOSP-MINIMAL | Sneha Rane      | `admin.hospm@qa.test`     | No             |
| `QA Clinic A`      | CLINIC   | QA-CLINIC-FULL  | Vikram Shah     | `admin.clina@qa.test`     | No             |
| `QA Clinic B`      | CLINIC   | QA-CLINIC-FULL  | Pooja Nair      | `admin.clinb@qa.test`     | No             |
| `QA Clinic Solo`   | CLINIC   | QA-CLINIC-FULL  | Dr Kiran Joshi  | `admin.clinsolo@qa.test`  | **Yes**        |
| `QA Pharmacy A`    | PHARMACY | QA-PHARM-SINGLE | Manish Patil    | `admin.pharma@qa.test`    | No             |
| `QA Pharmacy B`    | PHARMACY | QA-PHARM-SINGLE | Kavita Iyer     | `admin.pharmb@qa.test`    | No             |
| `QA Pharmacy Solo` | PHARMACY | QA-PHARM-SOLO   | Sameer Kale     | `admin.pharmsolo@qa.test` | No             |

Billing period: **MONTHLY** for all.

**Record for each tenant:** the numeric `id` and the tenant `customId` shown in the list. You need
both for API tests.

---

## 4. Step 3 — Staff, per tenant

Log in as each tenant admin and create staff. **Create the same roles in HOSPITAL_A and
HOSPITAL_B** — the isolation tests need a mirror-image tenant.

### HOSPITAL_A and HOSPITAL_B (repeat identically, swapping `hospa`→`hospb`)

| Role                  | Screen (admin sidebar)            | Name                  | Email                  |
| --------------------- | --------------------------------- | --------------------- | ---------------------- |
| DOCTOR                | Doctors                           | Dr Meera Kulkarni     | `doc1.hospa@qa.test`   |
| DOCTOR                | Doctors                           | Dr Arjun Rao          | `doc2.hospa@qa.test`   |
| RECEPTIONIST          | Receptionists                     | Priya Salunke         | `rec.hospa@qa.test`    |
| PHARMACIST            | Pharmacists                       | Nilesh Gupta          | `pharm.hospa@qa.test`  |
| NURSE_INCHARGE        | Nurses (tick _Is Incharge_)       | Sister Latha Menon    | `ni.hospa@qa.test`     |
| NURSE                 | Nurses                            | Staff Nurse Reena Das | `nurse.hospa@qa.test`  |
| OT_INCHARGE           | OT Incharge                       | Sunil More            | `ot.hospa@qa.test`     |
| DOCTOR (**inactive**) | Doctors → create, then Deactivate | Dr Old Record         | `docoff.hospa@qa.test` |

> **Staff nurses may have no login.** Whether `NURSE` gets credentials depends on the
> _Separate Nurse Login_ setting (Settings → Operations, default **OFF**). Turn it **ON** for
> HOSPITAL_A so `nurse.hospa@qa.test` can log in; leave it **OFF** for HOSPITAL_B so you can test
> both modes. Record which tenant is in which mode.

### CLINIC_A / CLINIC_B

| Role         | Name              | Email                 |
| ------------ | ----------------- | --------------------- |
| DOCTOR       | Dr Sanjay Bhosale | `doc.clina@qa.test`   |
| RECEPTIONIST | Ashwini Kadam     | `rec.clina@qa.test`   |
| PHARMACIST   | Rahul Sawant      | `pharm.clina@qa.test` |

> **Do not attempt to create NURSE, NURSE_INCHARGE or OT_INCHARGE in a clinic.** Those roles are
> hospital-only. If the UI offers them, that is a finding — see `TC-PERM-021`.

### PHARMACY_A / PHARMACY_B

| Role       | Name          | Email                  |
| ---------- | ------------- | ---------------------- |
| PHARMACIST | Deepak Jadhav | `pharm.pharma@qa.test` |

> **Do not create DOCTOR or RECEPTIONIST in a pharmacy tenant** — not a supported operational
> role (product decision). If the UI allows it, record it against `TC-PERM-024`.

---

## 5. Step 4 — Clinical reference data (HOSPITAL_A and HOSPITAL_B)

### Wards & Beds — admin → **Wards & Beds**

| Ward                            | Incharge           | Beds                   |
| ------------------------------- | ------------------ | ---------------------- |
| `General Ward A`                | Sister Latha Menon | GA-01 … GA-05          |
| `ICU Ward`                      | Sister Latha Menon | ICU-01, ICU-02         |
| `OT-1` (name must contain "OT") | Sister Latha Menon | OT-BED-01 (single bed) |

Leave bed statuses at **available**. Later cases will drive them through
`available → occupied → cleaning → available`.

> A ward **must have an incharge** before reception can admit into it.

### Medicines — admin → **Pharmacy** / **Medicine Inventory**

| Medicine             | Batch   | Qty   | Expiry                                  |
| -------------------- | ------- | ----- | --------------------------------------- |
| `QA Paracetamol 500` | `QA-B1` | 100   | +24 months                              |
| `QA Amoxicillin 250` | `QA-B2` | 50    | +18 months                              |
| `QA Expiring Soon`   | `QA-B3` | 10    | **+20 days** (drives Expiry Management) |
| `QA Zero Stock`      | `QA-B4` | **0** | +12 months                              |

### Supplier — pharmacy dashboard → **Suppliers**

`QA Supplier One`, contact `9900000001`.

---

## 6. Step 5 — Patients ⭐ the important ones

Create these in **HOSPITAL_A** unless stated. The phone numbers are chosen deliberately; several
cases depend on the exact relationships.

| #   | Name           | DOB           | Phone        | Tenant         | Why it exists                                 |
| --- | -------------- | ------------- | ------------ | -------------- | --------------------------------------------- |
| P1  | Rahul Patil    | 1987-04-02    | `9900011111` | HOSPITAL_A     | **Parent** — adult baseline                   |
| P2  | Aarav Patil    | 2017-09-21    | `9900011111` | HOSPITAL_A     | **Child sharing the parent's phone** ⭐       |
| P3  | Sunita Patil   | 1960-02-02    | `9900011111` | HOSPITAL_A     | third family member on one number             |
| P4  | Meera Joshi    | 1995-05-05    | `9900022222` | HOSPITAL_A     | free number — used for phone-change tests     |
| P5  | Old Record     | 1970-07-07    | `9900033333` | HOSPITAL_A     | create then **deactivate** — inactive patient |
| P6  | Rahul Patil    | 1987-04-02    | `9900011111` | **HOSPITAL_B** | **same phone, different tenant** ⭐           |
| P7  | Walk In Test   | (leave blank) | `9900044444` | HOSPITAL_A     | created via **Appointment**, not registration |
| P8  | Sanjay Bhosale | 1980-01-01    | `9900055555` | CLINIC_A       | clinic patient                                |
| P9  | Sanjay Bhosale | 1980-01-01    | `9900055555` | CLINIC_B       | clinic cross-tenant twin                      |

### How to create P2 and P3 (this is the duplicate-phone workflow)

1. Create **P1** normally — the phone is free, no dialog appears.
2. Create **P2** with the _same_ phone `9900011111`.
   → A dialog appears: **"Mobile number already registered"**, listing Rahul Patil.
   → Click **Register Different Patient**.
   → P2 is created and the server records the acknowledgement against that exact number.
3. Create **P3** the same way. The dialog now lists **both** Rahul and Aarav.
   → Click **Register Different Patient** again.

> If step 2 creates the patient with **no dialog**, the duplicate-phone feature is not deployed on
> your environment. Stop and confirm the build SHA before continuing — many cases depend on it.

### Record for every patient

| Field                 | Where to find it                                                        |
| --------------------- | ----------------------------------------------------------------------- |
| **Numeric `id`**      | DevTools → Network → the `POST /hospital/patients` response, field `id` |
| **`publicId`** (UUID) | same response, field `publicId`                                         |
| **`customId`**        | shown in the UI as the Patient ID, format `PAT<id>`                     |

You will need **numeric id and publicId** for every tenant-isolation test. Keep them in a
scratch table:

```
P1  id=____  publicId=________________________________  customId=PAT____
P2  id=____  publicId=________________________________  customId=PAT____
...
P6  id=____  publicId=________________________________  customId=PAT____   (HOSPITAL_B)
```

---

## 7. Step 6 — Transactional records (HOSPITAL_A)

Create these so the isolation and visibility tests have something to reach for. Record the
numeric id and publicId of each.

| Record                      | How                                                                                                 |
| --------------------------- | --------------------------------------------------------------------------------------------------- |
| Appointment                 | Receptionist → Appointments → Add, for P1 with Dr Meera                                             |
| OPD case                    | Receptionist → OPD → Add OPD, existing patient P1                                                   |
| Consultation + prescription | Doctor → OPD → open P1's case → Complete consultation                                               |
| Bill                        | generated by the OPD flow; find it under Billing                                                    |
| IPD admission               | Doctor requests admission from P1's IPD case → Receptionist admits to `General Ward A`, bed `GA-01` |
| Clinical document           | Receptionist → Patients → P1 → Documents → upload a **synthetic** PDF/JPG (never a real report)     |
| Pharmacy sale               | Pharmacist → Billing Counter → sell `QA Paracetamol 500`                                            |
| Support ticket              | Admin → Support → raise one                                                                         |

Create **one appointment and one OPD for P1 in HOSPITAL_B too** — the isolation tests compare
identically-shaped records across tenants.

---

## 8. Quick reference card (fill in and keep beside you)

```
PLATFORM   super admin ............ ____________________ / QaPass#2026

HOSPITAL_A  id=____  admin.hospa@qa.test   rec.hospa@qa.test   doc1.hospa@qa.test
            nurse.hospa@qa.test  ni.hospa@qa.test  ot.hospa@qa.test  pharm.hospa@qa.test
HOSPITAL_B  id=____  admin.hospb@qa.test   rec.hospb@qa.test   doc1.hospb@qa.test
HOSPITAL_M  id=____  admin.hospm@qa.test           (OPD-only plan)
CLINIC_A    id=____  admin.clina@qa.test   rec.clina@qa.test   doc.clina@qa.test
CLINIC_B    id=____  admin.clinb@qa.test
PHARMACY_A  id=____  admin.pharma@qa.test  pharm.pharma@qa.test
PHARMACY_B  id=____  admin.pharmb@qa.test
PHARM_SOLO  id=____  admin.pharmsolo@qa.test       (SINGLE_PHARMACIST_ADMIN)
CLINIC_SOLO id=____  admin.clinsolo@qa.test        (isSingleDoctor)

All passwords: QaPass#2026
```
