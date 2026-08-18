# 급상승 인기글 DDL 검토 피드백 (2026-08-18, PM 검토 반영)

> 대상: 2026-08-18 제안 DDL (visit_log + trending 3테이블).
> 결론: **설계 방향 승인. 아래 3가지를 반영한 수정본으로 재제출** 후 적용한다.
> 1차 적용 범위는 **집계 + 조회까지** — 급상승 알림(§9)은 이번에 구현하지 않는다 (반영 2).
> 스냅샷 차분 방식(좋아요 취소 반영), boards 조인 + trending_enabled 기본 1(동적 게시판),
> 계산 근거 전 컬럼 보존, 멱등 재실행, 조정 가이드까지 — 문서·설계 수준이 높다. 아래만 고치면 된다.

---

## 반영 1. visit_log — 세션 방식 → 시간 버킷 방문 기록으로 재설계

### 왜
제안서는 접속자를 **로그인~로그아웃 세션**으로 센다. 우리 서비스와 안 맞는다:
- 무상태 JWT — 서버에 세션 개념이 없고, refresh 쿠키로 로그인이 몇 주씩 유지된다
- 웹앱 사용자는 로그아웃을 거의 누르지 않는다 → "열린 세션"이 유령으로 쌓여
  만료 마감 배치를 돌려도 접속자 수가 실제 이용자와 동떨어진다
- 접속자 수는 급상승 점수의 **분모**다. 분모가 부정확하면 시간대 보정 전체가 왜곡된다

### 어떻게
접속의 진짜 신호는 로그인 이벤트가 아니라 **인증된 API 요청**이다 (앱을 여는 순간 반드시 발생).

```sql
CREATE TABLE `visit_log` (
  `user_id`      bigint   NOT NULL COMMENT '접속 회원 (FK 없음 — 탈퇴 후에도 통계 보존)',
  `visited_hour` datetime NOT NULL COMMENT '시간 버킷 (분·초 절삭, 예: 2026-08-18 21:00:00)',
  PRIMARY KEY (`user_id`, `visited_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='회원 접속 기록 (시간 버킷당 1행. 세션성 — 물리 삭제 예외)';
