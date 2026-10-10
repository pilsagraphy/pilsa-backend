package com.back.gallery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 활동 사진 화면 한 번에 — 학기 목록 · 지금 학기 · 고른 학기의 사진 · 업로드 제한 */
@Data
@AllArgsConstructor
public class GalleryResponse {
    private String currentSemester;
    private String semester;
    private List<String> semesters;
    private int maxFilesPerUpload;
    private List<GalleryPhotoDto> photos;
}
