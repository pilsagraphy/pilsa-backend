package com.back.admin.org.controller;

import com.back.admin.org.dto.PresidentRequest;
import com.back.admin.org.dto.TermRenameRequest;
import com.back.admin.org.service.AdminOrgService;
import com.back.org.dto.OrgPresidentDto;
import com.back.org.dto.OrgTermDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 운영 관리 > 조직도 편집 (PM 2026-10-10). 조회는 공개 API GET /api/org 를 그대로 쓴다.
 * 기수(역대 회장) 행과 학기별 명단을 관리자(Lv1+)가 고친다 — 전부 소프트삭제.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/org")
@Tag(name = "관리자-운영 관리-조직도 편집", description = "역대 회장(기수)과 학기별 임원진 편집. 조회는 GET /api/org (공개)")
public class AdminOrgController {

    private final AdminOrgService service;

    @Operation(summary = "기수 등록", description = "본문 `{ seqNo, name, startYear, endYear }` — endYear 가 null 이면 '현재'. 같은 기수가 있으면 409. 만든 기수를 돌려준다")
    @PostMapping("/presidents")
    public ResponseEntity<OrgPresidentDto> createPresident(@RequestBody PresidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPresident(request));
    }

    @Operation(summary = "기수 수정", description = "본문은 등록과 같다. 없으면 404")
    @PutMapping("/presidents/{presidentId}")
    public ResponseEntity<OrgPresidentDto> updatePresident(@PathVariable Long presidentId, @RequestBody PresidentRequest request) {
        return ResponseEntity.ok(service.updatePresident(presidentId, request));
    }

    @Operation(summary = "기수 삭제 (소프트)", description = "그 기수의 학기별 명단도 함께 숨긴다")
    @DeleteMapping("/presidents/{presidentId}")
    public ResponseEntity<Map<String, String>> deletePresident(@PathVariable Long presidentId) {
        service.deletePresident(presidentId);
        return ResponseEntity.ok(Map.of("message", "기수를 삭제했습니다."));
    }

    @Operation(summary = "회장 사진 올리기", description = "multipart `file` 한 장(이미지만). 바뀐 기수를 돌려준다 — photoUrl 이 /api/org/presidents/{id}/photo 로 바뀐다")
    @PostMapping("/presidents/{presidentId}/photo")
    public ResponseEntity<OrgPresidentDto> uploadPhoto(@PathVariable Long presidentId, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.uploadPhoto(presidentId, file));
    }

    @Operation(summary = "학기 명단 저장", description = """
            경로의 term 은 '2026-2학기' 같은 라벨. 없던 학기면 새로 생기고 있던 학기면 통째로 바뀐다.
            본문은 GET /api/org 의 officers[] 한 칸과 같다:
            `{ "roles": [{ "role": "회장", "names": ["최재연"] }], "teams": [{ "title": "제작스터디", "leader": "박수민", "members": ["김아란"] }], "advisors": ["가성연"] }`
            빈 이름은 건너뛴다. 전부 비면 400. 저장된 학기를 돌려준다""")
    @PutMapping("/presidents/{presidentId}/terms/{term}")
    public ResponseEntity<OrgTermDto> saveTerm(@PathVariable Long presidentId, @PathVariable String term, @RequestBody OrgTermDto request) {
        return ResponseEntity.ok(service.saveTerm(presidentId, term, request));
    }

    @Operation(summary = "학기 라벨 변경", description = "본문 `{ \"term\": \"새 라벨\" }`. 같은 기수에 그 라벨이 이미 있으면 409")
    @PatchMapping("/presidents/{presidentId}/terms/{term}")
    public ResponseEntity<OrgTermDto> renameTerm(@PathVariable Long presidentId, @PathVariable String term, @RequestBody TermRenameRequest request) {
        return ResponseEntity.ok(service.renameTerm(presidentId, term, request.getTerm()));
    }

    @Operation(summary = "학기 삭제 (소프트)", description = "그 학기의 명단 행을 전부 숨긴다. 없으면 404")
    @DeleteMapping("/presidents/{presidentId}/terms/{term}")
    public ResponseEntity<Map<String, String>> deleteTerm(@PathVariable Long presidentId, @PathVariable String term) {
        service.deleteTerm(presidentId, term);
        return ResponseEntity.ok(Map.of("message", "학기를 삭제했습니다."));
    }
}
