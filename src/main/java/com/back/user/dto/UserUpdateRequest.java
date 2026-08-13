package com.back.user.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 회원 정보 수정 요청 (부분 수정)
// - 이름/전화/학번/이메일       : 더블클릭 인라인 수정
// - UserType/adminLevel      : 재학상태(신분)/관리권한 선택 수정 — PR #66 권한 개편 반영
// 모든 필드 nullable → 전달된(non-null) 필드만 검증 후 수정
@Getter
@Setter
@ToString
public class UserUpdateRequest {
    private String name;         // 이름 (문자만)
    private String phone;        // 전화번호
    private String studentNo;    // 학번 (10자리 숫자)
    private String email;        // 이메일 (이메일 형식)
    private String UserType;   // 회원 구분 (STUDENT / ALUMNI)
    private Integer adminLevel;  // 관리 권한 레벨 (0: 일반 / 1~3: 관리자)
}
