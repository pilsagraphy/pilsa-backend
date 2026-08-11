-- reports_log 처리 시각 / 처리 결과 연결 컬럼 추가
-- Flyway/Liquibase 미사용 프로젝트라 자동 실행되지 않음. 직접 DB에 실행할 것.

ALTER TABLE reports_log
    ADD COLUMN resolved_at DATETIME NULL COMMENT '처리(수락/거절) 시각',
    ADD COLUMN resolution_action_id BIGINT NULL COMMENT '수락(삭제 처리)된 경우 연결되는 moderation_log 액션';

ALTER TABLE reports_log
    ADD CONSTRAINT fk_reports_resolution
        FOREIGN KEY (resolution_action_id) REFERENCES moderation_log (action_id) ON UPDATE CASCADE;
