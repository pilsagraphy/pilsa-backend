package com.back.global.mail.service;

import com.back.global.mail.service.AsyncMailService; // 메일 보내는애
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

    private static final long CODE_TTL = 180; // 3분
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public long sendCode(String email) {
        // 1. 6자리 난수 생성
        String code = String.format("%06d", secureRandom.nextInt(900_000) + 100_000);

        // 2. Redis에 저장 (키: auth:code:이메일 / 값: 코드:연장여부)
        redisTemplate.opsForValue().set("auth:code:" + email, code + ":0", CODE_TTL, TimeUnit.SECONDS);

        // 3. 메일 발송 (이전에 만든 비동기 서비스 호출)
        String subject = "[필사그래피] 이메일 인증 안내";
        String html =
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #eee; padding: 20px; text-align: center;'>" +
                        "  <img src='cid:logo' style='width: 80px; margin-bottom: 20px;' alt='필사 로고'>" +
                        "  <h2 style='color: #333;'>이메일 인증번호 안내</h2>" +
                        "  <p style='color: #666; font-size: 16px;'>안녕하세요! 필사그래피 서비스를 이용해 주셔서 감사합니다.</p>" +
                        "  <p style='color: #666; font-size: 16px;'>아래의 인증번호를 입력하여 인증을 완료해 주세요.</p>" +
                        "  <div style='background-color: #f9f9f9; padding: 20px; margin: 30px 0; border-radius: 8px;'>" +
                        "    <span style='font-size: 32px; font-weight: bold; color: #4A90E2; letter-spacing: 5px;'>" + code + "</span>" +
                        "  </div>" +
                        "  <p style='color: #999; font-size: 13px;'>이 인증번호는 3분 동안 유효합니다.</p>" +
                        "  <hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>" +
                        "  <p style='color: #bbb; font-size: 12px;'>본 메일은 발신 전용입니다. 문의사항은 고객센터를 이용해 주세요.</p>" +
                        "</div>";
        asyncMailService.sendHtmlWithLogo(email, subject, html, "인증번호: " + code);

        return CODE_TTL;
    }

    @Override
    public boolean verifyCode(String email, String code) {
        String key = "auth:code:" + email;
        String saved = redisTemplate.opsForValue().get(key);

        if (saved != null && saved.split(":")[0].equals(code)) {
            redisTemplate.delete(key);
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
}