# Phase 4: Manual Verification Test Guide
**Date:** 2026-07-06  
**Status:** Ready for Testing

---

## Quick Start

### 1. Servers Starting
- **Backend:** http://localhost:8080 (Spring Boot)
- **Frontend:** http://localhost:5173 (Vite Dev Server)
- **Database:** MySQL (configured in `.env`)

Wait 30-60 seconds for backend to fully start.

### 2. Login Credentials
Use **Super Admin** credentials:
- Email: (Check your `.env` or existing super admin account)
- Password: (Your existing password)

Navigate to: http://localhost:5173 → Platform Admin Login

---

## Test Data Setup

Optional: Insert test data to verify isolation. Use SQL file:
```bash
# In MySQL client:
source C:\Users\karti\AppData\Local\Temp\claude\e--Projects-HOSPITAL\d571c3aa-51dc-408f-b519-64a2c60e65a2\scratchpad\test_data.sql;
```

Or manually insert test data while logged in via UI.

---

## Phase 4.1: Sidebar Structure Verification ✓

**Steps:**
1. Log in as Platform Admin
2. Look at left sidebar
3. Verify the structure:

```
Sidebar
├─ Dashboard (icon)
├─ Hospital (expandable group)
│  ├─ Hospitals
│  ├─ Medicines
│  ├─ Inventory Items
│  ├─ Plans
│  ├─ Tickets
│  └─ FAQs
├─ Clinic (expandable group)
│  ├─ Clinics
│  ├─ Medicines
│  ├─ Inventory Items
│  ├─ Plans
│  ├─ Tickets
│  └─ FAQs
├─ Pharmacy (expandable group)
│  ├─ Pharmacies
│  ├─ Medicines
│  ├─ Plans
│  ├─ Tickets
│  └─ FAQs
└─ Audit Logs
```

**Expected Results:**
- [ ] Sidebar shows all 4 groups (Hospital, Clinic, Pharmacy, Audit Logs)
- [ ] Groups are expandable/collapsible
- [ ] Subtabs appear when groups expanded
- [ ] Dashboard tab visible at top
- [ ] Clicking a subtab navigates and shows correct content

**Status:** ☐ PASS ☐ FAIL

---

## Phase 4.2: Medicines Isolation - HOSPITAL

**Steps:**
1. Click **Hospital → Medicines**
2. Verify page loads

**Expected Results:**
- [ ] Page title shows "Medicines" under Hospital section
- [ ] Only HOSPITAL-type medicines display (Aspirin, Paracetamol, Amoxicillin if test data inserted)
- [ ] CLINIC medicines NOT visible (Ibuprofen, Cough Syrup not shown)
- [ ] PHARMACY medicines NOT visible (Vitamin C, Antacid not shown)
- [ ] "Add Medicine" button present
- [ ] Can create new medicine (will be tagged as HOSPITAL type)
- [ ] Can edit/delete existing HOSPITAL medicines

**Create Test:**
1. Click "Add Medicine"
2. Fill: Name = "Test Hospital Medicine", Type = "Tablet"
3. Submit
4. Verify it appears in list

**Delete Test:**
1. Find any medicine in list
2. Click delete button
3. Confirm deletion
4. Verify removed from list

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.3: Medicines Isolation - CLINIC

**Steps:**
1. Click **Clinic → Medicines**
2. Verify isolation from HOSPITAL

**Expected Results:**
- [ ] Only CLINIC-type medicines display (Ibuprofen, Cough Syrup, Allergy Medicine)
- [ ] HOSPITAL medicines NOT visible
- [ ] PHARMACY medicines NOT visible
- [ ] Can create medicine (tagged CLINIC type)
- [ ] Can edit/delete CLINIC medicines

**Create Test:**
1. Click "Add Medicine"
2. Fill: Name = "Test Clinic Medicine", Type = "Capsule"
3. Submit
4. Verify appears only in CLINIC tab, not in HOSPITAL tab

**Verify No Cross-Contamination:**
1. Go back to **Hospital → Medicines**
2. Verify "Test Clinic Medicine" does NOT appear there

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.4: Medicines Isolation - PHARMACY

**Steps:**
1. Click **Pharmacy → Medicines**
2. Verify isolation from other types

**Expected Results:**
- [ ] Only PHARMACY-type medicines display (Vitamin C, Antacid, Sleeping Pill)
- [ ] HOSPITAL medicines NOT visible
- [ ] CLINIC medicines NOT visible
- [ ] Can create medicine (tagged PHARMACY type)
- [ ] Can edit/delete PHARMACY medicines

**Create Test:**
1. Add: Name = "Test Pharmacy Medicine", Type = "Liquid"
2. Verify appears only in PHARMACY tab

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.5: Inventory Items Isolation - HOSPITAL

**Steps:**
1. Click **Hospital → Inventory Items**

**Expected Results:**
- [ ] Only HOSPITAL items display (Syringes, Bandages, Surgical Gloves)
- [ ] CLINIC items NOT visible
- [ ] PHARMACY items NOT visible
- [ ] Can add new item (tagged HOSPITAL)
- [ ] Can edit/delete HOSPITAL items

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.6: Inventory Items Isolation - CLINIC & PHARMACY

**Steps:**
1. Click **Clinic → Inventory Items**
   - [ ] Shows CLINIC items only (Gauze, Thermometer, Stethoscope)
   - [ ] No HOSPITAL or PHARMACY items

2. Click **Pharmacy → No Inventory Items tab**
   - Note: Pharmacy group does NOT have Inventory Items subtab (by design)

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.7: Support Tickets Isolation

