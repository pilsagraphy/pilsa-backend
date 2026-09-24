package com.back.admin.board.dto;

import lombok.Getter;
import lombok.Setter;

/** 카테고리 등록·수정 요청. 수정은 보낸 값만 바꾼다. */
@Getter
@Setter
public class CategorySaveRequest {

    /** 화면에 그대로 나가는 한글 이름. 게시판 안에서 겹칠 수 없다. */
    private String name;

    /** 목록에서 몇 번째에 둘지(1부터). 생략하면 맨 뒤. */
    private Integer displayOrder;
}
