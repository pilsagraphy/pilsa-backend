package com.back.student.notice.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class NoticeTop5Response {
    private Long postId;
    private String title;
    private LocalDateTime created;
} // 일단 구현해놓고 후순위때 수정
// 뭐 보이게할지 모르겠음 일단 글 번호, 제목, 생성일
