package com.back.gallery.mapper;

import com.back.gallery.dto.GalleryPhotoDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 활동 사진 (gallery_photos) — 공개 조회 · 회원 업로드/삭제 · 관리자 숨김/복원 */
@Mapper
public interface GalleryMapper {

    String findPolicySetting(@Param("code") String code);

    /** 사진이 있는 학기 라벨, 최신순 (includeHidden 이면 숨긴 사진만 있는 학기도) */
    List<String> findSemesters(@Param("includeHidden") boolean includeHidden);

    /** 한 학기의 사진(semester 가 null 이면 전체 학기, 최신 학기부터). includeHidden 이면 관리자용으로 hidden 도. keyword 는 escapeLike 를 거친 값(null 이면 검색 안 함) */
    List<GalleryPhotoDto> findPhotos(@Param("semester") String semester, @Param("includeHidden") boolean includeHidden, @Param("keyword") String keyword);

    /** 관리자 — 한 학기 안의 표시 순서. 그 학기 사진이 아니면 0 */
    int updateSortOrder(@Param("photoId") Long photoId, @Param("semester") String semester, @Param("sortOrder") int sortOrder);

    /** 관리자 — 등록 정보(학기·올린 사람). userId null = 올린 사람 없음 */
    int updateRegistration(@Param("photoId") Long photoId, @Param("semesterLabel") String semesterLabel, @Param("userId") Long userId);

    boolean existsActiveUser(@Param("userId") Long userId);

    GalleryPhotoDto findById(@Param("photoId") Long photoId);

    void insert(@Param("p") GalleryPhotoDto photo);

    int deleteOwn(@Param("photoId") Long photoId, @Param("userId") Long userId);

    int updateState(@Param("photoId") Long photoId, @Param("from") String from, @Param("to") String to);

    /** 관리자 — 제목·해시태그(쉼표 구분) 수정. 지운 사진은 제외 */
    int updateMeta(@Param("photoId") Long photoId, @Param("title") String title, @Param("hashtags") String hashtags);
}
