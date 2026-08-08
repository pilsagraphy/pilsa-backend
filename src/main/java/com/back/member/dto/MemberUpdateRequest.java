package com.back.member.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

// 회원 정보 수정 요청 (부분 수정)
// - 이름/전화/학번/이메일 : 더블클릭 인라인 수정
// - status/role          : 재학상태/권한 선택 수정
// 모든 필드 nullable → 전달된(non-null) 필드만 검증 후 수정
@Getter
@Setter
@ToString
public class MemberUpdateRequest {
    private String name;        // 이름 (문자만)
    private String phone;       // 전화번호 (8자리, 4-4)
    private String studentNo;   // 학번 (10자리 숫자)
    private String email;       // 이메일 (이메일 형식)
    private Integer status;     // 재학상태 (0: 재학 / 1: 휴학 / 2: 졸업)
    private String role;        // 권한 (ADMIN / STUDENTS / ALUMNI)
}
