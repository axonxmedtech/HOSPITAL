-- IPD hardening, phase E: insurance / TPA claims, and bed reservations.
--
-- 1. ipd_insurance_claims: one claim per admission at a time (payer, policy, claim number, the
--    pre-authorisation / final claim status, and the requested, approved and settled amounts),
--    with ipd_insurance_claim_events as its timeline. The approved amount is what the patient
--    does not have to pay at discharge; the settlement is posted to the IPD bill as a payment.
-- 2. bed_reservations: a free bed held for a planned admission until a given time. Only the
--    reserved patient can be admitted into it while it holds; an expired hold is ignored.
-- Additive only.

CREATE TABLE IF NOT EXISTS ipd_insurance_claims (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    hospital_id BIGINT NOT NULL,
    ipd_admission_id BIGINT NOT NULL,
    payer_type VARCHAR(20) NOT NULL,
    insurer_name VARCHAR(150) NOT NULL,
    tpa_name VARCHAR(150) NULL,
    policy_number VARCHAR(80) NULL,
    member_id VARCHAR(80) NULL,
    claim_number VARCHAR(80) NULL,
    status VARCHAR(30) NOT NULL,
    requested_amount DECIMAL(12,2) NULL,
    approved_amount DECIMAL(12,2) NULL,
    settled_amount DECIMAL(12,2) NULL,
    deduction_amount DECIMAL(12,2) NULL,
    deduction_reason VARCHAR(255) NULL,
    created_by VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ipd_insurance_claims_public_id (public_id),
    KEY idx_ipd_insurance_claims_admission (hospital_id, ipd_admission_id),
    KEY idx_ipd_insurance_claims_status (hospital_id, status)
);

CREATE TABLE IF NOT EXISTS ipd_insurance_claim_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    claim_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NULL,
    note VARCHAR(500) NULL,
    created_by VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ipd_claim_events_claim (hospital_id, claim_id)
);

CREATE TABLE IF NOT EXISTS bed_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    hospital_id BIGINT NOT NULL,
    ward_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    patient_id BIGINT NULL,
    patient_name VARCHAR(150) NOT NULL,
    patient_phone VARCHAR(20) NULL,
    expected_at DATETIME(6) NOT NULL,
    reserved_until DATETIME(6) NOT NULL,
    notes VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    ipd_admission_id BIGINT NULL,
    cancel_reason VARCHAR(255) NULL,
    created_by VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bed_reservations_public_id (public_id),
    KEY idx_bed_reservations_bed (hospital_id, bed_id, status)
);
