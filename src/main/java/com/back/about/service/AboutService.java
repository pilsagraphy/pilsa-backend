package com.back.about.service;

import com.back.about.dto.HistoryItemDto;
import com.back.about.dto.HistoryYearDto;
import com.back.about.dto.IntroSectionDto;
import com.back.about.mapper.AboutMapper;
import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 동아리 소개 · 연혁 (PM 2026-10-11 "관리자 페이지에서 고치게") — 예전엔 프론트 상수(constants/intro.js · history.js)였다.
 * 공개 조회는 누구나, 편집은 관리자(Lv1+). 전부 소프트삭제.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AboutService {

    private static final int TITLE_MAX = 100;
    private static final int CONTENT_MAX = 5000;
    private static final int TEXT_MAX = 1000;
    private static final int URL_MAX = 500;

    private final AboutMapper mapper;
    private final ObjectMapper objectMapper;

    // ---- 소개

    public List<IntroSectionDto> getIntro() {
        return mapper.findIntroSections();
    }

    @Transactional
    public IntroSectionDto createIntro(IntroSectionDto req) {
        AuthUtils.requireAdmin();
        validateIntro(req);
        if (req.getSortOrder() == null) req.setSortOrder(mapper.findIntroSections().size());
        mapper.insertIntroSection(req, AuthUtils.currentUserId());
        return mapper.findIntroSection(req.getSectionId());
    }

    @Transactional
    public IntroSectionDto updateIntro(Long sectionId, IntroSectionDto req) {
        AuthUtils.requireAdmin();
        validateIntro(req);
        if (req.getSortOrder() == null) req.setSortOrder(0);
        if (mapper.updateIntroSection(sectionId, req) == 0) throw new BaseException("문단이 없어요.", HttpStatus.NOT_FOUND);
        return mapper.findIntroSection(sectionId);
    }

    @Transactional
    public void deleteIntro(Long sectionId) {
        AuthUtils.requireAdmin();
        if (mapper.softDeleteIntroSection(sectionId) == 0) throw new BaseException("문단이 없어요.", HttpStatus.NOT_FOUND);
    }

    private void validateIntro(IntroSectionDto s) {
        s.setTitle(require(s.getTitle(), TITLE_MAX, "제목"));
        s.setContent(require(s.getContent(), CONTENT_MAX, "본문"));
    }

    // ---- 연혁

    /** 연도 오름차순으로 묶는다 (화면은 위에서 아래로 오래된 해부터) */
    public List<HistoryYearDto> getHistory() {
        Map<Integer, List<HistoryItemDto>> byYear = new LinkedHashMap<>();
        for (HistoryItemDto item : mapper.findHistoryItems()) {
            unpackImages(item);
            byYear.computeIfAbsent(item.getYear(), k -> new ArrayList<>()).add(item);
        }
        List<HistoryYearDto> out = new ArrayList<>();
        byYear.forEach((year, items) -> out.add(new HistoryYearDto(year, items)));
        return out;
    }

    /** 관리자 편집 화면용 — 평평한 목록 (연도·순서순) */
    public List<HistoryItemDto> getHistoryItems() {
        AuthUtils.requireAdmin();
        List<HistoryItemDto> items = mapper.findHistoryItems();
        items.forEach(this::unpackImages);
        return items;
    }

    @Transactional
    public HistoryItemDto createHistory(HistoryItemDto req) {
        AuthUtils.requireAdmin();
        validateHistory(req);
        if (req.getSortOrder() == null) {
            Integer max = mapper.findMaxHistoryOrder(req.getYear());
            req.setSortOrder(max == null ? 0 : max + 1);
        }
        mapper.insertHistoryItem(req, AuthUtils.currentUserId());
        return unpackImages(mapper.findHistoryItem(req.getItemId()));
    }

    @Transactional
    public HistoryItemDto updateHistory(Long itemId, HistoryItemDto req) {
        AuthUtils.requireAdmin();
        validateHistory(req);
        if (req.getSortOrder() == null) req.setSortOrder(0);
        if (mapper.updateHistoryItem(itemId, req) == 0) throw new BaseException("항목이 없어요.", HttpStatus.NOT_FOUND);
        return unpackImages(mapper.findHistoryItem(itemId));
    }

    @Transactional
    public void deleteHistory(Long itemId) {
        AuthUtils.requireAdmin();
        if (mapper.softDeleteHistoryItem(itemId) == 0) throw new BaseException("항목이 없어요.", HttpStatus.NOT_FOUND);
    }

    private void validateHistory(HistoryItemDto h) {
        if (h.getYear() == null || h.getYear() < 2000 || h.getYear() > 2100) throw new BaseException("연도를 확인해 주세요.", HttpStatus.BAD_REQUEST);
        h.setText(require(h.getText(), TEXT_MAX, "내용"));
        h.setHref(optional(h.getHref(), URL_MAX, "바로가기 주소"));
        h.setVideo(optional(h.getVideo(), URL_MAX, "영상 주소"));
        h.setLinkHref(optional(h.getLinkHref(), URL_MAX, "링크 주소"));
        h.setLinkLabel(optional(h.getLinkLabel(), TITLE_MAX, "링크 글자"));
        if (h.getLinkHref() == null) h.setLinkLabel(null);
        // 사진 목록은 요청의 images(객체) 를 JSON 으로 — src 없는 칸은 버린다
        List<HistoryItemDto.Image> images = new ArrayList<>();
        for (HistoryItemDto.Image img : h.getImages() == null ? List.<HistoryItemDto.Image>of() : h.getImages()) {
            if (img == null || img.getSrc() == null || img.getSrc().isBlank()) continue;
            if (img.getSrc().length() > URL_MAX) throw new BaseException("사진 주소가 너무 길어요.", HttpStatus.BAD_REQUEST);
            images.add(img);
        }
        try {
            h.setImagesJson(images.isEmpty() ? null : objectMapper.writeValueAsString(images));
        } catch (Exception e) {
            throw new BaseException("사진 목록을 저장할 수 없어요.", HttpStatus.BAD_REQUEST);
        }
    }

    private HistoryItemDto unpackImages(HistoryItemDto item) {
        if (item == null) return null;
        if (item.getImagesJson() != null && !item.getImagesJson().isBlank()) {
            try {
                item.setImages(objectMapper.readValue(item.getImagesJson(), new TypeReference<List<HistoryItemDto.Image>>() {}));
            } catch (Exception e) {
                item.setImages(new ArrayList<>());
            }
        }
        item.setImagesJson(null);
        return item;
    }

    private static String require(String v, int max, String what) {
        String s = v == null ? "" : v.strip();
        if (s.isEmpty()) throw new BaseException(what + "을(를) 적어 주세요.", HttpStatus.BAD_REQUEST);
        if (s.length() > max) throw new BaseException(what + "은(는) " + max + "자까지예요.", HttpStatus.BAD_REQUEST);
        return s;
    }

    private static String optional(String v, int max, String what) {
        String s = v == null ? "" : v.strip();
        if (s.isEmpty()) return null;
        if (s.length() > max) throw new BaseException(what + "은(는) " + max + "자까지예요.", HttpStatus.BAD_REQUEST);
        return s;
    }
}
