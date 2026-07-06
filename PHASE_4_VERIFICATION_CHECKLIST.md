# Phase 4: Verification Checklist
**Date:** 2026-07-06  
**Implementation Status:** ✅ COMPLETE (Phases 1-4 Ready for Testing)

## Overview
All backend and frontend changes have been implemented and compiled successfully. This document outlines the manual verification steps to validate tenant-type isolated platform admin dashboard.

---

## Quick Start

### Backend
```bash
cd backend
mvn spring-boot:run  # Starts on http://localhost:8080
```

### Frontend
```bash
cd frontend
npm run dev  # Starts on http://localhost:5173
```

---

## Manual Verification Checklist

### 1. Sidebar Structure Verification ✓
- [ ] Login to Platform Admin dashboard
- [ ] Verify sidebar shows grouped structure:
  - **Hospital** group with subtabs: Hospitals, Medicines, Inventory Items, Plans, Tickets, FAQs
  - **Clinic** group with subtabs: Clinics, Medicines, Inventory Items, Plans, Tickets, FAQs
  - **Pharmacy** group with subtabs: Pharmacies, Medicines, Plans, Tickets, FAQs
  - **Audit Logs** (standalone tab)
  - **Dashboard** (top level)
- [ ] Verify group expand/collapse works
- [ ] Verify subtab navigation works

### 2. Medicines - Tenant Isolation Verification
**Test Case:** Hospital vs Clinic vs Pharmacy medicines

```sql
-- Prepare test data (if needed):
INSERT INTO medicine_list (name, type, hospital_type) VALUES ('Aspirin', 'Tablet', 'HOSPITAL');
INSERT INTO medicine_list (name, type, hospital_type) VALUES ('Amoxicillin', 'Capsule', 'CLINIC');
INSERT INTO medicine_list (name, type, hospital_type) VALUES ('Ibuprofen', 'Tablet', 'PHARMACY');
```

- [ ] Navigate to **Hospital → Medicines**
  - [ ] Should see only HOSPITAL medicines (Aspirin)
  - [ ] Can create a new medicine → should have hospital_type = 'HOSPITAL'
  - [ ] Can edit/delete only HOSPITAL medicines
- [ ] Navigate to **Clinic → Medicines**
  - [ ] Should see only CLINIC medicines (Amoxicillin)
  - [ ] Can create new medicine → should have hospital_type = 'CLINIC'
- [ ] Navigate to **Pharmacy → Medicines**
  - [ ] Should see only PHARMACY medicines (Ibuprofen)
  - [ ] Can create new medicine → should have hospital_type = 'PHARMACY'
