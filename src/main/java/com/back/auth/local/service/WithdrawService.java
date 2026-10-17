package com.back.auth.local.service;

import com.back.auth.local.dto.WithdrawRequest;
import com.back.auth.local.dto.WithdrawTarget;
import com.back.auth.core.exception.AuthException;
import com.back.auth.local.mapper.WithdrawMapper;
import com.back.board.attachment.service.AttachmentService;
import com.back.board.draft.mapper.DraftMapper;
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
    // 초안·초안 첨부 정리용 (탈퇴자는 재로그인이 불가해 초안 삭제 API 를 다시 탈 수 없다)
    private final DraftMapper draftMapper;
    private final AttachmentService attachmentService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    // 탈퇴 시 소셜 연결·캘린더 연동을 같이 지운다 — 남겨 두면 그 소셜 계정으로 재가입·재연결이 영구히 막히고(탈퇴 행에 붙어 있어 409),
    // 캘린더 배치는 탈퇴자에게 계속 일정을 보낸다 (감사 2026-10-13)
    private final com.back.auth.social.mapper.SocialAccountMapper socialAccountMapper;
    private final com.back.mypage.calendar.service.GoogleCalendarService googleCalendarService;
    private final com.back.mypage.calendar.service.KakaoCalendarService kakaoCalendarService;
    private final com.back.mypage.calendar.service.NaverCalendarService naverCalendarService;
    // 연동 여부 선확인용 — 각 unlink 는 연동이 없으면 404 를 던지는데, 그 예외가 탈퇴 트랜잭션을 되돌린다 (anonymizeAndCleanup 주석)
    private final com.back.mypage.calendar.mapper.GoogleCalendarMapper googleCalendarMapper;
    private final com.back.mypage.calendar.mapper.KakaoCalendarMapper kakaoCalendarMapper;
    private final com.back.mypage.calendar.mapper.NaverCalendarMapper naverCalendarMapper;

    /** 소셜 전용 계정의 탈퇴 확인 문구 — 프론트 WithdrawModal 과 같은 값 */
    public static final String WITHDRAW_CONFIRM_TEXT = "회원 탈퇴";

    @Transactional
    public void withdraw(WithdrawRequest request) {
        Long userId = AuthUtils.currentUserId();

        WithdrawTarget target = withdrawMapper.findWithdrawTarget(userId);
        if (target == null) {
            throw new AuthException("탈퇴 처리할 수 없는 계정입니다.", HttpStatus.NOT_FOUND);
        }

        // 관리 권한이 있는 계정은 바로 탈퇴하지 못한다 — 강제 탈퇴와 같은 규칙 (PM 2026-10-18).
        // 그대로 탈퇴하면 탈퇴 행에 admin_level 이 남아 관리자 수 집계 등에 섞인다. 다른 관리자가 권한을 0으로 내린 뒤 진행
        if (target.getAdminLevel() != null && target.getAdminLevel() >= 1) {
            throw new AuthException("관리 권한이 있는 계정은 바로 탈퇴할 수 없어요. 다른 관리자에게 관리 권한 해제를 요청한 뒤 진행해 주세요.", HttpStatus.BAD_REQUEST);
        }

        // 본인 확인 — 토큰 탈취만으로는 탈퇴할 수 없게 현재 비밀번호를 재확인한다.
        // 비밀번호 없는 소셜 전용 계정은 확인 문구("회원 탈퇴")를 그대로 입력받는다 (PM 2026-10-13)
        if (com.back.auth.core.SocialOnlyPassword.is(target.getPasswordHash())) {
            if (!WITHDRAW_CONFIRM_TEXT.equals(request.getConfirmText() == null ? null : request.getConfirmText().trim())) {
                throw new AuthException("'" + WITHDRAW_CONFIRM_TEXT + "'를 정확히 입력해 주세요.", HttpStatus.BAD_REQUEST);
            }
        } else if (request.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), target.getPasswordHash())) {
            throw new AuthException("비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        anonymizeAndCleanup(userId, target);
        log.info("회원 탈퇴 처리 완료 - userId: {}", userId);
    }

    /**
     * 관리자 강제 탈퇴 (회원 목록 화면).
     * 가입 승인제 대신 "가입은 열어두고, 부원이 아닌 계정은 운영진이 정리"하는 운영 방식 (PM 확정 2026-08-16).
     * 처리 내용은 본인 탈퇴와 동일 — 재가입 쿨다운·제재 대조도 똑같이 적용된다.
     */
    @Transactional
    public void forceWithdraw(Long targetUserId, String reason) {
        // 관리 조치 중 **유일하게 되돌릴 수 없는** 처리(개인정보 즉시 파기)라 최고 레벨만 허용한다.
        // 블라인드/삭제/제재는 전부 소프트삭제 + 로그라 복구 가능하므로 Lv1 부터 가능.
        AuthUtils.requireAdminLevel(3);

        WithdrawTarget target = withdrawMapper.findWithdrawTarget(targetUserId);
        if (target == null) {
            throw new AuthException("존재하지 않거나 이미 탈퇴한 회원입니다.", HttpStatus.NOT_FOUND);
        }
        // 관리자 계정은 강제 탈퇴 불가 — 운영 사고(관리자끼리 삭제) 방지. 권한을 0으로 내린 뒤 진행해야 한다
        if (target.getAdminLevel() != null && target.getAdminLevel() >= 1) {
            throw new AuthException("관리자 계정은 강제 탈퇴할 수 없습니다. 관리 권한을 해제한 뒤 진행해주세요.", HttpStatus.BAD_REQUEST);
        }

        // 사유 필수 — 되돌릴 수 없는 유일한 조치라 이유를 남긴다 (PM 2026-10-17)
        String trimmedReason = reason == null ? "" : reason.trim();
        if (trimmedReason.isEmpty()) {
            throw new AuthException("강제 탈퇴 사유를 입력해주세요.", HttpStatus.BAD_REQUEST);
        }
        if (trimmedReason.length() > 300) {
            throw new AuthException("사유는 300자까지 쓸 수 있어요.", HttpStatus.BAD_REQUEST);
        }

        Long adminId = AuthUtils.currentUserId();
        // 이력은 파기 전에 가린 값으로 남긴다 — 이름·아이디 원문은 개인정보 즉시 파기 원칙에 따라 남기지 않는다
        withdrawMapper.insertForceWithdrawLog(targetUserId, maskName(target.getName()), maskLoginId(target.getLoginId()),
                target.getMemberType(), trimmedReason, adminId);
        anonymizeAndCleanup(targetUserId, target);
        log.info("관리자 강제 탈퇴 처리 - targetUserId: {}, by: {}", targetUserId, adminId);
    }

    // 본인 탈퇴/강제 탈퇴 공통 처리 — 개인정보 파기 + 부수 데이터 정리
    private void anonymizeAndCleanup(Long userId, WithdrawTarget target) {
        // 개인정보 파기 + 소프트삭제 (학번은 재가입 대조용 해시로 치환)
        withdrawMapper.anonymizeUser(userId, hashStudentNo(target.getStudentNo()));

        // 알림 수신 기기(웹 푸시 수신 주소도 개인정보)와 본인 알림함 정리
        notificationDeviceMapper.deleteByUserId(userId);
        notificationMapper.softDeleteAllByUser(userId);

        // 간편로그인 연결 — 소셜 계정 ID 도 개인정보이고, 남겨 두면 그 소셜 계정을 다시는 못 쓴다
        socialAccountMapper.deleteByUserId(userId);
        // 캘린더 연동 — 제공자 토큰 폐기까지 (폐기 실패는 각 unlink 가 삼킨다).
        // 연동이 있을 때만 부른다: unlink 는 연동이 없으면 404 를 던지고, @Transactional 메서드가 던진 예외는 여기서 잡아도
        // 이 탈퇴 트랜잭션을 rollback-only 로 만들어 커밋 때 UnexpectedRollbackException(500) — 탈퇴 전체가 되돌아간다.
        // 캘린더 3종을 다 연동한 회원이 아니면 본인 탈퇴·강제 탈퇴가 전부 "서버 오류"로 실패하던 원인 (2026-10-17)
        if (googleCalendarMapper.findByUserId(userId) != null) googleCalendarService.unlink(userId, false);
        if (kakaoCalendarMapper.findByUserId(userId) != null) kakaoCalendarService.unlink(userId, false);
        if (naverCalendarMapper.findByUserId(userId) != null) naverCalendarService.unlink(userId);

        // 임시저장(초안)과 초안에 묶인 선업로드 파일 정리 — 초안은 본인만 보던 미발행 개인 작업물이라
        // 개인정보 즉시 파기 원칙의 대상이고, 여기서 지우지 않으면 어떤 배치도 못 지운다:
        // 탈퇴자는 재로그인 불가(초안 삭제 API 재진입 불가), 04:50 정리 배치는 초안 귀속 파일을 명시 제외,
        // 04:30 탈퇴 행 배치가 users 를 지울 때는 FK CASCADE 로 행만 사라져 디스크 파일이 영구 고아가 된다.
        // 초안 삭제 API 와 같은 순서: 첨부 행+파일(커밋 후) 명시 삭제 → drafts DELETE
        for (Long draftId : draftMapper.findDraftIdsByUser(userId)) {
            attachmentService.deleteDraftAttachments(draftId);
            draftMapper.deleteDraft(draftId, userId);
        }

        // 잔여 인증 상태 정리 (인증번호·인증 통과 플래그) — 실패해도 탈퇴는 성공
        try {
            redisTemplate.delete("auth:code:" + target.getEmail());
            redisTemplate.delete("auth:findid:verified:" + target.getEmail());
            redisTemplate.delete("auth:mail:verified:" + target.getEmail());
        } catch (Exception e) {
            log.warn("탈퇴 시 Redis 정리 실패 - userId: {}, {}", userId, e.getMessage());
        }
    }

    /** 홍길동 → 홍*동, 남궁민수 → 남**수, 이현 → 이*. 누구인지 짐작만 되게 앞·끝 한 글자만 남긴다 */
    static String maskName(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        if (n.length() == 1) return "*";
        if (n.length() == 2) return n.charAt(0) + "*";
        return n.charAt(0) + "*".repeat(n.length() - 2) + n.charAt(n.length() - 1);
    }

    /** wm5256 → wm***6 (간편로그인 대기 안내와 같은 규칙) */
    static String maskLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) return null;
        if (loginId.length() <= 3) return loginId.charAt(0) + "***";
        return loginId.substring(0, 2) + "***" + loginId.charAt(loginId.length() - 1);
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
