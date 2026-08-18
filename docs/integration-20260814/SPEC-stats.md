# SPEC-stats — 통계 수집·집계 (접속 / 급상승 / 주간 가입)

> 작성: 2026-08-18, 브랜치 `통계`. DDL(테이블 3종 + `boards.trending_enabled`)은 PM이 qa_pilsa에 적용 완료 —
> 정본 기록은 [CHECKLIST.md §4](CHECKLIST.md) 참고. 이 문서는 **코드 쪽 계약**을 적는다.
> 이번 범위는 **수집·집계 4개**이며 조회 API는 포함하지 않는다(§5).

---

## 1. 테이블 3개로 끝내는 이유

### 제안본 5개 → 확정 3개 (결정 이력)

| 제안본 | 확정 | 왜 |
|--------|------|-----|
| `visit_log` (세션형) | **`stats_access_hourly`** (시간 버킷형) | 무상태 JWT라 세션이 없고 로그아웃을 안 눌러 유령 세션이 쌓인다 → 인증 요청 시 `INSERT IGNORE` 로 시간대당 1행 |
| `post_metric_snapshot` | **삭제** | 누적값을 집계 행에 저장해 다음 집계가 직전 행을 기준선으로 쓴다 |
| `trending_window` | **삭제** | 접속자 수는 `stats_access_hourly` 에서 그때그때 세면 된다 (중복 저장 불필요) |
| `trending_post` | **`stats_post_hourly`** | 위 둘을 흡수. PK `(stat_hour, post_id)` |
| — | **`stats_signup_weekly`** 신설 | 탈퇴 90일 정리가 users 행을 지워 과거 가입수가 소급 감소 → 스냅샷 필요 |

### 컬럼 확정

| 항목 | 제안본 | 확정 |
|------|--------|------|
| `notified_at` | 있음 | **제거** (알림 미구현) |
| `active_user_count` | `trending_window` 에 저장 | **저장 안 함** — 원본에서 계산 |
| `view_count`/`like_count`/`comment_count` | 별도 snapshot 테이블 | **집계 행에 포함** |
| `boards.trending_enabled` | 추가 | **그대로** |
| `notifications.type` | TRENDING 추가 | **변경 없음** (알림 미구현) |

### 로직 확정 2건

| 항목 | 제안본 | 확정 |
|------|--------|------|
| baseline | `AVG(raw_score)` 직전 6행 | **`SUM(raw_score)/6`** — 활동 없던 구간이 0으로 안 들어가 평소 수준이 부풀고, *조용하다 튀는 글*이 가장 못 잡히던 결함 |
| 적재 컷 | `delta <> 0` | **`raw_score >= 5`** (`trending_min_delta_score`) — 조회 1~2회짜리 구간까지 행을 만들 필요 없음. 스킵분은 누적값 덕에 다음 행에서 잡힘(손실 없음) |

### 범위·컨벤션 확정

- **알림 발송 전체 보류** — 1차는 집계까지. 기준을 먼저 튜닝하고 알림은 나중에. `notifications` 변경·알림 정책 2행도 보류
  (쓰는 코드가 없는 값은 넣지 않는다).
- **스냅샷 갱신 단계 삭제** — 누적값을 집계 INSERT 에 함께 넣으므로 "집계 후 스냅샷 갱신" 순서 의존성이 사라졌다.
- **관리자 통계 화면·API 는 3기.** 이번 커밋의 범위는 수집·집계 4개이며, 조회 API 계약은 §5 에만 적어 둔다.
- 정책 코드는 기존 15행과 같은 **소문자 snake_case**(`trending_xxx`) — 자바 쪽 상수명만 대문자다.
- `.sql` 파일은 레포에 커밋하지 않는다 — DDL 수동 적용 + [CHECKLIST.md](CHECKLIST.md) 기록.

### 제안본에서 그대로 채택한 것

가중치(조회 1·좋아요 5·댓글 3), 3관문(10점 / 3배 / 5위), 접속자수 보정 + 하한 5, freshness 반감기 24h,
후보 나이 7일, 신규 글 관문2 면제, 댓글은 `state='normal' AND is_private=0` 만, 통계 테이블 FK 미부여(탈퇴·삭제 후에도 보존),
계산 근거 전 컬럼 보존(delta·baseline·spike·freshness·final 을 다 남겨 사후 검증 가능), 멱등 재실행.

> 수치는 전부 `policy_settings` 로 빼고 **위 값들을 코드 기본값**으로 두었다 — 행을 넣지 않으면 채택안 그대로 동작한다.

### 남은 3개의 역할

| 테이블 | 성격 | 왜 이렇게 |
|--------|------|-----------|
| `stats_access_hourly` | 원본 (user_id + 시간버킷) | 일·주·월·학기·연 통계를 전부 GROUP BY로 뽑는다. 단위별 테이블·배치는 데이터가 수억 행일 때 얘기 |
| `stats_post_hourly` | 구간 집계 + 판정 결과 | 누적값을 행에 저장해 다음 구간의 기준선으로 쓴다 → 별도 스냅샷 테이블 불필요 |
| `stats_signup_weekly` | 스냅샷 | 탈퇴 행 정리 배치(04:30)가 users를 물리 삭제해 과거 수치가 **소급 감소**하고 member_type도 졸업 시 바뀐다. 지금 고정하지 않으면 소실 |

