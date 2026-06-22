-- ============================================
-- Super Admin Setup Script
-- Creates the Super Admin user for platform login
-- ============================================

USE hospital_management;

-- Create Super Admin user
-- Email: admin@hms.com
-- Password: admin123 (BCrypt encoded)
INSERT INTO users (email, password, name, role, hospital_id, is_active, public_id, created_at)
VALUES ('admin@hms.com', '$2a$10$0qQ.izyRhAq.JgTRSNsF4.XPhqD71NdGxlMJa22T/V2CMU.BXh/Gu', 'Super Admin', 'SUPER_ADMIN', NULL, TRUE, 'SUPER_ADMIN_001', NOW());

-- Verify the Super Admin was created
SELECT id, email, name, role, hospital_id, is_active, created_at 
FROM users 
WHERE role = 'SUPER_ADMIN';

SELECT 'Super Admin user created successfully' as status;
