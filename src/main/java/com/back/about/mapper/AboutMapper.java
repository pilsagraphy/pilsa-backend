package com.back.about.mapper;

import com.back.about.dto.HistoryItemDto;
import com.back.about.dto.IntroSectionDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 동아리 소개(intro_sections) · 연혁(history_items) — 공개 조회와 관리자 편집이 같이 쓴다. 전부 소프트삭제 */
@Mapper
public interface AboutMapper {

    List<IntroSectionDto> findIntroSections();

    IntroSectionDto findIntroSection(@Param("sectionId") Long sectionId);

    void insertIntroSection(@Param("s") IntroSectionDto section, @Param("userId") Long userId);

    int updateIntroSection(@Param("sectionId") Long sectionId, @Param("s") IntroSectionDto section);

    int softDeleteIntroSection(@Param("sectionId") Long sectionId);

    /** 연도 오름차순, 그 안은 sort_order */
    List<HistoryItemDto> findHistoryItems();

    HistoryItemDto findHistoryItem(@Param("itemId") Long itemId);

    void insertHistoryItem(@Param("h") HistoryItemDto item, @Param("userId") Long userId);

    int updateHistoryItem(@Param("itemId") Long itemId, @Param("h") HistoryItemDto item);

    int softDeleteHistoryItem(@Param("itemId") Long itemId);

    Integer findMaxHistoryOrder(@Param("year") Integer year);
}
