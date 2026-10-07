# Bug #7 – IPD Prescription Logic

## Bug Description

IPD prescriptions should be treated strictly as digital prescriptions and should not be connected to or deducted from the hospital's physical medical inventory.

The previous prescription logic could use the `medicineId` to look up the medicine from the physical inventory when a medicine name was not provided. This created an unnecessary dependency between digital prescriptions and physical medical stock.

## Root Cause

The issue was in:

`backend/src/main/java/com/hms/service/hospital/IpdAdmissionService.java`

Inside the `addIpdPrescription()` method, the previous logic attempted to resolve the medicine name from `MedicineRepository` using the `medicineId`.

This connected the digital prescription flow with the physical medical inventory.

## Changes Made

The prescription logic was changed so that:

- The medicine name is taken directly from the prescription request.
- A medicine name is required when creating an IPD prescription.
- The prescription no longer looks up the medicine name from physical inventory.
- Standard IPD prescriptions do not perform physical stock deduction.
- Standard IPD prescriptions do not add medicine charges to the IPD bill.
- The existing physical-stock functionality for administering medicines remains unchanged.

## Validation

### Backend Build

Ran:

`mvn clean compile`

Result:

`BUILD SUCCESS`

### Code Validation

Ran:

`git diff --check`

Result:

No errors reported.

### Functional Test

Created an IPD prescription for **Paracetamol** from:

Doctor → IPD → Medication → Prescribe Medicine

The prescription was successfully created and appeared under **Prescribed Medicines**.

The IPD billing total remained **₹500**, confirming that creating the prescription did not add a medicine charge.

The QA hospital's pharmacy inventory was empty during testing, so a before/after physical stock comparison could not be performed.

## Result

IPD prescriptions are now handled as digital prescriptions and no longer depend on physical medical inventory for resolving the prescribed medicine.

Physical inventory deduction remains available separately through the existing **Administer Stock Item** functionality.
