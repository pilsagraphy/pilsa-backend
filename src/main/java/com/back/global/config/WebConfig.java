package com.back.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  
  @Value("${app.upload.dir}")
  private String uploadDir;
  
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String path = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
    // 정적 서빙은 명예의전당 사진(공개 화면, attachments 와 무관한 시드 이미지)만 남긴다.
    // 게시판 첨부(/uploads/board-*)의 정적 서빙은 폐지(2026-08-23 PM 결정) — URL 만 알면
    // 비로그인도 열리는 구멍이라, 열람 권한(read_scope)을 검사하는 인증형 API
    // GET /api/user/files/{fileId} 가 유일한 접근 경로다.
    registry.addResourceHandler("/uploads/Honor/**")
        .addResourceLocations("file:" + path + "Honor/");
  }
}