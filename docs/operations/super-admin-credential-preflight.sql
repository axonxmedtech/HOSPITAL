-- ==============================================================================
-- Super Admin Credential Preflight & Remediation Guide
-- ==============================================================================
-- Purpose:
-- Inspect SUPER_ADMIN credentials before booting a staging or production release.
--
-- The application's KnownDefaultCredentialGuard strictly prevents startup in
-- non-development profiles (prod, production, staging, etc.) if ANY active
-- SUPER_ADMIN user holds:
--   1. The public development password ('pass123'), or
--   2. One of the BCrypt hashes published in legacy setup documentation/history.
--
-- Running this script identifies accounts that will trip KnownDefaultCredentialGuard
-- and provides authorized SQL remediation commands.
-- ==============================================================================

USE hospital_management;

-- ------------------------------------------------------------------------------
-- 1. Inventory all SUPER_ADMIN accounts
-- ------------------------------------------------------------------------------
SELECT id,
       email,
       name,
       role,
       is_active,
       CASE 
         WHEN password REGEXP '^[$]2[aby][$][0-9]{2}[$][./A-Za-z0-9]{53}$' THEN 'BCrypt'
         WHEN password IS NULL OR password = '' THEN 'EMPTY'
         ELSE 'MALFORMED/LEGACY'
       END AS password_type,
       SHA2(password, 256) AS hash_digest,
       created_at
FROM users
WHERE role = 'SUPER_ADMIN'
ORDER BY id;

-- ------------------------------------------------------------------------------
-- 2. Detect accounts matching published documentation hashes
-- ------------------------------------------------------------------------------
-- These exact digests correspond to published BCrypt hashes in commit history.
SELECT id, email, is_active, 'MATCHES_PUBLISHED_DOCUMENTATION_HASH' AS risk
FROM users
WHERE role = 'SUPER_ADMIN'
  AND (is_active IS NULL OR is_active = 1)
  AND SHA2(password, 256) IN (
    '490cd67e7254a31730c1edd5d6f9ca41d2d788065110c1fcbd4f1693384fd7a7', -- setup/setup-super-admin.sql
    '702af6bfdf4f027954c2657f8ad381bc3faefa3d71cc58dcf0ebcb15bdb23f7d'  -- README.md
  );

-- ------------------------------------------------------------------------------
-- 3. Remediation Procedures (Choose Option A or Option B)
-- ------------------------------------------------------------------------------

-- OPTION A: Deactivate the legacy/development super admin (RECOMMENDED if account is unused)
-- Inactive accounts cannot log in, and KnownDefaultCredentialGuard ignores them.
-- update query is removed due to the commit faliure

-- OPTION B: Rotate the password to a strong, securely generated BCrypt hash
-- Generate hash via Python: content is removed due to the commit faliure

-- ------------------------------------------------------------------------------
-- 4. Verification Check
-- After remediation, this query MUST return 0 rows before restarting production.
-- ------------------------------------------------------------------------------
SELECT COUNT(*) AS active_super_admins_with_published_hashes
FROM users
WHERE role = 'SUPER_ADMIN'
  AND (is_active IS NULL OR is_active = 1)
  AND SHA2(password, 256) IN (
    '490cd67e7254a31730c1edd5d6f9ca41d2d788065110c1fcbd4f1693384fd7a7',
    '702af6bfdf4f027954c2657f8ad381bc3faefa3d71cc58dcf0ebcb15bdb23f7d'
  );
