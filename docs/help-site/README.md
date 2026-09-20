# help.pilsa.co.kr 정적 페이지

Play 스토어·구글 OAuth 인증에 필요한 정책 문서 5종. **이 폴더가 원본이고, 서버에는 복사본이 올라간다.**
Next.js 앱과 무관한 순수 HTML 이라 프론트 배포에 딸려 가지 않는다 — 고친 뒤 아래 절차로 직접 올려야 한다.

| 파일 | 주소 |
|---|---|
| `terms-of-service.html` | https://help.pilsa.co.kr/terms-of-service.html |
| `privacy-policy.html` | https://help.pilsa.co.kr/privacy-policy.html |
| `sanction-policy.html` | https://help.pilsa.co.kr/sanction-policy.html |
| `account-deletion.html` | https://help.pilsa.co.kr/account-deletion.html |
| `child-safety.html` | https://help.pilsa.co.kr/child-safety.html |

프론트에서 이 주소들은 `constants/routes.js` 의 `HELP_LINKS` · `SANCTION_POLICY_URL` 이 들고 있다.

## 배포

같은 EC2(ssh `pilsa`)의 nginx 정적 경로에 얹는다. 파일 소유자가 root 라 홈으로 올린 뒤 `sudo cp` 한다.

```bash
scp docs/help-site/*.html pilsa:~/
ssh pilsa 'for f in terms-of-service privacy-policy sanction-policy account-deletion child-safety; do
  sudo cp ~/$f.html /usr/share/nginx/help/$f.html
  sudo chown root:root /usr/share/nginx/help/$f.html
  sudo chmod 644 /usr/share/nginx/help/$f.html
  rm -f ~/$f.html
done'
```

- nginx 설정: `/etc/nginx/conf.d/help-site.conf` (root `/usr/share/nginx/help`)
- 정적 파일이라 nginx 재시작은 필요 없다. 반영 확인: `curl -s https://help.pilsa.co.kr/privacy-policy.html | grep ...`

> 서버에서만 고치면 이 폴더와 어긋난다. 2026-09-13 에 실제로 그렇게 됐다 — 반드시 여기서 고치고 올릴 것.

## 내용을 바꿔야 하는 때

- 문의 주소·운영 주체·개인정보 보호책임자가 바뀔 때
- **수집 항목이나 위탁 업체가 늘 때** (예: 2026-09-13 구글 계정·캘린더 연동, Resend 메일 발송 추가)
- 제재 정책(`policy_settings`)의 기준이 바뀔 때
- 개인정보처리방침을 바꾸면 문서 끝의 **공고일자·시행일자**도 함께 갱신한다.
  이용자에게 불리한 중요한 변경은 시행 30일 전, 그 밖에는 7일 전에 공지한다고 방침에 적혀 있다.