```

- 기록: `JwtAuthenticationFilter` 인증 성공 시 `INSERT IGNORE` 1줄 (같은 사람·같은 시간대 = 1행)
- 로그아웃 기록·만료 세션 마감 배치·logged_out_at — **전부 불필요해짐** (선행 조건 파일 자체가 사라진다)
- 급상승 분모 (§8-(1) 교체):
  ```sql
  SELECT COUNT(DISTINCT user_id) FROM visit_log
  WHERE visited_hour >= #{windowStart} AND visited_hour < #{windowEnd}
  ```
- PM이 원한 "누가 얼마나 자주 접속하나"도 이 테이블이 답한다:
  회원별 접속일수(월간), 시간대 분포, DAU/MAU 전부 이 한 테이블에서 조회 가능
- 보존기간 정책 행 추가할 것: `visit_log_retention_days` (예: 365) — 알림 정리 배치(04:40)와 같은 패턴

## 반영 2. 알림(§9) — **이번에 구현하지 않는다 (PM 확정, 2026-08-18)**

- **§9 전체 보류.** 1차 적용 범위는 집계(§2~§8)와 조회(§10)까지 — 급상승 알림은 발송하지 않는다
- §6의 `notifications.type` 주석 변경(TRENDING 추가)과 §7의 알림 관련 정책 3행
  (`trending_notify_cooldown_hours`, `trending_notify_daily_cap`)도 **함께 보류** — 쓰는 코드가 없는 값을 미리 넣지 않는다
- 나중에 구현하게 되면 지켜야 할 것 (그때를 위한 기록):
  - SQL 직접 INSERT 금지 — 웹 푸시(NotificationPushService)를 우회한다.
    발행은 앱 경로: **알림 행 INSERT → 생성 id 확보 → sendToUser(receiverId, toastId, title, body, targetType, targetId, boardId)**
    (계약 상세: HANDOFF-notification-tasks.md "계약 변경" 절)
  - §9의 수신 대상 판정(canRead 동일 규칙)·쿨다운·일일 상한(remaining/seq 2중 컷)·
    대상 제외 목록(탈퇴·차단·발송 직전 state 재확인) 설계는 잘 만들어져 있으니 그대로 서비스 로직으로 옮기면 된다

## 반영 3. baseline 계산 수정 — 관문 2가 실제보다 엄격해지는 결함

trending_post 에는 **활동이 있던 구간만 행이 생긴다.** 그래서 "직전 6개 구간 평균"을
행 평균(AVG)으로 구하면 조용했던 구간이 0으로 안 들어가 평소 수준이 부풀고,
**가끔 조용하다 튀는 글(급상승의 전형)이 가장 못 잡히는** 역설이 생긴다.

> 예: 6구간 중 5구간 조용 + 1구간 raw 8 → 진짜 평소 ≈ 1.3, 그러나 AVG 는 8.
> 이번 구간 raw 20 → 2.5배로 관문 2(3배) 탈락. SUM/6 이면 15배로 정상 통과.

§8-(4) baseline 서브쿼리를 교체한다 — **행 평균 → 구간 수 고정 나눗셈**:

```sql
LEFT JOIN (
  SELECT x.`post_id`,
         SUM(x.`raw_score`) / #{baselineWindows} AS `baseline_score`
  FROM `stats_trending_post` x               -- ※ 확정 테이블명 기준 (최종 반영본의 명명 체계 참조)
  JOIN (SELECT `window_id` FROM `stats_window`
         WHERE `window_id` < #{windowId}
         ORDER BY `window_id` DESC LIMIT #{baselineWindows}) recent
    ON recent.`window_id` = x.`window_id`
  GROUP BY x.`post_id`
) b ON b.`post_id` = tp.`post_id`
```

- 최근 6구간에 행이 하나도 없는 글 → LEFT JOIN NULL → 지금처럼 관문 2 면제 (의도대로)
- 서비스 초기(과거 구간이 6개 미만)엔 baseline 이 다소 낮게 잡혀 급상승이 잘 뜬다 — 오픈 초기엔 오히려 자연스러움

## 컨벤션 3건 (수정본에 반영)

1. **.sql 파일을 레포에 커밋하지 않는다** (팀 컨벤션) — `docs/sql/...` 전제 제거.
   DDL 은 qa_pilsa 에 수동 적용하고 `docs/integration-*/CHECKLIST.md` 에 기록
2. `policy_settings` 코드는 기존 15행과 같은 **소문자 snake_case** —
   `TRENDING_INTERVAL_MINUTES` → `trending_interval_minutes` (전 13행 동일)
3. 회원 화면용 급상승 목록(§10-(A))은 **api_endpoints 에 planned 로 등록** 후 진행
   (경로는 예: `GET /api/user/boards/trending` — PM 과 확정할 것)

## 유지할 것 (바꾸지 말 것)

- post_metric_snapshot 차분 방식, FK 미부여 사유 (moderation_log 와 동일 논리)
- trending_post 의 계산 근거 전 컬럼 보존 + 결정적 정렬(동점 처리)
- 관문 1·2·3 구조와 policy_settings 조정 가이드(§1)
- 순서 의존성 명시 (스냅샷 갱신은 집계 뒤) — 단 visit_log 재설계로 "만료 세션 마감 선행" 항목은 삭제됨

---

# 최종 반영본 (2026-08-18 PM 확정) — 이대로 수정본을 만들면 된다

## 테이블 명명 체계 (PM 확정)

파생 통계 테이블은 **`stats_` 접두사**로 묶는다 — 통계 기능이 늘어도 `stats_*` 로 모이고,
자바 패키지 `com.back.admin.stats` 와 1:1 대응된다 (패키지=테이블 단위 컨벤션).

| 원안 이름 | 확정 이름 | 비고 |
|---|---|---|
| visit_log (세션형) | `visit_log` (시간 버킷형) | 기존 `*_log` 계열(ban/penalty/reports/moderation/warning) 유지 — 파생이 아닌 **원본 기록** |
| post_metric_snapshot | `stats_post_snapshot` | |
| trending_window | `stats_window` | |
| trending_post | `stats_trending_post` | |

`boards.trending_enabled` 컬럼명과 `policy_settings` 의 `trending_*` 코드는 그대로 —
기능(급상승)을 가리키는 이름이라 테이블 접두사와 별개다.
집계 배치 SQL(§8)과 조회(§10)의 테이블 참조도 전부 새 이름으로 바꿔서 수정본을 만들 것.

## 신규 테이블 4개

```sql
-- ① 접속 기록 (세션형 → 시간 버킷 재설계. JWT 필터가 인증 성공 시 INSERT IGNORE)
CREATE TABLE `visit_log` (
  `user_id`      bigint   NOT NULL COMMENT '접속 회원 (FK 없음 — 탈퇴 후에도 통계 보존)',
  `visited_hour` datetime NOT NULL COMMENT '시간 버킷 (분·초 절삭, 예: 2026-08-18 21:00:00)',
  PRIMARY KEY (`user_id`,`visited_hour`),
  KEY `idx_visit_log_hour` (`visited_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='회원 접속 기록 (시간 버킷당 1행. 세션성 — 물리 삭제 예외)';

-- ② 게시글 지표 직전 스냅샷 (원안에서 이름만 변경)
CREATE TABLE `stats_post_snapshot` (
  `post_id`       bigint   NOT NULL COMMENT '대상 게시글 (FK 없음 — 통계 기준선 보존)',
  `view_count`    int      NOT NULL DEFAULT '0' COMMENT '스냅샷 시점의 누적 조회수',
  `like_count`    int      NOT NULL DEFAULT '0' COMMENT '스냅샷 시점의 좋아요 수',
  `comment_count` int      NOT NULL DEFAULT '0' COMMENT '스냅샷 시점의 공개 댓글 수 (state=normal, is_private=0)',
  `captured_at`   datetime NOT NULL COMMENT '이 스냅샷을 찍은 시각',
  PRIMARY KEY (`post_id`),
  KEY `idx_stats_post_snapshot_captured` (`captured_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='게시글 지표 직전 스냅샷 (증가분 계산 기준선)';

-- ③ 집계 구간 (원안에서 이름 변경 — active_user_count 출처는 visit_log 시간 버킷)
CREATE TABLE `stats_window` (
  `window_id`         bigint   NOT NULL AUTO_INCREMENT COMMENT '집계 창 번호',
  `window_start`      datetime NOT NULL COMMENT '구간 시작 (이상)',
  `window_end`        datetime NOT NULL COMMENT '구간 종료 (미만)',
  `interval_minutes`  int      NOT NULL COMMENT '구간 길이(분). 실행 당시 policy_settings 값 스냅샷',
  `active_user_count` int      NOT NULL DEFAULT '0' COMMENT '구간 내 접속 회원 수 (visit_log 시간 버킷 기준, 점수 보정 분모)',
  `candidate_count`   int      NOT NULL DEFAULT '0' COMMENT '점수가 계산된 후보 글 수',
  `trending_count`    int      NOT NULL DEFAULT '0' COMMENT '급상승으로 선정된 글 수',
  `created_at`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '집계 실행 시각',
  PRIMARY KEY (`window_id`),
  UNIQUE KEY `uq_stats_window` (`window_start`,`window_end`),
  KEY `idx_stats_window_start` (`window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='급상승 집계 창 (구간별 실행 기록)';

-- ④ 구간별 점수·선정 결과 (원안에서 이름 변경 + notified_at 제거 — 알림 미구현이라 쓰는 코드 없음)
CREATE TABLE `stats_trending_post` (
  `window_id`      bigint        NOT NULL COMMENT '집계 창 (→stats_window)',
  `post_id`        bigint        NOT NULL COMMENT '대상 게시글 (FK 없음 — 통계 보존)',
  `board_id`       bigint        NOT NULL COMMENT '대상 게시판 (→boards)',
  `read_scope`     varchar(20)   NOT NULL COMMENT '집계 당시 게시판 열람 범위 스냅샷: MEMBER / STUDENT / ALUMNI',
  `view_delta`     int           NOT NULL DEFAULT '0' COMMENT '구간 내 조회 증가분',
  `like_delta`     int           NOT NULL DEFAULT '0' COMMENT '구간 내 좋아요 순증 (취소 시 음수 가능)',
  `comment_delta`  int           NOT NULL DEFAULT '0' COMMENT '구간 내 공개 댓글 순증',
  `raw_score`      decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '가중 합산 점수 (조회×1 + 좋아요×5 + 댓글×3)',
  `baseline_score` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '평소 수준 = 직전 N개 구간 raw_score 의 SUM/N (행 평균 아님)',
  `spike_ratio`    decimal(8,2)  DEFAULT NULL COMMENT '평소 대비 배수 = raw/baseline. NULL = 이력 없음(신규 글, 관문2 면제)',
  `freshness`      decimal(6,4)  NOT NULL DEFAULT '1.0000' COMMENT '글 나이 감쇠 계수 (1=방금, 0.5=하루 경과)',
  `final_score`    decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT '최종 점수 = raw_score / 접속자수보정 × freshness',
  `rank_no`        int           DEFAULT NULL COMMENT '구간 내 최종 점수 순위 (1위부터)',
  `is_trending`    tinyint(1)    NOT NULL DEFAULT '0' COMMENT '급상승 선정 여부 (관문 1·2·3 통과)',
  PRIMARY KEY (`window_id`,`post_id`),
  KEY `idx_stats_trending_post_score` (`window_id`,`final_score`),
  KEY `idx_stats_trending_post_selected` (`window_id`,`is_trending`,`rank_no`),
  KEY `idx_stats_trending_post_post` (`post_id`,`window_id`),
  KEY `idx_stats_trending_post_board` (`board_id`,`window_id`),
  CONSTRAINT `fk_stats_trending_post_window` FOREIGN KEY (`window_id`) REFERENCES `stats_window` (`window_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_stats_trending_post_board` FOREIGN KEY (`board_id`) REFERENCES `boards` (`board_id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='급상승 인기글 집계 결과 (구간 × 게시글)';
```

## 수정되는 기존 테이블 — AS-IS / TO-BE

| 테이블 | AS-IS | TO-BE |
|---|---|---|
| `boards` | `trending_enabled` 없음 | `ADD COLUMN trending_enabled tinyint(1) NOT NULL DEFAULT 1 COMMENT '급상승 집계 대상 여부 (1=포함). 신설 게시판 기본 포함' AFTER state` |
| `notifications` | type 주석 COMMENT/REPLY/... | **변경 없음** — 원안 §6(TRENDING)은 알림 미구현으로 보류 |

원안 대비: 테이블명 stats_ 체계로 변경 / visit_log 세션형 → 시간 버킷 / notified_at 제거 / notifications 무변경.

## policy_settings 추가 12행 (소문자 snake_case)

```sql
INSERT IGNORE INTO `policy_settings` (`code`, `setting_value`, `description`) VALUES
('trending_interval_minutes',   '60',  '급상승 집계 주기(분). 60=1시간'),
('trending_weight_view',        '1',   '점수 가중치 — 조회 1건'),
('trending_weight_like',        '5',   '점수 가중치 — 좋아요 1건'),
('trending_weight_comment',     '3',   '점수 가중치 — 댓글 1건'),
('trending_min_active_users',   '5',   '접속자수 보정 분모의 하한 (심야 소수 접속 시 점수 폭주 방지)'),
('trending_halflife_hours',     '24',  '글 나이 감쇠 반감기(시간). 24=하루 지난 글은 계수 0.5'),
('trending_min_score',          '10',  '관문1 — raw_score 최소 활동량'),
('trending_spike_ratio',        '3.0', '관문2 — 평소 대비 몇 배 이상이어야 급상승인지 (신규 글 면제)'),
('trending_baseline_windows',   '6',   '관문2 — 평소 수준 계산 구간 수 (SUM/N 고정 나눗셈)'),
('trending_top_n',              '5',   '관문3 — 구간당 선정 최대 글 수'),
('trending_post_max_age_hours', '168', '후보 글 최대 나이(시간). 168=7일'),
('visit_log_retention_days',    '365', '접속 기록 보존 일수 — 경과 시 새벽 배치가 물리 삭제');
```

원안 13행 대비: 알림용 2행(trending_notify_*) 제외, visit_log_retention_days 추가.
