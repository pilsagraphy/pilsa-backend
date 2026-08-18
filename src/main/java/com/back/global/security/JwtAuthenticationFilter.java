package com.back.global.security;

import com.back.auth.exception.BannedException;
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
import java.time.LocalDateTime;
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
        response.addHeader("Access-Control-Expose-Headers", "WWW-Authenticate, X-Token-Expired, X-Blocked, X-Ban-Type");

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
                    writeJson(response, HttpServletResponse.SC_GONE,
                            "{\"message\":\"탈퇴되었거나 승인되지 않은 계정입니다.\"}");
                    return;
                }

                // 정지/영구차단 계정 접근 차단 (세션 도중 제재된 경우 대비)
                boolean isPermanentBan = "permanent".equals(user.getBanStatus());
                boolean isTemporaryBan = "temporary".equals(user.getBanStatus())
                        && user.getBannedUntil() != null
                        && user.getBannedUntil().isAfter(LocalDateTime.now());
                if (isPermanentBan || isTemporaryBan) {
                    log.warn("제재된 계정 접근: {} (banStatus={})", loginId, user.getBanStatus());
                    response.setHeader("X-Ban-Type", user.getBanStatus());
                    // 로그인 시점의 BannedException 응답과 동일한 형태로 내려 프론트 분기가 한 곳에서 끝나게 한다
                    String body = isPermanentBan
                            ? "{\"message\":\"영구적으로 차단된 계정입니다.\",\"banType\":\"permanent\",\"bannedUntil\":null}"
                            : "{\"message\":\"정지된 계정입니다.\",\"banType\":\"temporary\",\"bannedUntil\":\""
                              + BannedException.formatBannedUntil(user.getBannedUntil()) + "\"}";
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, body);
                    return;
                }

                // member_type + admin_level → Security 권한으로 변환 (DB 최신값 기준)
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                // 알려진 member_type만 권한으로 매핑 (임의 문자열이 ROLE_로 승격되는 것 방지 - 이중 방어)
                if ("STUDENT".equals(user.getMemberType()) || "ALUMNI".equals(user.getMemberType())) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getMemberType()));
                }
                if (user.getAdminLevel() != null && user.getAdminLevel() >= 1) {
                    // 관리 레벨 1~3 → ROLE_ADMIN (기존 관리자 체크와 호환)
                    authorities.add(new SimpleGrantedAuthority(AuthUtils.ROLE_ADMIN));
                    // 실제 레벨도 권한으로 심는다 → 게시판 작성권한(write_level) 판정에 사용 (AuthUtils.adminLevel())
                    authorities.add(new SimpleGrantedAuthority(AuthUtils.ADMIN_LEVEL_PREFIX + user.getAdminLevel()));
                }

                // principal 은 loginId만 사용
                var userId = user.getUserId();
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
            }

        } catch (ExpiredJwtException e) {
            // 공개 경로는 토큰이 만료됐어도 통과시킨다 — 프론트가 stale 토큰을 전역 첨부해도
            // 비로그인 공개 리소스(/api/donations 등)가 401로 깨지지 않게 한다
            if (isPublicPath(path)) {
                chain.doFilter(request, response);
                return;
            }
            // 만료 위치/쓰레드까지 함께
            log.warn("엑세스 토큰 만료 -> {} ({}) exp={}",
                    request.getRequestURI(), Thread.currentThread().getName(), e.getClaims().getExpiration());

            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The access token expired\"");
            response.setHeader("X-Token-Expired", "1"); // 프런트에서 만료 케이스 식별용 (옵션)
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"message\":\"액세스 토큰이 만료되었습니다.\"}");
            return;
        } catch (JwtException e) {
            if (isPublicPath(path)) {
                chain.doFilter(request, response);
                return;
            }
            // JWT 관련 나머지 예외 — 토큰이 "있는데" 잘못된 경우만 401
            response.setHeader("WWW-Authenticate",
                    "Bearer error=\"invalid_token\", error_description=\"Invalid access token\"");
            response.setHeader("X-Token-Expired", "0");
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"message\":\"유효하지 않은 액세스 토큰입니다.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    // SecurityConfig의 permitAll 목록과 동기화할 것 (/api/auth/** 는 위에서 이미 스킵)
    private boolean isPublicPath(String path) {
        return path.startsWith("/api/mail/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/uploads/")
                || path.equals("/api/donations")
                || path.equals("/api/quotes/current")
                || path.equals("/api/event")
                || path.equals("/api/event/calendar.ics");
    }

    // 에러 응답은 항상 {"message": ...} JSON 객체 계약을 지킨다 (sendError는 본문에서 메시지가 탈락함)
    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
        response.flushBuffer();
    }
}