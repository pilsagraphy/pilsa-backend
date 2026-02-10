package com.back.global.mail;

public class MailServiceImpl
{
}

//package com.todolist.global.util.mail;
//
//import jakarta.mail.internet.InternetAddress;
//import jakarta.mail.internet.MimeMessage;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.mail.javamail.JavaMailSender;
//import org.springframework.mail.javamail.MimeMessageHelper;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//
//import java.nio.charset.StandardCharsets;
//
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class MailServiceImpl implements MailService {
//
//  private final JavaMailSender mailSender;
//
//  @Async // 비동기 실행으로 사용자 대기 시간 단축
//  @Override
//  public void sendHtmlWithLogo(String to, String subject, String html, String plainTextFallback) {
//    try {
//      MimeMessage mime = mailSender.createMimeMessage();
//      MimeMessageHelper helper = new MimeMessageHelper(
//          mime,
//          MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
//          StandardCharsets.UTF_8.name()
//      );
//
//      helper.setFrom(new InternetAddress("no-reply@todolist.co.kr", "TODO 관리자"));
//      helper.setTo(to);
//      helper.setSubject(subject);
//
//      String plain = (plainTextFallback == null || plainTextFallback.isBlank())
//          ? "HTML 메일을 지원하지 않는 환경입니다. 웹에서 확인해 주세요."
//          : plainTextFallback;
//      helper.setText(plain, html);
//
//      // 로고 리소스 삽입 (src/main/resources/static/logo.png 필요)
//      ClassPathResource logo = new ClassPathResource("static/logo.png");
//      if (logo.exists()) {
//        helper.addInline("logo", logo, "image/png");
//      }
//
//      mailSender.send(mime);
//      log.info("메일 발송 성공: to={}, subject={}", to, subject);
//
//    } catch (Exception e) {
//      log.error("메일 발송 실패: to={}, error={}", to, e.getMessage());
//      // 비동기 로직이므로 여기서 예외를 던져도 사용자 응답에는 지장이 없음
//    }
//  }
//}