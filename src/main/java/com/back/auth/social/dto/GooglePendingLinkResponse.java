package com.back.auth.social.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 구글 로그인 뒤 "연결된 회원 없음" 으로 보관 중인 구글 계정 안내 (로그인·회원가입 화면).
 *
 * emailMatched 가 true 면 같은 이메일로 가입된 회원이 있다 → "이미 가입된 계정(maskedLoginId)이에요 — 연결할까요?",
 * false 면 → "회원가입으로 진행할까요?". googleEmail 은 회원가입 폼의 이메일 칸을 채우는 데 쓴다
 * (동의를 마친 그 브라우저에만 내려가므로 마스킹하지 않는다).
 */
@Getter
@AllArgsConstructor
public class GooglePendingLinkResponse {
    /** 보관 중인 계정의 제공자 — GOOGLE | NAVER | KAKAO (2026-10-11). 대기가 없으면 null */
    private String provider;
    private boolean pending;
    private String googleEmail;
    private String maskedEmail;
    private boolean emailMatched;
    private String maskedLoginId;

    public static GooglePendingLinkResponse none() {
        return new GooglePendingLinkResponse(null, false, null, null, false, null);
    }
}
