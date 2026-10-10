package com.back.guestbook.service;

import com.back.global.exception.BaseException;
import com.back.global.security.AuthUtils;
import com.back.guestbook.dto.GuestbookDrawingRow;
import com.back.guestbook.dto.GuestbookNoteDto;
import com.back.guestbook.dto.GuestbookNoteRequest;
import com.back.guestbook.dto.GuestbookResponse;
import com.back.guestbook.mapper.GuestbookMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 방명록 (PM 2026-10-10) — 로그인 없이 남길 수 있는 손글씨 메모. 글씨체·잉크·종이·정렬을 고르고, 직접 그린 스티커를 붙인다.
 * - 학기 구분은 글을 남긴 시점의 학기(policy_settings semester1/2_start_month, 마이페이지 '이번 학기'와 같은 기준)로 자동.
 * - 이름은 늘 자유 입력. 로그인 상태면 user_id 가 붙어 본인이 고치거나 지울 수 있다. 비로그인 글은 관리자만 숨긴다 (PM 10/10 밤).
 * - 스티커는 관리자 등록이 아니라 작성자가 그림판 모달에서 그린 투명 PNG (data URL) — 서버가 파일로 저장하고 위치(%)·크기·회전을 함께 둔다.
 * - 도배 방지: 같은 IP(로그인이면 회원)는 guestbook_cooldown_seconds 안에 한 장만 (Redis). 고치기에는 적용하지 않는다.
 * - 모든 수치는 policy_settings 에서 읽고 코드엔 행이 없을 때의 기본값만 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestbookService {

    public static final String POLICY_MAX_LENGTH = "guestbook_max_length";
    public static final String POLICY_COOLDOWN_SECONDS = "guestbook_cooldown_seconds";
    public static final String POLICY_MAX_DRAWINGS = "guestbook_max_drawings";
    public static final String POLICY_DRAWING_MAX_KB = "guestbook_drawing_max_kb";
    private static final int DEFAULT_MAX_LENGTH = 300;
    private static final int DEFAULT_COOLDOWN_SECONDS = 60;
    private static final int DEFAULT_MAX_DRAWINGS = 3;
    private static final int DEFAULT_DRAWING_MAX_KB = 200;
    private static final int DEFAULT_SEMESTER1_START_MONTH = 3;
    private static final int DEFAULT_SEMESTER2_START_MONTH = 9;
    private static final int NAME_MAX = 30;
    private static final Set<String> FONTS = Set.of("pen", "brush", "gaegu", "himelody", "gamja", "poor");
    private static final Set<String> INKS = Set.of("black", "blueblack", "sepia", "burgundy", "forest", "pencil");
    private static final Set<String> PAPERS = Set.of("plain", "lined", "grid", "cream", "vintage");
    private static final Set<String> ALIGNS = Set.of("left", "center", "right");
    private static final String COOLDOWN_KEY = "guestbook:cooldown:";
    private static final String DRAWING_DIR = "uploads/guestbook/drawings";

    private final GuestbookMapper mapper;
    private final StringRedisTemplate redisTemplate;

    public GuestbookResponse getGuestbook(String semester) {
        String current = currentSemesterLabel();
        List<String> semesters = new ArrayList<>(mapper.findSemesters());
        if (!semesters.contains(current)) semesters.add(0, current); // 글이 아직 없어도 이번 학기 탭은 보인다
        String picked = semester == null || semester.isBlank() || !semesters.contains(semester) ? current : semester;
        // 방명록 화면은 누구에게나 normal 만. 숨긴 글의 복원은 운영 관리 > 방명록 관리에서 (PM 10/11)
        List<GuestbookNoteDto> notes = mapper.findNotes(picked, false);
        decorate(notes);
        return new GuestbookResponse(current, picked, semesters, maxLength(), maxDrawings(), drawingMaxKb(), notes);
    }

    @Transactional
    public GuestbookNoteDto write(GuestbookNoteRequest req, String clientIp) {
        Long me = AuthUtils.currentUserIdOrNull();
        GuestbookNoteDto note = new GuestbookNoteDto();
        applyFields(note, req);
        List<GuestbookNoteRequest.DrawingInput> drawings = validateDrawings(req.getDrawings(), true);

        // 도배 방지 — 로그인이면 회원 기준, 아니면 IP 기준으로 cooldown 동안 한 장
        String key = COOLDOWN_KEY + (me != null ? "u:" + me : "ip:" + hash(clientIp == null ? "" : clientIp));
        int cooldown = cooldownSeconds();
        if (cooldown > 0) {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(cooldown));
            if (!Boolean.TRUE.equals(first)) {
                throw new BaseException("방금 한 장 남기셨어요. " + cooldown + "초 뒤에 다시 남길 수 있어요.", HttpStatus.TOO_MANY_REQUESTS);
            }
        }

        note.setSemesterLabel(currentSemesterLabel());
        note.setUserId(me);
        note.setTilt(randomTilt());
        mapper.insertNote(note, clientIp == null ? null : hash(clientIp));
        saveDrawings(note.getNoteId(), drawings, true);

        return reload(note.getNoteId());
    }

    /**
     * 글 고치기 — 로그인해서 남긴 내 글만 (관리자도 남의 글은 못 고친다. 숨기기는 방명록 관리에서 — PM 10/11).
     * 학기·기울기는 그대로, 그림은 보낸 목록으로 맞춘다
     */
    @Transactional
    public GuestbookNoteDto edit(Long noteId, GuestbookNoteRequest req) {
        Long me = AuthUtils.currentUserId();
        GuestbookNoteDto note = new GuestbookNoteDto();
        applyFields(note, req);
        List<GuestbookNoteRequest.DrawingInput> drawings = validateDrawings(req.getDrawings(), false);
        if (mapper.updateOwnNote(noteId, me, note) == 0) {
            throw new BaseException("내가 남긴 글만 고칠 수 있어요.", HttpStatus.FORBIDDEN);
        }
        saveDrawings(noteId, drawings, false);
        return reload(noteId);
    }

    /** 지우기 — 로그인해서 남긴 내 글만 (deleted). 남의 글·비로그인 글은 관리자가 방명록 관리에서 숨긴다 */
    @Transactional
    public void delete(Long noteId) {
        Long me = AuthUtils.currentUserId();
        if (mapper.deleteOwnNote(noteId, me) == 0) {
            throw new BaseException("내가 남긴 글만 지울 수 있어요.", HttpStatus.FORBIDDEN);
        }
    }

    public GuestbookDrawingRow requireDrawing(Long drawingId) {
        GuestbookDrawingRow d = mapper.findDrawingById(drawingId);
        if (d == null) throw new BaseException("그림이 없어요.", HttpStatus.NOT_FOUND);
        return d;
    }

    /** 그림을 붙이고 isMine 을 매긴다 (관리자 목록도 같이 쓴다) */
    public void decorate(List<GuestbookNoteDto> notes) {
        if (notes.isEmpty()) return;
        Long me = AuthUtils.currentUserIdOrNull();
        List<Long> ids = notes.stream().map(GuestbookNoteDto::getNoteId).toList();
        Map<Long, GuestbookNoteDto> byId = new HashMap<>();
        for (GuestbookNoteDto n : notes) {
            byId.put(n.getNoteId(), n);
            // 고치고 지우는 건 로그인해서 남긴 내 글만 — 관리자도 예외 없음 (PM 10/11)
            boolean mine = me != null && me.equals(n.getUserId());
            n.setIsMine(mine);
            n.setCanManage(mine);
        }
        for (GuestbookNoteDto.Drawing d : mapper.findDrawingsOfNotes(ids)) {
            GuestbookNoteDto n = byId.get(d.getNoteId());
            if (n != null) n.getDrawings().add(d);
        }
    }

    // ---- 내부

    private GuestbookNoteDto reload(Long noteId) {
        GuestbookNoteDto saved = mapper.findNoteById(noteId);
        decorate(List.of(saved));
        return saved;
    }

    private void applyFields(GuestbookNoteDto note, GuestbookNoteRequest req) {
        String content = req.getContent() == null ? "" : req.getContent().strip();
        int maxLength = maxLength();
        if (content.isEmpty()) throw new BaseException("내용을 적어 주세요.", HttpStatus.BAD_REQUEST);
        if (content.length() > maxLength) throw new BaseException(maxLength + "자까지 적을 수 있어요.", HttpStatus.BAD_REQUEST);
        String name = req.getDisplayName() == null ? "" : req.getDisplayName().strip();
        if (name.isEmpty()) throw new BaseException("이름(닉네임)을 적어 주세요.", HttpStatus.BAD_REQUEST);
        if (name.length() > NAME_MAX) throw new BaseException("이름은 " + NAME_MAX + "자까지예요.", HttpStatus.BAD_REQUEST);
        note.setDisplayName(name);
        note.setContent(content);
        note.setFont(FONTS.contains(req.getFont()) ? req.getFont() : "pen");
        // 잉크는 기본 6색 키 또는 사용자가 컬러피커로 고른 #rrggbb (PM 10/10 밤)
        String inkIn = req.getInk() == null ? "" : req.getInk().trim().toLowerCase();
        note.setInk(INKS.contains(inkIn) || inkIn.matches("#[0-9a-f]{6}") ? inkIn : "black");
        note.setPaper(PAPERS.contains(req.getPaper()) ? req.getPaper() : "plain");
        note.setAlign(ALIGNS.contains(req.getAlign()) ? req.getAlign() : "left");
        note.setMarksSeed(req.getMarksSeed()); // 얼룩 배치 씨앗 — 프론트가 뽑은 값 그대로 (없으면 null → 화면이 noteId 로)
    }

    /** 수·크기·위치 검사. 새 글은 전부 dataUrl 이어야 하고, 고치기는 drawingId 로 기존 그림을 가리킬 수 있다 */
    private List<GuestbookNoteRequest.DrawingInput> validateDrawings(List<GuestbookNoteRequest.DrawingInput> raw, boolean creating) {
        List<GuestbookNoteRequest.DrawingInput> list = raw == null ? List.of() : raw;
        int max = maxDrawings();
        if (list.size() > max) throw new BaseException("스티커는 " + max + "개까지 붙일 수 있어요.", HttpStatus.BAD_REQUEST);
        int maxBytes = drawingMaxKb() * 1024;
        for (GuestbookNoteRequest.DrawingInput d : list) {
            boolean hasNew = d.getDataUrl() != null && !d.getDataUrl().isBlank();
            if (!hasNew && (creating || d.getDrawingId() == null)) {
                throw new BaseException("스티커 그림이 비어 있어요.", HttpStatus.BAD_REQUEST);
            }
            if (hasNew) {
                if (!d.getDataUrl().startsWith("data:image/png;base64,")) {
                    throw new BaseException("스티커는 PNG 그림만 붙일 수 있어요.", HttpStatus.BAD_REQUEST);
                }
                // base64 는 원본의 4/3 — 디코딩 전에 대략 거른다
                if ((long) d.getDataUrl().length() * 3 / 4 > maxBytes) {
                    throw new BaseException("스티커 그림이 너무 커요 (" + drawingMaxKb() + "KB 까지).", HttpStatus.BAD_REQUEST);
                }
            }
            d.setPosX(clamp(d.getPosX(), 0, 100, 80));
            d.setPosY(clamp(d.getPosY(), 0, 100, 20));
            d.setWidthPct(clamp(d.getWidthPct(), 8, 80, 30));
            d.setRotation(d.getRotation() == null ? 0 : Math.max(-180, Math.min(180, d.getRotation())));
            d.setOpacity(clamp(d.getOpacity(), 0.1, 1, 1));
            d.setOpacity(clamp(d.getOpacity(), 0.1, 1, 1));
        }
        return list;
    }

    private static double clamp(Double v, double min, double max, double def) {
        if (v == null || v.isNaN()) return def;
        return Math.max(min, Math.min(max, v));
    }

    /** 새 그림은 파일로 저장해 행을 만들고, 기존 그림은 자리만 갱신하며, 목록에 없는 그림은 뗀다 */
    private void saveDrawings(Long noteId, List<GuestbookNoteRequest.DrawingInput> drawings, boolean creating) {
        List<Long> keep = new ArrayList<>();
        int order = 0;
        for (GuestbookNoteRequest.DrawingInput in : drawings) {
            GuestbookDrawingRow row = new GuestbookDrawingRow();
            row.setNoteId(noteId);
            row.setPosX(in.getPosX());
            row.setPosY(in.getPosY());
            row.setWidthPct(in.getWidthPct());
            row.setRotation(in.getRotation());
            row.setOpacity(in.getOpacity());
            row.setOpacity(in.getOpacity());
            row.setSortOrder(order++);
            if (in.getDataUrl() != null && !in.getDataUrl().isBlank()) {
                row.setFileUrl(storePng(noteId, in.getDataUrl()));
                row.setFileType("image/png");
                mapper.insertDrawing(row);
                keep.add(row.getDrawingId());
            } else {
                row.setDrawingId(in.getDrawingId());
                if (mapper.updateDrawingPlacement(noteId, row) > 0) keep.add(in.getDrawingId());
            }
        }
        if (!creating) mapper.softDeleteDrawingsExcept(noteId, keep);
    }

    /** data URL 의 PNG 를 uploads/guestbook/drawings/{noteId}/ 에 쓴다. 경로 규칙은 FileStorageUtil 과 같아 load() 로 읽을 수 있다 */
    private String storePng(Long noteId, String dataUrl) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
        } catch (IllegalArgumentException e) {
            throw new BaseException("스티커 그림을 읽을 수 없어요.", HttpStatus.BAD_REQUEST);
        }
        // PNG 서명 확인 — 확장자만 믿지 않는다
        if (bytes.length < 8 || (bytes[0] & 0xFF) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            throw new BaseException("스티커는 PNG 그림만 붙일 수 있어요.", HttpStatus.BAD_REQUEST);
        }
        String relativeDir = DRAWING_DIR + "/" + noteId;
        File dir = new File(new File("").getAbsolutePath(), relativeDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BaseException("그림을 저장하지 못했어요.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String name = UUID.randomUUID().toString().replace("-", "") + ".png";
        try {
            Files.write(new File(dir, name).toPath(), bytes);
        } catch (IOException e) {
            log.warn("방명록 그림 저장 실패 - note {}: {}", noteId, e.getMessage());
            throw new BaseException("그림을 저장하지 못했어요.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return "/" + relativeDir + "/" + name;
    }

    /** 손으로 붙인 듯 눈에 띄게 기울인다 — 대부분 2~6도(양쪽), 넷 중 하나는 거의 똑바로 */
    private static int randomTilt() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextInt(4) == 0) return r.nextInt(-1, 2);
        int t = r.nextInt(2, 7);
        return r.nextBoolean() ? t : -t;
    }

    // ---- 정책

    public int maxLength() {
        return intPolicy(POLICY_MAX_LENGTH, DEFAULT_MAX_LENGTH, 10, 2000);
    }

    public int maxDrawings() {
        return intPolicy(POLICY_MAX_DRAWINGS, DEFAULT_MAX_DRAWINGS, 0, 10);
    }

    public int drawingMaxKb() {
        return intPolicy(POLICY_DRAWING_MAX_KB, DEFAULT_DRAWING_MAX_KB, 20, 2048);
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
