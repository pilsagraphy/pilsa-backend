package com.back.auth.dto;

import lombok.Getter;
import lombok.Setter;

// 탈퇴 처리 대상 조회 결과 (내부용) — 비밀번호 검증·학번 해시·Redis 정리에 필요한 최소 정보
@Getter
@Setter
public class WithdrawTarget {
    private String loginId;
    private String passwordHash;
    private String email;
    private String studentNo;
    private Integer adminLevel;
}
