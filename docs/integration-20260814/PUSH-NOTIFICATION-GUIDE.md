# 웹앱(TWA/PWA) 푸시 알림·앱 배지 기술 검토 (2026-08-16)

> PM 질문: "웹앱으로 구글 플레이에 올릴 건데, 토스트 알림(푸시)과 앱 아이콘 배지가 웹앱에서도 가능한가?
> 가능하다면 백엔드는 뭘 해야 하고, 프론트에게는 뭘 알려줘야 하나?"

## 1. 결론 요약

**가능하다.** 구글 플레이에 올리는 TWA(Trusted Web Activity)는 Chrome이 렌더링하므로, 표준 **웹 푸시(Push API)가 그대로 동작하며 안드로이드 네이티브 알림(상단 알림 트레이)으로 표시된다.** 앱이 꺼져 있어도 수신된다. TWA에서 알림 위임(androidbrowserhelper의 notification delegation)을 켜면 알림이 Chrome이 아니라 **우리 앱 이름·아이콘으로** 뜬다. Android 13+에서는 알림 런타임 권한(POST_NOTIFICATIONS) 동의가 추가로 필요하다.

**앱 아이콘 배지는 반쯤 가능하다.** 안드로이드는 8.0+부터 "알림이 떠 있는 동안" 런처가 아이콘에 점(dot) 또는 숫자를 자동 표시한다(런처마다 다름 — 삼성 One UI는 숫자, Pixel은 점만). 즉 **안드로이드에서 배지는 알림의 부산물**이고, 숫자를 직접 제어하는 Badging API(`navigator.setAppBadge`)는 **Chrome for Android에서 미지원**이다. `setAppBadge`가 실제로 동작하는 곳은 데스크톱 Chrome/Edge 81+와 iOS PWA 16.4+다. "unread-count를 아이콘 숫자로 정확히 반영"은 안드로이드 TWA에서는 보장 못 한다.

**iOS 제약:** iOS/iPadOS 16.4+에서만 웹 푸시가 되고, 그것도 **Safari에서 홈 화면에 추가(standalone 설치)한 PWA에 한해서**다. 권한 요청은 반드시 사용자 제스처(버튼 클릭) 안에서 호출해야 하며, 일반 Safari 탭에서는 푸시가 안 된다. TWA는 안드로이드 전용 포장 기술이라 iOS는 "홈 화면 추가 안내" 수준으로 접근하는 게 현실적. iOS 16.4+는 `setAppBadge`도 지원하므로 배지는 오히려 iOS PWA가 안드로이드보다 정확하다.

## 2. 동작 구조

서버가 기기로 직접 쏘는 게 아니라 **브라우저 벤더의 푸시 서비스를 경유**한다. Chrome 계열은 FCM 인프라를 쓰지만, 표준 Web Push 프로토콜(VAPID)로 보내므로 **Firebase SDK나 Firebase 프로젝트는 필요 없다.**

```
[가입 시 1회]
프론트: SW 등록 → pushManager.subscribe(VAPID 공개키)
      → 브라우저가 푸시 서비스(FCM)에서 구독 생성
      → 구독 객체(endpoint URL + 암호화 키 p256dh/auth)를 백엔드에 POST → DB 저장

[알림 발생 시마다]
백엔드: notifications 행 INSERT (기존 로직 그대로)
      → 저장된 구독의 endpoint 로 Web Push 전송 (VAPID 서명 + 페이로드 암호화)
      → 푸시 서비스(FCM) → 기기의 Chrome → Service Worker 'push' 이벤트
      → sw.js 가 showNotification() 호출 → 안드로이드 네이티브 알림 표시
      → 사용자가 탭 → 'notificationclick' → linkUrl 로 이동
```

## 3. Badging API 지원 범위와 unread-count 연동

| 플랫폼 | `setAppBadge` | 실질적 배지 동작 |
|---|---|---|
| 안드로이드 TWA (Chrome) | **미지원** | 알림이 떠 있으면 런처가 dot/숫자 자동 표시 |
| iOS PWA 16.4+ (홈 화면 설치) | 지원 | 숫자 배지 직접 제어 가능 |
| 데스크톱 Chrome/Edge 81+ (PWA 설치 시) | 지원 | 작업표시줄/독 아이콘에 숫자 |

```js
if ('setAppBadge' in navigator) {
  const { unreadCount } = await fetch('/api/user/mypage/toast/unread-count').then(r => r.json());
  unreadCount > 0 ? navigator.setAppBadge(unreadCount) : navigator.clearAppBadge();
}
```

호출 시점: 앱 포커스 시, SW의 push 핸들러 안(페이로드에 unreadCount 포함 권장), read-all/알림함 진입 시 `clearAppBadge()`. 미지원 환경에서는 조용히 건너뛰면 된다.

## 4. 백엔드가 해야 할 일 (3기 착수 시)

