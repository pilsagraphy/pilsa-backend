package com.back.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//swagger 권한 실험용
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pilsaOpenAPI() {
        // 1. 보안 스키마 이름 정의
        String securityJwtName = "JWT_Auth";

        // 2. 모든 API에 적용할 보안 요구사항 정의
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(securityJwtName);

        // 3. JWT 인증 방식 설정
        Components components = new Components().addSecuritySchemes(securityJwtName, new SecurityScheme()
                .name(securityJwtName)
                .type(SecurityScheme.Type.HTTP) // HTTP 방식
                .scheme("bearer")               // bearer 키워드 사용
                .bearerFormat("JWT"));          // 형식은 JWT

        return new OpenAPI()
                .info(new Info()
                        .title("Pilsa API")
                        .version("v2.0")
                        .description("""
                                필사그래피 홈페이지 백엔드 API 명세.

                                - 도메인(태그)별로 묶여 있고, 상단 필터 입력창으로 태그 검색이 됩니다.
                                - 에러 응답은 항상 JSON 객체 `{"message": "..."}` 입니다. 정지/차단은 `banType`, `bannedUntil` 필드가 추가됩니다.
                                - 미인증=401, 권한부족=403. 회원 API 는 로그인(Bearer 토큰)만 확인하고, 열람 대상은 데이터(게시판 정책)로 판정합니다.
                                - 정본 명세는 qa_pilsa `api_endpoints` 테이블입니다. 이 문서와 다르면 알려주세요."""))
                .addSecurityItem(securityRequirement) // 이 줄이 있어야 전역 자물쇠가 생깁니다.
                .components(components);
    }
}