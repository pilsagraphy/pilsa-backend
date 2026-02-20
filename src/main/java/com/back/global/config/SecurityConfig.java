package com.back.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

//    private final JwtAuthenticationFilter jwtFilter;
//
//    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
//        this.jwtFilter = jwtFilter;
//    }

    // 임시작성하였으므로 윤정민씨는 아래 주석 코드를 참고하여 더 정교하게 바꿔주시기 바랍니다.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable) // JWT 사용할 예정: CSRF 비활성(운영도 JWT면 보통 disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 누구나 접근
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/public/**",
                                "/api/mail/**", // 인증번호 관련 // 로그인 필요 없는거 - api 없어서 열어둠
                                "/swagger-ui/**", // 스웨거 관련
                                "/v3/api-docs/**" // 스웨거 관련
                        ).permitAll()

                        // 역할별 접근
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/stu/**").hasRole("STUDENTS")
                        .requestMatchers("/api/alu/**").hasRole("ALUMNI")

                        // 그 외는 로그인 필요
                        .anyRequest().authenticated()
                );
                // 필터 입히기
//                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

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
