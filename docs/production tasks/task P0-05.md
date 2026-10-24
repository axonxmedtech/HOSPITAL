# Task P0-05 Completed: Security Guard and Default Credentials

---

## 1. Context & Operational Findings

- **The Finding:** During rehearsal startups against a snapshot with pre-existing data, `KnownDefaultCredentialGuard` halted application boot with:
  `Refusing to start [production]: active SUPER_ADMIN account(s) [admin123@gmail.com] use a publicly known default credential. Change the password or deactivate the account before starting.`
- **Guard Validation:** The guard performed exactly as designed. The repository's historical development credentials (`admin123@gmail.com` with `pass123`) were present in the snapshot. Permitting an active super-admin account with publicly committed credentials in staging or production is a critical security vulnerability.
- **Mandate:** The zero-bypass policy was strictly upheld. No bypass flags, configuration toggles, or exemptions were added to `KnownDefaultCredentialGuard`.

---

## 2. Information Security Review & Verification

- **Redaction of Secrets:** Inspected [`KnownDefaultCredentialGuard.java`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/security/KnownDefaultCredentialGuard.java) and [`DataInitializer.java`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/component/DataInitializer.java). Verified that startup exceptions and log messages disclose ONLY account emails and counts, never plain-text passwords or BCrypt hashes.
- **Published Hash Digest Hashing:** In [`KnownDefaultCredentials.java`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/security/KnownDefaultCredentials.java), published documentation hashes are represented as SHA-256 digests (`PUBLISHED_HASH_DIGESTS`), preventing hash replication in source code.
- **Inactive Account Safety:** Verified that `userRepository.findActiveSuperAdminCredentials()` queries only accounts where `isActive = true` or `isActive IS NULL`. Inactive accounts (`is_active = 0`) are ignored by the guard and rejected by the authentication filter.

---

## 3. Automated Test Coverage

Enhanced existing test suites with explicit production profile verifications:

