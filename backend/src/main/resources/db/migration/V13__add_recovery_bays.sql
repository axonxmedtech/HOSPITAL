-- Flyway applies this version exactly once. A recovery bay is a named PACU/recovery location,
-- as a first-class tenant-owned resource -- not an ot_rooms row (a theatre frees the moment its
-- case COMPLETEs; recovery is a separate resource) and not a Ward/Bed. Occupancy is derived from
-- ot_recovery_episodes (an undischarged row referencing a bay means it is occupied), not stored
-- here.
CREATE TABLE recovery_bays (
  id BIGINT NOT NULL AUTO_INCREMENT,
  public_id VARCHAR(36) NOT NULL,
  hospital_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recovery_bay_public_id (public_id),
  UNIQUE KEY uk_recovery_bay_name (hospital_id, name),
  CONSTRAINT FK_recovery_bay_hospital FOREIGN KEY (hospital_id)
    REFERENCES hospitals (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MySQL 8 does not support ADD COLUMN IF NOT EXISTS. Nullable: existing recovery episodes (if
-- any) predate the bay concept and are not retroactively assigned one.
ALTER TABLE ot_recovery_episodes
    ADD COLUMN recovery_bay_id BIGINT NULL;
