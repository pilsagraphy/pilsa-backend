package com.back.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.enabled:true}")
    private boolean corsEnabled;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOriginsCsv;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        if (!corsEnabled) {
            // 비활성: 비활성 시 request -> null 말고 그냥 빈 source 반환
            return source;
        }

        CorsConfiguration conf = new CorsConfiguration();
        conf.setAllowCredentials(true); // 쿠키/Authorization 허용

        if (StringUtils.hasText(allowedOriginsCsv)) {
            Arrays.stream(allowedOriginsCsv.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(conf::addAllowedOriginPattern);
        }

        conf.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")); // 프론트가 보낼 수 있는 메서드 종류
        conf.setAllowedHeaders(List.of("*")); // 요청 헤더 중 허용할 목록
        conf.setExposedHeaders(List.of("X-Blocked")); // 프런트에서 읽을 커스텀 헤더
        conf.setMaxAge(3600L); // preflight 캐시

        source.registerCorsConfiguration("/**", conf); // 위 규칙을 백엔드 전체 엔드포인트에 적용
        return source;
    }
}

//    if (StringUtils.hasText(allowedOriginsCsv)) {
//      String[] origins = allowedOriginsCsv.split(",");
//      for (String o : origins) {
//        conf.addAllowedOriginPattern(o.trim()); // 패턴도 허용 (예: https://*.myapp.com)
//      }
//    }