접속자 수는 `stats_post_hourly`에 컬럼으로 저장하지 않는다 — 필요할 때 원본에서 세면 되고, 저장하면
같은 값이 두 곳에 남아 어긋난다.

---

## 2. 코드 구성 (`com.back.stats`)

| 과제 | 클래스 | 실행 시점 |
|------|--------|-----------|
| ① 접속 기록 | `access.service.AccessStatsRecorder` (+ `JwtAuthenticationFilter` 훅) | 인증 성공한 모든 요청 |
| ② 급상승 집계 | `trending.service.TrendingStatsBatch` / 주기 등록 `TrendingScheduleConfig` | `trending_interval_minutes` (기본 60분) |
| ③ 주간 가입 통계 | `signup.service.SignupStatsBatch` | 일 1회 04:50 |
| ④ 보존기간 정리 | `retention.service.StatsRetentionBatch` | 일 1회 05:00 |
| 정책 로드 | `policy.StatsPolicy` | 전 배치 공용 |

배치 순번: 04:00 제재 캐시 → 04:30 탈퇴 행 → 04:40 알림(담당자 과제) → **04:50 주간 가입** → **05:00 통계 정리**.

### ① 접속 기록
- `INSERT IGNORE INTO stats_access_hourly (user_id, access_hour)` — 시간 버킷은 **DB 시각**(`NOW()`)으로 절삭한다.
  집계 배치의 구간 경계와 같은 시계를 쓰게 해서 경계 어긋남을 없앤다.
- 같은 사람·같은 시간대 = 1행(PK 충돌 무시). 로그아웃은 기록하지 않는다.
- 요청 스레드에서 떼어내고(`@Async`, `REQUIRES_NEW`) 실패는 삼켜 로그만 남긴다 — 통계가 서비스 가용성보다 앞설 수 없다.
- 로그인 시점이 아니라 **인증 성공 시점**에 둔다: 로그인만 세면 토큰으로 계속 쓰는 세션이 통계에서 빠진다.

### ② 급상승 집계
집계 대상은 **직전에 끝난 구간** `[현재구간시작 - interval, 현재구간시작)`이다. 진행 중인 구간은 분모(접속자 수)가
덜 차서 구간끼리 점수를 비교할 수 없다.

1. **후보**: `posts.state='normal'` + `boards.state='normal' AND trending_enabled=1`
   + 글 나이 ≤ `trending_post_max_age_hours`(기본 168h). 나이·작성 시점은 **구간 종료 시각** 기준으로 판정한다.
2. **증가분**: 그 글의 가장 최근 행(구간 시작 **이전**)의 누적값이 기준선. 첫 등장은 0으로 본다(COALESCE).
   댓글은 `state='normal' AND is_private=0`만 센다 — 블라인드·비밀댓글이 노출을 키우면 조치가 역효과를 낸다.
3. **점수**: `raw = 조회Δ×1 + 좋아요Δ×5 + 댓글Δ×3`,
   `final = raw / MAX(구간 접속자수, trending_active_user_floor) × freshness`,
   `freshness = 1 / (1 + 글나이시간 / trending_freshness_scale_hours)`.
4. **적재 컷**: `raw < trending_min_delta_score`(기본 5)면 **행을 만들지 않는다**. 누적값을 행에 저장하므로
   건너뛴 증가분은 다음 적재 행에서 전부 잡힌다(손실 없음).
5. **baseline**: 직전 `trending_baseline_windows`(6)개 구간 `raw_score`의 **SUM/N**.
   행 평균(AVG) 금지 — 조용했던 구간엔 행이 없어 AVG는 "활동한 구간들의 평균"이 되고 평소 수준을 과대평가한다.
   `spike_ratio = raw / baseline`, 이력이 없으면 NULL.
6. **급상승 3관문**(셋 다 통과): `raw >= trending_min_score`(10) / `spike_ratio >= trending_spike_ratio`(3.0,
   **NULL이면 면제**) / `rank_no <= trending_top_n`(5). 순위는 `final_score` 내림차순.
7. **알림 없음** — 급상승 알림은 1차 미구현(PM 확정). notifications를 건드리지 않는다.

### ③ 주간 가입 통계
- 최근 `signup_stats_recalc_weeks`(2)주를 users에서 다시 집계해 UPSERT.
  주 시작일 = `DATE_SUB(DATE(created_at), INTERVAL WEEKDAY(created_at) DAY)` (월요일).
- **탈퇴자도 센다** — "그 주에 몇 명이 가입했나"는 나중 탈퇴로 바뀌지 않는다.
- 지나간 주의 행은 손대지 않는다. 하루 배치가 한 번 실패해도 다음 날 실행이 그 주를 메운다.

### ④ 보존기간 정리
- `stats_retention_days`(1825=5년) 경과 행을 `stats_access_hourly`·`stats_post_hourly`에서 **물리 삭제**
  (통계는 소프트삭제 대전제의 예외 — 증적이 아니라 집계 재료다).
