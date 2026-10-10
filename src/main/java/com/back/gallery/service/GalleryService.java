package com.back.gallery.service;

import com.back.gallery.dto.GalleryPhotoDto;
import com.back.gallery.dto.GalleryResponse;
import com.back.gallery.mapper.GalleryMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 활동 사진 (PM 2026-10-10 밤) — 예전엔 프론트 상수(constants/gallery.js)였다. 이제 로그인 회원 누구나 올리고,
 * 본인은 지우고(deleted) 관리자는 숨긴다(hidden, 복원 가능). 학기는 올린 시점에 서버가 매긴다(방명록과 같은 기준).
 * 시드로 넣은 예전 사진은 프론트 정적 파일(/images/gallery/..)을 그대로 가리키고, 새 사진은 uploads/gallery/{yyyyMM}/ 에 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GalleryService {

    public static final String POLICY_MAX_FILES = "gallery_max_files_per_upload";
    private static final int DEFAULT_MAX_FILES = 10;
    private static final int DEFAULT_SEMESTER1_START_MONTH = 3;
    private static final int DEFAULT_SEMESTER2_START_MONTH = 9;
    private static final int TITLE_MAX = 100;
    private static final int HASHTAGS_MAX = 255;
    private static final int UPLOAD_SORT_ORDER = 9999; // 시드(순서 있음) 뒤에, 그 안에서는 최신 먼저

    private final GalleryMapper mapper;
    private final FileStorageUtil fileStorageUtil;

    public GalleryResponse getGallery(String semester) {
        boolean admin = AuthUtils.isAdmin();
        String current = currentSemesterLabel();
        List<String> semesters = new ArrayList<>(mapper.findSemesters(admin));
        if (!semesters.contains(current)) semesters.add(0, current); // 아직 사진이 없어도 이번 학기 구간은 보인다
        String picked = semester == null || semester.isBlank() || !semesters.contains(semester) ? current : semester;
        List<GalleryPhotoDto> photos = mapper.findPhotos(picked, admin);
        decorate(photos);
        return new GalleryResponse(current, picked, semesters, maxFiles(), photos);
    }

    /** 회원 업로드 — 여러 장. ratios 는 파일과 같은 순서의 가로÷세로(프론트가 잰 값, 없으면 1) */
    @Transactional
    public List<GalleryPhotoDto> upload(List<MultipartFile> files, List<Double> ratios, String title, String hashtags) {
        Long me = AuthUtils.currentUserId();
        if (files == null || files.isEmpty()) throw new BaseException("사진을 골라 주세요.", HttpStatus.BAD_REQUEST);
        int max = maxFiles();
        if (files.size() > max) throw new BaseException("한 번에 " + max + "장까지 올릴 수 있어요.", HttpStatus.BAD_REQUEST);
        String t = title == null ? null : title.strip();
        if (t != null && t.length() > TITLE_MAX) throw new BaseException("제목은 " + TITLE_MAX + "자까지예요.", HttpStatus.BAD_REQUEST);
        String tags = normalizeHashtags(hashtags);

        String semester = currentSemesterLabel();
        String dir = "uploads/gallery/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<GalleryPhotoDto> saved = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String type = file.getContentType();
            if (file.isEmpty() || type == null || !type.startsWith("image/") || type.contains("svg")) {
                throw new BaseException("이미지 파일만 올릴 수 있어요.", HttpStatus.BAD_REQUEST);
            }
            GalleryPhotoDto p = new GalleryPhotoDto();
            p.setSemesterLabel(semester);
            p.setUserId(me);
            p.setFileUrl(fileStorageUtil.save(file, dir));
            p.setFileType(type);
            Double r = ratios != null && i < ratios.size() ? ratios.get(i) : null;
            p.setRatio(r == null || r.isNaN() || r < 0.2 || r > 5 ? 1.0 : Math.round(r * 1000) / 1000.0);
            p.setTitle(t == null || t.isEmpty() ? null : t);
            p.setHashtagsRaw(tags);
            p.setSortOrder(UPLOAD_SORT_ORDER);
            mapper.insert(p);
            saved.add(mapper.findById(p.getPhotoId()));
        }
        decorate(saved);
        return saved;
    }

    /** 본인 사진은 deleted, 관리자가 남의 사진을 지우면 hidden(복원 가능) */
    @Transactional
    public void delete(Long photoId) {
        Long me = AuthUtils.currentUserId();
        if (mapper.deleteOwn(photoId, me) > 0) return;
        if (AuthUtils.isAdmin() && mapper.updateState(photoId, "normal", "hidden") > 0) return;
        throw new BaseException("내가 올린 사진만 지울 수 있어요.", HttpStatus.FORBIDDEN);
    }

    @Transactional
    public GalleryPhotoDto restore(Long photoId) {
        AuthUtils.requireAdmin();
        if (mapper.updateState(photoId, "hidden", "normal") == 0) throw new BaseException("복원할 사진이 없어요.", HttpStatus.NOT_FOUND);
        GalleryPhotoDto p = mapper.findById(photoId);
        decorate(List.of(p));
        return p;
    }

    public GalleryPhotoDto requirePhoto(Long photoId) {
        GalleryPhotoDto p = mapper.findById(photoId);
        if (p == null) throw new BaseException("사진이 없어요.", HttpStatus.NOT_FOUND);
        return p;
    }

    /** imageUrl · hashtags[] · isMine · canManage 채우기 (관리자 목록도 같이 쓴다). 파일 경로는 응답에서 뺀다 */
    public void decorate(List<GalleryPhotoDto> photos) {
        Long me = AuthUtils.currentUserIdOrNull();
        boolean admin = AuthUtils.isAdmin();
        for (GalleryPhotoDto p : photos) {
            String url = p.getFileUrl();
            p.setImageUrl(url != null && url.startsWith("/uploads/") ? "/api/gallery/photos/" + p.getPhotoId() + "/image" : url);
            p.setFileUrl(null);
            p.setHashtags(splitHashtags(p.getHashtagsRaw()));
            p.setHashtagsRaw(null);
            boolean mine = me != null && me.equals(p.getUserId());
            p.setIsMine(mine);
            p.setCanManage(mine || admin);
        }
    }

    public int maxFiles() {
        return intPolicy(POLICY_MAX_FILES, DEFAULT_MAX_FILES, 1, 50);
    }

    /** 오늘이 속한 학기 라벨 — 방명록·마이페이지 '이번 학기'와 같은 월 기준 */
    public String currentSemesterLabel() {
        int s1 = intPolicy("semester1_start_month", DEFAULT_SEMESTER1_START_MONTH, 1, 12);
        int s2 = intPolicy("semester2_start_month", DEFAULT_SEMESTER2_START_MONTH, 1, 12);
        LocalDate today = LocalDate.now();
        int y = today.getYear();
        int m = today.getMonthValue();
        if (m >= s1 && m < s2) return y + "-1학기";
        if (m >= s2) return y + "-2학기";
        return (y - 1) + "-2학기";
    }

    private int intPolicy(String code, int def, int min, int max) {
        try {
            int v = Integer.parseInt(mapper.findPolicySetting(code).trim());
            return v < min || v > max ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    /** '#a, b #c' → '#a,#b,#c' (쉼표 구분 저장) */
    private static String normalizeHashtags(String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<String> out = new ArrayList<>();
        for (String s : raw.split("[,\\s]+")) {
            String v = s.strip();
            if (v.isEmpty()) continue;
            if (!v.startsWith("#")) v = "#" + v;
            if (!out.contains(v)) out.add(v);
        }
        String joined = String.join(",", out);
        if (joined.length() > HASHTAGS_MAX) throw new BaseException("해시태그가 너무 길어요.", HttpStatus.BAD_REQUEST);
        return joined.isEmpty() ? null : joined;
    }

    private static List<String> splitHashtags(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(raw.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
    }
}
