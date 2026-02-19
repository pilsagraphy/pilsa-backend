package com.back.global.mail.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendHtmlWithLogo(String to, String subject, String html, String plainTextFallback) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            // 보내는 사람 주소와 이름 설정
            helper.setFrom(new InternetAddress("pilsagraphy@gmail.com", "필사그래피 관리자"));
            helper.setTo(to);
            helper.setSubject(subject);

            String plain = (plainTextFallback == null || plainTextFallback.isBlank())
                    ? "HTML 메일을 지원하지 않는 환경입니다."
                    : plainTextFallback;
            helper.setText(plain, html);

            // 로고 파일이 src/main/resources/static/logo.png에 있다면 실행됨
            ClassPathResource logo = new ClassPathResource("static/logo.png");
            if (logo.exists()) {
                helper.addInline("logo", logo, "image/png");
            }

            mailSender.send(mime);
            log.info("메일 발송 성공: to={}", to);

        } catch (Exception e) {
            log.error("메일 발송 실패: {}", e.getMessage());
        }
    }
}