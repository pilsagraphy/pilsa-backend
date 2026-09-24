package com.back.admin.event.mapper;

import com.back.event.dto.EventImageRow;
import com.back.event.dto.EventRequest;
import com.back.event.dto.EventUpdateRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 일정 관리(관리자) 매퍼 — 등록/수정/삭제.
 * 회원 달력 조회·캘린더 피드 쿼리는 event 도메인의 EventMapper 가 담당한다 (쿼리 중복 없음).
 */
@Mapper
public interface AdminEventMapper {

    // 1. 일정 등록
    void insertEvent(@Param("request") EventRequest request, @Param("userId") Long userId);

    // 2. 일정 수정 (전달된 필드만)
    int updateEvent(@Param("eventId") Long eventId, @Param("request") EventUpdateRequest request);

    // 3. 일정 삭제 (소프트)
    int deleteEvent(@Param("eventId") Long eventId);

    // 4. 카테고리 정본 이름 조회 (등록/수정 시 event_categories 에 있는 값만 허용 + 표기 정규화).
    //    존재 확인이 아니라 이름을 되돌려주는 이유는 콜레이션이 대소문자를 무시하기 때문 ("mt" → "MT")
    String findActiveCategoryName(@Param("name") String name);

    // 5. 일정 존재 확인 (수정 시 404 를 카테고리 400 보다 먼저 판정하기 위함)
    boolean existsEvent(@Param("eventId") Long eventId);

    // 6. 일정 이미지 등록 — image_id 를 row 에 채워 돌려준다
    void insertImage(@Param("row") EventImageRow row, @Param("sortOrder") int sortOrder);

    // 7. 일정의 살아 있는 이미지 수 (장수 제한용)
    int countImages(@Param("eventId") Long eventId);

    // 8. 이미지 한 장 — 삭제 전 확인 (이 일정 소속인지 함께)
    EventImageRow findImage(@Param("eventId") Long eventId, @Param("imageId") Long imageId);

    // 9. 이미지 소프트 삭제
    int softDeleteImage(@Param("imageId") Long imageId);
}
