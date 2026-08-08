CREATE TABLE IF NOT EXISTS ai_agent_run_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  run_id BIGINT NOT NULL,
  rating VARCHAR(20) NOT NULL,
  reason VARCHAR(40) NOT NULL,
  comment_text VARCHAR(1000) NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_ai_agent_run_feedback_owner_run (user_id, run_id),
  KEY idx_ai_agent_run_feedback_run_created (run_id, created_at),
  KEY idx_ai_agent_run_feedback_reason_created (reason, created_at),
  CONSTRAINT chk_ai_agent_run_feedback_rating CHECK (rating IN ('helpful','not_helpful')),
  CONSTRAINT chk_ai_agent_run_feedback_revision CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
