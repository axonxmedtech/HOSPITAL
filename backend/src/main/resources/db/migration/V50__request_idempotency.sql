-- Idempotency for clinical orders (RequestIdempotencyService): an IPD prescription or a set of lab
-- tests ordered twice by a retried request (double-click, resend after a lost response) is applied
-- once; the retry is answered with the records the first attempt created. `scope` names the kind of
-- order, so one key can never replay a different kind. result_ids: comma-separated created ids.
--
-- Additive only. Idempotent: DatabaseMigrationRunner.ensureRequestIdempotencyTable does the same on
-- non-Flyway (dev) profiles. Must run after V49 (Flyway out-of-order is off in prod).
CREATE TABLE IF NOT EXISTS request_idempotency (
  id BIGINT NOT NULL AUTO_INCREMENT,
  hospital_id BIGINT NOT NULL,
  scope VARCHAR(40) NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL,
  result_ids VARCHAR(500) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_request_idempotency (hospital_id, scope, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
