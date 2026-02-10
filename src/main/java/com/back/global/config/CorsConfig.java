package com.back.global.config;

public class CorsConfig {
}

//package com.blue.global.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.util.StringUtils;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.CorsConfigurationSource;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//
//import java.util.List;
//
//@Configuration
//public class CorsConfig {
//
//  @Value("${app.cors.enabled:false}")
//  private boolean corsEnabled;
//
//  @Value("${app.cors.allowed-origins:}")
//  private String allowedOriginsCsv;
//
//  @Bean
//  public CorsConfigurationSource corsConfigurationSource() {
//    if (!corsEnabled) {
//      // 비활성: 시큐리티에서 http.cors()가 적용되지 않게 null 리턴
//      return request -> null;
//    }
//
//    CorsConfiguration conf = new CorsConfiguration();
//    conf.setAllowCredentials(true); // 쿠키/Authorization 허용
//
//    if (StringUtils.hasText(allowedOriginsCsv)) {
//      String[] origins = allowedOriginsCsv.split(",");
//      for (String o : origins) {
//        conf.addAllowedOriginPattern(o.trim()); // 패턴도 허용 (예: https://*.myapp.com)
//      }
//    }
//
//    conf.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS")); // 프론트가 보낼 수 있는 메서드 종류
//    conf.setAllowedHeaders(List.of("Authorization","Content-Type","X-Requested-With","Accept","Origin")); // 요청 헤더 중 허용할 목록
//    conf.setExposedHeaders(List.of("X-Blocked")); // 프런트에서 읽을 커스텀 헤더
//    conf.setMaxAge(3600L); // preflight 캐시
//
//    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//    source.registerCorsConfiguration("/**", conf); // 위 규칙을 백엔드 전체 엔드포인트에 적용
//    return source;
//  }
//}
