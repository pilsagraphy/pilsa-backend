package com.back.org.dto;

import lombok.Data;

/** org_presidents 한 행 (역대 회장 = 기수). photoUrl 은 서버 uploads 경로(/uploads/org/..) 또는 프론트 정적 경로(/images/..) */
@Data
public class OrgPresidentRow {
    private Long presidentId;
    private Integer seqNo;
    private String name;
    private Integer startYear;
    private Integer endYear;
    private String photoUrl;
    private String photoType;
}
