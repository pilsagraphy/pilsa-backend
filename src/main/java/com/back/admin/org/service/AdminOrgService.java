package com.back.admin.org.service;

import com.back.admin.org.dto.PresidentRequest;
import com.back.admin.org.mapper.AdminOrgMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import com.back.org.dto.OrgMemberRow;
import com.back.org.dto.OrgPresidentDto;
import com.back.org.dto.OrgPresidentRow;
import com.back.org.dto.OrgTermDto;
import com.back.org.mapper.OrgMapper;
import com.back.org.service.OrgService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 조직도 편집 — 운영 관리 > 조직도 편집 화면 (PM 2026-10-10). 관리자(Lv1+) 누구나.
 * 기수(회장)는 행 단위로 고치고, 한 학기의 명단은 통째로 바꿔 끼운다(기존 행 소프트삭제 → 새 행 삽입) —
 * 직책·팀·순서를 행마다 맞춰 고치는 것보다 화면이 보낸 모양을 그대로 저장하는 쪽이 단순하고 어긋날 데가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminOrgService {

    private static final int NAME_MAX = 50;
    private static final int TERM_MAX = 20;
    private static final int ROLE_MAX = 20;
    private static final int TITLE_MAX = 50;

    private final AdminOrgMapper adminOrgMapper;
    private final OrgMapper orgMapper;
    private final FileStorageUtil fileStorageUtil;

    public OrgPresidentDto createPresident(PresidentRequest request) {
        AuthUtils.requireAdmin();
        validatePresident(request);
        OrgPresidentRow dup = adminOrgMapper.findPresidentBySeqNo(request.getSeqNo());
        if (dup != null) {
            throw new BaseException(request.getSeqNo() + "대는 이미 있습니다.", HttpStatus.CONFLICT);
        }
        OrgPresidentRow holder = new OrgPresidentRow();
        adminOrgMapper.insertPresident(request, holder, AuthUtils.currentUserId());
        return OrgService.toPresidentDto(orgMapper.findPresidentById(holder.getPresidentId()));
    }

    public OrgPresidentDto updatePresident(Long presidentId, PresidentRequest request) {
        AuthUtils.requireAdmin();
        requirePresident(presidentId);
        validatePresident(request);
        OrgPresidentRow dup = adminOrgMapper.findPresidentBySeqNo(request.getSeqNo());
        if (dup != null && !dup.getPresidentId().equals(presidentId)) {
            throw new BaseException(request.getSeqNo() + "대는 이미 있습니다.", HttpStatus.CONFLICT);
        }
        adminOrgMapper.updatePresident(presidentId, request);
        return OrgService.toPresidentDto(orgMapper.findPresidentById(presidentId));
    }

    /** 기수 삭제 — 그 기수의 모든 학기 명단도 함께 (소프트). 사진 파일은 증적처럼 남겨 둔다 */
    public void deletePresident(Long presidentId) {
        AuthUtils.requireAdmin();
        requirePresident(presidentId);
        adminOrgMapper.softDeleteMembersOfPresident(presidentId);
        adminOrgMapper.softDeletePresident(presidentId);
    }

    /** 사진 교체 — 이미지만. 예전 서버 사진은 커밋 뒤 삭제(프론트 정적 사진 /images/.. 는 파일이 아니라 건드릴 게 없다) */
    public OrgPresidentDto uploadPhoto(Long presidentId, MultipartFile file) {
        AuthUtils.requireAdmin();
        OrgPresidentRow row = requirePresident(presidentId);
        String type = file == null ? null : file.getContentType();
        if (file == null || file.isEmpty() || type == null || !type.startsWith("image/") || type.contains("svg")) {
            throw new BaseException("이미지 파일만 올릴 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        String url = fileStorageUtil.save(file, "uploads/org/" + presidentId);
        adminOrgMapper.updatePhoto(presidentId, url, type);
        if (row.getPhotoUrl() != null && row.getPhotoUrl().startsWith("/uploads/") && !row.getPhotoUrl().equals(url)) {
            fileStorageUtil.deleteAfterCommit(List.of(row.getPhotoUrl()));
        }
        return OrgService.toPresidentDto(orgMapper.findPresidentById(presidentId));
    }

    /** 한 학기 명단 저장 — 없던 학기면 새로 생기고, 있던 학기면 통째로 바뀐다 */
    public OrgTermDto saveTerm(Long presidentId, String term, OrgTermDto request) {
        AuthUtils.requireAdmin();
        requirePresident(presidentId);
        String label = requireTermLabel(term);
        List<OrgMemberRow> rows = flatten(presidentId, label, request);
        if (rows.isEmpty()) {
            throw new BaseException("이름을 한 명 이상 넣어 주세요.", HttpStatus.BAD_REQUEST);
        }
        adminOrgMapper.softDeleteMembersOfTerm(presidentId, label);
        adminOrgMapper.insertMembers(rows, AuthUtils.currentUserId());
        return findTerm(presidentId, label);
    }

    public void deleteTerm(Long presidentId, String term) {
        AuthUtils.requireAdmin();
        requirePresident(presidentId);
        String label = requireTermLabel(term);
        if (adminOrgMapper.softDeleteMembersOfTerm(presidentId, label) == 0) {
            throw new BaseException("해당 학기가 없습니다.", HttpStatus.NOT_FOUND);
        }
    }

    public OrgTermDto renameTerm(Long presidentId, String term, String newTerm) {
        AuthUtils.requireAdmin();
        requirePresident(presidentId);
        String from = requireTermLabel(term);
        String to = requireTermLabel(newTerm);
        if (from.equals(to)) return findTerm(presidentId, from);
        if (adminOrgMapper.countMembersOfTerm(presidentId, to) > 0) {
            throw new BaseException("'" + to + "' 학기가 이미 있습니다.", HttpStatus.CONFLICT);
        }
        if (adminOrgMapper.renameTerm(presidentId, from, to) == 0) {
            throw new BaseException("해당 학기가 없습니다.", HttpStatus.NOT_FOUND);
        }
        return findTerm(presidentId, to);
    }

    // ---- 내부

    private OrgPresidentRow requirePresident(Long presidentId) {
        OrgPresidentRow row = orgMapper.findPresidentById(presidentId);
        if (row == null) {
            throw new BaseException("해당 기수가 없습니다.", HttpStatus.NOT_FOUND);
        }
        return row;
    }

    private void validatePresident(PresidentRequest r) {
        if (r.getSeqNo() == null || r.getSeqNo() < 1 || r.getSeqNo() > 999) {
            throw new BaseException("기수는 1 이상의 숫자여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        r.setName(clean(r.getName(), NAME_MAX, "이름"));
        if (r.getStartYear() == null || r.getStartYear() < 2000 || r.getStartYear() > 2100) {
            throw new BaseException("시작 연도를 확인해 주세요.", HttpStatus.BAD_REQUEST);
        }
        if (r.getEndYear() != null && (r.getEndYear() < r.getStartYear() || r.getEndYear() > 2100)) {
            throw new BaseException("종료 연도는 시작 연도보다 빠를 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private String requireTermLabel(String term) {
        return clean(term, TERM_MAX, "학기");
    }

    private static String clean(String value, int max, String what) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new BaseException(what + "을(를) 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        if (v.length() > max) {
            throw new BaseException(what + "은(는) " + max + "자까지입니다.", HttpStatus.BAD_REQUEST);
        }
        return v;
    }

    /** 화면 모양(roles / teams / advisors) → org_members 행. 빈 이름은 건너뛰고 순서는 보이는 차례 그대로 */
    private static List<OrgMemberRow> flatten(Long presidentId, String term, OrgTermDto req) {
        List<OrgMemberRow> rows = new ArrayList<>();
        int order = 0;
        if (req == null) return rows;
        for (OrgTermDto.RoleLine line : nz(req.getRoles())) {
            String role = clean(line.getRole(), ROLE_MAX, "직책");
            for (String name : names(line.getNames())) {
                rows.add(row(presidentId, term, OrgService.GROUP_CHAIR, null, role, name, order++));
            }
        }
        for (OrgTermDto.Team team : nz(req.getTeams())) {
            String title = clean(team.getTitle(), TITLE_MAX, "팀 이름");
            if (team.getLeader() != null && !team.getLeader().isBlank()) {
                rows.add(row(presidentId, term, OrgService.GROUP_TEAM, title, OrgService.ROLE_TEAM_LEADER,
                        clean(team.getLeader(), NAME_MAX, "팀장 이름"), order++));
            }
            for (String name : names(team.getMembers())) {
                rows.add(row(presidentId, term, OrgService.GROUP_TEAM, title, OrgService.ROLE_TEAM_MEMBER, name, order++));
            }
        }
        for (String name : names(req.getAdvisors())) {
            rows.add(row(presidentId, term, OrgService.GROUP_ADVISOR, null, null, name, order++));
        }
        return rows;
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static List<String> names(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String s : nz(raw)) {
            if (s == null || s.isBlank()) continue;
            out.add(clean(s, NAME_MAX, "이름"));
        }
        return out;
    }

    private static OrgMemberRow row(Long presidentId, String term, String group, String title, String role, String name, int order) {
        OrgMemberRow r = new OrgMemberRow();
        r.setPresidentId(presidentId);
        r.setTermLabel(term);
        r.setGroupType(group);
        r.setGroupTitle(title);
        r.setRole(role);
        r.setName(name);
        r.setSortOrder(order);
        return r;
    }

    private OrgTermDto findTerm(Long presidentId, String term) {
        List<OrgMemberRow> rows = new ArrayList<>();
        for (OrgMemberRow r : orgMapper.findMembers()) {
            if (r.getPresidentId().equals(presidentId) && r.getTermLabel().equals(term)) rows.add(r);
        }
        return OrgService.buildTermPublic(term, rows);
    }
}
