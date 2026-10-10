package com.back.org.dto;

import lombok.Data;

/**
 * org_members 한 행 — 어떤 기수(president_id)의 어떤 학기(term_label)에 누가 어떤 자리였는지.
 * group_type: chair(회장단) / team(팀) / advisor(자문). team 은 group_title 에 팀 이름, role 에 팀장·팀원.
 */
@Data
public class OrgMemberRow {
    private Long memberId;
    private Long presidentId;
    private String termLabel;
    private String groupType;
    private String groupTitle;
    private String role;
    private String name;
    private Integer sortOrder;
}
