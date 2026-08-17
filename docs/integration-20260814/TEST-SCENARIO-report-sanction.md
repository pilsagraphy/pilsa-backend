# 신고·제재 시나리오 테스트

> 위에서부터 순서대로 실행하는 **연결된 시나리오**다. 중간에 만든 postId 를 뒤에서 계속 쓰므로 건너뛰지 말 것.
> 성공만 확인하지 않는다 — **같은 API 를 반복 호출해 실패 응답까지** 확인한다.
>
> 도구: Swagger `http://localhost:8080/swagger-ui/index.html`.
> 로그인: `POST /api/auth/login` → 응답의 `accessToken` 을 우측 상단 **Authorize** 에 넣는다 (`Bearer` 빼고 토큰 값만).
> **계정을 바꿀 때마다 Authorize 를 다시 넣는다.** 비밀번호는 전 계정 동일 (TEST-PLAN §1 — wm5256 과 같음).

## 등장 계정

| 계정 | 역할 |
|---|---|
| `t_stu2` | **가해자** — 신고당할 글·댓글 작성, 벌점 누적으로 정지까지 감 |
| `t_stu` | **신고자** — 신고 접수 전담 |
| `t_alu` | 제2 신고자 (동일 대상 중복 신고 → 벌점 이중 부과 방지 확인용) |
| `t_adm1` | **관리자 Lv1** — 블라인드/복원/삭제/제재 해제 전부 Lv1 로 수행 (Lv1 이면 충분함을 겸사 확인) |

메모할 값: `{P1}`~`{P7}` 게시글 id, `{C1}` 댓글 id. 게시판은 자유게시판 `boardId=2` 고정.

## 사전 초기화 (이전 실행 잔재 제거 — 처음 돌리면 생략 가능)

```sql
-- t_stu2(97) 제재 초기화 + 테스트 신고 제거 (재실행 시 중복신고 409 가 잘못 뜨는 것 방지)
DELETE FROM ban_log      WHERE user_id = 97;
DELETE FROM warning_log  WHERE user_id = 97;
DELETE FROM penalty_log  WHERE user_id = 97;
UPDATE users SET ban_status='none', banned_until=NULL WHERE user_id = 97;
DELETE FROM reports_log  WHERE reporter_id IN (96, 98);
```

---

## 1단계. 콘텐츠 준비 — `t_stu2` 로그인

`POST /api/auth/login` → `{"loginId":"t_stu2","password":"(공통 비밀번호)"}`

① 글 7개 등록 — `POST /api/user/boards/2/posts` (multipart) 를 **7번** 호출:
```
title:   신고테스트 글1   (2~7도 동일 패턴)
content: 시나리오용 본문
isAnonymous: false
```
→ 매번 `200 {"message":"...","postId":N}` — **postId 를 {P1}~{P7} 로 메모**

② `{P1}` 에 자기 댓글 1개 — `POST /api/user/boards/2/posts/{P1}/comments`
```json
{"content":"신고테스트 댓글","parentCommentId":null,"isAnonymous":false,"isPrivate":false}
```
③ `GET /api/user/boards/2/posts/{P1}` → 응답 comments 배열에서 **commentId 를 {C1} 로 메모**

---

## 2단계. 신고 접수 — `t_stu` 로 로그인 교체

같은 API(`POST /api/user/reports`)를 값만 바꿔 **7번 호출**한다. 순서대로:

| # | 요청 본문 | 기대 응답 |
|---|---|---|
| 1 | `{"targetType":"post","targetId":{P1},"reasonId":2}` | `200 {"message":"신고가 접수되었습니다."}` |
| 2 | **1번과 완전히 동일한 본문 재호출** | `409 {"message":"이미 신고한 게시글/댓글입니다."}` |
| 3 | `{"targetType":"comment","targetId":{C1},"reasonId":1}` | `200` — 같은 대상이라도 글/댓글은 별개 신고 |
| 4 | `{"targetType":"post","targetId":999999,"reasonId":1}` | `404 {"message":"존재하지 않는 게시글/댓글입니다."}` |
| 5 | `{"targetType":"user","targetId":{P1},"reasonId":1}` | `400 {"message":"targetType은 post 또는 comment여야 합니다."}` |
| 6 | `{"targetType":"post","targetId":{P1}}` (reasonId 누락) | `400 {"message":"신고 대상과 사유는 필수입니다."}` |
| 7 | `{"targetType":"post","targetId":{P2},"reasonId":8,"detail":"기타 사유 상세"}` | `200` — 기타(8)는 detail 함께 |

**자기 글 신고 실패 확인** — t_stu 본인 글이 필요하다:
- `POST /api/user/boards/2/posts` 로 글 1개 등록 (postId `{P0}`)
- `POST /api/user/reports` → `{"targetType":"post","targetId":{P0},"reasonId":1}`
- 기대: `400 {"message":"본인이 작성한 게시글/댓글은 신고할 수 없습니다."}`

