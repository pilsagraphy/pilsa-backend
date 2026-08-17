# 기획안(1차 디자인 시안) 대비 백엔드 부족 기능 백로그

> 디자인 시안 PDF 전장(34판) + pilsa-frontend 코드를 대조해 정리. 2026-08-14, Claude 작성.
> 표기: 🔴 필수(기획 화면이 직접 요구) / 🟡 보완(있으면 화면 완성) / 🔵 정책 확정 필요(기획 질문)
> API 형식은 제안 초안이며, 착수 시 노션 명세에 확정본을 등록할 것.

---

## A. 게시판 관리 (관리자 > 커뮤니티 관리)

> ⚠️ **2026-08-14 2차 반영으로 아래 항목 중 다수가 이미 구현 완료됨.**
> 완료: A-1(게시판 CRUD·동적화), A-2 일부(댓글 관리는 미구현이나 조치 모듈 준비됨), G-1, G-2, G-4.
> H-1(알림)은 **부분 완료** — 웹 푸시·기기 등록부만 되어 있고 **발행과 알림함 API 는 담당자 과제**(H-1 항목 참고).
> 각 항목 머리에 상태를 표기했다. 미구현 항목만 팀원에게 배정하면 된다.

### A-1. ✅ **완료(2026-08-14)** 게시판 CRUD API — 시안 p4 "게시판 관리"
현재 게시판이 `BoardType` enum(1=공지, 2=자유, 3=정보)으로 **하드코딩**되어 있어
시안의 "새 게시판 생성" 버튼과 정면 충돌. DB에는 이번에 `boards.read_scope`/`write_level` 컬럼을
선반영해 두었으므로(#61), 정책을 DB 기반으로 전환하는 작업이 선행 과제.

| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| GET | /api/admin/boards | - | [{boardId, name(code), postCount, readScope, writeLevel, displayOrder}] | ADMIN |
| POST | /api/admin/boards | {name, readScope, writeLevel} | {boardId} | ADMIN |
| PUT | /api/admin/boards/{boardId} | {name?, readScope?, writeLevel?} | message | ADMIN |

- 열람 권한 드롭다운: 재학생(STUDENT)/동문(ALUMNI)/회원 전체(MEMBER)/전체 공개(ALL)
- 작성 권한 드롭다운: 일반회원(0)/관리 Lv.1~3(1~3)
- **선행 리팩터링**: BoardType의 adminWrite·defaultCategoryId·uploadDir을 boards 테이블(또는
  board 정책 컬럼)로 이전 → 신규 게시판이 코드 수정 없이 동작하도록. `/api/stu/{boardId}` 권한 검사도
  SecurityConfig 고정 규칙 대신 boards.read_scope/write_level 기반 동적 검사로 전환.
- 🔵 "전체 게시판 동기화" 버튼(시안 p5)의 의미 기획 확인 필요.

### A-2. 🔴 댓글 관리(전체 목록) API — 시안 p6 (담당 제안: 올리)
신고된 댓글이 아닌 **전체 댓글**을 게시판 필터/검색으로 조회·조치하는 화면. moderation 모듈 재사용으로 저비용.

| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| GET | /api/admin/comments | ?page&size&boardId&keyword | {totalPages,totalCount,comments:[{commentId,postId,boardId,boardName,authorName,content,created,state}]} | ADMIN |
| PATCH | /api/admin/comments/{id}/blind | {reasonId?,detail?} | message | ADMIN |
| PATCH | /api/admin/comments/{id}/restore | - | message | ADMIN |
| DELETE | /api/admin/comments/{id} | {reasonId?,detail?} | message (벌점 +2 자동) | ADMIN |
| POST | /api/admin/comments/bulk-delete | {commentIds,reasonId?,detail?} | BulkResult | ADMIN |

### A-3. 🟡 이전글/다음글 응답 확장 — 시안 p25 하단 내비게이션
현재 `prevPostApi`/`nextPostApi`가 post_id만 반환. 시안은 **카테고리 뱃지 + 제목 + 날짜** 표시.
→ BoardDetailResponse에 `{prev: {postId,title,categoryName,created}, next: {...}}` 구조 확장.

### A-4. 🟢 본문 인라인 이미지 업로드 (선업로드) — **구현 완료 (2026-08-17, A-5와 함께)**
| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| POST | /api/user/boards/{boardId}/posts/images | multipart(file) | {attachmentId, url:/files/{id}, ...} | 로그인+쓰기 |
| GET  | /files/{attachmentId} | - | 파일 스트리밍 | 공개 |
- 에디터 삽입 즉시 선업로드 → **안정 URL `/files/{attachmentId}`** 반환(소유권 draft→post 이전에도 불변).
- 미사용 고아 이미지 정리 = 업로드 대기(post_id·draft_id 둘 다 NULL) 24h 경과분 새벽 04:15 배치(`PendingAttachmentCleanupScheduler`).
- 🔵 본문 저장 포맷: **이번 구현은 HTML 기준**(리치 에디터 `<img src="/files/{id}">`). XSS 새니타이즈는 프론트 렌더 책임(HANDOFF-editor §0).
  → HANDOFF-editor.md 의 "content=마크다운" 기술과 상충하므로 PM/프론트와 최종 포맷 확정 필요(REVIEW-NOTES 참고).

### A-5. 🟢 임시저장(글 저장하기) — **구현 완료 (2026-08-17)**
📄 확정 DDL·근거: [SPEC-A5-drafts-DDL.md](SPEC-A5-drafts-DDL.md) (초안 [SPEC-A5-drafts.md](SPEC-A5-drafts.md) 대체). 패키지: `com.back.board.draft`.

| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| POST | /api/user/boards/{boardId}/drafts | {title?,content?,categoryId?,isAnonymous?,attachmentIds?} | {message,draftId,slotNo} | 로그인+쓰기 |
| PUT | /api/user/boards/{boardId}/drafts/{draftId} | 위와 동일 | {message} | 본인 |
| GET | /api/user/boards/{boardId}/drafts | - | {count, drafts:[{draftId,slotNo,title,preview,attachCnt,updatedAt}]} | 본인 |
| GET | /api/user/boards/{boardId}/drafts/{draftId} | - | 초안 전체+첨부(이어쓰기용) | 본인 |
| DELETE | /api/user/boards/{boardId}/drafts/{draftId} | - | {message} (물리 파일 포함 삭제) | 본인 |
| POST | /api/user/boards/{boardId}/drafts/attachments | multipart(file) | {attachmentId, url, ...} | 로그인+쓰기 |
| POST(수정) | /api/user/boards/{boardId}/posts | form-data에 draftId? 추가 | 기존과 동일(발행 시 초안+첨부 이관 후 초안 삭제) | 기존과 동일 |

- posts.state 재사용 금지 → **별도 drafts 테이블**(목록/조회수/신고/제재 쿼리 오염 방지).
- drafts는 소프트삭제 대전제의 **예외**(세션성 데이터) → state 컬럼 없이 물리 삭제.
- 보관 상한: **slot_no 1~5 + UNIQUE(user_id, slot_no)** 로 DB 강제(포화 시 409). 슬롯은 회원 단위(게시판 무관).
- 첨부/이미지: attachments 를 **방식 A(완화 CHECK)** 로 확장해 초안 첨부 지원. 발행은 UPDATE(소유권 이전) → DELETE(초안) 순서 고정.

### A-6. 🟡 목록 정렬 옵션 확장 — FE가 `sort=liked`(좋아요순) 사용 예정. 현재 created/viewCount만 지원.

## B. 관리자 홈 대시보드 — 시안 p1 (담당 제안: 도이)

### B-1. 🔴 대시보드 통계/최근 목록 API
| 메서드 | 경로 | 응답 | 권한 |
|--------|------|------|------|
| GET | /api/admin/dashboard | {newMembers, pendingReports, newPosts, totalMembers, recentReports:[5], recentMembers:[5]} | ADMIN |
- 🔵 "신규"의 기간 기준(오늘? 7일?) 기획 확정 필요.
- recentReports: [{targetType,targetId,boardCode,preview,createdAt}] / recentMembers: [{memberType,loginId,name,joinedAt}]

## C. 마이페이지 — 시안 p33·34 (전체 부재, 대형) (담당 제안: 가람)
> remote 브랜치 `마이페이지-현황조회`가 존재(착수 흔적, PR 미제출). 이어받아 진행 권장.

### C-1. 🔴 프로필/활동 요약
| 메서드 | 경로 | 응답 | 권한 |
|--------|------|------|------|
| GET | /api/user/mypage | {loginId, name, joinedAt, postCount, commentCount, likedCount, semester:{posts,comments,receivedLikes}} | 본인 |
- "이번 학기" 기준일(3/1, 9/1) 상수화. 삭제(state != normal) 글 포함 여부 🔵 확정 필요.

### C-2. 🔴 내가 쓴 글 / 내가 쓴 댓글 / 좋아요 누른 글 목록
| 메서드 | 경로 | 요청 | 응답 |
|--------|------|------|------|
| GET | /api/user/mypage/posts | ?page&size&sort&categoryId&keyword | 목록(제목/좋아요/조회수/작성일) |
| GET | /api/user/mypage/comments | ?page&size&sort&keyword | 목록(제목=원글, 내용, 작성일) |
| GET | /api/user/mypage/likes | ?page&size&sort&categoryId&keyword | 목록(제목/좋아요/조회수/작성일) |

### C-3. 🔴 내 정보 수정 — "정보 수정" 버튼. 수정 가능 범위(비밀번호? 전화? 전공?) 🔵 기획 확정 필요.

## D. 일정 달력 — 시안 p9·10 (담당 제안: 예지)

### D-1. 🔴 시간(시:분) + 종일 일정 지원
시안의 등록/수정 폼은 년/월/일 + **시:분 드롭다운 + 종일 체크박스**. 현재 API는 'YYYY-MM-DD' 날짜만 수신.
- EventRequest: `startAt`/`endAt`을 'YYYY-MM-DD HH:mm'로 확장 + `allDay`(boolean, true면 00:00~23:59 저장)
- 조회 응답에도 시간/allDay 포함. events 테이블은 datetime이라 **DDL 불필요**.

### D-2. 🔴 일정 카테고리 — **담당자 과제**
`events.category` 가 varchar 자유 입력이라 표기가 갈리고 프론트가 필터·색상 구분을 만들 수 없다.
- **`event_categories` 테이블 + 기본 시드는 적용 완료** (PM). API 설계·구현이 과제
- 지시서: `docs/integration-20260814/HANDOFF-event-category-tasks.md`
- `categories` 테이블은 게시판 전용(board_id 소속)이라 일정에 재사용 금지

## E. 방명록 — 시안 사이드바(회원 영역) (부재) (담당 제안: 해미)
### E-1. 🔴 guestbook 테이블 + CRUD
| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| GET | /api/guestbook | ?page&size | 목록 | 로그인 |
| POST | /api/guestbook | {content, isAnonymous?} | message | 로그인 |
| DELETE | /api/guestbook/{id} | - | message | 본인/관리자 |
- 🔵 방명록 화면 시안 상세(익명/비밀 여부, 관리자 관리 탭 존재)가 이번 PDF에 없음 — 기획 확인 후 착수.
- 소프트삭제 대전제 적용(state 컬럼 포함) + 신고 대상 포함 여부 확정.

## F. 활동 사진(갤러리) — 시안 p18·19 (부재) (담당 제안: 해미 또는 신규 배정)
### F-1. 🔴 갤러리 테이블 + 조회/관리 API
| 메서드 | 경로 | 요청 | 응답 | 권한 |
|--------|------|------|------|------|
| GET | /api/gallery | ?page&size | [{galleryId,title,tags,thumbnailUrl,images:[...]}] | 로그인 |
| POST | /api/admin/gallery | multipart(title,tags,files[]) | {galleryId} | ADMIN |
| PUT/DELETE | /api/admin/gallery/{id} | ... | message | ADMIN |
- 시안: 앨범 카드(제목 "필사그래피 MT" + 해시태그 "#바다 #날씨_최고 #2026") 형태.

## G. 신고/제재 보완

- G-1. ✅ **완료(2026-08-14)** 정지/차단 응답 구조화 — 로그인·세션 중 차단 모두 `{message, banType, bannedUntil}` JSON.
- G-2. ✅ **완료(2026-08-14)** 댓글 신고 목록 행에 원글 postId 포함 — ReportedItemResponse 확장.
- G-3. ✅ **종결(PM 확정)** 경고 단계는 **ban_policy 3단계**(1주/1달/영구)가 정답 — 시안 `N/5`는 오표기, **FE가 `/3`으로 수정**할 것.
- G-4. ✅ **완료(2026-08-14)** 이미 삭제된 대상 신고 409 거부 + 본인 글 신고 400 거부.

## H. 기타

- H-1. 🔶 **부분 완료 → 나머지는 담당자 과제(2026-08-16 PM 지시)**
  알림(헤더 종 아이콘). **완성**: notifications/notification_devices 테이블,
  웹 푸시 발송(VAPID·만료 기기 자동 정리), 수신 기기 등록/해제 API.
  **미구현(담당자 과제)**: **알림 발행 자체**(현재 알림이 하나도 생기지 않음 —
  `BoardServiceImpl.createComment` 에 TODO 자리만 있음)와 알림함 API(목록·읽음·삭제·미읽음수).
  → 과제 지시서 **`HANDOFF-notification-tasks.md`** (경로·요청/응답을 정해주지 않고 요구사항만 제시.
  담당자가 설계 → PM 확인 → 구현 → `api_endpoints` 25·26·27·28·29 를 직접 갱신).
- H-2. ⚪ 아이디 저장(로그인 체크박스) — FE 로컬스토리지 처리로 충분, 백엔드 불필요.
- H-3. 🔵 재학생 인증 절차(회원가입 승인 플로우) — 현재 가입 즉시 활성. 승인 대기 개념 유무 기획 확인.

---

## 우선순위 제안 (스프린트 관점)

| 순위 | 항목 | 근거 |
|------|------|------|
| 1 | C. 마이페이지 | 회원 화면 완성의 마지막 큰 조각, 착수 흔적 있음 |
| 2 | A-2. 댓글 관리 | 관리자 커뮤니티 관리 4메뉴 중 유일한 공백, moderation 재사용으로 저비용 |
| 3 | B-1. 대시보드 | 관리자 첫 화면, 조회만이라 리스크 낮음 |
| 4 | D-1. 일정 시간 | DDL 불필요, 소규모 |
| 5 | A-4/A-5. 에디터 업로드·임시저장 | 글쓰기 UX 핵심 |
| 6 | A-1. 게시판 CRUD(+동적화) | 파급 범위 커서 설계 리뷰 선행 |
| 7 | E/F. 방명록·갤러리 | 신규 도메인, 기획 상세 확인 후 |
