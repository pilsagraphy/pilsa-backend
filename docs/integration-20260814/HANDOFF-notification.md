# [전달] 알림 기능 — 디자인·프론트 안내 (2026-08-16 확정)

> 대상: 디자인팀, 프론트엔드.
> 백엔드 구현은 완료 상태이며, 이 문서의 내용만 반영하면 됩니다. 기술 상세는 [PUSH-NOTIFICATION-GUIDE.md](PUSH-NOTIFICATION-GUIDE.md) 참고.

## 0. 한 장 요약 — "알림처럼 보이는 것"은 3가지고, 서로 다른 물건입니다

| | 무엇 | 언제 보이나 | 브라우저 권한 | 상태 |
|---|---|---|---|---|
| ① 알림함 | 종 아이콘 → 알림 목록 | 앱 안에서 | 불필요 | **기존대로** (`GET /api/user/mypage/toast`) |
| ② 인앱 토스트 | 화면 구석에 잠깐 뜨는 팝업 | 앱이 열려 있을 때 | 불필요 | 프론트 UI (서버 무관) |
| ③ OS 알림 (푸시) | 폰 상태바/알림 트레이 | **앱이 꺼져 있어도** | **필요** | **이번에 신설** |

- 발행되는 알림은 2종뿐: **내 글에 댓글(COMMENT), 내 댓글에 답글(REPLY)**. 신고 처리·제재·공지 알림은 없음(확정).
- ③이 실패해도(권한 거부 등) ①②는 정상 동작 — 푸시는 전달 채널일 뿐 알림의 원본은 서버 DB.

## 1. 디자인팀에 요청 (신규 시안 2장)

1. **모바일 마이페이지 — "이 기기에서 알림 받기" 토글 1개**
   - 상태는 **ON/OFF 2가지만**. "브라우저에서 차단됨" 전용 화면은 만들지 않는다(카카오톡이 OS 차단을 앱에서 안내하지 않는 것과 같은 선).
   - 문구는 "알림 받기"가 아니라 "**이 기기에서** 알림 받기" — 기기별 설정이라 폰에서 켜도 다른 기기엔 적용 안 됨.
2. **알림 유도 바텀시트 1장** — "새 댓글·답글 알림을 받아보세요" + [알림 켜기] / [나중에]
   - 웹앱 설치 후 첫 로그인 시 1회 노출. [나중에] 선택 시 7일 뒤 1회만 재노출, 이후 침묵.

## 2. 노출 정책 (확정)

- **PC 웹 = 알림함만.** 토글·바텀시트는 **모바일에서만** 노출 (에브리타임 방식).
- **판별은 화면 폭으로 하지 않는다** — PC에서 창을 좁힌 사용자에게 새는 것을 막기 위함. 화면 폭은 레이아웃에만 쓰고, 기능 노출은 아래로 판별:

```js
const isStandalone   = matchMedia('(display-mode: standalone)').matches   // 설치된 앱 창으로 실행 중인가 (진입 경로 무관)
                    || navigator.standalone === true;                     // 구형 iOS 사파리 보완
const isMobileDevice = navigator.userAgentData?.mobile ?? /Android|iPhone|iPad/i.test(navigator.userAgent);
const pushSupported  = 'serviceWorker' in navigator && 'PushManager' in window;

const 토글_노출     = isMobileDevice && pushSupported;
const 바텀시트_노출 = 토글_노출 && isStandalone && !기기등록됨 && Notification.permission !== 'denied';
```

- 플랫폼 지원: 안드로이드(브라우저·설치형·플레이스토어 TWA) ⭕ / 아이폰은 **홈 화면에 추가한 경우만**(iOS 16.4+) ⭕, 사파리 일반 탭 ✕(토글 자체가 숨음).

## 3. 프론트 구현 절차

### 3-1. 토글 ON (= 바텀시트의 [알림 켜기]와 동일)
```js
// 반드시 클릭 핸들러 안에서 (iOS는 제스처 필수, 크롬도 아니면 팝업 강등)
const reg = await navigator.serviceWorker.register('/sw.js');
const perm = await Notification.requestPermission();
if (perm !== 'granted') { toast('브라우저에서 알림이 차단되어 있어요'); return; }  // 차단 처리는 이 한 줄이 전부

const { publicKey } = await api('/api/user/mypage/toast/vapid-key');
const sub = await reg.pushManager.subscribe({ userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey) });
await api.put('/api/user/mypage/toast/devices', { enabled: true, ...sub.toJSON() });  // 수신 동의 → 토글 ON
```

### 3-2. 토글 OFF (사용자가 직접 끔)
```js
const sub = await reg.pushManager.getSubscription();
if (sub) {
  await api.put('/api/user/mypage/toast/devices', { enabled: false, endpoint: sub.endpoint });
  await sub.unsubscribe();          // 사용자가 끈 경우에만 구독까지 해제
}
```
**서버 먼저, 브라우저 나중.** `unsubscribe()` 를 먼저 하면 endpoint 를 잃어버려서
서버에 어느 기기를 끄라고 말할 수 없다.

### 3-2-1. 로그아웃 — 토글 OFF 와 다르게 처리한다
```js
const sub = await reg.pushManager.getSubscription();
if (sub) {
  await api.put('/api/user/mypage/toast/devices', { enabled: false, endpoint: sub.endpoint });
  // ❗ unsubscribe() 는 하지 않는다 — 재로그인 시 알림 설정을 되살리는 유일한 근거다
}
await api.post('/api/auth/logout');
```
서버 행은 지운다(로그아웃 중에는 알림이 배달되면 안 된다 — 공용 기기에서 남의 알림 내용이 뜬다).
하지만 브라우저 구독은 남긴다. **"구독이 남아 있다 = 사용자가 알림을 끈 게 아니다"** 가 되어,
재로그인 때 3-2-2 로 자동 복구된다. 여기서 `unsubscribe()` 까지 하면 근거가 사라져
**로그인할 때마다 알림을 다시 켜야 하는** 화면이 된다.

