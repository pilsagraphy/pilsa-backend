# 급상승 인기글 — 제안본(2026-08-18) 대비 변경점

> 설계 방향·판정 기준(가중치, 3관문, freshness, 조정 가이드)은 **그대로 채택**. 아래만 바뀜.
> 확정 DDL·정책은 **qa_pilsa 적용 완료**. 구현 지시서: [HANDOFF-stats.md](HANDOFF-stats.md)

## 테이블 — 5개 → 3개

| 제안본 | 확정 | 왜 |
|---|---|---|
| `visit_log` (세션형) | **`stats_access_hourly`** (시간 버킷형) | 무상태 JWT라 세션이 없고 로그아웃을 안 눌러 유령 세션이 쌓임 → 인증 요청 시 `INSERT IGNORE` |
| `post_metric_snapshot` | **삭제** | 누적값을 집계 행에 저장 → 다음 집계가 직전 행을 기준선으로 씀 |
| `trending_window` | **삭제** | 접속자 수는 `stats_access_hourly` 에서 그때그때 세면 됨 (중복 저장 불필요) |
| `trending_post` | **`stats_post_hourly`** | 위 둘 흡수. PK `(stat_hour, post_id)` |
| — | **`stats_signup_weekly`** 신설 | 탈퇴 90일 정리가 `users` 행을 지워 과거 가입수가 소급 감소 → 스냅샷 필요 |

## 컬럼

| 항목 | 제안본 | 확정 |
|---|---|---|
| `notified_at` | 있음 | **제거** (알림 미구현) |
| `active_user_count` | `trending_window` 에 저장 | **저장 안 함** (원본에서 계산) |
| `view_count`/`like_count`/`comment_count` | 별도 snapshot 테이블 | **집계 행에 포함** |
| `boards.trending_enabled` | 추가 | **그대로** |
| `notifications.type` | TRENDING 추가 | **변경 없음** (알림 미구현) |

## 로직 2건

| 항목 | 제안본 | 확정 |
|---|---|---|
| baseline | `AVG(raw_score)` 직전 6행 | **`SUM(raw_score)/6`** — 활동 없던 구간이 0으로 안 들어가 평소 수준이 부풀고, *조용하다 튀는 글*이 가장 못 잡히던 결함 |
| 적재 컷 | `delta <> 0` | **`raw_score >= 5`** (`trending_min_delta_score`) — 조회 1~2회짜리 구간까지 행을 만들 필요 없음. 스킵분은 누적값 덕에 다음 행에서 잡힘(손실 없음) |

## 범위

- **§9 알림 발송 전체 보류** — 1차는 집계 + 조회까지. 기준을 먼저 튜닝하고 알림은 나중에
- **§6 `notifications` 변경, 알림 정책 2행 보류** — 쓰는 코드가 없는 값은 넣지 않음
- **§8-(6) 스냅샷 갱신 단계 삭제** — 누적값을 집계 INSERT 에 함께 넣으므로 순서 의존성도 사라짐
- 관리자 통계 화면·API 는 **3기**

## 컨벤션

- 정책 코드 `TRENDING_XXX` → **`trending_xxx`** (기존 15행과 동일한 소문자 snake_case)
- 보존기간 정책 추가: `stats_retention_days=1825`(5년), `signup_stats_recalc_weeks=2`
- **.sql 파일은 레포에 커밋하지 않음** — DDL 수동 적용 + [CHECKLIST.md](CHECKLIST.md) 기록

## 그대로 채택한 것

가중치(조회1·좋아요5·댓글3), 3관문(10점 / 3배 / 5위), 접속자수 보정 + 하한 5, freshness 반감기 24h,
후보 나이 7일, 신규 글 관문2 면제, 댓글은 `state=normal AND is_private=0` 만, FK 미부여 사유,
계산 근거 전 컬럼 보존, 멱등 재실행, §1 조정 가이드, §10 조회 쿼리 골격.
