-- Create wards and beds tables (MVP)
CREATE TABLE IF NOT EXISTS wards (
  ward_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  ward_name VARCHAR(100) NOT NULL,
  bed_price DECIMAL(10,2) NOT NULL,
  total_beds INT NOT NULL,
  floor_number INT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS beds (
  bed_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  ward_id BIGINT NOT NULL,
  bed_code VARCHAR(100) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'available',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ward_id) REFERENCES wards(ward_id)
);
