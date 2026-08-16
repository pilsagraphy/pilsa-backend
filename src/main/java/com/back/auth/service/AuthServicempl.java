package com.back.auth.service;

import com.back.auth.dto.*;
import com.back.auth.mapper.AuthMapper;
import com.back.auth.dto.WithdrawTarget;
import com.back.auth.dto.WithdrawnBanInfo;
import com.back.auth.exception.AuthException;
import com.back.auth.mapper.WithdrawMapper;
import com.back.auth.exception.BannedException;
import com.back.global.security.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.back.auth.service.MailService;
import com.back.auth.dto.FindIdVerifyRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServicempl implements AuthService {

    private final AuthMapper authMapper;
    private final WithdrawMapper withdrawMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MailService mailService;
    private final StringRedisTemplate redisTemplate;

    // 운영과 개발 구분
    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;

    // 리프레시 쿠키 설정 메서드
    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/api/auth/token");
        // 브라우저 창 닫아도 쿠키 유지 : 20분(리프레시 토큰 유지시간)
        // cookie.setMaxAge(60 * 20);
        response.addCookie(cookie);
    }

    // 정지/영구차단 계정 로그인 차단 (만료된 임시정지는 통과 - 스케줄러가 캐시를 정리하기 전이라도 로그인 허용)
    // 프론트가 "2026.03.30 00:00 부터 다시 로그인 할 수 있습니다" 화면을 그릴 수 있도록
    // 메시지 문자열이 아니라 banType/bannedUntil 필드로 내려준다.
    private void checkNotBanned(UserDto user) {
        if ("permanent".equals(user.getBanStatus())) {
            throw new BannedException("영구적으로 차단된 계정입니다.", "permanent", null);
        }
        if ("temporary".equals(user.getBanStatus())
                && user.getBannedUntil() != null
                && user.getBannedUntil().isAfter(LocalDateTime.now())) {
            throw new BannedException("정지된 계정입니다.", "temporary", user.getBannedUntil());
        }
    }

    // 로그인(소셜로그인은 후순위)
    public AuthResponse login(LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse response) {
        // DB 조회
        UserDto user = authMapper.findByLoginId(request.getLoginId());

        // 아이디/비밀번호 확인
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        // 승인된 사용자만 로그인 허용
        if (user.getIsDeleted() == true) {
            throw new AuthException("승인되지 않은 계정입니다.", HttpStatus.UNAUTHORIZED);
        }

        // 정지/영구차단 계정 로그인 차단
        checkNotBanned(user);

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // 리프레시 토큰을 쿠키에 저장
        addRefreshTokenCookie(response, refreshToken);

        // Refresh Token exp 추출
        var claims = jwtUtil.validateRefreshToken(refreshToken);
        long refreshExp = claims.getExpiration().getTime();

        authMapper.updateLastLoginAt(request.getLoginId());

        return new AuthResponse(accessToken, user.getUserId(), user.getMemberType(), user.getAdminLevel(), refreshExp);
    }

    // 로그아웃
    public void logout(HttpServletResponse response, HttpServletRequest request) {
        // 1) refreshToken 쿠키 제거
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setMaxAge(0); // 즉시 만료
        cookie.setPath("/api/auth/token");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        response.addCookie(cookie);

        // 2) 세션키로 닫기 (쿠키에서 값 읽고 jti 추출)
        try {
            String refreshToken = null;
            if (request.getCookies() != null) {
                for (Cookie c : request.getCookies()) {
                    if ("refreshToken".equals(c.getName())) {
                        refreshToken = c.getValue();
                        break;
                    }
                }
            }
//            if (refreshToken != null && !refreshToken.isBlank()) {
//                String sessionKey = jwtUtil.extractJti(refreshToken);
//                if (sessionKey != null) {
//                    int n = authMapper.updateLogoutLogBySession(sessionKey);
//                    if (n == 0) {
//                        log.debug("닫을 세션 로그가 없음 (이미 로그아웃 되었을 수 있음). sessionKey={}", sessionKey);
//                    }
//                } else {
//                    log.debug("refreshToken에서 jti를 추출할 수 없음 (만료/위조 가능). 이메일 기반 닫기는 생략.");
//                }
//            } else {
//                log.debug("refreshToken 쿠키 없음. 이메일 기반 닫기는 생략.");
//            }
        } catch (Exception e) {
            log.warn("로그아웃 로그 기록 실패: {}", e.getMessage(), e);
        }
    }

    // 회원가입
    @Transactional
    public void signup(SignupRequest request) {

        // 입력 형식 검증 — 규칙은 프론트(pilsa-frontend schemas/auth.js zod)와 동일하며,
        // 정규식은 policy_settings(signup_*_regex)에서 로드한다. 프론트 검증만으로는 API 직접 호출을 못 막는다.
        validateSignupFormat(request);

        // 이메일 인증을 실제로 통과했는지 서버에서 확인 — 프론트 화면 검증은 API 직접 호출로 우회된다.
        // (인증 성공 시 MailServiceImpl 이 30분짜리 통과 플래그를 남긴다)
        if (redisTemplate.opsForValue().get("auth:mail:verified:" + request.getEmail()) == null) {
            throw new AuthException("이메일 인증이 완료되지 않았거나 만료되었습니다. 이메일 인증을 다시 진행해주세요.", HttpStatus.FORBIDDEN);
        }

        // 중복 아이디 확인
        if (authMapper.existsByLoginId(request.getLoginId())) {
            throw new AuthException("이미 존재하는 아이디입니다.", HttpStatus.CONFLICT);
        }

        // 중복 이메일 확인
        if (authMapper.existsByEmail(request.getEmail())) {
            throw new AuthException("이미 존재하는 이메일입니다.", HttpStatus.CONFLICT);
        }

        // 학번/전화 중복 확인 — UNIQUE 컬럼인데 사전 검사가 없으면 INSERT 에서 1062 → 500 으로 터진다
        if (request.getStudentNo() == null || request.getStudentNo().isBlank()) {
            throw new AuthException("학번은 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        if (authMapper.existsByStudentNo(request.getStudentNo())) {
            throw new AuthException("이미 가입된 학번입니다.", HttpStatus.CONFLICT);
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()
                && authMapper.existsByPhone(request.getPhone())) {
            throw new AuthException("이미 등록된 전화번호입니다.", HttpStatus.CONFLICT);
        }

        // 재가입 대조 — 탈퇴 시 학번은 복원 불가능한 해시로 보관된다(부정 이용 방지, 개인정보처리방침 명시).
        // 같은 학번으로 탈퇴한 계정 중 제재 상태가 남아 있으면 가입을 거부해 "제재 → 탈퇴 → 재가입" 우회를 차단한다.
        String studentNoHash = WithdrawService.hashStudentNo(request.getStudentNo());
        java.util.List<WithdrawnBanInfo> withdrawnRows = withdrawMapper.findWithdrawnBanByHash(studentNoHash);
        java.time.LocalDateTime latestWithdrawnAt = null;
        for (WithdrawnBanInfo ban : withdrawnRows) {
            if ("permanent".equals(ban.getBanStatus())) {
                throw new AuthException("가입이 제한된 학번입니다.", HttpStatus.FORBIDDEN);
            }
            if ("temporary".equals(ban.getBanStatus())
                    && ban.getBannedUntil() != null
                    && ban.getBannedUntil().isAfter(java.time.LocalDateTime.now())) {
                throw new AuthException("가입이 제한된 학번입니다. ("
                        + ban.getBannedUntil().toLocalDate() + " 이후 가입 가능)", HttpStatus.FORBIDDEN);
            }
            if (ban.getWithdrawnAt() != null
                    && (latestWithdrawnAt == null || ban.getWithdrawnAt().isAfter(latestWithdrawnAt))) {
                latestWithdrawnAt = ban.getWithdrawnAt();
            }
        }
        // 재가입 쿨다운 — 탈퇴/재가입 반복으로 계정 행을 양산하는 어뷰징 차단 (기간은 policy_settings 로 조정)
        if (latestWithdrawnAt != null) {
            int cooldownDays = parseCooldownDays(withdrawMapper.findPolicySetting("rejoin_cooldown_days"));
            java.time.LocalDateTime rejoinableAt = latestWithdrawnAt.plusDays(cooldownDays);
            if (rejoinableAt.isAfter(java.time.LocalDateTime.now())) {
                throw new AuthException("탈퇴 후 " + cooldownDays + "일 동안 재가입할 수 없습니다. ("
                        + rejoinableAt.toLocalDate() + " 이후 가능)", HttpStatus.FORBIDDEN);
            }
        }

        // 비밀번호 암호화
        String encodedPw = passwordEncoder.encode(request.getPassword());

        UserSignupDto user = new UserSignupDto();

        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setMajor(request.getMajor());
        user.setStudentNo(request.getStudentNo());
        user.setEmail(request.getEmail());
        user.setLoginId(request.getLoginId());
        user.setPasswordHash(encodedPw);
        // 가입은 재학생 기본, 관리자는 별도 승격(admin_level)으로만 부여.
        // memberType은 화이트리스트로만 허용 - 임의 문자열(예: "ADMIN")이 member_type에 저장되면
        // JWT 필터의 "ROLE_" + memberType 변환으로 권한이 상승할 수 있어 반드시 차단한다.
        String memberType = request.getMemberType() != null ? request.getMemberType() : "STUDENT";
        if (!"STUDENT".equals(memberType) && !"ALUMNI".equals(memberType)) {
            throw new AuthException("유효하지 않은 회원 구분입니다. (STUDENT/ALUMNI)", HttpStatus.BAD_REQUEST);
        }
        user.setMemberType(memberType);
        user.setAdminLevel(0);
        user.setIsDeleted(Boolean.FALSE);

        authMapper.insertUser(user);

        // 인증 통과 플래그는 1회용 — 같은 인증으로 계정을 반복 생성하지 못하게 소진시킨다
        redisTemplate.delete("auth:mail:verified:" + request.getEmail());
    }

    // 회원가입 - 아이디 중복
    @Override
    public boolean existsByLoginId(String loginId) {
        return authMapper.existsByLoginId(loginId);
    }

    // 회원가입 - 이메일 중복 확인
    @Override
    public boolean existsByEmail(String email) {
        return authMapper.existsByEmail(email);
    }

    @Override
    public void verifyFindIdCode(FindIdVerifyRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            throw new AuthException("이메일과 인증번호를 모두 입력해주세요.", HttpStatus.BAD_REQUEST);
        }

        boolean exists = authMapper.existsByEmail(request.getEmail());
        if (!exists) {
            throw new AuthException("해당 이메일로 가입된 계정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        boolean verified = mailService.verifyCode(request.getEmail(), request.getCode());
        if (!verified) {
            throw new AuthException("인증번호가 일치하지 않거나 만료되었습니다.", HttpStatus.UNAUTHORIZED);
        }

        redisTemplate.opsForValue().set(
                "auth:findid:verified:" + request.getEmail(),
                "true",
                10,
                TimeUnit.MINUTES
        );
    }

    @Override
    public String findLoginIdAfterVerification(String email) {
        if (email == null || email.isBlank()) {
            throw new AuthException("이메일을 입력해주세요.", HttpStatus.BAD_REQUEST);
        }

        String verifiedKey = "auth:findid:verified:" + email;
        String verified = redisTemplate.opsForValue().get(verifiedKey);

        if (!"true".equals(verified)) {
            throw new AuthException("이메일 인증이 완료되지 않았거나 만료되었습니다. 이메일 인증을 다시 진행해주세요.", HttpStatus.UNAUTHORIZED);
        }

        String loginId = authMapper.findLoginIdByEmail(email);
        if (loginId == null || loginId.isBlank()) {
            throw new AuthException("해당 이메일로 가입된 아이디를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        // 1회 조회 후 제거
        redisTemplate.delete(verifiedKey);

        return loginId;
    }

    // 비밀번호 초기화 전 단계 - 아이디/이메일 일치 확인 후 인증번호 발송
    @Override
    public long verifyLoginIdAndEmailAndSendCode(String loginId, String email) {
        boolean exists = authMapper.existsByLoginIdAndEmail(loginId, email);

        if (!exists) {
            throw new AuthException("해당 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        long expireTime = mailService.sendCode(email);

        if (expireTime <= 0) {
            throw new AuthException("인증번호 발송에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return expireTime;
    }

    // 회원가입 입력 형식 검증 — 프론트 zod 스키마(schemas/auth.js)와 규칙·문구를 맞춘다.
    // 정규식은 policy_settings 로 조정 가능하며, 행이 없거나 잘못된 정규식이면 코드 기본값을 쓴다.
    // 길이 상한(이름·아이디 50, 이메일 150)은 DB 컬럼 초과로 500이 터지는 것을 막는 가드.
    private void validateSignupFormat(SignupRequest request) {
        requireMatch(request.getName(), "signup_name_regex",
                "^[a-zA-Zㄱ-ㅎ가-힣]{2,50}$",
                "이름은 2글자 이상, 한글/영문만 입력할 수 있습니다.");
        if (request.getMajor() == null || request.getMajor().isBlank() || request.getMajor().length() > 150) {
            throw new AuthException("학과를 입력해주세요.", HttpStatus.BAD_REQUEST);
        }
        requireMatch(request.getStudentNo(), "signup_student_no_regex",
                "^[0-9]{10}$",
                "학번은 숫자 10자리를 정확히 입력해주세요.");
        requireMatch(request.getEmail(), "signup_email_regex",
                "^[^@ ]+@[^@ ]+[.][^@ ]+$",
                "올바른 이메일 형식이 아닙니다.");
        if (request.getEmail().length() > 150) {
            throw new AuthException("올바른 이메일 형식이 아닙니다.", HttpStatus.BAD_REQUEST);
        }
        requireMatch(request.getPhone(), "signup_phone_regex",
                "^010-[0-9]{4}-[0-9]{4}$",
                "전화번호는 010-0000-0000 형식으로 입력해주세요.");
        requireMatch(request.getLoginId(), "signup_login_id_regex",
                "^[a-zA-Z0-9]{8,50}$",
                "아이디는 8자 이상, 영문과 숫자만 입력할 수 있습니다.");
        requireMatch(request.getPassword(), "signup_password_regex",
                "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,20}$",
                "비밀번호는 문자, 숫자, 특수문자를 포함한 8~20자여야 합니다.");
    }

    // 정책 정규식 검사 — 값이 null 이거나 불일치면 400. 정책 행이 없거나 컴파일 불가면 기본 정규식 사용
    private void requireMatch(String value, String policyCode, String defaultRegex, String message) {
        String regex = authMapper.findPolicySetting(policyCode);
        java.util.regex.Pattern pattern;
        try {
            pattern = java.util.regex.Pattern.compile(
                    (regex == null || regex.isBlank()) ? defaultRegex : regex);
        } catch (Exception e) {
            pattern = java.util.regex.Pattern.compile(defaultRegex);
        }
        if (value == null || !pattern.matcher(value).matches()) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }

    // 재가입 쿨다운 일수 (policy_settings.rejoin_cooldown_days, 기본 30)
    private int parseCooldownDays(String settingValue) {
        try {
            return Integer.parseInt(settingValue);
        } catch (Exception e) {
            return 30;
        }
    }

    // 비밀번호 재설정
    @Override
    public void resetPassword(PasswordResetRequest request) {
        UserDto user = authMapper.findByLoginId(request.getLoginId());
        if (user == null) {
            throw new AuthException("해당 아이디는 존재하지 않습니다.", HttpStatus.NOT_FOUND);
        }

        // 이메일 인증(인증번호) 통과 여부를 서버에서 확인 — 없으면 아이디만 알면 남의 비밀번호를 바꿀 수 있다 (계정 탈취 구멍)
        String verifiedKey = "auth:mail:verified:" + user.getEmail();
        if (redisTemplate.opsForValue().get(verifiedKey) == null) {
            throw new AuthException("이메일 인증이 완료되지 않았거나 만료되었습니다. 이메일 인증을 다시 진행해주세요.", HttpStatus.UNAUTHORIZED);
        }

//        // 승인 Y / 대기 N / 탈퇴 X
//        if ("X".equals(user.getUserApproved())) {
//            throw new AuthException("계정이 비활성화되어 변경할 수 없습니다.", HttpStatus.GONE);
//        }

        // 비밀번호 암호화 후 저장
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        authMapper.updatePassword(user.getLoginId(), encodedNewPassword);
        redisTemplate.delete(verifiedKey); // 1회용 소진
    }

    // 이메일 찾기 - 학번+이름 일치 시 마스킹된 이메일 반환
    @Override
    public String findMaskedEmail(String studentNo, String name) {
        if (studentNo == null || studentNo.isBlank() || name == null || name.isBlank()) {
            throw new AuthException("학번과 이름을 모두 입력해주세요.", HttpStatus.BAD_REQUEST);
        }

        // 학번+이름이 '같은 사용자'를 가리킬 때만 조회됨 (하나라도 불일치하면 null)
        String email = authMapper.findEmailByStudentNoAndName(studentNo.trim(), name.trim());
        if (email == null || email.isBlank()) {
            throw new AuthException("입력하신 학번과 이름에 일치하는 회원 정보가 없습니다.", HttpStatus.NOT_FOUND);
        }

        return maskEmail(email);
    }

    // 이메일 마스킹: 로컬파트(@앞) 앞 2글자만 노출 + "***", @도메인은 그대로
    // 예) abcdefg@gmail.com -> ab***@gmail.com
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***"; // 형식이 이상하면 방어적으로 전부 가림
        }
        String local = email.substring(0, at);
        String domain = email.substring(at); // '@' 포함
        String prefix = local.substring(0, Math.min(2, local.length()));
        return prefix + "***" + domain;
    }

    // 리프레시 토큰을 가지고 있는 쿠키의 존재 여부 확인
    public boolean checkRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())
                        && cookie.getValue() != null
                        && !cookie.getValue().isEmpty()) {
                    return true; // refreshToken 쿠키 존재
                }
            }
        }
        return false; // 없음
    }

    // 리프레시토큰 연장(재발급) -> 로그인 시 수동 연장
    public AuthResponse extend(String refreshToken, HttpServletResponse response) {
        try {
            // 1) 기존 토큰 검증 + oldJti 추출
            var claims = jwtUtil.validateRefreshToken(refreshToken);
            String loginId = claims.getSubject();

            // 2) DB에서 user 다시 조회
            UserDto user = authMapper.findByLoginId(loginId);
            if (user == null) {
                throw new AuthException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED);
            }
            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                throw new AuthException("탈퇴했거나 승인되지 않은 계정입니다.", HttpStatus.UNAUTHORIZED);
            }
            checkNotBanned(user);

            // 3) 새 토큰 발급
            String newAccessToken = jwtUtil.generateAccessToken(user);
            String newRefreshToken = jwtUtil.generateRefreshToken(user);

//            // 4) jti 회전 (열린 행만 대상)
//            String oldJti = jwtUtil.extractJti(refreshToken);
//            String newJti = jwtUtil.extractJti(newRefreshToken);
//            if (oldJti != null && newJti != null && !oldJti.equals(newJti)) {
//                try {
//                    authMapper.rotateSessionKey(oldJti, newJti);
//                } catch (Exception e) {
//                    // 회전 실패해도 세션 연장은 진행 (로그만 영향)
//                    log.warn("rotateSessionKey 실패 oldJti={} newJti={} : {}", oldJti, newJti, e.getMessage());
//                }
//            }

            // 5) 쿠키 교체
            addRefreshTokenCookie(response, newRefreshToken);

            // 6) 응답
            var newClaims = jwtUtil.validateRefreshToken(newRefreshToken);
            long refreshExp = newClaims.getExpiration().getTime();

            return new AuthResponse(newAccessToken, user.getUserId(), user.getMemberType(), user.getAdminLevel(), refreshExp);

        } catch (ExpiredJwtException e) {
            // Refresh Token 자체가 만료된 경우
            // 정상종료이므로 사용자의 재로그인이 필요함
            log.info("Refresh token expired at {}", e.getClaims().getExpiration());
            throw new AuthException("Refresh token (로그인을 다시 해주세요.)", HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            // 비정상 종료
            log.warn("Invalid refresh token: {}", e.getMessage());
            throw new AuthException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
    }

    // 엑세스 토큰 발급/재발급 (+ 리프레시 토큰 회전: 활동 중이면 refresh도 계속 갱신)
    public AuthResponse refresh(String refreshToken, HttpServletResponse response) {
        try {
            var claims = jwtUtil.validateRefreshToken(refreshToken);
            String loginId = claims.getSubject();

            // DB에서 user 다시 조회
            UserDto user = authMapper.findByLoginId(loginId);
            if (user == null) {
                throw new AuthException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED);
            }
            if (Boolean.TRUE.equals(user.getIsDeleted())) {
                throw new AuthException("탈퇴했거나 승인되지 않은 계정입니다.", HttpStatus.UNAUTHORIZED);
            }
            checkNotBanned(user);

            // 액세스 재발급 + 리프레시 토큰 회전(sliding): 재발급받을 때마다 refresh 쿠키도 새로 교체
            String newAccessToken = jwtUtil.generateAccessToken(user);
            String newRefreshToken = jwtUtil.generateRefreshToken(user);
            addRefreshTokenCookie(response, newRefreshToken);

            long refreshExp = jwtUtil.validateRefreshToken(newRefreshToken).getExpiration().getTime();

            return new AuthResponse(newAccessToken, user.getUserId(), user.getMemberType(), user.getAdminLevel(), refreshExp);

        } catch (ExpiredJwtException e) {
            // Refresh Token 자체가 만료된 경우
            // 정상종료이므로 사용자의 재로그인이 필요함
            log.info("Refresh token expired at {}", e.getClaims().getExpiration());
            throw new AuthException("Refresh token expired(로그인을 다시 해주세요.)", HttpStatus.UNAUTHORIZED);

        } catch (JwtException e) {
            // 비정상 종료
            log.warn("Invalid refresh token: {}", e.getMessage());
            throw new AuthException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
        }
    }
}