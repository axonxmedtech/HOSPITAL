# Admission selection validation

Baseline: `origin/staging` at `d0709078954cff33f8643f97b0c8bcf6f6b47a28` (the approved release tree).

Authenticated HTTP tests with disposable synthetic fixtures reproduced these baseline defects:

- No patient or OPD: HTTP 200 instead of 400, selecting the first active patient.
- Patient inconsistent with the selected OPD: HTTP 200 instead of 400.
- Explicit foreign doctor, both direct admission and OPD override: HTTP 200 instead of 404.
- Inactive or nonexistent explicit doctor, and inactive OPD-derived doctor: HTTP 200 instead of 404.

The shared transactional service now requires a positive patient/OPD selection, verifies an explicitly supplied patient matches the selected OPD, locks the tenant-scoped patient and rejects inactive patients. Every resolved doctor (explicit, inherited from OPD or the existing optional-doctor fallback) is validated using the active, tenant-scoped repository query before admission persistence. Foreign/missing doctors are indistinguishable. Existing role, module, facility, billing, bed and transaction protections remain in place.

`AdmissionSelectionValidationTest` exercises authenticated HTTP, real repositories and persistence effects, including `/clinic` and `/pharmacy` aliases. Hospital tokens can use the shared aliases under existing routing policy; aliases must still enforce the same selection checks. Existing facility-isolation tests cover actual Clinic/Pharmacy tenants. Refusals must preserve admission/billing counts, available-bed state and OPD status. Positive controls cover direct admission and matching OPD/patient selections. Existing OPD-only and transaction tests remain required.

`AdmissionSelectionMySqlIT` inherits the same contracts and runs them against disposable MySQL 8. Docker absence fails this new integration class rather than silently skipping it.

Reproduce focused verification with Java 17:

```sh
mvn -f backend/pom.xml -Dtest=AdmissionSelectionValidationTest,AdmissionBedWardIsolationTest,IpdAdmissionServiceTest,IpdAdmissionTransactionTest test
mvn -f backend/pom.xml -Dit.test=AdmissionSelectionMySqlIT failsafe:integration-test failsafe:verify
```

The second command requires Docker and compiled tests. Full CI runs Maven `verify`, including existing MySQL migration/concurrency tests; its explicit synthetic MySQL datasource is required for `IpdConcurrencyIT`. No live database is used.

No migrations, schema changes, frontend source changes or data corrections are required. Deploy the reviewed backend through the existing controlled staging process only after CI/review approval. This PR does not activate artifacts or change running services.