**중복 신고의 벌점 이중 부과 방지 준비** — `t_alu` 로 로그인 교체 후 같은 대상을 또 신고:
- `{"targetType":"post","targetId":{P1},"reasonId":5}` → `200` (신고자가 다르면 접수됨 — {P1} 에 신고 2건)

DB 확인:
```sql
SELECT reporter_id, target_type, target_id, reason_id, status FROM reports_log ORDER BY report_id DESC LIMIT 5;
-- {P1} post 에 pending 2건(신고자 96, 98), {C1} comment 1건, {P2} 1건
```

---

## 3단계. 권한 실패 — 계정 그대로(`t_alu`), 관리자 API 를 찔러본다

| 호출 | 기대 |
|---|---|
| `GET /api/admin/reports/posts` | `403 {"message":"접근 권한이 없습니다."}` |
| `PATCH /api/admin/reports/select-delete` (본문 아무거나) | `403` |
| Authorize 를 **비우고** `POST /api/user/reports` | `401 {"message":"인증이 필요합니다. ..."}` |

---

## 4단계. 블라인드 — `t_adm1` 로 로그인 교체

① 신고 목록 확인 — `GET /api/admin/reports/posts?page=1&size=10&status=pending`
→ `{P1}`(신고 2건 그룹핑), `{P2}` 가 보인다

② 블라인드 — `PATCH /api/admin/reports/select-blind`
```json
{"targetType":"post","targetIds":[{P1}],"reasonId":2,"detail":"욕설 신고 다수"}
```
→ `200 {"successCount":1,"failCount":0,"failures":[]}`

③ **블라인드는 벌점도 없고 신고도 종료하지 않는다** — DB 확인:
```sql
SELECT state FROM posts WHERE post_id = {P1};                      -- blind
SELECT status FROM reports_log WHERE target_id = {P1} AND target_type='post';  -- 여전히 pending 2건
SELECT COUNT(*) FROM penalty_log WHERE user_id = 97;               -- 0 (블라인드는 벌점 없음)
```

④ 작성자 차단 확인 — **`t_stu2` 로 로그인 교체**:
- `GET /api/user/boards/2/posts/{P1}` → `404` (블라인드 글은 학생 화면에서 없는 글)
- `PATCH /api/user/boards/2/posts/{P1}` (title/content 수정 시도) → `404` — **작성자 본인도 수정 불가 (증적 보호)**

---

## 5단계. 복원(반려) — `t_adm1` 로 로그인 교체

① `PATCH /api/admin/reports/select-restore`
```json
{"targetType":"post","targetIds":[{P1}]}
```
→ `200 {"successCount":1,...}` (복원은 사유를 받지 않는다)

② **같은 본문으로 즉시 재호출** → 이미 normal 이라 no-op — `failures` 에 담기거나 successCount 0. **중요한 건 로그가 중복 생기지 않는 것**:
```sql
SELECT COUNT(*) FROM moderation_log WHERE target_id={P1} AND target_type='post' AND applied_state='normal';  -- 1 (재호출해도 1)
SELECT status FROM reports_log WHERE target_id={P1} AND target_type='post';  -- rejected 2건 (pending 전부 일괄 반려)
```

③ `t_stu2` 로 `GET /api/user/boards/2/posts/{P1}` → `200` — 글이 되살아났다

---

## 6단계. 삭제와 벌점 — `t_adm1`

① `PATCH /api/admin/reports/select-delete`
```json
{"targetType":"post","targetIds":[{P2}],"reasonId":1,"detail":"스팸 확정"}
```
→ `200 {"successCount":1,"failCount":0,"failures":[]}`

② **동일 본문 재호출** → `200 {"successCount":0,"failCount":1,"failures":[{"id":{P2},"message":"이미 삭제된 게시글입니다."}]}` — **벌점이 두 번 붙지 않는다**:
```sql
SELECT points, void_action_id FROM penalty_log WHERE user_id=97;   -- +2 한 건뿐
SELECT status FROM reports_log WHERE target_id={P2} AND target_type='post';  -- resolved
```

③ 삭제된 대상 신고 거부 — **`t_alu` 로 교체** 후 `POST /api/user/reports`
`{"targetType":"post","targetId":{P2},"reasonId":1}` → `409 {"message":"이미 삭제된 게시글/댓글입니다."}`

④ 복원하면 벌점 회수 — **`t_adm1`** 로 `select-restore` `{"targetType":"post","targetIds":[{P2}]}`:
```sql
SELECT points, void_action_id FROM penalty_log WHERE user_id=97;   -- void_action_id 채워짐 (회수)
```
> ⚠ 단, 이미 rejected/resolved 된 신고는 되살아나지 않는다 — 신고 상태는 처리 이력이다.