**Steps:**
1. Click **Hospital → Tickets**
   - [ ] Shows HOSPITAL tickets only
   - [ ] No CLINIC or PHARMACY tickets

2. Click **Clinic → Tickets**
   - [ ] Shows CLINIC tickets only
   - [ ] Can change ticket status (OPEN → RESOLVED)
   - [ ] No HOSPITAL or PHARMACY tickets

3. Click **Pharmacy → Tickets**
   - [ ] Shows PHARMACY tickets only
   - [ ] Can change status
   - [ ] No other types visible

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.8: FAQs Isolation

**Steps:**
1. Click **Hospital → FAQs**
   - [ ] Shows HOSPITAL FAQs only
     - "What is OPD?"
     - "How to book appointment?"
   - [ ] No CLINIC or PHARMACY FAQs

2. Click **Clinic → FAQs**
   - [ ] Shows CLINIC FAQs only
   - [ ] Can add/edit/delete FAQs
   - [ ] No other types

3. Click **Pharmacy → FAQs**
   - [ ] Shows PHARMACY FAQs only
   - [ ] Can manage FAQs
   - [ ] Isolated from other types

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.9: Data Persistence Test

**Steps:**
1. Create a medicine: **Hospital → Medicines → Add "Persistent Medicine"**
2. Refresh page (F5)
   - [ ] Medicine still visible in Hospital tab
3. Switch to **Clinic → Medicines**
   - [ ] "Persistent Medicine" NOT visible here
4. Back to **Hospital → Medicines**
   - [ ] "Persistent Medicine" still there

**Expected Result:** Data persists correctly across navigation without cross-contamination.

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.10: Search & Filter Isolation

**Steps:**
1. **Hospital → Medicines**
2. In search box, type "aspirin"
   - [ ] Only HOSPITAL aspirin shows (if exists)
3. **Clinic → Medicines**
4. Search same term
   - [ ] Only CLINIC results show (if any)
5. **Pharmacy → Medicines**
6. Same search
   - [ ] Only PHARMACY results show (if any)

**Expected Result:** Search respects type isolation.

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.11: API Level Verification (Optional - Postman/curl)

If you want to verify API isolation directly:

```bash
# Get Hospital medicines
curl -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  "http://localhost:8080/platform/medicines?hospitalType=HOSPITAL&page=0&size=10"

# Get Clinic medicines
curl -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  "http://localhost:8080/platform/medicines?hospitalType=CLINIC&page=0&size=10"

# Get all medicines (backward compatibility)
curl -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  "http://localhost:8080/platform/medicines?page=0&size=10"
```

**Expected Result:** Each returns only medicines for that type.

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.12: Browser Console Check

**Steps:**
1. Open DevTools (F12)
2. Go to Console tab
3. Check for any red errors
4. Go through all tabs (Hospital, Clinic, Pharmacy, Audit Logs)
5. Verify no JavaScript errors appear

**Expected Result:** No errors or warnings related to our changes.

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Phase 4.13: Regression Testing - Existing Features

**Steps:**
1. Hospital Admin dashboard still works
   - [ ] Can log in as Hospital Admin
   - [ ] Dashboard loads
   - [ ] Medicines/inventory work normally at hospital level

2. Plans, Audit Logs, other features unchanged
   - [ ] Plans tab works
   - [ ] Audit Logs tab works
   - [ ] No new errors introduced

**Status:** ☐ PASS ☐ FAIL  
**Notes:** ___________________

---

## Overall Verification Summary

| Area | Status | Issues |
|------|--------|--------|
| Sidebar Structure | ☐ PASS ☐ FAIL | _______ |
| Medicines - Hospital | ☐ PASS ☐ FAIL | _______ |
| Medicines - Clinic | ☐ PASS ☐ FAIL | _______ |
| Medicines - Pharmacy | ☐ PASS ☐ FAIL | _______ |
| Inventory - Hospital | ☐ PASS ☐ FAIL | _______ |
| Inventory - Clinic | ☐ PASS ☐ FAIL | _______ |
| Tickets Isolation | ☐ PASS ☐ FAIL | _______ |
| FAQs Isolation | ☐ PASS ☐ FAIL | _______ |
| Data Persistence | ☐ PASS ☐ FAIL | _______ |
| Search Isolation | ☐ PASS ☐ FAIL | _______ |
| Browser Console | ☐ PASS ☐ FAIL | _______ |
| Regressions | ☐ PASS ☐ FAIL | _______ |

---

## Critical Issues Found

**Issue #1:**
- Description: ___________
- Impact: ___________
- Fix Applied: ___________

**Issue #2:**
- Description: ___________
- Impact: ___________
- Fix Applied: ___________

---

## Sign-Off

**Overall Status:**
- ☐ ALL TESTS PASSED - Ready for Deployment
- ☐ MINOR ISSUES FOUND - Document and fix
- ☐ MAJOR ISSUES - Rollback and investigate

**Tested By:** ___________  
**Date:** ___________  
**Time Spent:** ___________  

**Comments:**
```
[Your detailed notes here]
```

---

## Next Steps After Verification

1. If ALL PASS:
   - Push to main branch
   - Deploy to staging/production
   - Update documentation

2. If ISSUES found:
   - Document in Issues section above
   - Debug and fix
   - Re-run affected tests
   - Iterate until all PASS

3. Known Limitations:
   - Plans type-specific behavior pending (not critical)
   - CSV imports for medicines don't set hospitalType yet (workaround: import via UI)