- `stats_signup_weekly`는 지우지 않는다(연 52행, 사라진 원본을 대신하는 유일한 기록).

---

## 3. 재실행 안전(멱등) 근거

| 장치 | 없으면 생기는 일 |
|------|------------------|
| 기준선을 `stat_hour < 구간시작` 행에서만 읽는다 | 같은 구간 재실행 시 자기 행이 기준선이 되어 증가분이 0으로 무너진다 |
| 후보 조건을 wall clock이 아니라 구간 종료 시각으로 판정 | 나중에 재실행하면 후보 집합이 달라진다 |
| PK(`stat_hour`,`post_id`) upsert | 재실행마다 행이 중복 적재된다 |
| 동점 정렬을 `post_id`로 확정 | 순위가 실행마다 흔들린다 |
| 주간 가입은 주 단위 UPSERT | 같은 주가 여러 행으로 쌓인다 |

누락 구간은 `TrendingStatsBatch.aggregateBucket(구간시작, 구간종료)`로 같은 경로를 손으로 재실행하면 된다.
단, 누적 카운터는 과거로 되돌릴 수 없으므로 **오래된 구간을 뒤늦게 메우면 그 사이 증가분이 그 구간에 몰려 기록된다**(설계상 허용).

---

## 4. 정책 수치 (`policy_settings` / 기본값은 `StatsPolicy`)

| code | 기본 | 뜻 |
|------|------|-----|
| `trending_interval_minutes` | 60 | 집계 주기(분). 다음 실행부터 반영 |
| `trending_post_max_age_hours` | 168 | 후보 최대 글 나이 |
| `trending_min_delta_score` | 5 | 적재 컷 |
| `trending_baseline_windows` | 6 | 평소 수준 산출 구간 수 |
| `trending_min_score` | 10 | 관문1 |
| `trending_spike_ratio` | 3.0 | 관문2 |
| `trending_top_n` | 5 | 관문3 |
| `trending_weight_view` / `_like` / `_comment` | 1 / 5 / 3 | raw_score 가중치 |
| `trending_active_user_floor` | 5 | 점수 분모 하한 |
| `trending_freshness_scale_hours` | 24 | freshness 감쇠 기준 |
| `signup_stats_recalc_weeks` | 2 | 가입 재집계 구간(주) |
| `stats_retention_days` | 1825 | 접속·게시글 집계 보존 일수 |

행이 없어도 코드 기본값으로 동작한다. 행을 넣으면 **재배포 없이** 조정된다.
값이 비었거나 숫자가 아니면 경고 로그 후 기본값으로 되돌린다 — 잘못된 설정이 배치를 멈추게 하지 않는다.

---

## 5. 이번 범위에서 제외 — 조회 API

수집·집계까지가 이번 과제다. 조회 API를 붙일 때의 계약만 미리 못 박아 둔다:

- **권한 필터 필수**: `admin_level >= 1` 이거나 `read_scope='MEMBER'` 또는 `read_scope = member_type`.
  집계 행에 `read_scope` 스냅샷이 있으므로 boards 재조인 없이 필터할 수 있다.
- 급상승 목록은 `idx_stats_post_hourly_selected (stat_hour, is_trending, rank_no)`를 타게
  "최신 구간 + `is_trending=1` + rank 순"으로 뽑는다.
- 접속 통계(일/주/월/학기/연)는 `stats_access_hourly` GROUP BY로 만든다 — 집계 테이블을 새로 만들지 않는다.
- 경로를 정할 때 `api_endpoints` 테이블에 먼저 등록하고 코드가 그 표기를 따른다.

---

## 6. 검증 상태

- `./gradlew compileJava` 통과.
- `TrendingStatsBatchTest` (DB 없이 매퍼 대역) — 적재 컷 / 첫 등장 spike NULL 면제 / baseline=SUM/N /
  관문2 탈락 / top_n 초과 탈락 / 후보 없음 시 미적재 4케이스 통과.
- **실DB 기동 검증은 미실시** — 접속 기록 1행 생성, 급상승 배치 1구간 적재, 주간 가입 UPSERT 멱등,
  정리 배치 삭제 건수는 QA 기동 후 확인이 필요하다(§7).

## 7. QA 기동 시 확인할 것

1. 로그인 후 아무 API 호출 → `SELECT * FROM stats_access_hourly` 에 (내 user_id, 현재 시각의 정시) 1행.
   같은 시간대에 여러 번 호출해도 1행 유지.
2. 급상승 배치는 기동 후 한 주기(기본 60분) 뒤 첫 실행 — 로그 `급상승 집계 완료 - 구간 ...` 확인.
   즉시 보려면 `trending_interval_minutes`를 낮추거나 `aggregateBucket`을 임시 호출.
3. `SignupStatsBatch`/`StatsRetentionBatch`는 04:50 / 05:00. 즉시 확인은 cron 을 임시 조정.
4. 같은 구간을 두 번 집계해도 `stats_post_hourly` 행 수가 늘지 않는지(멱등) 확인.
