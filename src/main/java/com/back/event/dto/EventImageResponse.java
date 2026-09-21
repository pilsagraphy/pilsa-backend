package com.back.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 일정에 붙은 이미지 한 장 (회원 달력·관리자 화면 공용).
 *
 * url 은 인증 없이 열리는 공개 조회 경로다 — 일정 자체가 비로그인 공개(/api/event/**)라
 * 이미지도 같은 기준으로 연다. 게시판 첨부(/api/user/files)와 달리 img 태그에 그대로 넣을 수 있다.
 */
@Getter
@AllArgsConstructor
public class EventImageResponse {
    private Long imageId;
    private String url;       // /api/event/images/{imageId}
    private String fileName;  // 원본 파일명
}
