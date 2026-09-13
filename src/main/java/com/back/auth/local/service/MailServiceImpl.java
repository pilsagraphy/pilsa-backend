package com.back.auth.local.service;

import com.back.auth.core.mapper.AuthMapper; // 정책값(policy_settings) 조회
import com.back.auth.local.service.AsyncMailService; // 메일 보내는애
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate; // 레디스 도구
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit; // 시간 단위
import java.security.SecureRandom; // 난수 생성기

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final StringRedisTemplate redisTemplate;
    // 메일 발송을 담당하는 별도의 서비스가 있다면 주입받음
    private final AsyncMailService asyncMailService;
    private final AuthMapper authMapper;

    private static final long CODE_TTL = 180; // 3분
    private static final long DEFAULT_VERIFIED_TTL_MINUTES = 30; // 인증 통과 플래그 기본 유효시간
    private final SecureRandom secureRandom = new SecureRandom();

    // 메일 본문에서 쓰는 값들. 브랜드 색은 #212121 하나로 통일한다(프론트 CI 색과 동일)
    private static final String BRAND_COLOR = "#212121";
    private static final String SITE_URL = "https://pilsa.co.kr";
    // 로고는 cid 인라인이 아니라 원격 이미지다 — 인라인으로 넣으면 아이폰이 첨부파일로도 보여 준다.
    // 흰 바탕으로 구워 둔 파일이라 다크 모드 메일에서도 로고가 묻히지 않는다
    private static final String LOGO_URL = SITE_URL + "/icons/email-logo.png";
    // 문의는 메일로 받는다. 인증 메일을 받아 본 사람이 그 자리에서 답장하듯 눌러 쓸 수 있는 것이 자연스럽고,
    // 구글 폼은 로그인 화면을 거치는 사람이 있어 메일 안에서는 걸림돌이 된다.
    private static final String INQUIRY_EMAIL = "help@pilsa.co.kr";
    private static final String INQUIRY_URL = "mailto:" + INQUIRY_EMAIL;
    private static final String PRIVACY_URL = "https://help.pilsa.co.kr/privacy-policy.html";
    private static final String CLUB_ADDRESS = "경희대학교 국제캠퍼스 학생회관 614호";

    @Override
    public long sendCode(String email) {
        // 1. 6자리 난수 생성
        String code = String.format("%06d", secureRandom.nextInt(900_000) + 100_000);

        // 2. Redis에 저장 (키: auth:code:이메일 / 값: 코드:연장여부)
        redisTemplate.opsForValue().set("auth:code:" + email, code + ":0", CODE_TTL, TimeUnit.SECONDS);

        // 3. 메일 발송 (비동기)
        //
        // 스팸함으로 가지 않게 하려고 지키는 것들:
        //  - 로고는 <img src="https://..."> 원격 이미지다. cid 인라인으로 넣으면 아이폰(Gmail 앱·Apple Mail)이
        //    그 이미지를 첨부파일로도 보여 줘서, 인증 메일에 첨부가 달려 오는 것처럼 보인다.
        //  - HTML 과 같은 내용의 평문 본문을 함께 보낸다. 평문이 한 줄뿐이면 스팸 점수가 크게 오른다.
        //  - 보내는 곳이 누구인지(동아리명·주소), 왜 받았는지, 문의·개인정보처리방침을 본문에 적는다.
        //    수신자가 이유를 알 수 있는 메일일수록 스팸으로 덜 걸린다.
        String subject = "[필사그래피] 이메일 인증번호 " + code;
        String fontStack = "-apple-system, BlinkMacSystemFont, \"Apple SD Gothic Neo\", \"Malgun Gothic\", \"Noto Sans KR\", sans-serif";
        String linkStyle = "color: " + BRAND_COLOR + "; text-decoration: underline;";

        String html =
                "<div style='background-color: #ffffff; padding: 24px 0;'>" +
                        "<div style='font-family: " + fontStack + "; max-width: 480px; margin: 0 auto; padding: 28px 24px; color: " + BRAND_COLOR + "; border: 1px solid #eeeeee; border-radius: 12px;'>" +
                        // 로고 + 워드마크 (이미지가 차단돼도 alt 와 워드마크로 누가 보냈는지 알 수 있다)
                        "  <div style='margin: 0 0 22px; padding: 0 0 18px; border-bottom: 1px solid #f0f0f0; text-align: center;'>" +
                        // margin: 0 auto — block 으로 두고 좌우 auto 여백을 줘야 자기 줄에서 가운데에 선다.
                        // inline-block 이면 워드마크와 같은 줄에 붙어 baseline 기준으로 어긋나 보인다
                        "    <img src='" + LOGO_URL + "' width='48' height='48' alt='필사그래피' style='display: block; width: 48px; height: 48px; border: 0; border-radius: 12px; margin: 0 auto 10px;'>" +
                        "    <span style='font-size: 13px; font-weight: 700; letter-spacing: 3px; color: " + BRAND_COLOR + ";'>PILSAGRAPHY</span>" +
                        "  </div>" +
                        // 인증번호를 맨 위에 — 대부분은 이 숫자만 보고 창을 닫는다
                        "  <p style='margin: 0 0 10px; font-size: 12px; letter-spacing: 0.5px; color: #919191; text-align: center;'>이메일 인증번호</p>" +
                        "  <div style='background-color: #f7f7f7; padding: 20px 12px; margin: 0 0 14px; border-radius: 10px; text-align: center;'>" +
                        "    <span style='font-size: 32px; font-weight: 700; letter-spacing: 8px; color: " + BRAND_COLOR + ";'>" + code + "</span>" +
                        "  </div>" +
                        "  <p style='margin: 0 0 20px; font-size: 13px; line-height: 1.6; color: #757575; text-align: center;'>회원가입 · 비밀번호 재설정에 사용하는 번호입니다.</p>" +
                        // 안내는 서술형 대신 목록으로 — 읽는 부담을 줄인다
                        "  <ul style='margin: 0 0 22px; padding-left: 20px; font-size: 13px; line-height: 1.8; color: #454545;'>" +
                        "    <li>유효시간 <strong>3분</strong>이 지나면 재발송해 주세요</li>" +
                        "    <li>요청하지 않으셨다면 이 메일을 무시하셔도 됩니다</li>" +
                        "    <li>인증번호는 다른 사람에게 알려 주지 마세요</li>" +
                        "  </ul>" +
                        "  <hr style='border: none; border-top: 1px solid #eeeeee; margin: 0 0 14px;'>" +
                        "  <p style='margin: 0 0 4px; font-size: 11px; line-height: 1.7; color: #919191; text-align: center;'>필사그래피 · " + CLUB_ADDRESS + "</p>" +
                        "  <p style='margin: 0 0 4px; font-size: 11px; line-height: 1.7; color: #919191; text-align: center;'>" +
                        "<a href='" + SITE_URL + "' style='" + linkStyle + "'>홈페이지</a>" +
                        " · <a href='" + INQUIRY_URL + "' style='" + linkStyle + "'>문의</a>" +
                        " · <a href='" + PRIVACY_URL + "' style='" + linkStyle + "'>개인정보처리방침</a></p>" +
                        "  <p style='margin: 0; font-size: 11px; line-height: 1.7; color: #919191; text-align: center;'>회원 인증을 위해 발송된 안내 메일이며, 광고성 정보가 없습니다.</p>" +
                        "</div></div>";

        String plain = "[필사그래피] 이메일 인증번호\n\n"
                + "  " + code + "\n\n"
                + "회원가입 · 비밀번호 재설정에 사용하는 번호입니다.\n\n"
                + "- 유효시간 3분이 지나면 재발송해 주세요\n"
                + "- 요청하지 않으셨다면 이 메일을 무시하셔도 됩니다\n"
                + "- 인증번호는 다른 사람에게 알려 주지 마세요\n\n"
                + "--------------------------------------------------\n"
                + "필사그래피 · " + CLUB_ADDRESS + "\n"
                + "홈페이지: " + SITE_URL + "\n"
                + "문의: " + INQUIRY_EMAIL + "\n"
                + "개인정보처리방침: " + PRIVACY_URL + "\n"
                + "회원 인증을 위해 발송된 안내 메일이며, 광고성 정보가 없습니다.\n";

        asyncMailService.sendHtml(email, subject, html, plain);

        return CODE_TTL;
    }

    @Override
    public boolean verifyCode(String email, String code) {
        String key = "auth:code:" + email;
        String saved = redisTemplate.opsForValue().get(key);

        if (saved != null && saved.split(":")[0].equals(code)) {
            redisTemplate.delete(key);
            // 인증 통과 흔적을 남긴다 — 회원가입/비밀번호 초기화가 "인증을 실제로 통과했는지" 서버에서 확인하는 근거.
            // (프론트 화면 검증만으로는 API 직접 호출을 못 막는다)
            // 유효시간은 policy_settings.mail_verified_ttl_minutes (기본 30분) — 만료 후 시도하면 재인증 안내
            redisTemplate.opsForValue().set("auth:mail:verified:" + email, "1", verifiedTtlMinutes(), TimeUnit.MINUTES);
            return true;
        }
        return false;
    }

    @Override
    public long extendTime(String email) {
        String key = "auth:code:" + email;
        String saved = redisTemplate.opsForValue().get(key);
        //데이터가 없거나, 한번 연장 했으면 연장 안해줌. 연장기회는 1번!
        if (saved == null || saved.split(":")[1].equals("1")) return -1;

        String code = saved.split(":")[0]; // 인증번호는 유지
        redisTemplate.opsForValue().set(key, code + ":1", CODE_TTL, TimeUnit.SECONDS); // 3분으로 다시 연장해줌
        return CODE_TTL;
    }

    @Override
    public long getRemainingTime(String email) {
        return redisTemplate.getExpire("auth:code:" + email, TimeUnit.SECONDS);
    }

    // 인증 통과 플래그 유효시간(분) — policy_settings 에서 로드, 없거나 숫자가 아니면 기본 30
    private long verifiedTtlMinutes() {
        try {
            return Long.parseLong(authMapper.findPolicySetting("mail_verified_ttl_minutes"));
        } catch (Exception e) {
            return DEFAULT_VERIFIED_TTL_MINUTES;
        }
    }
}