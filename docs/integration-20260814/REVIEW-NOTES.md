# 통합 검토 노트 — 절차·소프트삭제·확장성·FE 대조

> PM 추가 지시(로그인 재검토 / 신고·제재·블라인드 절차 / 소프트삭제 대전제 / 확장성·유지보수 / FE 대조)에 대한 결과 보고. 2026-08-14.

## 1. 로그인·권한 재검토 결과

| 항목 | 판정 | 조치 |
|------|------|------|
| 회원가입 memberType 무검증 → `member_type='ADMIN'`으로 가입 시 JWT 필터의 `"ROLE_"+memberType` 변환으로 **ROLE_ADMIN 획득 가능** | 🚨 취약점 | **수정 완료**: signup 화이트리스트(STUDENT/ALUMNI) + 필터에서 알려진 값만 매핑(이중 방어) |
| JWT 권한이 토큰 클레임이 아닌 **DB 최신값** 기준 부여 | ✅ 양호 | 강등/차단이 즉시 반영됨 (스테일 토큰 무력) |
| 로그인/extend/refresh 모두 checkNotBanned + is_deleted 검사 | ✅ 양호 | #66+#68 결합 정상 |
| 리프레시 sliding(재발급 시 회전) + 쿠키 path `/api/auth/token` | ✅ 양호 | |
| `extend()`와 `refresh()`가 사실상 동일 로직 | 🟡 중복 | 후속 통합 권장 (동작 영향 없음) |
| 정지 계정 응답에 해제 일시가 message 문자열에만 존재 | 🟡 | BACKLOG G-1 (구조화 필드) |
| `/api/stu/**`에 ALUMNI 포함(#66) vs #51 핫픽스(제외) | 🔵 정책 | #66 설계 채택함. 시안 p16 사이드바가 재학생 전용 메뉴 구성이라 **PM 최종 확정 필요** |

## 2. 신고·제재·블라인드 절차 검토 결과

상태 전이: `normal ↔ blind → deleted` (deleted에서 blind/normal 복귀는 관리자 "상태 복원"만).

| 절차 | 검증 결과 |
|------|-----------|
| 조치 멱등성 | `UPDATE ... WHERE state <> 목표상태` 조건부 갱신 → 재클릭/중복요청에도 로그·벌점 중복 없음 ✅ |
| 동일 대상 다중 신고 → 이중 벌점 | 처리(반려/삭제)가 **대상 단위**로 pending 신고 전부 일괄 종결 → 두 번째 신고 건 처리로 벌점이 또 부과되는 경로 차단 ✅ (#68 원안의 신고건 단위 처리에서 개선) |
| 삭제 → 주의(+2) → 경고(10pt) → 정지/차단 | ModerationService.softDelete → penalty 기록 → PenaltyEscalationService가 경계 통과 횟수만큼 경고 발행, ban_policy 매칭(1주/1달/영구) ✅ |
| 복원 시 벌점 회수 | restore가 해당 대상의 미회수 penalty를 void 처리 ✅ (이미 발행된 '경고'까지 소급 회수하지는 않음 — 정책 확인 포인트) |
| 반려 시 삭제글 보호 | 이미 deleted인 대상은 반려해도 복원하지 않음(벌점·증적 보호) ✅ |
| 블라인드/삭제 글 은닉 | 학생 목록/상세/top5/이전다음/댓글수 모두 `state='normal'` 필터 ✅ |
| 조치된 콘텐츠 증적 보호 | 블라인드/삭제 글·댓글은 작성자 본인도 수정 불가(매퍼 state 가드) ✅ (리뷰 지적 반영) |
| 정지 판정 | 로그인·매 요청 모두 banned_until 실시간 비교, 스케줄러(매일 04시)는 캐시 정리만 담당 ✅ |
| ban_log 불변식 | 신규 차단 기록 전 기존 활성 행 close → 활성 행 최대 1개, 수동/자동 해제가 전체 열린 행 정리(자기치유) ✅ |
| 신고 접수 중복 방지 | uq_reports_active 유니크 + DuplicateKeyException → 409 ✅ |
| 수동 정지/차단(#60)과 자동 제재(#68) 공존 | 둘 다 ban_log 기록 + users 캐시 갱신으로 일원화 ✅. 수동 정지의 warning_no=1 재사용은 FK 통과용(문서화됨) |

미결(BACKLOG 참조): 경고 분모 5단계 여부(G-3), 삭제된 대상 신고 접수 허용(G-4).

## 3. 소프트삭제 대전제 검토 결과

`src/main/resources/mapper` 전수 조사 — 물리 `DELETE FROM` 잔존처:

| 대상 | 위치 | 판정 |
|------|------|------|
| posts | 없음 (학생 본인삭제·관리자 삭제 모두 `state='deleted'`) | ✅ 대전제 충족 |
| comments | 없음 (동일) | ✅ 대전제 충족 |
| post_likes | BoardMapper.deleteLike | ✅ 물리 유지가 맞음 (토글 본질) |
| events | EventMapper.deleteEvent | 🔵 물리 삭제. 일정은 moderation/감사 대상이 아니라 물리 유지가 합리적이라 판단. PM 이견 시 state 방식 전환 가능 |
| quotes | QuoteMapper.deleteQuote | 🔵 동일 (관리자 콘텐츠) |
| attachments | 삭제 경로 없음 (소프트삭제 복원 대비 파일 보존) | ✅ |
| comments FK(parent) | DB가 ON DELETE CASCADE 없이 적용됨 → 물리삭제가 사라진 현 구조에선 문제 없음 | ✅ 현행 유지 |

## 4. 확장성·유지보수 검토 결과

| 관찰 | 평가/제안 |
|------|-----------|
| BoardType enum 하드코딩(1/2/3) | 🚨 기획의 "새 게시판 생성"과 정면 충돌 — BACKLOG A-1 (DB 기반 정책 전환) 최우선 설계 과제 |
| moderation 모듈(post/comment 다형성) | ✅ 대상 유형 추가(방명록 등)에 확장 용이 — 신규 콘텐츠 도메인도 이 모듈 경유할 것 |
| policy_settings로 제재 수치 외부화 | ✅ 코드 수정 없이 정책 조정 가능 |
| boards.read_scope/write_level 선반영 | ✅ 게시판 동적 권한의 DB 기반 마련 (코드 전환은 A-1에서) |
| checkAdminRole/getCurrentUserId 도메인별 중복 5곳+ | 🟡 global.security의 공통 유틸(예: AuthUtils)로 수렴 권장 (기능 동일해 시급성 낮음) |
| extend/refresh 중복 | 🟡 위 1절 참조 |
| 응답 포맷 | 도메인별 {message} 계열로 일관 ✅. 페이지 응답도 {totalPages,totalCount,목록} 패턴 수렴 중 |
| 매퍼 XML 위치/네임스페이스 | 패키지-테이블명 일치화 후 mapper/{도메인}/ 구조 일관 ✅ |
| 스케줄러 | @EnableScheduling + 일 1회 캐시 정리. 서버 다중화 시 중복 실행 주의(현재 단일 인스턴스 전제) 🟡 |

## 5. pilsa-frontend 대조 결과 (기획 반영 전 코드 기준)

| FE 현황 | 백엔드 판단 |
|---------|-------------|
| `/api/stu/free|info|notices/...` 경로별 모듈 호출 | #62에서 `/api/stu/{boardId}/posts`로 통합됨 — **FE 마이그레이션 필요 목록** 전달 대상 (방향은 백엔드가 맞음: 게시판 동적화 대비) |
| 신고 사유 상수 = reasons 테이블 코드와 1:1 일치 | ✅ 그대로 사용 가능 (`POST /api/stu/reports`) |
| 회원관리 라벨: 일반회원/관리 Lv.1~3, 재학생/동문회 | ✅ member_type+admin_level 각색과 정확히 대응 |
| 일정 `GET /api/public/schedules?from=YYYY-MM&to=YYYY-MM` | ✅ 그대로 동작 (월 문자열 변환 지원) |
| `GET /api/role` 사용 | ✅ 응답이 {role}→{memberType,adminLevel}로 변경됨 — FE 수정 필요 안내 |
| 로그인 응답 role 사용처 | AuthResponse가 memberType/adminLevel로 변경 — FE 수정 필요 안내 |
| honor(명예의전당) `/api/public/honor` | ✅ 패키지명만 donation으로 변경, 경로 불변 |
| admin members/community — 아직 더미 데이터 | 신규 API(#57/#60/#68)가 계약 선점 — 노션 명세 공유로 정렬 |
| gallery/guestbook 라우트 존재, API 없음 | BACKLOG E/F |
