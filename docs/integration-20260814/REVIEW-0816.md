# 2026-08-16 전체 재검토 — 게시판(리치 에디터)·알림·검토 필요 API 정리

> 배경: 프론트가 리치 에디터를 처음 다뤄 게시판 API 계약이 헷갈리는 상태였고,
> 알림은 웹앱(구글 플레이 TWA) 출시 시 푸시/배지가 가능한지 미확정이라 두 영역이 phase `검토 필요`로 남아 있었다.
> 이번 검토로 `검토 필요` 9건 중 8건을 해소했다 (남은 1건은 인라인 이미지 — 아래 §4).

---

## 1. `검토 필요` 9건 처분 결과

| id | API | 처분 |
|----|-----|------|
| 5 | POST 게시글 등록 | **2기 확정.** 어제 합의(postId 응답·검증 400·isPinned 제거) 코드 반영 완료 |
| 7 | PUT 게시글 수정 | **2기 확정.** 합의({message} 응답·증분 첨부·검증 400) 코드 반영 완료 |
| 13 | POST 인라인 이미지 | **유일하게 검토 필요 유지** — 본문 포맷(HTML)·XSS 새니타이즈·첨부 방식 통일(①) 확정 대기 |
| 25~29 | 알림(toast) 5종 | **2기 확정.** 인앱 알림 API는 현행 그대로 유효. 웹앱 푸시·배지는 별개 전달 채널로 붙인다(PM 결정: 2기 범위) → [PUSH-NOTIFICATION-GUIDE.md](PUSH-NOTIFICATION-GUIDE.md) |
| 39 | GET 관리자 게시글 상세 | **2기 확정.** 코드와 일치 확인. 첨부 예시를 실제 DTO 형태로 정정 |

phase 분포: 1기 8 / 2기 70 / 3기 5 / 검토 필요 1 (id=13).

## 2. 어제 합의 → 코드 반영 (이번에 수정한 것 4건, 컴파일 통과)

| 합의 | 수정 전 코드 | 수정 |
|------|------------|------|
| 등록 응답에 postId | message만 반환 (PK는 매퍼가 채우고 버림) | `BoardResponse`에 postId 추가(NON_NULL), 등록 응답에 포함 |
| 수정 응답은 {message}만 | 상세 객체(BoardDetailResponse) 전체 반환 | `{message}` 반환으로 변경 — 상세는 어차피 이동하며 GET 재호출 |
| title/content 필수 → 400 | 수정 DTO에 @NotBlank 없어 **빈 문자열이 DB에 그대로 반영** | `BoardUpdateRequest`에 @NotBlank 2개 추가 |
| isPinned 요청 필드 없음 | Swagger 설명이 isPinned를 요청 필드처럼 안내 (프론트 오도) | @Operation 문구 정정 |

이미 코드에 있던 것(수정 불필요): isPinned 필드 제거·카테고리 PINNED 서버 판정, 등록 검증(@NotBlank+@Size 200), 첨부 증분(deleteAttachmentIds 소프트삭제+files 추가), 카테고리 목록 토큰 분기, 일반회원 '중요' 강제 전송 방어.

## 3. 프론트용 리치 에디터 데이터 계약 (혼란 해소용 — 이대로 안내)

```
[글쓰기 화면에서 일어나는 일]

1. 본문 작성        → content 는 **마크다운 문자열** (HTML 아님 — PM 확정 2026-08-16)
2. 이미지 삽입      → 그 순간 POST .../posts/images 로 선업로드 → {url} 을 ![](url) 로 본문에 삽입
                      (에디터에 표시할 URL이 즉시 필요하므로 발행/임시저장 구분 없이 선업로드 — 이 API가 A-4)
3. 파일 첨부        → 발행 폼의 files[] 에 담아 두었다가 발행 multipart 에 함께 전송
4. 글 저장하기(임시) → POST .../drafts (2번째부터는 PUT .../drafts/{draftId} 덮어쓰기)
                      본문 속 이미지 URL은 마크다운 텍스트라 그대로 보존됨 (임시저장이어도 이미지는 이미 서버에 있음)
5. 발행             → POST .../posts (multipart) + draftId 포함 시 초안 자동 삭제
                      응답의 postId 로 상세 페이지 이동
6. 수정             → PUT .../posts/{postId} (multipart)
                      첨부는 증분: 지울 것만 deleteAttachmentIds, 새 것만 files. 응답은 message만
```

