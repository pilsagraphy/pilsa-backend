package com.back.auth.service;

import com.back.auth.dto.WithdrawRequest;
import com.back.auth.dto.WithdrawTarget;
import com.back.auth.exception.AuthException;
import com.back.auth.mapper.WithdrawMapper;
import com.back.global.security.AuthUtils;
import com.back.mypage.notification.mapper.NotificationDeviceMapper;
import com.back.mypage.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 회원 탈퇴 (PM 확정 2026-08-16).
 *
 * - 탈퇴는 제재 여부와 무관하게 항상 허용 (구글 플레이 계정 삭제 정책·개인정보 삭제권과 정합)
 * - 개인정보(이름/이메일/아이디/전화/비밀번호)는 즉시 파기하고, 학번만 복원 불가능한 해시로 치환 보관
 *   → 부정 이용(제재 회피 재가입) 방지 목적의 최소 보존. 재가입 시 signup 이 해시를 대조해
 *     영구차단자는 영구 거부, 정지자는 정지 종료일까지 거부한다.
 * - 글/댓글은 그대로 남는다(커뮤니티 맥락 보존) — 작성자 표기는 users 조인이라 '탈퇴한 회원'으로 즉시 바뀐다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService {

    private static final String HASH_PREFIX = "del:";

    private final WithdrawMapper withdrawMapper;
    private final NotificationDeviceMapper notificationDeviceMapper;
    private final NotificationMapper notificationMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void withdraw(WithdrawRequest request) {
        Long userId = AuthUtils.currentUserId();

        WithdrawTarget target = withdrawMapper.findWithdrawTarget(userId);
        if (target == null) {
            throw new AuthException("탈퇴 처리할 수 없는 계정입니다.", HttpStatus.NOT_FOUND);
        }

        // 본인 확인 — 토큰 탈취만으로는 탈퇴할 수 없게 현재 비밀번호를 재확인한다
        if (request.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), target.getPasswordHash())) {
            throw new AuthException("비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        // 개인정보 파기 + 소프트삭제 (학번은 재가입 대조용 해시로 치환)
        withdrawMapper.anonymizeUser(userId, hashStudentNo(target.getStudentNo()));

        // 알림 수신 기기(웹 푸시 수신 주소도 개인정보)와 본인 알림함 정리
        notificationDeviceMapper.deleteByUserId(userId);
        notificationMapper.softDeleteAllByUser(userId);

        // 잔여 인증 상태 정리 (인증번호·아이디찾기 통과 플래그) — 실패해도 탈퇴는 성공
        try {
            redisTemplate.delete("auth:code:" + target.getEmail());
            redisTemplate.delete("auth:findid:verified:" + target.getEmail());
        } catch (Exception e) {
            log.warn("탈퇴 시 Redis 정리 실패 - userId: {}, {}", userId, e.getMessage());
        }

        log.info("회원 탈퇴 처리 완료 - userId: {}", userId);
    }

    /**
     * 학번 → 복원 불가능한 해시 (SHA-256, Base64Url 43자 + 접두사 = 47자 ≤ varchar(50)).
     * 원문은 파기하되 재가입 시 같은 학번인지 대조할 수 있게 하는 장치.
     * signup 의 재가입 판정도 반드시 이 메서드를 쓴다 — 형식이 갈리면 대조가 무너진다.
     */
    public static String hashStudentNo(String studentNo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(studentNo.trim().getBytes(StandardCharsets.UTF_8));
            return HASH_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 환경", e);
        }
    }
}
