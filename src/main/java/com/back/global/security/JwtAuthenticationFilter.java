package com.back.global.security;

import com.back.auth.mapper.AuthMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// JWT Access Token 검증 필터
// - 매 요청마다 Authorization 헤더 검사
// - 토큰이 유효하면 JWT 해석해서 SecurityContext에 이메일 + 권한 심음

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AuthMapper authMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        // token 관련 API는 AccessToken 검증 건너뜀
        // 로그인/회원가입 등 인증이 필요 없는 경로는 통과
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        // 프런트에서 읽을 커스텀 헤더 노출 (CORS)
        response.addHeader("Access-Control-Expose-Headers", "WWW-Authenticate, X-Token-Expired, X-Blocked");

        // Authorization 헤더 추출
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            Claims claims = jwtUtil.validateAccessToken(token);
            String loginId = claims.getSubject();

            // 검증 성공: 어떤 요청(URI) 때문에 검증됐는지 + 쓰레드명 로깅
            log.debug("엑세스 토큰 검증 -> {} ({})",
                    request.getRequestURI(), Thread.currentThread().getName());

            if (loginId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // DB에서 차단 상태 확인
                var user = authMapper.findByLoginId(loginId);
                if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
                    log.warn("차단된 계정(is_deleted=true) 접근: {}", loginId);
                    response.setHeader("X-Blocked", "true"); // 프론트에서 탈퇴여부를 판단할 사용자 정의 헤더
                    response.sendError(HttpServletResponse.SC_GONE, "탈퇴되었거나 승인되지 않은 계정입니다.");
                    response.flushBuffer();
                    return;
                }

                // member_type + admin_level → Security 권한으로 변환 (DB 최신값 기준)
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (user.getMemberType() != null && !user.getMemberType().isBlank()) {
                    // ROLE_STUDENT / ROLE_ALUMNI
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getMemberType()));
                }
                if (user.getAdminLevel() != null && user.getAdminLevel() >= 1) {
                    // 관리 레벨 1~3 → ROLE_ADMIN (기존 관리자 체크와 호환)
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }

                // principal 은 loginId만 사용
                var userId = user.getUserId();
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
            }

        } catch (ExpiredJwtException e) {
            // 만료 위치/쓰레드까지 함께
            log.warn("엑세스 토큰 만료 -> {} ({}) exp={}",
                    request.getRequestURI(), Thread.currentThread().getName(), e.getClaims().getExpiration());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The access token expired\"");
            response.setHeader("X-Token-Expired", "1"); // 프런트에서 만료 케이스 식별용 (옵션)
            response.flushBuffer();
            return;
        } catch (JwtException e) {
            // JWT 관련 나머지 예외
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 토큰이 "있는데" 잘못된 경우만 401
            response.setHeader("WWW-Authenticate",
                    "Bearer error=\"invalid_token\", error_description=\"Invalid access token\"");
            response.setHeader("X-Token-Expired", "0");
            response.flushBuffer();
            return;
        }

        chain.doFilter(request, response);
    }
}