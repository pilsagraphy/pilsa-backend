package com.back.org.mapper;

import com.back.org.dto.OrgMemberRow;
import com.back.org.dto.OrgPresidentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 조직(역대 회장 · 학기별 임원진) 조회 — 공개 화면과 관리자 편집 화면이 같이 쓴다 */
@Mapper
public interface OrgMapper {

    /** 기수 순 (state=normal) */
    List<OrgPresidentRow> findPresidents();

    OrgPresidentRow findPresidentById(@Param("presidentId") Long presidentId);

    /** 전 기수의 명단 행 (state=normal). president_id → term_label → sort_order 순 */
    List<OrgMemberRow> findMembers();
}
