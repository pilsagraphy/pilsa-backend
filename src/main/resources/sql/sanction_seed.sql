-- 유저 정지/영구차단 + 주의·경고 패널티 시스템 초기 시드 데이터
-- Flyway/Liquibase 미사용 프로젝트라 자동 실행되지 않음. 직접 DB에 실행할 것.
-- qa_pilsa DB에는 이미 아래와 동일한 값으로 세팅되어 있음이 확인됨(2026-08-11) - 새 환경/로컬 DB용 참고 스크립트.
-- 주의: policy_settings.code는 'caution_per_warning'이 아니라 'cautions_per_warning'(복수형)이 실제 사용 중인 이름임.

INSERT INTO ban_policy (code, warning_no, ban_type, ban_days, description) VALUES
 ('BAN_W1', 1, 'temporary', 7,    '경고 1점: 1주일 정지'),
 ('BAN_W2', 2, 'temporary', 30,   '경고 2점: 한달 정지'),
 ('BAN_W3', 3, 'permanent', NULL, '경고 3점: 영구 차단')
ON DUPLICATE KEY UPDATE
    ban_type = VALUES(ban_type),
    ban_days = VALUES(ban_days),
    description = VALUES(description);

INSERT INTO policy_settings (code, setting_value, description) VALUES
 ('caution_per_delete',    '2',   '게시글/댓글 삭제 시 부여되는 주의 포인트'),
 ('cautions_per_warning',  '10',  '경고 1점으로 전환되는 주의 포인트 총합'),
 ('caution_ttl_days',      '365', '주의 포인트 유효기간(일)'),
 ('warning_ttl_days',      '365', '경고 유효기간(일)')
ON DUPLICATE KEY UPDATE
    setting_value = VALUES(setting_value);
