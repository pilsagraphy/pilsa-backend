package com.back.gallery.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 활동 사진 한 장 (gallery_photos). imageUrl 은 프론트 정적 파일(/images/gallery/..)이거나 서버 사진(/api/gallery/photos/{id}/image).
 * ratio = 가로÷세로 (갤러리가 줄을 짜는 기준). isMine: 로그인한 본인이 올린 사진, canManage: 본인 또는 관리자(지울 수 있다).
 */
@Data
public class GalleryPhotoDto {
    private Long photoId;
    private String semesterLabel;
    private Long userId;
    private String uploaderName;
    private String fileUrl;
    private String fileType;
    private String imageUrl;
    private Double ratio;
    private String title;
    private List<String> hashtags = new ArrayList<>();
    private String hashtagsRaw;
    private Integer sortOrder;
    private String state;
    private Boolean isMine;
    private Boolean canManage;
    private LocalDateTime createdAt;
}
