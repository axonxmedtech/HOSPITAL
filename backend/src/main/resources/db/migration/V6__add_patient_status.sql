-- Add status column to patients table
ALTER TABLE patients 
ADD COLUMN status VARCHAR(20) DEFAULT 'REGISTERED' NOT NULL;

-- Update existing patients to REGISTERED status
UPDATE patients 
SET status = 'REGISTERED' 
WHERE status IS NULL OR status = '';

-- Add index for faster status-based queries
CREATE INDEX idx_patients_status ON patients(status);
