package com.back.guestbook.service;

import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.guestbook.dto.GuestbookNoteDto;
import com.back.guestbook.dto.GuestbookNoteRequest;
import com.back.guestbook.dto.GuestbookResponse;
import com.back.guestbook.dto.GuestbookStickerDto;
import com.back.guestbook.mapper.GuestbookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 방명록 (PM 2026-10-10) — 로그인 없이 남길 수 있는 한 줄 메모. 잉크·종이·관리자 스티커로 꾸민다.
 * - 학기 구분은 글을 남긴 시점의 학기(policy_settings semester1/2_start_month, 마이페이지 '이번 학기'와 같은 기준)로 자동.
 * - 로그인 상태면 '회원 이름으로' 남길 수 있고 그때만 user_id 가 붙어 본인이 지울 수 있다. 닉네임 글은 관리자만 숨긴다.
 * - 도배 방지: 같은 IP(로그인이면 회원)는 guestbook_cooldown_seconds 안에 한 장만 (Redis).
 * - 모든 수치는 policy_settings 에서 읽고 코드엔 행이 없을 때의 기본값만 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestbookService {

    public static final String POLICY_MAX_LENGTH = "guestbook_max_length";
    public static final String POLICY_COOLDOWN_SECONDS = "guestbook_cooldown_seconds";
    public static final String POLICY_MAX_STICKERS = "guestbook_max_stickers";
    private static final int DEFAULT_MAX_LENGTH = 300;
    private static final int DEFAULT_COOLDOWN_SECONDS = 60;
    private static final int DEFAULT_MAX_STICKERS = 3;
    private static final int DEFAULT_SEMESTER1_START_MONTH = 3;
    private static final int DEFAULT_SEMESTER2_START_MONTH = 9;
    private static final int NAME_MAX = 30;
    private static final Set<String> INKS = Set.of("ink", "gray", "pencil");
    private static final Set<String> PAPERS = Set.of("plain", "cream", "lined");
    private static final String COOLDOWN_KEY = "guestbook:cooldown:";

    private final GuestbookMapper mapper;
    private final StringRedisTemplate redisTemplate;

    public GuestbookResponse getGuestbook(String semester) {
        String current = currentSemesterLabel();
        List<String> semesters = new ArrayList<>(mapper.findSemesters());
        if (!semesters.contains(current)) semesters.add(0, current); // 글이 아직 없어도 이번 학기 탭은 보인다
        String picked = semester == null || semester.isBlank() || !semesters.contains(semester) ? current : semester;
        List<GuestbookNoteDto> notes = mapper.findNotes(picked);
        attachStickers(notes);
        Long me = AuthUtils.currentUserIdOrNull();
        for (GuestbookNoteDto n : notes) {
            n.setIsMine(me != null && me.equals(n.getUserId()));
        }
        return new GuestbookResponse(current, picked, semesters, maxLength(), maxStickers(), mapper.findStickers(), notes);
    }

    @Transactional
    public GuestbookNoteDto write(GuestbookNoteRequest req, String clientIp) {
        Long me = AuthUtils.currentUserIdOrNull();
        boolean asMember = Boolean.TRUE.equals(req.getAsMember()) && me != null;

        String content = req.getContent() == null ? "" : req.getContent().strip();
        int maxLength = maxLength();
        if (content.isEmpty()) throw new BaseException("내용을 적어 주세요.", HttpStatus.BAD_REQUEST);
        if (content.length() > maxLength) throw new BaseException(maxLength + "자까지 적을 수 있어요.", HttpStatus.BAD_REQUEST);

        String name = asMember ? null : (req.getDisplayName() == null ? "" : req.getDisplayName().strip());
        if (!asMember) {
            if (name.isEmpty()) throw new BaseException("이름(닉네임)을 적어 주세요.", HttpStatus.BAD_REQUEST);
            if (name.length() > NAME_MAX) throw new BaseException("이름은 " + NAME_MAX + "자까지예요.", HttpStatus.BAD_REQUEST);
        }

        // 같은 스티커를 두 번 고르면 하나로 (순서는 고른 차례)
        Set<Long> picked = new LinkedHashSet<>();
        for (Long id : req.getStickerIds() == null ? List.<Long>of() : req.getStickerIds()) if (id != null) picked.add(id);
        List<Long> stickerIds = new ArrayList<>(picked);
        int maxStickers = maxStickers();
        if (stickerIds.size() > maxStickers) throw new BaseException("스티커는 " + maxStickers + "개까지 붙일 수 있어요.", HttpStatus.BAD_REQUEST);
        if (!stickerIds.isEmpty() && mapper.countStickersByIds(stickerIds) != stickerIds.size()) {
            throw new BaseException("없는 스티커가 있어요. 새로고침 뒤 다시 골라 주세요.", HttpStatus.BAD_REQUEST);
        }

        // 도배 방지 — 로그인이면 회원 기준, 아니면 IP 기준으로 cooldown 동안 한 장
        String key = COOLDOWN_KEY + (me != null ? "u:" + me : "ip:" + hash(clientIp));
        int cooldown = cooldownSeconds();
        if (cooldown > 0) {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(cooldown));
            if (!Boolean.TRUE.equals(first)) {
                throw new BaseException("방금 한 장 남기셨어요. " + cooldown + "초 뒤에 다시 남길 수 있어요.", HttpStatus.TOO_MANY_REQUESTS);
            }
        }

        GuestbookNoteDto note = new GuestbookNoteDto();
        note.setSemesterLabel(currentSemesterLabel());
        note.setDisplayName(asMember ? mapper.findUserName(me) : name);
        note.setUserId(asMember ? me : null);
        note.setContent(content);
        note.setInk(INKS.contains(req.getInk()) ? req.getInk() : "ink");
        note.setPaper(PAPERS.contains(req.getPaper()) ? req.getPaper() : "plain");
        note.setTilt(ThreadLocalRandom.current().nextInt(-3, 4)); // 손으로 붙인 듯 살짝 기울어진다
        mapper.insertNote(note, clientIp == null ? null : hash(clientIp));
        if (!stickerIds.isEmpty()) mapper.insertNoteStickers(note.getNoteId(), stickerIds);

        GuestbookNoteDto saved = mapper.findNoteById(note.getNoteId());
        attachStickers(List.of(saved));
        saved.setIsMine(asMember);
        return saved;
    }

    /** 본인(회원 이름으로 남긴 글)만. 닉네임 글은 주인을 알 수 없어 관리자가 숨긴다 */
    @Transactional
    public void deleteMine(Long noteId) {
        Long me = AuthUtils.currentUserId();
        if (mapper.deleteOwnNote(noteId, me) == 0) {
            throw new BaseException("내가 남긴 글만 지울 수 있어요.", HttpStatus.FORBIDDEN);
        }
    }

    public GuestbookStickerDto requireSticker(Long stickerId) {
        GuestbookStickerDto s = mapper.findStickerById(stickerId);
        if (s == null) throw new BaseException("스티커가 없어요.", HttpStatus.NOT_FOUND);
        return s;
    }

    public void attachStickers(List<GuestbookNoteDto> notes) {
        if (notes.isEmpty()) return;
        List<Long> ids = notes.stream().map(GuestbookNoteDto::getNoteId).toList();
        Map<Long, GuestbookNoteDto> byId = new HashMap<>();
        for (GuestbookNoteDto n : notes) byId.put(n.getNoteId(), n);
        for (GuestbookNoteDto.StickerOnNote s : mapper.findNoteStickers(ids)) {
            GuestbookNoteDto n = byId.get(s.getNoteId());
            if (n != null) n.getStickers().add(s);
        }
    }

    // ---- 정책

    public int maxLength() {
        return intPolicy(POLICY_MAX_LENGTH, DEFAULT_MAX_LENGTH, 10, 2000);
    }

    public int maxStickers() {
        return intPolicy(POLICY_MAX_STICKERS, DEFAULT_MAX_STICKERS, 0, 10);
    }

    private int cooldownSeconds() {
        return intPolicy(POLICY_COOLDOWN_SECONDS, DEFAULT_COOLDOWN_SECONDS, 0, 86400);
    }

    private int intPolicy(String code, int def, int min, int max) {
        try {
            int v = Integer.parseInt(mapper.findPolicySetting(code).trim());
            return v < min || v > max ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    /** 오늘이 속한 학기 라벨 — '2026-2학기'. 마이페이지 '이번 학기'(MyPageServiceImpl)와 같은 월 기준 */
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

    private static String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
