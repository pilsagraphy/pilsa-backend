package com.back.org.service;

import com.back.org.dto.OrgMemberRow;
import com.back.org.dto.OrgPresidentDto;
import com.back.org.dto.OrgPresidentRow;
import com.back.org.dto.OrgResponse;
import com.back.org.dto.OrgTermDto;
import com.back.org.mapper.OrgMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 조직 조회 — org_presidents · org_members 의 평평한 행을 화면이 쓰는 중첩 구조(기수 → 학기 → 회장단/팀/자문)로 조립한다.
 * 예전엔 프론트 상수(constants/leader.js · organization.js)에 박혀 있어 임원이 바뀔 때마다 배포가 필요했다 (PM 2026-10-10).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgService {

    public static final String GROUP_CHAIR = "chair";
    public static final String GROUP_TEAM = "team";
    public static final String GROUP_ADVISOR = "advisor";
    public static final String ROLE_TEAM_LEADER = "팀장";
    public static final String ROLE_TEAM_MEMBER = "팀원";

    private final OrgMapper orgMapper;

    public OrgResponse getOrganization() {
        List<OrgPresidentRow> presidents = orgMapper.findPresidents();
        List<OrgMemberRow> rows = orgMapper.findMembers();

        // president_id → (term_label → 행들). 학기는 TreeMap 으로 라벨 순
        Map<Long, TreeMap<String, List<OrgMemberRow>>> byPresident = new LinkedHashMap<>();
        for (OrgMemberRow row : rows) {
            byPresident.computeIfAbsent(row.getPresidentId(), k -> new TreeMap<>())
                    .computeIfAbsent(row.getTermLabel(), k -> new ArrayList<>())
                    .add(row);
        }

        List<OrgPresidentDto> result = new ArrayList<>();
        String currentTerm = null;
        for (OrgPresidentRow p : presidents) {
            OrgPresidentDto dto = toPresidentDto(p);
            TreeMap<String, List<OrgMemberRow>> terms = byPresident.get(p.getPresidentId());
            if (terms != null) {
                for (Map.Entry<String, List<OrgMemberRow>> e : terms.entrySet()) {
                    dto.getOfficers().add(buildTerm(e.getKey(), e.getValue()));
                }
                if (!terms.isEmpty()) {
                    currentTerm = terms.lastKey();
                }
            }
            result.add(dto);
        }
        return new OrgResponse(currentTerm, result);
    }

    public static OrgPresidentDto toPresidentDto(OrgPresidentRow p) {
        OrgPresidentDto dto = new OrgPresidentDto();
        dto.setPresidentId(p.getPresidentId());
        dto.setSeqNo(p.getSeqNo());
        dto.setOrder(p.getSeqNo() != null && p.getSeqNo() == 1 ? "초대 회장" : p.getSeqNo() + "대 회장");
        dto.setName(p.getName());
        dto.setStartYear(p.getStartYear());
        dto.setEndYear(p.getEndYear());
        dto.setPeriod("(" + p.getStartYear() + "~" + (p.getEndYear() == null ? "현재" : p.getEndYear()) + ")");
        dto.setPhotoUrl(publicPhotoUrl(p));
        return dto;
    }

    /** 서버에 올린 사진은 공개 API 경로로, 프론트 정적 파일(/images/..)은 그대로 */
    public static String publicPhotoUrl(OrgPresidentRow p) {
        String url = p.getPhotoUrl();
        if (url == null || url.isBlank()) return null;
        return url.startsWith("/uploads/") ? "/api/org/presidents/" + p.getPresidentId() + "/photo" : url;
    }

    /** 한 학기의 행들 → { roles(직책 순), teams(팀 순, 팀장/팀원), advisors } — 순서는 sort_order 가 정한다 */
    public static OrgTermDto buildTermPublic(String term, List<OrgMemberRow> rows) {
        return buildTerm(term, rows);
    }

    static OrgTermDto buildTerm(String term, List<OrgMemberRow> rows) {
        OrgTermDto dto = new OrgTermDto();
        dto.setTerm(term);
        Map<String, OrgTermDto.RoleLine> roles = new LinkedHashMap<>();
        Map<String, OrgTermDto.Team> teams = new LinkedHashMap<>();
        for (OrgMemberRow row : rows) {
            switch (row.getGroupType()) {
                case GROUP_CHAIR -> roles.computeIfAbsent(row.getRole(), r -> {
                    OrgTermDto.RoleLine line = new OrgTermDto.RoleLine();
                    line.setRole(r);
                    return line;
                }).getNames().add(row.getName());
                case GROUP_TEAM -> {
                    OrgTermDto.Team team = teams.computeIfAbsent(row.getGroupTitle(), t -> {
                        OrgTermDto.Team created = new OrgTermDto.Team();
                        created.setTitle(t);
                        return created;
                    });
                    if (ROLE_TEAM_LEADER.equals(row.getRole())) {
                        team.setLeader(row.getName());
                    } else {
                        team.getMembers().add(row.getName());
                    }
                }
                case GROUP_ADVISOR -> dto.getAdvisors().add(row.getName());
                default -> { }
            }
        }
        dto.getRoles().addAll(roles.values());
        dto.getTeams().addAll(teams.values());
        return dto;
    }
}
