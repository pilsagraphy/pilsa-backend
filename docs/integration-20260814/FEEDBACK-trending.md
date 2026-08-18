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
  FROM `trending_post` x
  JOIN (SELECT `window_id` FROM `trending_window`
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
