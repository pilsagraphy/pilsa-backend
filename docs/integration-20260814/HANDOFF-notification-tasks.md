# 알림(toast) — 담당자 과제

> 경로·요청/응답은 정해주지 않는다. **설계해서 PM 확인받고 구현할 것.**

## 이미 있는 것

| 무엇 | 위치 |
|---|---|
| 웹 푸시 발송 | `mypage/notification/service/NotificationPushService.java` |
| 수신 기기 등록/해제 | `mypage/notification/controller/NotificationDeviceController.java`, `mapper/NotificationDeviceMapper.java` |
| 알림 종류 정의 | `mypage/notification/dto/NotificationType.java` |
| 테이블 | `notifications`, `notification_devices` (스키마는 DB에서 확인) |
| 참고 문서 | `PUSH-NOTIFICATION-GUIDE.md`(푸시 구조), `HANDOFF-notification.md`(프론트 규약) |

## 할 일

**A. 알림 발행** — 지금 알림이 하나도 생기지 않는다. `BoardServiceImpl.createComment` 에 TODO 로 자리만 있다.
- `NotificationType` 5종이 각각 **어느 도메인 어느 시점**에 발행되어야 하는지 판단해서 연결
- 알림이 **가면 안 되는 경우**는 무엇인가
- 발행 실패가 본 기능(댓글 등록 등)을 망가뜨리면 안 된다 — 어떻게 할 것인가

**B. 알림함 API** — 종 아이콘 뱃지 / 목록 / 읽음(단건·전체) / 삭제.
- 몇 개의 API 가 필요한가, 경로·메서드·응답은 어떤 모양인가
- 남의 알림을 읽음·삭제하는 요청은 어떻게 막을 것인가
- 목록 조회 시 미읽음 수를 따로 또 불러야 하는가

**C. 빠진 것 제안** — 직접 써본다고 생각하고 필요한 걸 근거와 함께 가져올 것.

## 확정된 정책 (설계에 반영)

- 목록은 **페이징 없이 전체 반환**
- 단, **최근 N개월치만** — N 은 `policy_settings.notification_list_months` (현재 2). 하드코딩 금지
- 알림 API 는 `toast` 네임스페이스 (`구독`이라는 단어는 캘린더 구독과 혼동되므로 쓰지 않는다)

## 규칙

`CLAUDE.md` 정독. 특히 — 소프트삭제 대전제 / 사용자 판별은 `AuthUtils`(userId 를 요청으로 받지 말 것) /
`@Param` / `Boolean isXxx` / 에러는 `{"message":...}` / 스웨거 문서화.

## 완료 기준

1. 설계안 PM 확인 (API 목록 + 요청·응답 + 실패 케이스)
2. 컴파일 + 실기동 호출 확인
3. `api_endpoints` 25·26·27·28·29 를 직접 갱신 (planned → active)
4. 본인 브랜치 → PR