순서도 중요하다 — 로그아웃 후에는 토큰이 없어서 `PUT` 이 401 로 실패한다.

### 3-2-2. 로그인 직후 — 알림 설정 자동 복구
```js
const reg = await navigator.serviceWorker.getRegistration();
const sub = await reg?.pushManager.getSubscription();

if (sub && Notification.permission === 'granted') {
  const { devices } = await api.get('/api/user/mypage/toast/devices');
  if (!devices.some(d => d.endpoint === sub.endpoint)) {
    await api.put('/api/user/mypage/toast/devices', { enabled: true, ...sub.toJSON() });
  }
}
```
사용자에게 아무것도 묻지 않는다 — 권한은 이미 허용된 상태라 `requestPermission()` 이 다시 뜨지 않는다.
이 절이 없으면 로그아웃/재로그인마다 알림이 꺼진 채로 남는다.

**한계(알고 쓸 것)**: 브라우저 권한과 구독은 **오리진 단위라 계정과 무관**하다. 그래서 A 가 알림을 켜둔
기기에서 로그아웃한 뒤 B 가 로그인하면, B 는 동의한 적 없는데도 위 로직으로 알림이 켜진다
(내용은 B 본인 알림이라 유출은 없다). 계정 단위 수신 의사를 서버가 기억해야 한다면
`users` 에 수신 동의 플래그를 두고 `GET devices` 응답에 실어주는 방식이 필요하다 — 현재는 미도입.

### 3-2-3. 토글 초기 상태 (설정 화면 진입 시)
```js
const sub = await reg.pushManager.getSubscription();
const { devices } = await api.get('/api/user/mypage/toast/devices');
const on = !!sub && devices.some(d => d.endpoint === sub.endpoint);   // 이 값으로 토글을 그린다
```
브라우저 구독만 보고 판단하면 안 된다. 서버 행은 프론트 모르게 사라질 수 있다 —
다른 기기에서 로그아웃했거나, 발송이 404/410 으로 실패해 서버가 자동 정리한 경우다.
그때 브라우저에는 구독이 남아 있어서 **토글은 켜져 있는데 알림은 안 오는** 화면이 된다.
`on === false` 인데 `sub` 가 있으면 3-1 을 다시 호출해 재등록하면 복구된다.

### 3-3. sw.js — 수신·클릭 (포그라운드면 인앱 토스트, 아니면 OS 알림)
```js
self.addEventListener('push', e => {
  const d = e.data.json();   // 서버 페이로드: { title, body, toastId, targetType, targetId, boardId }
  e.waitUntil((async () => {
    const wins = await clients.matchAll({ type: 'window', includeUncontrolled: true });
    const focused = wins.find(w => w.focused);
    if (focused) focused.postMessage({ type: 'toast', ...d });          // 앱 보는 중 → 인앱 토스트(②)
    else await self.registration.showNotification(d.title, { body: d.body, data: d }); // → OS 알림(③)
  })());
});
self.addEventListener('notificationclick', e => {
  e.notification.close();
  const d = e.notification.data;
  // 화면 경로는 프론트가 조립한다 (백엔드는 프론트 라우팅을 모른다 — linkUrl 을 내려주지 않음)
  const url = d.targetType === 'post' ? `/boards/${d.boardId}/posts/${d.targetId}` : '/notifications';
  e.waitUntil(clients.openWindow(`${url}?toastId=${d.toastId}`));
});
```
페이지 진입 후 `?toastId=` 가 있으면 `PATCH /api/user/mypage/toast/{toastId}/read` 를 호출한다
— 응답의 `unreadCount` 로 뱃지를 갱신한다. **이걸 안 하면 OS 알림으로 들어온 사용자의 뱃지가 안 줄어든다.**
(읽음 API 는 멱등이라 이미 읽은 알림을 다시 눌러도 200 + 동일 응답으로 이동에 지장 없다)

### 3-4. 앱 배지 (지원 환경에서만)
```js
if ('setAppBadge' in navigator) {  // iOS PWA·데스크톱만 지원. 안드로이드는 알림이 떠 있으면 런처가 자동 표시
  const { unreadCount } = await api('/api/user/mypage/toast/unread-count');
  unreadCount > 0 ? navigator.setAppBadge(unreadCount) : navigator.clearAppBadge();
}
```

## 4. 신규 API 3본 (구현 완료 — 상세 예시는 Swagger "마이페이지-알림")

| 메서드·경로 | 용도 |
|---|---|
| `GET /api/user/mypage/toast/devices` | 수신 동의 상태 (내 기기 목록 → 토글 초기값 판정) |
| `PUT /api/user/mypage/toast/devices` | 수신 동의/거부 **하나로 통합**. `{enabled:true, endpoint, keys}` / `{enabled:false, endpoint}` |
| `GET /api/user/mypage/toast/vapid-key` | 구독용 공개키 (값 불변 — 상수 보관 가능) |

`PUT` 하나인 이유: 프론트가 현재 서버 상태를 몰라도 "원하는 상태"만 보내면 되고, 같은 요청을 두 번 보내도
결과가 같다(동의는 UPSERT, 거부는 없는 기기여도 성공). 응답에 `{enabled, deviceCount, message}` 가 담겨
처리 후 목록을 재조회할 필요도 없다.

기존 알림함 API(목록/unread-count/read/read-all/delete)는 **변경 없음**.
