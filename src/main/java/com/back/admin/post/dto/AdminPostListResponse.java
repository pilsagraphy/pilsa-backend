package com.back.admin.post.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 게시글 관리 목록 한 행
@Getter
@Setter
public class AdminPostListResponse {
    private Long postId;
    private Long boardId;
    private String boardName;   // boards.name (한글 게시판명)
    private String title;
    private String authorName;   // 관리자 화면에는 익명글도 실제 작성자명 표시
    private String authorLoginId;   // 조치 확인 모달 '대상 회원' 표기용 (users.login_id). 탈퇴 회원은 null
    private String authorStudentNo; // 조치 확인 모달 '대상 회원' 표기용 (users.student_no). 탈퇴 회원은 null
    private int commentCount;
    private int likeCount;
    private int viewCount;
    private LocalDateTime created;
    private String state;        // normal / blind (deleted 는 목록에서 제외)
}
