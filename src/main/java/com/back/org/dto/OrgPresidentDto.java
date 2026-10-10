package com.back.org.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 역대 회장 카드 한 장 — 기수 정보 + 재임 중 학기별 임원진 */
@Data
public class OrgPresidentDto {
    private Long presidentId;
    private Integer seqNo;
    /** '초대 회장' / '2대 회장' — seqNo 에서 만든 표기 */
    private String order;
    private String name;
    private Integer startYear;
    /** null 이면 현재 */
    private Integer endYear;
    /** '(2021~2022)' / '(2026~현재)' */
    private String period;
    /** 서버 사진이면 /api/org/presidents/{id}/photo, 프론트 정적 사진이면 /images/.. 그대로. 없으면 null */
    private String photoUrl;
    private List<OrgTermDto> officers = new ArrayList<>();
}
