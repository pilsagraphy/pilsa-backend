# API 명세 ↔ 코드 대조표 (2026-08-15)

`qa_pilsa.api_endpoints` 84행과 실제 컨트롤러 매핑 65개를 (HTTP 메서드 + 경로) 기준으로 기계 대조한 결과다.
경로변수명 차이(`{postId}` vs `{id}`)는 같은 것으로 취급했다.

| 구분 | 건수 | DB status | 의미 |
|------|------|-----------|------|
| 일치 | 65 | `active` | 명세와 코드가 완전히 같음 |
| 미개발 | 19 | `planned` | 아직 개발 안 함 — 불일치 아님 |

> **불일치(mismatch) 0건, 명세에 없는 구현 0건.** 구현된 API 65개가 명세와 1:1로 맞는다.

---

## 2026-08-15 에 맞춘 것

| 항목 | 처리 |
|---|---|
| `GET /api/donations` (명예의전당) | 명세에 행이 없어 추가 (id=133) |
| `GET /api/user/mypage/profile` | 경로를 **`GET /api/role`** 로 변경 + 명세 추가 (id=134). 1기 `{"role":"STUDENTS"}` → 2기 `{"memberType","adminLevel"}` |
| 관리자 단건 조치 5종 | **코드에서 제거** — 블라인드/복원/삭제/신고반려/신고삭제 |
| 일괄 처리 `bulk-*` | 명세의 **`select-*` 3종으로 통일** (id=50·51·52, planned → active) |
| 관리자 메서드 6건 | 명세대로 **PATCH** 로 변경 — 게시판 수정/삭제, 문장 삭제, 회원 수정/정지/차단 |
| 알림 5건 | 코드 경로를 명세의 **`/api/user/mypage/toast`** 로 변경, 삭제는 `DELETE` → `PATCH .../delete` |
| 게시판 수정(36) 응답 예시 | "성공 실패 메세지"로만 적혀 있어 실제 반환값(AdminBoardResponse)으로 채움 |
| 알림 목록(25) linkUrl | 옛 경로 `/api/boards/...` → `/api/user/boards/...` 로 정정 |

### 조치 API는 선택 처리 3종뿐

단건도 `targetIds` 에 1건만 담아 같은 API로 처리한다. 신고관리·게시글관리·댓글관리 화면이 공유한다.

| API | 사유 | 벌점 | 신고 상태 |
|---|---|---|---|
| `PATCH /api/admin/reports/select-restore` | 안 받음 | — | `rejected` (반려) |
| `PATCH /api/admin/reports/select-delete` | 받음(미전달 시 최신 신고 사유) | 주의 +2 + 에스컬레이션 | `resolved` |
| `PATCH /api/admin/reports/select-blind` | 받음 | 없음 | `pending` 유지 (임시 조치) |

항목마다 독립 트랜잭션(REQUIRES_NEW)이라 일부가 실패해도 나머지는 처리되고, 결과는 `{successCount, failCount, failures[]}` 로 돌아온다.

---

## 미개발 19건 (`planned`)

| 영역 | 건수 | 엔드포인트 |
|---|---|---|
| 임시저장 (SPEC-A5) | 5 | `GET/POST .../drafts`, `GET/PUT/DELETE .../drafts/{draftId}` |
| 마이페이지 | 5 | `GET /api/user/mypage`, `/comments`, `/likes`, `/posts`, `PATCH /password/reset` |
| 관리자 대시보드 | 3 | `GET /api/admin/dashboard`, `/recent-reports`, `/recent-members` |
| 일정 | 2 | `GET /api/event/{eventId}`, `GET /api/event/google` |
| 게시판 부가 | 3 | `GET .../attachments/{attachmentId}`, `POST .../posts/images`, `GET /api/user/reports/reasons` |
| 관리자 댓글관리 | 1 | `GET /api/admin/comments` |

> `GET /api/user/mypage`(22)는 마이페이지 **활동 통계**(가입일·글수·댓글수·좋아요수·이번 학기 집계)라
> 신분·권한만 돌려주는 `GET /api/role`(134)과는 별개 API다. 서로 대체 관계가 아니다.

---

## 남은 판단거리

- 알림 5건(id 25~29)은 **phase 가 아직 `검토 필요`** 다. 경로·응답은 명세대로 맞췄지만 1기/2기 판정은 안 끝났다.
- `api_endpoints.status` 값: `active`(구현 완료·명세 일치) / `planned`(개발 예정) / `mismatch`(명세와 코드 불일치). 현재 mismatch 는 0건이다.
