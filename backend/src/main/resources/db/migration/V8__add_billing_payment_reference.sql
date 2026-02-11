-- Add payment_reference column to billing for UPI/online transaction reference
ALTER TABLE billing
ADD COLUMN payment_reference VARCHAR(100);