1. [`KnownDefaultCredentialGuardTest.java`](file:///e:/Projects/HOSPITAL/backend/src/test/java/com/hms/security/KnownDefaultCredentialGuardTest.java):
   - `productionRefusesActiveDefaultSuperAdmin_namingProductionProfileAndEmail()`: Verifies that in `production` profile, having an active default super admin throws `IllegalStateException` disclosing the email and `[production]` without leaking hashes or passwords.
   - `deactivatingTheDefaultAccount_allowsProductionStartup()`: Proves that when the default super admin is deactivated, production startup proceeds cleanly.
2. [`KnownDefaultCredentialGuardDatabaseTest.java`](file:///e:/Projects/HOSPITAL/backend/src/test/java/com/hms/security/KnownDefaultCredentialGuardDatabaseTest.java):
   - `productionGuard_acceptsRotatedActiveAdminAndInactiveLegacyDefault()`: Confirms that against a real database instance under `production` profile, an active admin with rotated credentials and an inactive legacy admin pass startup validation without exceptions.
   - `productionGuard_refusesActiveLegacyDefaultAccount()`: Confirms that activating the legacy default admin in `production` halts startup with `IllegalStateException`.
3. [`DataInitializerTest.java`](file:///e:/Projects/HOSPITAL/backend/src/test/java/com/hms/component/DataInitializerTest.java):
   - Confirmed that deployed runtimes (`prod`, `production`, `staging`, `qa`) never create default credentials when environment variables are omitted.
   - Confirmed password complexity validation on bootstrap credentials (>= 12 characters, upper, lower, digit, symbol).

---

## 4. Documentation & Operational Runbooks

- **Operator Runbook:** Created [`docs/operations/CREDENTIAL_ROTATION.md`](file:///e:/Projects/HOSPITAL/docs/operations/CREDENTIAL_ROTATION.md) documenting:
  - Option 1 (Deactivation): `UPDATE users SET is_active = 0 WHERE email = 'admin123@gmail.com' AND role = 'SUPER_ADMIN';`
  - Option 2 (BCrypt Password Rotation): Step-by-step instructions for generating BCrypt hashes and updating records.
  - Option 3 (Environment Variable Bootstrap): Configuring `INITIAL_SUPER_ADMIN_EMAIL` and `INITIAL_SUPER_ADMIN_PASSWORD`.
- **Preflight SQL Script:** Created [`docs/operations/super-admin-credential-preflight.sql`](file:///e:/Projects/HOSPITAL/docs/operations/super-admin-credential-preflight.sql) allowing operators to inspect and verify super-admin credentials prior to deployment.
- **Checklist:** Updated [`plan.md`](file:///e:/Projects/HOSPITAL/plan.md).

Searched for "KnownDefaultCredentialGuard"
Viewed KnownDefaultCredentialGuard.java:1-62
Viewed KnownDefaultCredentials.java:1-66
Viewed BootstrapProfiles.java:1-42
Searched for "DEVELOPMENT_SUPER_ADMIN_EMAIL"
Viewed DataInitializer.java:1-137
Viewed KnownDefaultCredentialGuardTest.java:1-139
Viewed KnownDefaultCredentialGuardDatabaseTest.java:1-95
Viewed DataInitializerTest.java:1-157
Listed directory operations
Searched for "setup-super-admin.sql"
Viewed setup-super-admin.sql:1-34
Searched for "findActiveSuperAdminCredentials"
Viewed UserRepository.java:40-65
Viewed patient-duplicate-phone-preflight.sql:1-57
Viewed KnownDefaultCredentialGuardTest.java:50-75
Viewed KnownDefaultCredentialGuardTest.java:60-85
Edited KnownDefaultCredentialGuardTest.java
Viewed KnownDefaultCredentialGuardDatabaseTest.java:60-95
Edited KnownDefaultCredentialGuardDatabaseTest.java
Created super-admin-credential-preflight.sql
Created CREDENTIAL_ROTATION.md
Created task%20P0-05.md
Viewed plan.md:320-333
Edited plan.md

### Task P0-05 Completed: Security Guard and Default Credentials

---

#### 1. Rehearsal Finding & Security Mandate

- **The Finding:** During rehearsal startups against a database snapshot containing legacy development data, [`KnownDefaultCredentialGuard`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/security/KnownDefaultCredentialGuard.java) correctly refused startup with:
  `Refusing to start [production]: active SUPER_ADMIN account(s) [admin123@gmail.com] use a publicly known default credential. Change the password or deactivate the account before starting.`
- **Validation:** The guard functioned exactly as designed. The repository's public development super-admin credential (`admin123@gmail.com` / `pass123`) was present in the snapshot. Starting a production system with a known default super-admin credential is a severe vulnerability.
- **Zero-Bypass Policy:** The guard remains 100% active and unbypassed. No configuration overrides, bypass flags, or exemptions were added.

---

#### 2. Information Security Review

- **No Password or Hash Leaks:** Verified that exception messages and log output from [`KnownDefaultCredentialGuard.java`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/security/KnownDefaultCredentialGuard.java) and [`DataInitializer.java`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/component/DataInitializer.java) disclose only account emails and counts, never plain-text passwords or BCrypt hashes.
- **Hash Hiding:** In [`KnownDefaultCredentials.java`](file:///e:/Projects/HOSPITAL/backend/src/main/java/com/hms/security/KnownDefaultCredentials.java), published documentation hashes from commit history are stored only as SHA-256 digests (`PUBLISHED_HASH_DIGESTS`), preventing hash replication in source code.
- **Inactive Account Safety:** Confirmed that `userRepository.findActiveSuperAdminCredentials()` queries only active super admins (`WHERE u.role = 'SUPER_ADMIN' AND (u.isActive IS NULL OR u.isActive = true)`). Deactivated accounts (`is_active = 0`) are ignored by the guard and rejected by the authentication filter.

---

#### 3. Automated Test Coverage

Enhanced test suites with explicit production profile tests:

1. [`KnownDefaultCredentialGuardTest.java`](file:///e:/Projects/HOSPITAL/backend/src/test/java/com/hms/security/KnownDefaultCredentialGuardTest.java):
   - `productionRefusesActiveDefaultSuperAdmin_namingProductionProfileAndEmail()`: Verifies that in the `production` profile, having an active default super admin throws `IllegalStateException` naming only the email address and `[production]` without leaking hashes or passwords.
   - `deactivatingTheDefaultAccount_allowsProductionStartup()`: Proves that when the default super admin is deactivated, production startup proceeds cleanly.
2. [`KnownDefaultCredentialGuardDatabaseTest.java`](file:///e:/Projects/HOSPITAL/backend/src/test/java/com/hms/security/KnownDefaultCredentialGuardDatabaseTest.java):
   - `productionGuard_acceptsRotatedActiveAdminAndInactiveLegacyDefault()`: Confirms against a real database instance under the `production` profile that an active admin with rotated credentials and an inactive legacy admin pass startup validation without exceptions.
   - `productionGuard_refusesActiveLegacyDefaultAccount()`: Confirms that activating the legacy default admin in `production` halts startup with `IllegalStateException`.
3. [`DataInitializerTest.java`](file:///e:/Projects/HOSPITAL/backend/src/test/java/com/hms/component/DataInitializerTest.java):
   - Confirmed that deployed runtimes (`prod`, `production`, `staging`, `qa`) never auto-create default super admins when environment variables are omitted.
   - Confirmed password complexity validation on bootstrap credentials (>= 12 characters, upper, lower, digit, symbol).

---

#### 4. Operational Runbook & Preflight Script

- **Preflight Inspection Script:** Created [`docs/operations/super-admin-credential-preflight.sql`](file:///e:/Projects/HOSPITAL/docs/operations/super-admin-credential-preflight.sql) allowing operators to inspect and verify super-admin accounts prior to restarting or deploying.
- **Operator Runbook:** Created [`docs/operations/CREDENTIAL_ROTATION.md`](file:///e:/Projects/HOSPITAL/docs/operations/CREDENTIAL_ROTATION.md) documenting:
  - **Option 1 (Deactivation):**
    ```sql
    UPDATE users SET is_active = 0 WHERE email = 'admin123@gmail.com' AND role = 'SUPER_ADMIN';
    ```
  - **Option 2 (BCrypt Password Rotation):** Secure offline generation of BCrypt hashes and database updates.
  - **Option 3 (Environment Variable Bootstrap):** Configuring `INITIAL_SUPER_ADMIN_EMAIL` and `INITIAL_SUPER_ADMIN_PASSWORD` on initial deployment.
- **Task Summary:** Documented in [`docs/production tasks/task P0-05.md`](file:///e:/Projects/HOSPITAL/docs/production%20tasks/task%20P0-05.md).
- **Checklist:** Updated [`plan.md`](file:///e:/Projects/HOSPITAL/plan.md).
