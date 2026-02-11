-- Create OPD table to track each outpatient visit (vitals captured per visit)
CREATE TABLE IF NOT EXISTS opd (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(100) UNIQUE,
    patient_id BIGINT REFERENCES patient(id),
    receptionist_id BIGINT REFERENCES "user"(id),
    doctor_id BIGINT REFERENCES doctor(id),
    bp VARCHAR(50),
    temperature DOUBLE PRECISION,
    pulse INTEGER,
    weight DOUBLE PRECISION,
    spo2 INTEGER,
    problem TEXT,
    visit_type VARCHAR(20),
    token_number INTEGER,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS queue_entry (
    id BIGSERIAL PRIMARY KEY,
    opd_id BIGINT REFERENCES opd(id) ON DELETE CASCADE,
    doctor_id BIGINT REFERENCES doctor(id),
    token_number INTEGER,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_queue_doctor ON queue_entry(doctor_id, token_number);
