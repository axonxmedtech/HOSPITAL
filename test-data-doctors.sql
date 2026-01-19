-- ============================================
-- Cross-Hospital Verification Data
-- Phase-1 Check: "Verify cross-hospital isolation"
-- ============================================

-- 1. Create Doctor for Hospital 1 (City General)
-- Email: doctor@city.com / admin123
INSERT INTO users (email, password, name, role, hospital_id, created_at)
VALUES ('doctor@city.com', '$2a$10$0qQ.izyRhAq.JgTRSNsF4.XPhqD71NdGxlMJa22T/V2CMU.BXh/Gu', 'Dr. Smith', 'DOCTOR', 1, NOW());

INSERT INTO doctors (name, email, phone, specialization, hospital_id, created_at)
VALUES ('Dr. Smith', 'doctor@city.com', '9876543210', 'Cardiology', 1, NOW());

-- 2. Create Doctor for Hospital 2 (Metro Medical)
-- Email: doctor@metro.com / admin123
INSERT INTO users (email, password, name, role, hospital_id, created_at)
VALUES ('doctor@metro.com', '$2a$10$0qQ.izyRhAq.JgTRSNsF4.XPhqD71NdGxlMJa22T/V2CMU.BXh/Gu', 'Dr. Jones', 'DOCTOR', 2, NOW());

INSERT INTO doctors (name, email, phone, specialization, hospital_id, created_at)
VALUES ('Dr. Jones', 'doctor@metro.com', '9876555210', 'Pediatrics', 2, NOW());

-- 3. Create Patient for Hospital 1 ONLY
INSERT INTO patients (name, age, gender, phone, address, hospital_id, created_at)
VALUES ('City Patient Alice', 30, 'FEMALE', '1111111111', 'City Street', 1, NOW());

-- 4. Create Patient for Hospital 2 ONLY
INSERT INTO patients (name, age, gender, phone, address, hospital_id, created_at)
VALUES ('Metro Patient Bob', 45, 'MALE', '2222222222', 'Metro Avenue', 2, NOW());

SELECT 'Verification Data Created' as status;
