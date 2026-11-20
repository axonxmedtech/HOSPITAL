-- Settings → Sidebar: which tabs each role does not see, per hospital. Display only — it never
-- grants or removes a permission. No row for a role means that role's built-in defaults.
--
-- Idempotent: DatabaseMigrationRunner creates the same table on non-Flyway (dev) profiles.

CREATE TABLE IF NOT EXISTS hospital_sidebar_settings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hospital_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    hidden_tabs VARCHAR(1000) NOT NULL DEFAULT '',
    updated_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_hospital_sidebar_role (hospital_id, role),
    CONSTRAINT fk_hospital_sidebar_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals (id) ON DELETE CASCADE
);
