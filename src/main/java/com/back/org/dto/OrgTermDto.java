package com.back.org.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 학기의 임원진 — 화면(역대 회장 '임원진 보기' 팝업 · 소개 페이지 조직도)이 쓰는 모양 그대로.
 * 관리자 학기 저장 요청 본문도 이 모양이다 (term 은 경로로 받으므로 본문에서는 무시).
 */
@Data
public class OrgTermDto {
    private String term;
    /** 회장단: 직책마다 한 줄 — [{ role: '회장', names: ['..'] }, { role: '부회장', names: [..] }] */
    private List<RoleLine> roles = new ArrayList<>();
    /** 팀: { title, leader(팀장, 없으면 null), members(팀원) } */
    private List<Team> teams = new ArrayList<>();
    /** 자문 */
    private List<String> advisors = new ArrayList<>();

    @Data
    public static class RoleLine {
        private String role;
        private List<String> names = new ArrayList<>();
    }

    @Data
    public static class Team {
        private String title;
        private String leader;
        private List<String> members = new ArrayList<>();
    }
}