- 검증: title 필수·200자, content 필수 — 위반 시 400 `{"message"}` (등록·수정 동일)
- 상단 고정: isPinned 필드는 **없다**. 카테고리에서 '중요'를 고르면(관리자에게만 보임) 서버가 고정
- 서버는 content 마크다운을 그대로 저장·반환. 렌더링 시 프론트가 새니타이즈(마크다운 → HTML 변환 단계)

## 4. 결정 대기 (PM 확정 필요)

1. **첨부 방식 통일 (합의문의 ①)**: 게시글=발행 시 multipart `files` vs 초안=`attachmentIds`(선업로드)로 갈라져 있음.
   SPEC-A5 §6(attachments.draft_id 안)과 합의문의 attachmentIds 안도 서로 다름 — §6은 파일을 초안에 직접 귀속,
   합의안은 별도 업로드 후 id 연결. **인라인 이미지는 어차피 선업로드가 강제**되므로, 업로드 엔드포인트가 생기는 김에
   §6 + usage_type(§6-6)으로 한 번에 정리하는 안을 권장. 결정 나면 id 13·16·18 명세 확정 가능.
2. ~~임시저장 보관 상한 N~~ → **5개 확정** (2026-08-16). policy_settings.draft_max_count=5 등록 — 구현 시 하드코딩 금지, 이 값을 로드할 것.
3. ~~알림 발행 범위~~ → **확정 (2026-08-16)**: 내가 작성한 글에 달린 댓글(COMMENT) + 내가 작성한 댓글에 달린 대댓글(REPLY) **만** 발행한다.
   현재 코드가 정확히 이 동작이므로 변경 없음. REPORT_RESOLVED/SANCTION/NOTICE 는 발행하지 않는 것으로 확정.

## 5. 시안 대비 신규 발견 갭 (관리자 — 결정 대기)

| # | 시안 근거 (PM 코멘트) | 상태 |
|---|---|---|
| 1 | "블라인드·삭제만 모아보기" | **위치 정정(2026-08-16)**: 최초 보고에서 게시글 관리로 잘못 귀속 — PM 확인 결과 **신고 관리 소관**. 신고 목록에는 status 필터(pending/rejected/resolved)가 이미 있어 처리 상태 기준 모아보기는 가능. 대상 state(blind/deleted) 기준이 따로 필요하면 그때 파라미터 추가 |
| 2 | "신고관리 이동 시 회원 ID 전달" | **사용 안 함 확정(PM, 2026-08-16)**: 작성자 필터는 만들지 않는다 |
| 3 | 첨부 다운로드 파일명 | **해소(2026-08-16)**: 저장 방식을 UUID → **원본 파일명**(uploads/board-{boardId}/{postId}/원본명, 중복 시 "이름 (1).ext")으로 변경. 첨부 교체/삭제 시 물리 파일도 함께 지워 고아 파일 방지. attachments 0행 시점이라 마이그레이션 불필요 |

## 6. 웹앱 푸시·배지 결론 (상세: PUSH-NOTIFICATION-GUIDE.md)

- **TWA(안드로이드)에서 웹 푸시 → 네이티브 알림 표시 가능.** Firebase SDK 불필요(표준 VAPID).
- **배지**: 안드로이드는 알림이 떠 있는 동안 런처가 자동 표시(숫자 직접 제어 불가). iOS PWA 16.4+와 데스크톱은 setAppBadge로 숫자 제어 가능.
- 기존 toast API는 그대로 유지 — 푸시는 순수 증축(푸시 수신처 등록 테이블 + 등록 API + 발송 훅). **PM 결정: 2기 범위로 개발**.
- 프론트에 지금 알릴 것: linkUrl 상대경로 규칙 유지, unread-count 계약 유지, manifest/SW 뼈대는 미리 갖춰도 무방.

---

## 7. 2026-08-16 2차 반영 (PM 지시 이행)

