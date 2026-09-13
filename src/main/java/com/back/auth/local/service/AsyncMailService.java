package com.back.auth.local.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 메일 발송 (인증번호 등). 본문은 text/plain + text/html 두 벌을 담은 multipart/alternative 다.
 *
 * <p><b>이미지를 붙이지 않는다.</b> 예전에는 로고를 {@code addInline("logo", ...)} 로 넣고 본문에서 {@code cid:logo} 로
 * 참조했는데, 아이폰(Gmail 앱·Apple Mail)은 이 인라인 이미지를 <b>첨부파일</b>로도 함께 보여 준다 — 인증번호 메일에
 * 첨부파일이 달려 오니 받는 사람이 의심하게 되고, 스팸 판정에도 불리하다. 로고는 글자(워드마크)로 대신한다.
 *
 * <p><b>스팸함으로 가지 않게 하는 것들</b>: (1) HTML 과 짝이 되는 <b>제대로 된 평문 본문</b>을 함께 보낸다 —
 * 평문이 한 줄뿐인 HTML 메일은 스팸 점수가 크게 오른다. (2) From 은 실제 인증 계정({@code spring.mail.username})과
 * 같아야 SPF·DKIM 이 어긋나지 않는다. (3) 회신 주소를 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncMailService {

    private final JavaMailSender mailSender;

    /**
     * 보내는 사람 주소. 기본값은 SMTP 인증 계정({@code spring.mail.username}) — Gmail 로 보낼 때는 인증 계정과
     * From 이 같아야 발신자가 고쳐 쓰이지 않는다. 나중에 도메인 메일(예: no-reply@pilsa.co.kr)로 옮기면
     * 발송 서비스의 SMTP 사용자명이 이메일이 아닐 수 있어(Resend·Brevo 등) 이 값만 따로 지정하면 된다.
     */
    @Value("${mail.from.address:${spring.mail.username}}")
    private String senderAddress;

    // 영문으로 둔다 — application.properties 는 ISO-8859-1 로 읽혀 한글을 적으면 보낸이가 깨진다(실제로 겪었다).
    // 영문이면 설정 파일에 그대로 둘 수 있어 무엇이 나가는지 설정만 보고도 안다
    @Value("${mail.from.name:Pilsagraphy}")
    private String senderName;

    @Async
    public void sendHtml(String to, String subject, String html, String plainText) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            // 평문+HTML 두 벌(multipart/alternative)을 실으므로 multipart 모드여야 한다.
            // MULTIPART_MODE_NO 로 두면 setText(plain, html) 이 "Not in multipart mode" 로 던진다.
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime,
                    MimeMessageHelper.MULTIPART_MODE_MIXED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(new InternetAddress(senderAddress, senderName, StandardCharsets.UTF_8.name()));
            helper.setReplyTo(new InternetAddress(senderAddress, senderName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);

            String plain = (plainText == null || plainText.isBlank())
                    ? "HTML 메일을 지원하지 않는 환경입니다."
                    : plainText;
            helper.setText(plain, html);

            mailSender.send(mime);
            log.info("메일 발송 성공: to={}", to);

        } catch (Exception e) {
            log.error("메일 발송 실패: {}", e.getMessage());
        }
    }
}
