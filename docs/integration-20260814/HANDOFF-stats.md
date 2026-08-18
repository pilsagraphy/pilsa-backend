# 통계 — 담당자 과제

> **테이블·정책은 qa_pilsa 에 이미 적용됨. 코딩만 하면 된다.** DDL 은 CHECKLIST.md 참고.
> 관리자 통계 화면/API 는 3기 과제 — 이번 범위 아님.

## 적용된 테이블 3개

| 테이블 | 내용 | 비고 |
|---|---|---|
| `stats_access_hourly` | `user_id` + `access_hour`(시간 버킷) | 접속 원본. 일·주·월·학기·연 통계는 전부 이 테이블 `GROUP BY` |
| `stats_signup_weekly` | 주 시작일(월) + 가입수/재학/졸업 | 8주치 백필 완료 |
| `stats_post_hourly` | 구간 × 게시글 집계 + 급상승 판정 | 스냅샷·구간 테이블 없음 (아래 참조) |

`boards.trending_enabled`(기본 1) 추가됨 — 집계는 이 컬럼으로 대상 게시판을 고른다.

## 코딩할 것 4개

**① 접속 기록** — `JwtAuthenticationFilter` 인증 성공 시
- `INSERT IGNORE INTO stats_access_hourly (user_id, access_hour) VALUES (?, 시간버킷)`
- 같은 사람·같은 시간대 = 1행. 로그아웃 기록 없음

**② 급상승 집계 배치** — `trending_interval_minutes`(60분) 주기
- 후보: `posts.state='normal'` + `boards.state='normal' AND trending_enabled=1` + 글 나이 ≤ `trending_post_max_age_hours`
- 기준선(직전 누적값)은 **그 글의 가장 최근 행**에서:
  `(SELECT view_count FROM stats_post_hourly WHERE post_id=? ORDER BY stat_hour DESC LIMIT 1)` → 첫 등장은 `COALESCE(...,0)`
- 접속자 수: `SELECT COUNT(DISTINCT user_id) FROM stats_access_hourly WHERE access_hour >= ? AND access_hour < ?`
  → 컬럼으로 저장하지 않는다. 필요할 때 조인해서 쓴다
- 점수: `raw = 조회Δ×1 + 좋아요Δ×5 + 댓글Δ×3` → `final = raw / MAX(접속자수, 5) × freshness`
  `freshness = 1/(1 + 글나이시간/24)`
- **적재 컷**: `raw_score >= trending_min_delta_score`(5) 인 글만 INSERT. 미달 구간은 건너뛴다
  (누적값을 행에 저장하므로 건너뛴 증가분은 다음 행에서 전부 잡힘 — 손실 없음)
- baseline: **행 평균(AVG) 금지** → 직전 `trending_baseline_windows`(6)개 **구간**의 `SUM(raw_score)/6`
- 급상승 판정 3관문 (셋 다 통과):
  `raw_score >= trending_min_score`(10) / `spike_ratio >= trending_spike_ratio`(3.0, NULL이면 면제) / `rank_no <= trending_top_n`(5)
- 댓글은 `state='normal' AND is_private=0` 만 카운트

**③ 주간 가입 통계 배치** — 일 1회
- 최근 `signup_stats_recalc_weeks`(2)주를 `users` 에서 다시 집계해 UPSERT (멱등)
- 주 시작일: `DATE_SUB(DATE(created_at), INTERVAL WEEKDAY(created_at) DAY)`

**④ 정리 배치** — 일 1회, `stats_retention_days`(1825=5년) 경과 행 물리 삭제
- 대상: `stats_access_hourly`, `stats_post_hourly` (통계는 소프트삭제 예외)
- `stats_signup_weekly` 는 **지우지 않는다** (연 52행)
- 기존 배치 순번: 04:00 제재캐시 → 04:30 탈퇴행 → 04:40 알림 → **그 뒤로**

## 금지·주의

- **수치 하드코딩 금지** — 위 모든 숫자는 `policy_settings` 에서 읽는다 (행 없으면 코드 기본값)
- **알림 발송 안 한다** — 급상승 알림은 1차 미구현 (PM 확정). `notifications` 손대지 말 것
- **.sql 파일 레포 커밋 금지** — DDL 은 수동 적용 + CHECKLIST 기록
- 배치는 재실행 안전하게 (같은 구간 다시 돌려도 결과 동일)
- 조회 시 권한 필터 필수 — `admin_level>=1` 이거나 `read_scope='MEMBER'` 또는 `read_scope=member_type`

## 왜 이렇게 나눴나 (질문 대비)

- **스냅샷·구간 테이블 없음**: 누적값과 접속자 수를 집계 행/원본에서 얻으므로 테이블 2개가 불필요해짐
- **가입 통계만 스냅샷**: 탈퇴 90일 정리 배치가 `users` 행을 물리 삭제해 과거 수치가 소급 감소.
  `member_type` 도 졸업 시 바뀜 → 지금 안 남기면 소실
- **접속은 단위별 테이블 없음**: `stats_access_hourly` 하나에서 `GROUP BY` 로 모든 기간이 나옴.
  단위별 테이블·배치를 만드는 건 데이터가 수억 행일 때 얘기
- **행 폭증 없음**: 최근 7일 글 + 컷 통과 구간만 적재 → 우리 규모 하루 수십 행
