package com.back.auth.local.dto;

import lombok.Getter;
import lombok.Setter;

// 회원 탈퇴 요청 — 본인 확인용 현재 비밀번호 (토큰 탈취만으로 탈퇴할 수 없게 하는 방어선)
@Getter
@Setter
public class WithdrawRequest {
    private String password;
}
