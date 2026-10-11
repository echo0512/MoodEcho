-- Existing installations: run against moodecho_app before starting the updated backend.
-- Additive and repeatable. No existing diary or vector data is changed.
CREATE TABLE IF NOT EXISTS diary_embedding_job (
    diary_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    state VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_until DATETIME(6) NULL,
    claim_token VARCHAR(36) NULL,
    error_code VARCHAR(40) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_embedding_job_due (state, next_attempt_at),
    INDEX idx_embedding_job_lease (state, lease_until),
    INDEX idx_embedding_job_user (user_id),
    CONSTRAINT fk_embedding_job_diary FOREIGN KEY (diary_id) REFERENCES diary(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记语义索引后台任务';