| 항목 | 내용 |
|---|---|
| 본문 포맷 | **마크다운 확정** (HTML 아님). 명세 id 5·7·13 갱신 |
| 구글 캘린더 구독 | **구현 완료** — `GET /api/event/calendar.ics` (ICS 피드, PUBLIC). 프론트 [구독하기] = `calendar.google.com/calendar/render?cid={인코딩한 피드 URL}` 열기. 한 번 구독하면 이후 일정 등록/수정/삭제가 자동 반영(구글이 주기 재조회). OAuth·구글 API 불필요, 애플/아웃룩도 같은 URL 사용 가능 |
| 일정 카테고리 | 구현했다가 **PM 지시로 당일 롤백**(코드+DB) — 팀원 과제로 전환. 완성 구현본은 git 브랜치 `archive/event-categories`(커밋 da6da1d)에 보관: DDL+시드, GET /api/event/categories, 등록/수정 카테고리 검증까지 포함 |
| 첨부 저장 방식 | UUID → **원본 파일명** (uploads/board-{boardId}/{postId}/원본명). 금지문자·경로이탈 새니타이즈, 같은 글 내 중복 이름은 " (1)" 부여. 첨부 삭제/교체 시 물리 파일도 삭제(고아 방지, 경로 이탈 가드 포함) |
| 임시저장 상한 | **5개 확정** — policy_settings.draft_max_count=5 |
| 알림 범위 | **COMMENT/REPLY 만 발행 확정** (현행 코드 그대로, 변경 없음) |
| 스웨거 | 구현 완료 API 전체에 @Tag(도메인 그룹)·@Operation(설명+요청/응답 예시) 부착 + swagger-ui 검색(filter)·정렬 설정 |

---

## 8. 2026-08-16 3차 반영 (패키지 재편 + 웹 푸시 2기 개발)