- [ ] Verify NO cross-contamination (Hospital tab doesn't show Clinic medicines, etc.)

### 3. Inventory Items - Tenant Isolation Verification
**Test Case:** Hospital vs Clinic vs Pharmacy inventory items

```sql
-- Prepare test data (if needed):
INSERT INTO inventory_items (name, type, hospital_type) VALUES ('Syringes', 'Consumable', 'HOSPITAL');
INSERT INTO inventory_items (name, type, hospital_type) VALUES ('Gauze', 'Consumable', 'CLINIC');
INSERT INTO inventory_items (name, type, hospital_type) VALUES ('Bottles', 'Container', 'PHARMACY');
```

- [ ] Navigate to **Hospital → Inventory Items**
  - [ ] Should see only HOSPITAL items (Syringes)
  - [ ] Can create new item → should have hospital_type = 'HOSPITAL'
- [ ] Navigate to **Clinic → Inventory Items**
  - [ ] Should see only CLINIC items (Gauze)
  - [ ] Can create new item → should have hospital_type = 'CLINIC'
- [ ] Navigate to **Pharmacy → Inventory Items**
  - [ ] Should see only PHARMACY items (Bottles)
  - [ ] Can create new item → should have hospital_type = 'PHARMACY'

### 4. Support Tickets - Tenant Isolation Verification
**Test Case:** Hospital vs Clinic vs Pharmacy tickets

```sql
-- Prepare test data (if needed):
INSERT INTO support_tickets (hospital_id, hospital_name, admin_name, subject, message, priority, hospital_type) 
VALUES (1, 'City Hospital', 'Admin1', 'Test Ticket', 'Hospital ticket', 'MEDIUM', 'HOSPITAL');

INSERT INTO support_tickets (hospital_id, hospital_name, admin_name, subject, message, priority, hospital_type) 
VALUES (2, 'Clinic A', 'Admin2', 'Clinic Issue', 'Clinic ticket', 'HIGH', 'CLINIC');

INSERT INTO support_tickets (hospital_id, hospital_name, admin_name, subject, message, priority, hospital_type) 
VALUES (3, 'Pharmacy X', 'Admin3', 'Pharmacy Issue', 'Pharmacy ticket', 'LOW', 'PHARMACY');
```

- [ ] Navigate to **Hospital → Tickets**
  - [ ] Should see only HOSPITAL tickets
  - [ ] Can change status (OPEN → RESOLVED)
- [ ] Navigate to **Clinic → Tickets**
  - [ ] Should see only CLINIC tickets
- [ ] Navigate to **Pharmacy → Tickets**
  - [ ] Should see only PHARMACY tickets

### 5. FAQs - Tenant Isolation Verification
**Test Case:** Hospital vs Clinic vs Pharmacy FAQs

```sql
-- Prepare test data (if needed):
INSERT INTO faqs (question, answer, hospital_type) VALUES (
  'What is OPD?', 'Outpatient Department', 'HOSPITAL'
);

INSERT INTO faqs (question, answer, hospital_type) VALUES (
  'What is a clinic visit?', 'Basic medical consultation', 'CLINIC'
);

INSERT INTO faqs (question, answer, hospital_type) VALUES (
  'How to order medicines?', 'Call pharmacy number', 'PHARMACY'
);
```

- [ ] Navigate to **Hospital → FAQs**
  - [ ] Should see only HOSPITAL FAQs
  - [ ] Can create/edit/delete HOSPITAL FAQs
- [ ] Navigate to **Clinic → FAQs**
  - [ ] Should see only CLINIC FAQs
- [ ] Navigate to **Pharmacy → FAQs**
  - [ ] Should see only PHARMACY FAQs

### 6. API Level Verification (Postman / curl)

**Test medicines endpoint with hospitalType:**
```bash
# Get Hospital medicines
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/platform/medicines?hospitalType=HOSPITAL"

# Get Clinic medicines
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/platform/medicines?hospitalType=CLINIC"

# Get Pharmacy medicines
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/platform/medicines?hospitalType=PHARMACY"

# Get all medicines (backward compatibility - no hospitalType)
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/platform/medicines"
```

- [ ] Each query returns only medicines for that type
- [ ] Creating medicine with hospitalType parameter sets the column correctly
- [ ] Updating/deleting with wrong hospitalType returns 404

### 7. Data Persistence Verification
- [ ] Create a medicine in Hospital group
- [ ] Refresh page
- [ ] [ ] Medicine still appears in Hospital group
- [ ] Switch to Clinic group
- [ ] [ ] Medicine does NOT appear in Clinic group
- [ ] Switch back to Hospital
- [ ] [ ] Medicine still there

### 8. Edge Cases & Boundary Testing

- [ ] **Empty groups:** Navigate to a type with no medicines/items → should show "No data" gracefully
- [ ] **Search with type isolation:**
  - [ ] In Hospital Medicines, search for "aspirin" → only finds Hospital aspirin (if exists)
  - [ ] Same search in Clinic → only finds Clinic results
- [ ] **Pagination with type isolation:**
  - [ ] Create 15+ items in Hospital type
  - [ ] Verify pagination works correctly for that type only
- [ ] **Null hospitalType handling:**
  - [ ] Old data with NULL hospitalType should appear in admin merged view (backward compat)
  - [ ] Should NOT appear in typed views (HOSPITAL, CLINIC, PHARMACY)

### 9. Regression Testing - Existing Features

- [ ] Hospital Admin dashboard works unchanged
- [ ] Hospital-level medicines/inventory still work
- [ ] Plans management still functional
- [ ] Audit Logs tab works
- [ ] Navigation between different tabs doesn't break state
- [ ] Form validation still works
- [ ] Error messages display correctly

### 10. Browser & Console Verification

- [ ] No JavaScript errors in browser console
- [ ] No network 500 errors in Network tab
- [ ] Page loads in <3 seconds
- [ ] Sidebar collapse/expand smooth
- [ ] Tab switching doesn't show loading artifacts

---

## Known Limitations & Notes

### Inventory Items API Clarification
- Current implementation uses `/platform/inventory-master` endpoint
- This serves both:
  - **Global catalog** (InventoryMasterItem) - no hospitalType
  - **Tenant-isolated items** (InventoryItem) - with hospitalType isolation
- When `hospitalType` parameter is provided → uses InventoryItem (isolated)
- When `hospitalType` is null → uses InventoryMasterItem (global, backward compat)

### Data Cleanup Notes
- Database was cleaned (medicine_list and inventory_items rows deleted) during Phase 1
- Old data with NULL hospitalType will appear in admin merged views
- New data created via UI will have proper hospitalType set

---

## Test Results Template

Use this to document test results:

```
## Test Results - [DATE]

### Medicines Isolation
- [ ] HOSPITAL medicines: [PASS/FAIL] - Notes: ___
- [ ] CLINIC medicines: [PASS/FAIL] - Notes: ___
- [ ] PHARMACY medicines: [PASS/FAIL] - Notes: ___

### Inventory Items Isolation
- [ ] HOSPITAL items: [PASS/FAIL] - Notes: ___
- [ ] CLINIC items: [PASS/FAIL] - Notes: ___
- [ ] PHARMACY items: [PASS/FAIL] - Notes: ___

### Tickets Isolation
- [ ] HOSPITAL tickets: [PASS/FAIL] - Notes: ___
- [ ] CLINIC tickets: [PASS/FAIL] - Notes: ___
- [ ] PHARMACY tickets: [PASS/FAIL] - Notes: ___

### FAQs Isolation
- [ ] HOSPITAL FAQs: [PASS/FAIL] - Notes: ___
- [ ] CLINIC FAQs: [PASS/FAIL] - Notes: ___
- [ ] PHARMACY FAQs: [PASS/FAIL] - Notes: ___

### Overall Status
- Sidebar rendering: [PASS/FAIL]
- API isolation: [PASS/FAIL]
- Data persistence: [PASS/FAIL]
- Backward compatibility: [PASS/FAIL]

**Signed off by:** _________
**Date:** _________
```

---

## Troubleshooting

### Sidebar not showing groups
- Clear browser cache (Ctrl+Shift+Del)
- Check browser console for JavaScript errors
- Verify React Router is properly configured

### API returns 500 error
- Check backend logs for exception
- Verify hospitalType parameter is being passed correctly
- Check database has hospital_type columns

### Data not filtering by type
- Verify database has proper hospital_type values
- Check service layer is applying type filters
- Verify repository queries include hospitalType condition

### Old data appearing everywhere
- Data with NULL hospitalType is expected in merged views
- Create new test data with explicit hospitalType values
- Or update old records: `UPDATE medicine_list SET hospital_type = 'HOSPITAL' WHERE hospital_type IS NULL;`

---

## Sign-Off
- **Implementation:** ✅ Complete
- **Backend Build:** ✅ Success
- **Frontend Build:** ✅ Success
- **Ready for Testing:** ✅ Yes

**Next Steps:**
1. Run manual verification checklist above
2. Document any issues found
3. Fix bugs if any found
4. Once all tests pass, proceed with final deployment

