package com.back.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 로그인한 사용자의 표시 이름.
 *
 * 헤더의 "OOO님" 표기용이라 이름만 담는다 — 마이페이지 상세(아이디·가입일 등)와는 별개다.
 * 이름은 개인정보라 회원 본인만 조회하며, 다른 회원의 이름은 게시글·댓글 응답의 authorName 으로만 노출된다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserNameResponse {

    @Schema(description = "로그인한 사용자의 이름", example = "홍길동")
    private String name;
}
