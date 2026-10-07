-- ============================================
-- Super Admin Setup Script
-- Creates the first Super Admin for platform login on a deployed database.
--
-- No credential is shipped here: this repository is public, so any password or hash written in
-- it is known to everyone. Before running, replace the two placeholders below with
--   * an e-mail address you control, and
--   * a BCrypt hash of a strong password you generated yourself, for example (the password is
--     prompted for, so it never reaches the command line or shell history):
--       python3 -c "import bcrypt,getpass;print(bcrypt.hashpw(getpass.getpass().encode(),bcrypt.gensalt(10)).decode())"
-- Alternatively set INITIAL_SUPER_ADMIN_EMAIL / INITIAL_SUPER_ADMIN_PASSWORD for the first startup
-- (see DataInitializer). Local development seeds its own development-only account.
--
-- The INSERT inserts nothing until both placeholders have been replaced with valid values.
-- ============================================

USE hospital_management;

SET @super_admin_email = 'REPLACE_WITH_YOUR_EMAIL';
SET @super_admin_password_hash = 'REPLACE_WITH_BCRYPT_HASH';

INSERT INTO users (email, password, name, role, hospital_id, is_active, public_id, created_at)
SELECT @super_admin_email, @super_admin_password_hash, 'Super Admin', 'SUPER_ADMIN', NULL, TRUE, 'SUPER_ADMIN_001', NOW()
FROM DUAL
WHERE @super_admin_email <> 'REPLACE_WITH_YOUR_EMAIL'
  AND @super_admin_password_hash REGEXP '^[$]2[aby][$][0-9]{2}[$][./A-Za-z0-9]{53}$';

SELECT ROW_COUNT() AS super_admins_created;

-- Verify the Super Admin
SELECT id, email, name, role, hospital_id, is_active, created_at
FROM users
WHERE role = 'SUPER_ADMIN';
