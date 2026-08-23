package com.back.board.attachment.service;

import com.back.board.attachment.dto.AttachmentDownload;
import com.back.board.attachment.dto.AttachmentFileRow;
import com.back.board.attachment.dto.AttachmentUploadResponse;
import com.back.board.attachment.dto.PendingAttachmentRow;
import com.back.board.attachment.mapper.AttachmentMapper;
import com.back.board.dto.BoardPolicy;
import com.back.board.exception.BoardException;
import com.back.board.service.BoardPolicyService;
import com.back.global.security.AuthUtils;
import com.back.global.util.FileStorageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 파일 선업로드 · 인증형 파일 조회 (리치 에디터 지원).
 *
 * 왜 선업로드인가 —
 * 에디터에 이미지를 넣는 순간 화면에 보여줄 URL이 즉시 필요하다. 발행 시점에 올리는 방식으로는
 * 에디터가 이미지를 표시할 방법이 없다. 그래서 파일을 고른 그 순간 올리고(글은 아직 없다 → post_id IS NULL),
 * 받은 url 을 본문 마크다운에 심는다. 발행/수정 때 그 파일들을 글에 연결한다.
 *
 * 본문 이미지와 첨부의 차이 — 업로드 API는 하나이고, 다른 것은 usage_type 뿐이다.
 *  - inline     : 본문에 삽입되는 이미지. 첨부 목록(상세의 attachments)에는 나오지 않는다
 *  - attachment : 상세 화면 아래 첨부 목록에 노출되는 파일
 *
 * 고아 파일 정리 —
 *  - 올렸지만 발행하지 않은 파일 → PendingAttachmentPurgeScheduler (기본 24시간)
 *  - 발행 후 수정에서 본문에서 지운 이미지 → syncInlineAttachments (저장 시점 정리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

    public static final String USAGE_INLINE = "inline";
    public static final String USAGE_ATTACHMENT = "attachment";

    // 정책값이 비어 있을 때만 쓰는 최후 기본값 (정상 운영에서는 policy_settings 가 이긴다)
    private static final String DEFAULT_IMAGE_EXTENSIONS = "jpg,jpeg,png,gif,webp,bmp";
    private static final String DEFAULT_FILE_EXTENSIONS = "pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,md,zip,hwp,hwpx";
    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    /**
     * 본문 마크다운에 남아 있는 이 서버의 파일 주소 패턴.
     * 프론트가 attachmentIds 에 인라인 이미지 id 를 빠뜨려도 본문에 남아 있으면 연결해 준다 —
     * 그러지 않으면 발행된 글의 이미지가 정리 배치에 지워져 깨진다.
     */
    private static final Pattern INLINE_FILE_URL = Pattern.compile("/api/user/files/(\\d{1,18})");

    private final AttachmentMapper attachmentMapper;
    private final BoardPolicyService boardPolicyService;
    private final FileStorageUtil fileStorageUtil;

    // ---------------------------------------------------------------- 업로드

    /**
     * 파일 1개 선업로드. 글이 없는 상태이므로 업로드 시점에 게시판 작성 권한을 검사한다 —
     * 그러지 않으면 글을 쓸 수도 없는 사람이 서버에 파일만 쌓을 수 있다.
     */
    @Transactional
    public AttachmentUploadResponse upload(Long boardId, MultipartFile file, String usage) {
        BoardPolicy policy = boardPolicyService.requireWritable(boardId);
        if (!policy.isAttachmentAllowed()) {
            throw new BoardException("이 게시판은 파일 업로드를 사용하지 않습니다.", HttpStatus.FORBIDDEN);
        }
        if (file == null || file.isEmpty()) {
            throw new BoardException("업로드할 파일이 없습니다.", HttpStatus.BAD_REQUEST);
        }

        String originName = originName(file);
        String extension = extensionOf(originName);
        boolean isImage = allowedExtensions("upload_image_extensions", DEFAULT_IMAGE_EXTENSIONS).contains(extension);
        boolean isFile = allowedExtensions("upload_file_extensions", DEFAULT_FILE_EXTENSIONS).contains(extension);
        if (!isImage && !isFile) {
            throw new BoardException("허용되지 않는 파일 형식입니다.", HttpStatus.BAD_REQUEST);
        }

        String usageType = resolveUsageType(usage, isImage);
        Long uploaderId = AuthUtils.currentUserId();

        // 저장 위치는 업로더별 폴더 — 글 번호를 아직 모르므로 글 폴더에 넣을 수 없다.
        // 연결 시 파일을 옮기지 않는다(경로 이동은 실패 지점만 늘린다). 본문·첨부가 쓰는 주소는
        // 물리 경로가 아니라 /api/user/files/{id} 이므로 파일이 어디에 있어도 URL 은 변하지 않는다.
        String savedPath = fileStorageUtil.save(file, policy.uploadDir() + "/user-" + uploaderId);

        AttachmentFileRow row = new AttachmentFileRow();
        row.setUploaderId(uploaderId);
        row.setFileName(originName);
        row.setFileUrl(savedPath);
        row.setFileType(contentType(file, isImage, extension));
        row.setUsageType(usageType);
        row.setFileSize(file.getSize());
        attachmentMapper.insertPending(row);

        log.info("파일 선업로드 - attachmentId: {}, boardId: {}, usage: {}, name: {}",
                row.getAttachmentId(), boardId, usageType, originName);
        return new AttachmentUploadResponse(row.getAttachmentId(), originName, file.getSize(), isImage, usageType);
    }

    // ---------------------------------------------------------------- 조회(다운로드)

    /**
     * 인증형 파일 조회. 정적 경로(/uploads/**)는 URL만 알면 비로그인도 열리므로
     * 이 API가 파일 → 글 → 게시판을 타고 read_scope 를 판정한다(경로에 boardId 가 없어도 검사 가능).
     *
     * 권한이 없는 경우도 404다 — 어떤 파일이 존재하는지 자체를 알려주지 않는다.
     */
    public AttachmentDownload download(Long fileId) {
        AttachmentFileRow row = attachmentMapper.findFileRow(fileId);
        if (row == null || !"normal".equals(row.getState())) {
            throw notFound();
        }

        if (row.getPostId() == null) {
            // 아직 글에 연결되지 않은 선업로드 파일 — 올린 본인만 볼 수 있다(작성 중 미리보기)
            if (!AuthUtils.currentUserId().equals(row.getUploaderId())) {
                throw notFound();
            }
        } else {
            // 블라인드·삭제된 글의 첨부는 일반 회원에게 노출하지 않는다(관리자는 조치 검토를 위해 열람 가능)
            if (!AuthUtils.isAdmin() && !"normal".equals(row.getPostState())) {
                throw notFound();
            }
            BoardPolicy policy = boardPolicyService.get(row.getBoardId());
            if (!policy.canRead(AuthUtils.memberType(), AuthUtils.adminLevel())) {
                throw notFound();
            }
        }

        File file = fileStorageUtil.load(row.getFileUrl());
        if (file == null) {
            log.warn("DB에는 있으나 디스크에 없는 첨부 - attachmentId: {}, path: {}", fileId, row.getFileUrl());
            throw notFound();
        }

        String contentType = row.getFileType() == null || row.getFileType().isBlank()
                ? FALLBACK_CONTENT_TYPE : row.getFileType();
        // 이미지만 inline — 그 외는 무조건 다운로드로 내린다.
        // (html·svg 를 inline 으로 내리면 API 오리진에서 스크립트가 실행될 수 있다)
        boolean inline = contentType.startsWith("image/") && !contentType.contains("svg");
        return new AttachmentDownload(file, row.getFileName(), contentType, file.length(), inline);
    }

    // ---------------------------------------------------------------- 글 연결

    /**
     * 선업로드 파일을 글에 연결한다 (등록·수정 공통).
     *
     * 연결 대상 = 요청의 attachmentIds ∪ 본문 마크다운에 남아 있는 /api/user/files/{id}.
     * 본문을 함께 훑는 이유는 프론트가 인라인 이미지 id 를 attachmentIds 에 빠뜨려도
     * 발행된 글의 이미지가 정리 배치에 지워지지 않게 하기 위함이다.
     */
    @Transactional
    public void linkToPost(Long postId, List<Long> attachmentIds, String content) {
        List<Long> targets = mergeIds(attachmentIds, extractInlineIds(content));
        if (targets.isEmpty()) {
            return;
        }
        int linked = attachmentMapper.linkToPost(postId, targets, AuthUtils.currentUserId());
        if (linked < targets.size()) {
            // 이미 다른 글에 연결된 id·남의 id·없는 id 는 조용히 지나간다(발행 자체는 성공시킨다).
            // 초안의 draftId 처리와 같은 방침 — 여기서 실패시키면 사용자가 쓴 글을 잃는다
            log.info("선업로드 연결 - 요청 {}건 중 {}건 연결 (postId: {})", targets.size(), linked, postId);
        }
    }

    /**
     * 수정 저장 시, 본문에서 사라진 인라인 이미지를 정리한다(소프트삭제 + 물리 파일 삭제).
     * 첨부 목록용 파일(usage_type=attachment)은 대상이 아니다 — 그쪽은 deleteAttachmentIds 로만 지운다.
     */
    @Transactional
    public void syncInlineAttachments(Long postId, String content, List<Long> attachmentIds) {
        List<Long> keep = mergeIds(attachmentIds, extractInlineIds(content));
        List<PendingAttachmentRow> removed = attachmentMapper.findUnreferencedInline(postId, keep);
        if (CollectionUtils.isEmpty(removed)) {
            return;
        }
        List<Long> ids = removed.stream().map(PendingAttachmentRow::getAttachmentId).toList();
        attachmentMapper.softDeleteByIds(ids);
        removed.forEach(row -> fileStorageUtil.delete(row.getFileUrl()));
        log.info("본문에서 제거된 인라인 이미지 정리 - postId: {}, {}건", postId, ids.size());
    }

    // ---------------------------------------------------------------- 임시저장(draft) 연동

    /**
     * 초안 저장 시 첨부 재조정 (생성·덮어쓰기 공통).
     *
     * keep = 요청의 attachmentIds ∪ 본문 마크다운에 남아 있는 /api/user/files/{id} —
     * 글 발행과 같은 규칙이라, 프론트가 인라인 이미지 id 를 attachmentIds 에 빠뜨려도 초안 첨부가 지워지지 않는다.
     *  ① keep 을 이 초안에 귀속 (내가 올린 대기분/이미 이 초안 것만)
     *  ② 이전엔 이 초안에 묶였으나 keep 에 없는 첨부는 행·물리파일까지 삭제
     * 순서 중요: 먼저 귀속해야 방금 넣은 첨부가 ②의 삭제 대상에서 빠진다.
     * 초안 첨부는 발행 전 개인 작업물이라 물리 삭제한다(drafts 와 같은 소프트삭제 예외).
     */
    @Transactional
    public void reconcileDraftAttachments(Long draftId, List<Long> attachmentIds, String content) {
        List<Long> keep = mergeIds(attachmentIds, extractInlineIds(content));
        if (!keep.isEmpty()) {
            attachmentMapper.linkToDraft(draftId, keep, AuthUtils.currentUserId());
        }
        List<PendingAttachmentRow> removed = attachmentMapper.findDraftAttachmentsExcept(draftId, keep);
        deleteDraftRows(draftId, removed);
    }

    /**
     * 초안 삭제 시 첨부 정리 — 행과 물리 파일을 **명시적으로** 지운다.
     * FK CASCADE 에 맡기면 DB 행은 사라져도 디스크 파일이 고아로 남으므로,
     * 반드시 초안(drafts) DELETE 보다 먼저 이 메서드를 호출한다(CASCADE 는 백스톱).
     */
    @Transactional
    public void deleteDraftAttachments(Long draftId) {
        deleteDraftRows(draftId, attachmentMapper.findDraftAttachments(draftId));
    }

    /**
     * 발행 이관: 초안 첨부의 소유권을 게시글로 넘긴다 (post_id 세팅 + draft_id 비움 — CASCADE 대상 제외).
     * ⚠ 반드시 초안 DELETE 보다 먼저, 같은 트랜잭션에서 호출할 것 (SPEC-A5 §6-3).
     */
    @Transactional
    public void transferDraftToPost(Long draftId, Long postId) {
        int moved = attachmentMapper.transferDraftAttachmentsToPost(draftId, postId, AuthUtils.currentUserId());
        if (moved > 0) {
            log.info("초안 첨부 발행 이관 - draftId: {} → postId: {}, {}건", draftId, postId, moved);
        }
    }

    /** 초안 첨부 행 + 물리 파일 삭제 공통 (행 삭제 → 파일 삭제 순서. 파일 삭제 실패는 로그만) */
    private void deleteDraftRows(Long draftId, List<PendingAttachmentRow> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }
        List<Long> ids = rows.stream().map(PendingAttachmentRow::getAttachmentId).toList();
        attachmentMapper.deleteDraftAttachmentsByIds(draftId, ids);
        rows.forEach(row -> fileStorageUtil.delete(row.getFileUrl()));
        log.info("초안 첨부 정리 - draftId: {}, {}건 (행+물리파일)", draftId, ids.size());
    }

    // ---------------------------------------------------------------- 정리 배치

    /** 글에 연결되지 않은 채 보존시간이 지난 선업로드 파일을 지운다 (DB 행 → 물리 파일 순서) */
    @Transactional
    public int purgeExpiredPending(int hours) {
        List<PendingAttachmentRow> expired = attachmentMapper.findExpiredPending(hours);
        if (CollectionUtils.isEmpty(expired)) {
            return 0;
        }
        List<Long> ids = expired.stream().map(PendingAttachmentRow::getAttachmentId).toList();
        int deleted = attachmentMapper.deletePendingByIds(ids);
        expired.forEach(row -> fileStorageUtil.delete(row.getFileUrl()));
        return deleted;
    }

    /** 보존시간(시간 단위) — policy_settings.pending_upload_purge_hours (하드코딩 금지) */
    public int purgeHours(int defaultHours) {
        try {
            return Integer.parseInt(attachmentMapper.findPolicySetting("pending_upload_purge_hours"));
        } catch (Exception e) {
            return defaultHours;
        }
    }

    // ---------------------------------------------------------------- 내부 헬퍼

    private BoardException notFound() {
        return new BoardException("존재하지 않는 파일입니다.", HttpStatus.NOT_FOUND);
    }

    /** 본문 마크다운에 남아 있는 파일 id (![](/api/user/files/31) 형태) */
    private List<Long> extractInlineIds(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        Matcher matcher = INLINE_FILE_URL.matcher(content);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // 자릿수 제한 덕에 사실상 발생하지 않지만, 본문 파싱이 저장을 막지는 않게 한다
            }
        }
        return ids;
    }

    private List<Long> mergeIds(List<Long> requested, List<Long> fromContent) {
        Set<Long> merged = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(requested)) {
            requested.stream().filter(Objects::nonNull).forEach(merged::add);
        }
        merged.addAll(fromContent);
        return new ArrayList<>(merged);
    }

    private String resolveUsageType(String usage, boolean isImage) {
        if (usage == null || usage.isBlank()) {
            // 미지정이면 이미지는 본문 삽입, 그 외는 첨부 목록 — 화면 흐름과 일치하는 기본값
            return isImage ? USAGE_INLINE : USAGE_ATTACHMENT;
        }
        String normalized = usage.trim().toLowerCase(Locale.ROOT);
        if (USAGE_INLINE.equals(normalized)) {
            if (!isImage) {
                throw new BoardException("본문에 삽입할 수 있는 것은 이미지 파일뿐입니다.", HttpStatus.BAD_REQUEST);
            }
            return USAGE_INLINE;
        }
        if (USAGE_ATTACHMENT.equals(normalized)) {
            return USAGE_ATTACHMENT;
        }
        throw new BoardException("usage 는 inline 또는 attachment 만 사용할 수 있습니다.", HttpStatus.BAD_REQUEST);
    }

    /** policy_settings 의 확장자 목록 (소문자 집합). 비어 있으면 코드 기본값 */
    private Set<String> allowedExtensions(String code, String fallback) {
        String value = attachmentMapper.findPolicySetting(code);
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(token -> token.trim().toLowerCase(Locale.ROOT))
                .filter(token -> !token.isEmpty())
                .forEach(result::add);
        return result;
    }

    private String originName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "file";
        }
        // 브라우저에 따라 전체 경로가 올 수 있어 마지막 세그먼트만 취한다 (file_name varchar(250))
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        return name.length() > 250 ? name.substring(0, 250) : name;
    }

    private String extensionOf(String originName) {
        int dot = originName.lastIndexOf('.');
        return dot < 0 || dot == originName.length() - 1
                ? "" : originName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 저장할 MIME 타입. 브라우저가 이미지에 octet-stream 을 보내는 경우가 있어
     * 이미지는 확장자로 보정한다 — 그러지 않으면 조회 시 img 태그가 렌더링하지 못한다.
     */
    private String contentType(MultipartFile file, boolean isImage, String extension) {
        String given = file.getContentType();
        if (isImage && (given == null || !given.startsWith("image/"))) {
            return "image/" + ("jpg".equals(extension) ? "jpeg" : extension);
        }
        return given == null || given.isBlank() ? FALLBACK_CONTENT_TYPE : given;
    }
}
