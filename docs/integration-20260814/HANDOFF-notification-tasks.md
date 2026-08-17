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
- 단건 읽음·삭제는 **아래 확정 응답을 그대로 따를 것** (프론트와의 계약이라 임의 변경 금지)

**C. 빠진 것 제안** — 직접 써본다고 생각하고 필요한 걸 근거와 함께 가져올 것.

## 확정된 정책 (설계에 반영)

- 목록은 **페이징 없이 전체 반환**
- 단, **최근 N개월치만** — N 은 `policy_settings.notification_list_months` (현재 2). 하드코딩 금지
- 알림 API 는 `toast` 네임스페이스 (`구독`이라는 단어는 캘린더 구독과 혼동되므로 쓰지 않는다)

### 단건 읽음 응답 (확정)

사용자는 알림을 **읽기만 하지 않고 그 대상으로 이동한다.** 읽음 처리 응답 하나로 이동까지 되어야 한다
— 목록을 다시 부르지 않아도 되고, 푸시 알림을 눌러 목록을 거치지 않고 들어온 경우에도 동작해야 하기 때문이다.

```json
{
  "message": "읽음 처리되었습니다.",
  "notificationId": 12,
  "type": "COMMENT",
  "targetType": "post", "targetId": 171, "boardId": 2,
  "linkUrl": "/api/user/boards/2/posts/171",
  "unreadCount": 2
}
```

- **이미 읽은 알림을 다시 호출해도 200 + 동일 응답**(멱등). 재클릭 시에도 이동해야 하므로 no-op 로 끝내면 안 된다
- 없거나 본인 알림이 아니면 404
- `linkUrl` 은 **API 경로이며 화면 경로가 아니다.** 게시판은 데이터로 정의되므로 백엔드는 프론트 라우팅을 알 수 없다
  — 프론트가 `boardId`/`targetId` 로 화면 경로를 조립한다. **백엔드가 프론트 경로를 만들어 넣지 말 것**
- 목록(`GET .../toast`) 응답의 각 항목에도 같은 이유로 `boardId` 를 포함한다 (프론트가 `linkUrl` 문자열을 파싱하게 두지 말 것)

### 단건 삭제 응답 (확정)

```json
{"message":"삭제되었습니다.","notificationId":12,"unreadCount":2}
```

- 소프트삭제. 이동 정보는 싣지 않는다 — 지운 알림으로 갈 일은 없다
- 대상은 **경로변수로만** 받는다. 본문에 id 를 또 받으면 경로와 어긋났을 때 무엇을 믿을지 문제가 된다

## 규칙

`CLAUDE.md` 정독. 특히 — 소프트삭제 대전제 / 사용자 판별은 `AuthUtils`(userId 를 요청으로 받지 말 것) /
`@Param` / `Boolean isXxx` / 에러는 `{"message":...}` / 스웨거 문서화.

## 테스트 방법

프론트가 없으니 **스웨거**(`http://localhost:8080/swagger-ui/index.html`)로 검증한다.
로그인 후 응답의 `accessToken` 을 우측 상단 **Authorize** 에 넣는다 — `Bearer` 는 빼고 **토큰 값만**.
계정을 바꿀 때마다 다시 넣어야 한다. 테스트 계정은 `TEST-PLAN.md` §1 참고
(`t_stu`·`t_stu2` 두 개를 쓰면 "상대에게만 알림" 검증이 된다).

**발행(과제 A) 검증**
1. `t_stu` 로 글을 쓰고 → `t_stu2` 로 그 글에 댓글을 단다
2. `SELECT * FROM notifications ORDER BY notification_id DESC LIMIT 5;`
3. 확인할 것
   - `t_stu` 에게 알림이 **생겼는가**
   - 본인이 자기 글에 댓글 → 알림이 **안 생겨야** 한다
   - 대댓글일 때 원글 작성자와 부모 댓글 작성자가 같은 사람이면 알림이 **중복되지 않는가**
   - 알림 발행이 실패해도 **댓글 등록은 성공**하는가 (일부러 깨뜨려서 확인)

**알림함(과제 B) 검증**
- 남의 알림 id 로 읽음·삭제 요청 → 그 행이 **바뀌지 않아야** 한다 (200/404 중 무엇을 줄지는 본인 설계)
- 삭제한 알림이 목록에 안 나오는지, DB 에서는 어떻게 남는지
- 최근 N개월 밖의 알림이 목록에서 빠지는지 (`created_at` 을 과거로 UPDATE 해서 확인)

**웹 푸시까지 확인하려면 (선택)**
기기 등록은 원래 브라우저가 만든 구독 정보가 필요하다. 크롬 콘솔에서 직접 만든다 (localhost/HTTPS 에서만 동작).

1. `GET /api/user/mypage/toast/vapid-key` 로 공개키 복사
2. 아무 페이지 콘솔에서 실행 후, 출력된 JSON 에 `"enabled": true` 를 넣어
   `PUT .../toast/devices` 본문으로 보내기 (등록·해제가 이 API 하나로 통합돼 있다)

```js
const blob = new Blob([''], {type: 'text/javascript'});
const reg = await navigator.serviceWorker.register(URL.createObjectURL(blob));
await Notification.requestPermission();

const key = 'VAPID_PUBLIC_KEY';   // 1번에서 복사한 값
const raw = atob(key.replace(/-/g,'+').replace(/_/g,'/'));
const sub = await reg.pushManager.subscribe({
  userVisibleOnly: true,
  applicationServerKey: Uint8Array.from([...raw].map(c => c.charCodeAt(0)))
});
console.log(JSON.stringify(sub.toJSON()));
```

3. 발행 기능을 붙인 뒤 댓글을 달면 브라우저에 알림이 떠야 한다.

> 등록만 검증할 거면 더미 문자열(`"endpoint":"https://fcm.googleapis.com/fcm/send/test-0001"` 등)로도 된다.
> 대신 실제 발송은 실패하고, 서버가 실패한 기기를 자동 정리하므로 그 행은 사라질 수 있다.
> **`application.properties` 의 VAPID 키는 건드리지 말 것** — 바꾸면 등록된 기기가 전부 무효가 된다.

## 완료 기준

1. 설계안 PM 확인 (API 목록 + 요청·응답 + 실패 케이스)
2. 컴파일 + 실기동 호출 확인
3. `api_endpoints` 25·26·27·28·29 를 직접 갱신 (planned → active)
4. 본인 브랜치 → PR