---

## 7단계. 에스컬레이션 — 벌점 10점 → 경고 1회 → 1주 정지

수치: 삭제 1건 = +2 (`caution_per_delete`), 10점 = 경고 1회 (`cautions_per_warning`), 경고 1회 = 1주 정지 (`ban_policy BAN_W1`).
→ **유효 벌점 5건이 필요하다.** 6단계에서 {P2} 는 회수됐으므로 {P3}~{P7} 5건을 쓴다.

① `t_adm1` 로 일괄 삭제 — `PATCH /api/admin/reports/select-delete`
```json
{"targetType":"post","targetIds":[{P3},{P4},{P5},{P6},{P7}],"reasonId":1,"detail":"도배"}
```
→ `200 {"successCount":5,"failCount":0}` (신고 없이도 관리자는 즉시 조치 가능 — 이게 관리자의 특권)

② DB 로 파이프라인 전체 확인:
```sql
SELECT SUM(points) FROM penalty_log WHERE user_id=97 AND void_action_id IS NULL;  -- 10
SELECT COUNT(*) FROM warning_log WHERE user_id=97;                                -- 1
SELECT source, warning_no, ban_type, ends_at FROM ban_log WHERE user_id=97;       -- auto / 1 / temporary / 7일 뒤
SELECT ban_status, banned_until FROM users WHERE user_id=97;                      -- temporary / 7일 뒤
```

③ 정지 회원 로그인 차단 — **로그아웃 후 `t_stu2` 로 로그인 시도**:
→ `403 {"message":"정지된 계정입니다.","banType":"temporary","bannedUntil":"(7일 뒤)"}`

④ 제재 화면 확인 — `t_adm1` 로:
- `GET /api/admin/sanctions/users` → t_stu2 가 `tag:"temporary"` 로 보임
- `GET /api/admin/sanctions/users/97` → `{"warningCount":1,"cautionRemainder":0,"reportDeletedCount":...}`
- `GET /api/admin/sanctions/users/97/reports/posts` → 삭제된 글 내역

---

## 8단계. 제재 해제 — `t_adm1`

① `POST /api/admin/sanctions/users/97/lift` (본문 없음)
→ `200 {"message":"제재가 해제되었습니다."}`

② **동일 호출 재실행** → 이미 해제된 상태에 대한 응답 확인 (에러 없이 처리되거나 안내 메시지 — 500 만 아니면 정상)

③ `t_stu2` 로 로그인 → **성공.** DB 확인:
```sql
SELECT ban_status FROM users WHERE user_id=97;                        -- none
SELECT lifted_by, lifted_at FROM ban_log WHERE user_id=97;            -- t_adm1 의 userId(99) 기록
SELECT COUNT(*) FROM warning_log WHERE user_id=97;                    -- 1 — 경고는 남는다!
```
> ⚠ **해제는 정지만 푼다. 경고 1회는 유효(시효 365일)** — 다음에 또 10점을 채우면 경고 2회 = **한 달 정지**로 직행한다. 이게 의도된 동작이다.

---

## 9단계. 정리 (다음 테스트를 위한 원상복구)

```sql
DELETE FROM ban_log      WHERE user_id = 97;
DELETE FROM warning_log  WHERE user_id = 97;
DELETE FROM penalty_log  WHERE user_id = 97;
UPDATE users SET ban_status='none', banned_until=NULL WHERE user_id = 97;
DELETE FROM reports_log  WHERE reporter_id IN (96, 98);
-- 테스트 글은 소프트삭제 상태 그대로 둬도 무방 (학생 화면에 안 보임)
```

## 체크리스트 요약

- [ ] 신고: 성공 / 재호출 409 / 본인 글 400 / 없는 대상 404 / 잘못된 타입 400 / 사유 누락 400
- [ ] 신고자가 다르면 같은 대상도 접수됨 (그룹핑 확인)
- [ ] 학생이 관리자 API → 403, 미인증 → 401
- [ ] 블라인드: 벌점 없음 + 신고 pending 유지 + 작성자도 404 (조회·수정 모두)
- [ ] 복원: 신고 일괄 rejected + 재호출해도 로그 중복 없음
- [ ] 삭제: 벌점 +2 + 신고 일괄 resolved + 재호출 시 failures 부분 성공 + 벌점 이중 부과 없음
- [ ] 삭제된 대상 신고 → 409
- [ ] 복원 시 벌점 회수 (void_action_id)
- [ ] 10점 도달 → 경고 1 + ban_log(auto) + 로그인 403 (banType/bannedUntil)
- [ ] 해제 → 로그인 성공, 경고는 잔존 (다음 정지는 한 달)
