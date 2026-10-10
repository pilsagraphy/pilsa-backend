package com.back.about.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 연혁 항목 한 줄 (history_items). 예전 constants/history.js 의 { text, href?, video?, link?, images? } 와 같은 모양.
 * - href: 글 자체가 바로가기(인스타 게시물 등). video: 서버 public/videos 의 mp4 경로. link: 영상 아래 바로가기 한 줄.
 * - images: [{ src, alt, wide?, zoom? }] — src 는 프론트 정적(/history/..) 또는 서버 사진(/api/history/images/..)
 */
@Data
public class HistoryItemDto {
    private Long itemId;
    private Integer year;
    private Integer sortOrder;
    private String text;
    private String href;
    private String video;
    private String linkHref;
    private String linkLabel;
    /** DB 에 저장되는 JSON 문자열. 응답에는 images 로 풀어서 나간다 */
    private String imagesJson;
    private List<Image> images = new ArrayList<>();
    private String state;

    @Data
    public static class Image {
        private String src;
        private String alt;
        private Boolean wide;
        private Double zoom;
    }
}
