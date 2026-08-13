package com.back.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.back.global.security.JwtAuthenticationFilter;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtFilter;
    
    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }
    
    // 임시작성하였으므로 윤정민씨는 아래 주석 코드를 참고하여 더 정교하게 바꿔주시기 바랍니다.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(withDefaults())
            .csrf(AbstractHttpConfigurer::disable) // JWT 사용할 예정: CSRF 비활성(운영도 JWT면 보통 disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 프리플라이트 요청 전체 허용
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 누구나 접근
                .requestMatchers(
                    "/api/auth/**",
                    "/api/public/**",
                    "/api/mail/**", // 인증번호 관련
                    "/swagger-ui/**", // 스웨거 관련
                    "/v3/api-docs/**", // 스웨거 관련
                    "/uploads/**" // 파일 경로
                ).permitAll()
                
                // 관리자 화면 전용 경로만 URL 레벨에서 막는다 (admin_level>=1 → ROLE_ADMIN)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // 그 외 회원 기능(/api/stu/**, /api/reports, /api/notifications ...)은
                // "로그인 여부"만 URL에서 확인하고, 실제 접근 가능 여부는 데이터로 판정한다.
                //  - 게시판: boards.read_scope(열람 대상) / boards.write_level(작성 최소 관리레벨)
                //  - 그 외 : 각 서비스가 AuthUtils 로 신분·관리레벨을 확인
                // 신분(재학생/졸업생)을 URL 접두사로 가르지 않는 이유: 관리자가 런타임에 만든 게시판의
                // 열람 대상을 정적 URL 패턴으로는 표현할 수 없기 때문이다.
                .requestMatchers("/api/stu/**", "/api/alu/**").authenticated()
                
                // 그 외는 로그인 필요
                .anyRequest().authenticated()
            )
            // 미인증(토큰 누락/무효)과 권한 부족을 구분해서 응답
            //  - 기본 EntryPoint는 미인증도 403으로 떨어져서 "로그인했는데 왜 403?" 혼선을 유발
            //    (특히 multipart 게시글 등록에서 Authorization 헤더가 빠질 때) → 401로 명확히 구분
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setHeader("WWW-Authenticate", "Bearer");
                    response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED,
                            "인증이 필요합니다. (Authorization 헤더 누락 또는 유효하지 않은 토큰)");
                })
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN,
                            "접근 권한이 없습니다."))
            )
            // 필터 입히기
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    
    // 비밀번호 암호화
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

//package com.blue.global.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
//import com.blue.global.security.JwtAuthenticationFilter;
//import static org.springframework.security.config.Customizer.withDefaults;
//
//@Configuration
//@EnableWebSecurity
/// / 스프링 시큐리티 설정
//public class SecurityConfig {
//
//  private final JwtAuthenticationFilter jwtFilter;
//
//  public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
//    this.jwtFilter = jwtFilter;
//  }
//
//  @Bean
//  // 시큐리티 필터
//  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
//    http
//        .cors(withDefaults())
//        .csrf(AbstractHttpConfigurer::disable) // JWT 사용할 예정: CSRF 비활성(운영도 JWT면 보통 disable)
//        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
//        .authorizeHttpRequests(auth -> auth
//            // 모든 권한
//            .requestMatchers(
//                "/actuator/health",
//                "/api/auth/**",
//                "/api/mail/**").permitAll()
//
//            // 로그인한 사람만
//            .requestMatchers(
//                "/api/ping",
//                "/api/common/**",
//                "/api/work/**",
//                "/api/info/**",
//                "/api/sheets/**").authenticated()
//
//            // 리드(슈퍼+매니저) 전용 DB API
//            .requestMatchers(
//                "/api/lead/**").hasAnyRole("SUPERADMIN","MANAGER")
//
//            // 본사 (최고 관리자)
//            .requestMatchers(
//                "/api/super/**").hasRole("SUPERADMIN")
//
//            // 관리자 (팀장)
//            .requestMatchers(
//                "/api/admin/**").hasRole("MANAGER")
//
//            // 직원 (일반 사용자)
//            .requestMatchers(
//                "/api/staff/**").hasRole("STAFF")
//            .anyRequest().authenticated()
//        )
//        // 필터 입히기
//        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
//    return http.build();
//  }
//
//  @Bean
//  // 비밀번호 암호화
//  public PasswordEncoder passwordEncoder() {
//    return new BCryptPasswordEncoder();
//  }
//}
