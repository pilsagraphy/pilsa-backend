// 전체 페이지 갯수
// NoticeListResponse 안에 totalPages를 넣으면, 글이 10개 보일 때 똑같은 '전체 페이지 수' 정보가 10번이나 중복해서 들어감 이걸 막기 위함

package com.back.student.notice.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

// 공지사항 전체 조회
@Getter
@Setter
public class NoticePageResponse {
    private int totalPages;                // 전체 페이지 갯수
    private List<NoticeListResponse> notices; // 해당 페이지 글들 정보
}