| 항목 | 내용 |
|---|---|
| 신고 접수 이동 | `com.back.report` → **`com.back.board.report`** (컨트롤러/서비스/DTO/예외/매퍼+XML). 게시글·댓글에 대한 회원 기능이므로 board 하위. URL 불변 |
| 이 주의 문장 이동 | `com.back.quote` → **`com.back.admin.quote`** 전체. 공개 랜덤(/api/quotes/current)도 예외적으로 admin 패키지 소속(PM 허용). URL 불변 |
| 신분·권한 조회 이동 | `mypage.profile` → **`com.back.auth`**, 클래스명 Profile* → **Role*** (RoleController/RoleService/RoleResponse/RoleMapper). 공용 기능은 mypage 밖으로(PM). URL /api/role 불변 |
| 웹 푸시 (2기 확정) | **구현 완료.** "구독" 용어 배제 — 테이블 `notification_devices`(알림 수신 기기 등록부), API 는 toast 네임스페이스: `POST/DELETE /api/user/mypage/toast/devices`, `GET .../toast/vapid-key` (명세 id 136·137·138). 알림 저장 직후 등록 기기로 @Async 발송(NotificationPushService), 404/410 기기 자동 정리. 라이브러리 nl.martijndwars:web-push 5.1.1, VAPID 키는 application.properties |
| 이메일 인증번호 이동 | `com.back.global.mail` → **`com.back.auth`** (컨트롤러/DTO/서비스/예외를 auth 하위 폴더로 병합). 인증번호는 회원가입·계정찾기의 부속 기능이라 인증 도메인 소속. global 은 인프라 계층(config/security/util/exception)만 남김. URL /api/mail/** 불변 |
| 일정 관리(관리자) 분리 | 일정 등록/수정/삭제를 **`com.back.admin.event`**(AdminEventController/AdminEventService)로 분리. event 패키지는 공개 조회+캘린더 피드만 담당. 매퍼(EventMapper)·DTO 는 event 도메인 공유(admin.board↔BoardMapper 패턴). URL 불변 |
| 신고 처리(관리자) 이동 | **완료** — PM 결정: Sanction 단어 유지(팀원 hams9494 가 PR #68 에서 도입한 용어 존중). ReportAdmin*(컨트롤러/서비스/DTO/매퍼+XML) 전부 `com.back.admin.sanction` 으로 이동, 클래스명·URL 불변. **com.back.report 패키지 소멸** |

### §8 보충 — 푸시 UX 정책 (PM 확정)
- 알림 토글은 **모바일에서만 노출** (PC 웹은 알림함만 — 에브리타임 방식). 서버 변경 없음.
- 토글은 **켜짐/꺼짐 2상태만**. 브라우저 차단 상태 전용 UI 없음 — 켜기 실패 시 에러 토스트 한 줄("브라우저에서 알림이 차단되어 있어요").
- 디자인 요청: ① 모바일 마이페이지 "이 기기에서 알림 받기" 토글 1개 ② 알림 유도 바텀시트 1장([알림 켜기]/[나중에]) — 웹앱 설치 후 첫 로그인 시 1회 노출(소프트 프롬프트). 실제 권한 팝업은 [알림 켜기] 클릭 시에만.

---

## 9. 2026-08-16 4차 — 회원 탈퇴 (구글 플레이 계정 삭제 정책 대응, PM 확정)

| 항목 | 내용 |
|---|---|
| 신규 API | `PATCH /api/user/mypage/withdraw` (id=139) — 비밀번호 재확인 후 탈퇴. 제재 여부 무관 항상 허용 |
| 개인정보 파기 | 이름→'탈퇴한 회원', 이메일→`deleted_{id}@removed.local`, 아이디→`deleted_{id}`, 전화→NULL, 비밀번호→무효값. NOT NULL+UNIQUE 제약 때문에 이메일/아이디는 비우는 대신 user_id 기반 더미로 치환 |
| 학번 해시 보관 | `del:` + Base64Url(SHA-256) 형태로 치환(47자) — 원문 파기 + 재가입 대조 능력 유지. **개인정보처리방침에 "부정 이용 방지 목적 보관" 명시 필요** |
| 재가입 정책 | 일반 탈퇴자=자유 재가입 / 영구차단 탈퇴자=영구 거부 / 정지 중 탈퇴자=정지 종료일까지 거부 (signup 이 해시 대조) — 카카오·게임사 표준 패턴 |
| signup 보강 | 학번/전화 사전 중복검사 추가 (기존엔 UNIQUE 1062 → 500 으로 터지던 버그 동시 수정) |
| 관리자 화면 | 제재 목록/상세에서 탈퇴자 **항상 미노출** (쿼리 is_deleted=0 — UI 필터 아님). 게시글·신고 화면은 name 치환으로 자동 익명화 |
| 부수 정리 | 알림 수신 기기 물리 삭제, 본인 알림함 소프트삭제, Redis 인증 상태 삭제, refreshToken 쿠키 만료 |
| 프론트 과제 | ① 마이페이지 [회원 탈퇴] 화면(비밀번호 재입력) ② 앱 없이 접근 가능한 웹 계정삭제 안내 페이지(구글 정책 필수 — Play Console Data safety 에 URL 입력) |
| 재가입 쿨다운 (추가) | 탈퇴 후 **30일간 재가입 불가**(policy_settings.rejoin_cooldown_days) — 탈퇴/재가입 반복으로 계정 행을 양산하는 어뷰징 차단. 대조 키는 학번 해시 하나(아이디·이메일 무관) |
| 관리자 강제 탈퇴 (추가) | `PATCH /api/admin/users/{userId}/withdraw` (id=140) — 가입 승인제 대신 "가입은 열어두고 부원 아닌 계정은 운영진이 정리". 관리자 계정은 대상 불가 |
| 이메일 인증 서버 검증 (추가) | 인증 성공 시 30분짜리 통과 플래그 저장 → **회원가입·비밀번호 초기화가 플래그를 검증**(1회용 소진). 프론트 화면 검증만으로는 API 직접 호출을 못 막던 구멍 봉쇄. 특히 비밀번호 초기화는 기존에 아이디만 알면 남의 비밀번호를 바꿀 수 있던 **계정 탈취 구멍**이었음 (id=84 명세 갱신) |
| 탈퇴 행 자동 정리 (추가) | **활동·제재 이력이 전혀 없는 탈퇴 행은 90일 후 새벽 배치(04:30)가 물리 삭제** — 익명화 행 무기한 잔존 방지(네이버식 기간 보존 후 자동 삭제). 글 있는 행은 작성자 표기 조인 때문에, 제재 이력 행은 재가입 차단 근거라 보존. policy_settings.withdrawn_purge_days |
| 인증 만료 안내 (개선) | 통과 플래그(policy_settings.mail_verified_ttl_minutes, 기본 30분) 만료 후 시도 시 메시지: "이메일 인증이 완료되지 않았거나 만료되었습니다. 이메일 인증을 다시 진행해주세요." |
| 남은 한계 (인지) | 학번이 자기 신고값이라 **가짜 학번 재가입은 해시 대조로 못 막는다** — 강제 탈퇴로 사후 정리하는 운영으로 커버(PM 결정). donations.display_name 은 스냅샷이라 탈퇴해도 명예의전당 표기는 유지(정책 확인 필요) |
