package com.back.admin.event.mapper;

import com.back.admin.event.dto.EventTemplateDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 일정 템플릿 (event_templates) — 관리자 전용, 소프트삭제 */
@Mapper
public interface AdminEventTemplateMapper {

    List<EventTemplateDto> findAll();

    EventTemplateDto findById(@Param("templateId") Long templateId);

    void insert(@Param("t") EventTemplateDto template, @Param("userId") Long userId);

    int update(@Param("templateId") Long templateId, @Param("t") EventTemplateDto template);

    int softDelete(@Param("templateId") Long templateId);
}
