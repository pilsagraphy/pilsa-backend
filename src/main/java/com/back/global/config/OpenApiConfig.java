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
                                - 도메인(태그)별로 묶여 있고, 상단 필터 입력창으로 도메인(태그)별 검색이 됩니다."""))
                .addSecurityItem(securityRequirement) // 이 줄이 있어야 전역 자물쇠가 생깁니다.
                .components(components);
    }
}