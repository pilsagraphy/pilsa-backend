package com.back.auth.local.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 같은 학번(해시)으로 탈퇴한 계정의 제재 상태 — 재가입 허용 여부 판정용
@Getter
@Setter
public class WithdrawnBanInfo {
    private String banStatus;          // none / temporary / permanent
    private LocalDateTime bannedUntil; // temporary 의 만료 시각
    private LocalDateTime withdrawnAt; // 탈퇴 처리 시각 — 재가입 쿨다운 판정용
}
