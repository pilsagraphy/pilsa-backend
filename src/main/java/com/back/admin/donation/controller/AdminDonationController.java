package com.back.admin.donation.controller;

import com.back.admin.donation.dto.DonationAdminDto;
import com.back.admin.donation.mapper.AdminDonationMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 운영 관리 > 명예의 전당 관리 (PM 2026-10-11). 관리자(Lv1+). 후원 행 등록·수정·사진·삭제(소프트).
 * 사진은 /uploads/Honor/ 에 두어 공개 정적 서빙(WebConfig)으로 바로 보인다. 로직이 짧아 서비스 계층 없이 매퍼를 바로 쓴다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/donations")
@Tag(name = "관리자-운영 관리-명예의 전당 관리", description = "후원 행 CRUD + 사진. 공개 조회는 GET /api/donations")
public class AdminDonationController {

    private static final int NAME_MAX = 50;
    private static final int MESSAGE_MAX = 255;

    private final AdminDonationMapper mapper;
    private final FileStorageUtil fileStorageUtil;

    @Operation(summary = "후원 목록 (관리)", description = "금액 많은 순. `[{ donationId, userId, userName, userLoginId, displayName, amount, affiliation, major, message, donatedAt, isAnonymous, photoUrl }]`")
    @GetMapping
    public ResponseEntity<List<DonationAdminDto>> list() {
        AuthUtils.requireAdmin();
        return ResponseEntity.ok(mapper.findAll());
    }

    @Operation(summary = "후원 등록", description = "본문 `{ userId(회원, 필수), displayName, amount, affiliation?, major?, message?, donatedAt?('2026-10-11T00:00:00', 비우면 지금), isAnonymous? }`. 201")
    @PostMapping
    @Transactional
    public ResponseEntity<DonationAdminDto> create(@RequestBody DonationAdminDto body) {
        AuthUtils.requireAdmin();
        validate(body, true);
        mapper.insert(body, AuthUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.findById(body.getDonationId()));
    }

    @Operation(summary = "후원 수정", description = "본문은 등록과 같다. 없으면 404")
    @PutMapping("/{donationId}")
    @Transactional
    public ResponseEntity<DonationAdminDto> update(@PathVariable Long donationId, @RequestBody DonationAdminDto body) {
        AuthUtils.requireAdmin();
        validate(body, false);
        if (mapper.update(donationId, body) == 0) throw new BaseException("후원 행이 없어요.", HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(mapper.findById(donationId));
    }

    @Operation(summary = "후원자 사진 올리기", description = "multipart `file`(이미지). /uploads/Honor/ 에 저장돼 바로 공개된다. 바뀐 행을 돌려준다")
    @PostMapping("/{donationId}/photo")
    @Transactional
    public ResponseEntity<DonationAdminDto> photo(@PathVariable Long donationId, @RequestPart("file") MultipartFile file) {
        AuthUtils.requireAdmin();
        DonationAdminDto row = mapper.findById(donationId);
        if (row == null || !"normal".equals(row.getState())) throw new BaseException("후원 행이 없어요.", HttpStatus.NOT_FOUND);
        String type = file == null ? null : file.getContentType();
        if (file == null || file.isEmpty() || type == null || !type.startsWith("image/") || type.contains("svg")) {
            throw new BaseException("이미지 파일만 올릴 수 있어요.", HttpStatus.BAD_REQUEST);
        }
        String url = fileStorageUtil.save(file, "uploads/Honor");
        mapper.updatePhoto(donationId, url);
        if (row.getPhotoUrl() != null && row.getPhotoUrl().startsWith("/uploads/") && !row.getPhotoUrl().equals(url)) {
            fileStorageUtil.deleteAfterCommit(List.of(row.getPhotoUrl()));
        }
        return ResponseEntity.ok(mapper.findById(donationId));
    }

    @Operation(summary = "후원 삭제 (소프트)", description = "명예의 전당에서 빠진다")
    @DeleteMapping("/{donationId}")
    @Transactional
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long donationId) {
        AuthUtils.requireAdmin();
        if (mapper.softDelete(donationId) == 0) throw new BaseException("후원 행이 없어요.", HttpStatus.NOT_FOUND);
        return ResponseEntity.ok(Map.of("message", "삭제했습니다."));
    }

    /** creating 이면 탈퇴하지 않은 회원만 받는다 (수정은 후원자가 나중에 탈퇴했어도 행을 고칠 수 있게) */
    private void validate(DonationAdminDto d, boolean creating) {
        if (d.getUserId() == null || !mapper.existsUser(d.getUserId(), creating)) throw new BaseException("후원한 회원을 골라 주세요.", HttpStatus.BAD_REQUEST);
        String name = d.getDisplayName() == null ? "" : d.getDisplayName().strip();
        if (name.isEmpty()) throw new BaseException("표시 이름을 적어 주세요.", HttpStatus.BAD_REQUEST);
        if (name.length() > NAME_MAX) throw new BaseException("표시 이름은 " + NAME_MAX + "자까지예요.", HttpStatus.BAD_REQUEST);
        d.setDisplayName(name);
        if (d.getAmount() == null || d.getAmount() <= 0) throw new BaseException("금액을 확인해 주세요.", HttpStatus.BAD_REQUEST);
        d.setAffiliation(trimOrNull(d.getAffiliation(), NAME_MAX, "소속"));
        d.setMajor(trimOrNull(d.getMajor(), NAME_MAX, "학과"));
        d.setMessage(trimOrNull(d.getMessage(), MESSAGE_MAX, "메시지"));
        if (d.getDonatedAt() == null) d.setDonatedAt(LocalDateTime.now());
        if (d.getIsAnonymous() == null) d.setIsAnonymous(false);
    }

    private static String trimOrNull(String v, int max, String what) {
        String s = v == null ? "" : v.strip();
        if (s.isEmpty()) return null;
        if (s.length() > max) throw new BaseException(what + "은(는) " + max + "자까지예요.", HttpStatus.BAD_REQUEST);
        return s;
    }
}