1. **VAPID 키 쌍 생성·보관** — 1회 생성. 개인키는 환경변수/서버 설정으로만 보관(레포 커밋 금지 — .sql 미커밋 컨벤션과 동일 원칙). 공개키는 프론트에 그대로 전달. **키를 바꾸면 기존 구독 전부 무효**이므로 분실 주의.

2. **구독 테이블** — 세션성 데이터라 소프트삭제 예외(물리 삭제 OK). DDL은 관행대로 수동 적용 후 CHECKLIST 기록.
```sql
CREATE TABLE push_subscriptions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  endpoint VARCHAR(500) NOT NULL,
  p256dh VARCHAR(255) NOT NULL,
  auth VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_endpoint (endpoint), KEY idx_user (user_id)
);
```

3. **구독 API 설계안** — 기존 알림 네임스페이스와 나란히.
```
POST   /api/user/mypage/push/subscriptions   — 구독 등록 (subscription.toJSON() 그대로 수신, UPSERT)
DELETE /api/user/mypage/push/subscriptions   — 구독 해제 (로그아웃·알림 끄기)
GET    /api/user/mypage/push/vapid-key       — 공개키 (프론트 하드코딩으로 대체 가능)
```
응답은 기존 `{"message": ...}` 컨벤션.

4. **발송 시점** — `NotificationService.publish()`가 단일 진입점이므로 insertNotification 성공 직후에 푸시 발송을 붙이면 모든 알림 타입이 한 번에 커버된다. 발송은 **@Async** — 외부 HTTP 호출이 댓글 작성 트랜잭션을 지연·실패시키면 안 됨. 실패는 log.warn만.

5. **라이브러리** — `nl.martijndwars:web-push:5.1.1` (+ bouncycastle). Java 17 호환, VAPID 서명·페이로드 암호화(RFC 8291) 처리. 페이로드는 알림 행 값 그대로: `{title, body: message, linkUrl, unreadCount}`.

6. **만료 정리** — 발송 응답이 **410 Gone/404면 그 구독 행을 즉시 삭제** (알림 차단·앱 삭제·브라우저 초기화). 이것만 지켜도 좀비 구독이 안 쌓인다.

## 5. 프론트 전달 체크리스트

1. **manifest.json**: name, icons(192/512, maskable), `display: "standalone"`, start_url — TWA 심사·설치 배너 전제조건
2. **Service Worker 등록** — HTTPS 필수
3. **권한 요청 시점(UX)**: 페이지 로드 즉시 `Notification.requestPermission()` 금지. "푸시 알림 켜기" 토글 등 **사용자 클릭 핸들러 안에서** 요청(iOS는 제스처 필수). 한번 차단되면 되돌리기 어렵다
4. **구독**: `pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: urlBase64ToUint8Array(공개키) })`
5. **구독 객체를 백엔드로 POST** + `pushsubscriptionchange` 이벤트에서 재구독·재전송
6. **push 핸들러**: `event.waitUntil(self.registration.showNotification(title, { body, icon, badge, data: { linkUrl } }))` — 받은 푸시는 반드시 알림으로 표시(userVisibleOnly 계약)
7. **notificationclick 딥링크**: `event.notification.data.linkUrl`로 openWindow/focus — **linkUrl은 이미 알림 API 응답에 있는 필드를 그대로 재사용**
8. **배지**: §3 분기 코드. 안드로이드에서는 no-op임을 인지
9. (TWA 포장) assetlinks.json + androidbrowserhelper 알림 위임 + Android 13 알림 권한

## 6. 기존 toast API와의 관계

**푸시는 전달 채널일 뿐, 알림의 원본은 지금처럼 notifications 테이블이다.** 인앱 목록/unread-count/read/delete API는 전혀 바뀌지 않고, 푸시가 실패해도(권한 거부·구독 만료) 인앱 알림은 정상 동작한다. 추가되는 것은 구독 테이블 1개 + 구독 API + publish() 뒤의 비동기 발송 훅뿐 — 기존 코드의 수정이 아니라 순수 증축이다.

## 7. 시기: 3기로 미뤄도 된다

- 푸시의 가치는 "설치된 앱"일 때 발생하는데 스토어 출시 자체가 3기 계획이다.
- 구조가 전부 증축형이라 나중에 붙여도 마이그레이션·API 파괴가 없다. 2기에 미리 깔아야 할 기반이 없다.
- 단, **프론트가 지금 알아두면 좋은 것**: (1) 알림 응답의 `linkUrl`은 나중에 딥링크로 그대로 쓰므로 앱 내 상대경로 규칙을 유지할 것 (2) unread-count API 계약은 배지 연동에 재사용되니 안정 유지 (3) manifest·SW 뼈대는 푸시와 무관하게 PWA 설치성 요건이라 여유 있을 때 먼저 갖춰도 손해 없음.
