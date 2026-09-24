package com.back.board.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 카테고리 목록 한 행.
 *
 * 목록은 요청 파라미터 없이 내려가며, 백엔드가 토큰의 사용자로 관리자 여부를 판정해
 * 관리자에게만 '중요'(code=PINNED) 카테고리를 포함시킨다. 프론트는 받은 목록을 그대로 그리면 된다.
 */
@Getter
@Setter
public class CategoryResponse {
    private Long categoryId;
    private String name;   // 화면 표시 이름 (자랑/정보/질문/일상/모임/중요)
    private String code;   // 고유 코드. PINNED 이면 선택 시 상단 고정(is_pinned) 처리되는 카테고리
}
