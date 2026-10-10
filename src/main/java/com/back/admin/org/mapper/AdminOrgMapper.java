package com.back.admin.org.mapper;

import com.back.admin.org.dto.PresidentRequest;
import com.back.org.dto.OrgMemberRow;
import com.back.org.dto.OrgPresidentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 조직도 편집 (org_presidents · org_members) — 전부 소프트삭제 */
@Mapper
public interface AdminOrgMapper {

    OrgPresidentRow findPresidentBySeqNo(@Param("seqNo") Integer seqNo);

    /** 생성된 키는 holder.presidentId 로 돌려받는다 */
    void insertPresident(@Param("p") PresidentRequest request, @Param("holder") OrgPresidentRow holder, @Param("userId") Long userId);

    int updatePresident(@Param("presidentId") Long presidentId, @Param("p") PresidentRequest request);

    int updatePhoto(@Param("presidentId") Long presidentId, @Param("photoUrl") String photoUrl, @Param("photoType") String photoType);

    int softDeletePresident(@Param("presidentId") Long presidentId);

    /** 기수의 전 학기 명단 소프트삭제 (기수 삭제 때) */
    int softDeleteMembersOfPresident(@Param("presidentId") Long presidentId);

    int countMembersOfTerm(@Param("presidentId") Long presidentId, @Param("term") String term);

    int softDeleteMembersOfTerm(@Param("presidentId") Long presidentId, @Param("term") String term);

    void insertMembers(@Param("rows") List<OrgMemberRow> rows, @Param("userId") Long userId);

    int renameTerm(@Param("presidentId") Long presidentId, @Param("from") String from, @Param("to") String to);
}
