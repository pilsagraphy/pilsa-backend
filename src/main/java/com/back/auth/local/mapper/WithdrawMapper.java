package com.back.auth.local.mapper;

import com.back.auth.local.dto.WithdrawTarget;
import com.back.auth.local.dto.WithdrawnBanInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 회원 탈퇴 매퍼.
 *
 * 탈퇴 = is_deleted=1 + 개인정보 파기(이름/이메일/아이디/전화/비밀번호) + 학번은 해시로 치환 보관.
 * 학번 해시는 부정 이용(제재 회피 재가입) 방지 목적의 최소 보존이며, 개인정보처리방침에 명시한다.
 * ban_status/banned_until 은 재가입 대조 판정에 쓰이므로 절대 리셋하지 않는다.
 */
@Mapper
public interface WithdrawMapper {

    /** 탈퇴 대상 조회 (이미 탈퇴한 계정은 null) */
    WithdrawTarget findWithdrawTarget(@Param("userId") Long userId);

    /**
     * 개인정보 파기 + 소프트삭제.
     * 이메일/아이디는 NOT NULL+UNIQUE 라 비울 수 없어 user_id 기반 더미로 치환하고,
     * 전화는 NULL 허용이라 NULL, 학번은 재가입 대조용 해시로 치환한다.
     */
    int anonymizeUser(@Param("userId") Long userId, @Param("studentNoHash") String studentNoHash);

    /** 같은 학번(해시)으로 탈퇴한 계정들의 제재 상태·탈퇴 시각 — 재가입 허용 판정용 */
    List<WithdrawnBanInfo> findWithdrawnBanByHash(@Param("studentNoHash") String studentNoHash);

    /** policy_settings 값 조회 (rejoin_cooldown_days 등) */
    String findPolicySetting(@Param("code") String code);

    /** 보존기간이 지났고 활동·제재 이력이 전혀 없는 탈퇴 행 (물리 삭제 후보) */
    List<Long> findPurgeableWithdrawnUserIds(@Param("retentionDays") int retentionDays);

    /** 후보의 잔여 알림 행 물리 삭제 (users FK 해제용 — 알림은 증적 가치 없음) */
    int deleteNotificationsByUserIds(@Param("userIds") List<Long> userIds);

    /** 탈퇴 행 물리 삭제 — findPurgeableWithdrawnUserIds 로 걸러진 id 만 넣을 것 */
    int deleteUsersByIds(@Param("userIds") List<Long> userIds);
}
