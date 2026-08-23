package com.back.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

/**
 * 첨부파일 물리 저장/삭제.
 *
 * 저장 파일명은 UUID 가 아니라 **원본 파일명 그대로** 쓴다 (PM 결정 2026-08-16).
 * 다른 오리진에서 정적 경로(/uploads/**)로 내려받아도 파일명이 원본으로 떨어지게 하기 위함이다.
 * 같은 글에 같은 이름을 또 올리면 "이름 (1).ext" 식으로 번호를 붙여 충돌만 피한다.
 * → 글마다 하위 폴더(post_id)를 나눠 저장하므로 글 사이의 이름 충돌은 애초에 없다.
 */
@Slf4j
@Component
public class FileStorageUtil {

    public String save(MultipartFile file, String relativeDir) {
        if (file.isEmpty()) return null;

        try {
            String basePath = new File("").getAbsolutePath();
            File dir = new File(basePath + File.separator + relativeDir);
            if (!dir.exists()) dir.mkdirs(); // 폴더 없으면 자동으로 만들어줌

            String savedName = uniquify(dir, sanitize(file.getOriginalFilename()));
            file.transferTo(new File(dir, savedName));

            return "/" + relativeDir + "/" + savedName; // DB에 저장할 경로 반환
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류 발생", e);
        }
    }

    /**
     * DB에 저장된 file_url(/uploads/...)로 물리 파일을 읽는다 (인증형 파일 조회 API 용).
     * 파일이 없거나 uploads 밖을 가리키면 null — 호출측이 404로 응답한다.
     * 정적 서빙(/uploads/**)과 달리 이 경로는 권한 검사를 통과한 뒤에만 불린다.
     */
    public File load(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;
        try {
            File base = new File(new File("").getAbsolutePath(), "uploads").getCanonicalFile();
            File target = new File(new File("").getAbsolutePath(), fileUrl).getCanonicalFile();
            // 경로 이탈 방어: uploads 밖 파일은 내려주지 않는다 (조작된 file_url 대비)
            if (!target.getPath().startsWith(base.getPath())) {
                log.warn("uploads 밖 경로 조회 시도 차단 - {}", fileUrl);
                return null;
            }
            return target.isFile() ? target : null;
        } catch (IOException e) {
            log.warn("물리 파일 조회 중 오류 - {}: {}", fileUrl, e.getMessage());
            return null;
        }
    }

    /**
     * DB에 저장된 file_url(/uploads/...)로 물리 파일을 삭제한다.
     * 첨부를 교체/삭제할 때 디스크에 고아 파일이 남지 않게 하는 용도 (PM 결정 2026-08-16).
     * 삭제 실패는 로그만 남긴다 — 파일이 이미 없어도 요청 자체는 성공해야 한다.
     */
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            File base = new File(new File("").getAbsolutePath(), "uploads").getCanonicalFile();
            File target = new File(new File("").getAbsolutePath(), fileUrl).getCanonicalFile();
            // 경로 이탈 방어: uploads 폴더 밖은 절대 지우지 않는다 (조작된 file_url 대비)
            if (!target.getPath().startsWith(base.getPath())) {
                log.warn("uploads 밖 경로 삭제 시도 차단 - {}", fileUrl);
                return;
            }
            if (!target.delete() && target.exists()) {
                log.warn("물리 파일 삭제 실패 - {}", fileUrl);
                return;
            }
            deleteEmptyParents(target.getParentFile(), base);
        } catch (IOException e) {
            log.warn("물리 파일 삭제 중 오류 - {}: {}", fileUrl, e.getMessage());
        }
    }

    /**
     * 파일을 지운 뒤 빈 폴더를 위로 올라가며 정리한다 (uploads 자체는 남긴다).
     * 글 단위 폴더(uploads/board-2/185)를 쓰므로, 마지막 첨부가 지워지면 빈 폴더가 계속 쌓이기 때문.
     */
    private void deleteEmptyParents(File dir, File base) {
        while (dir != null
                && dir.getPath().startsWith(base.getPath())
                && !dir.equals(base)) {
            String[] children = dir.list();
            if (children == null || children.length > 0 || !dir.delete()) {
                break; // 파일이 남아 있거나 삭제 실패면 중단
            }
            dir = dir.getParentFile();
        }
    }

    /**
     * 원본 파일명 정리: 경로 구분자·경로 이탈(../)·OS 금지 문자를 제거한다.
     * 업로드 파일명은 사용자 입력이므로 그대로 파일시스템에 쓰면 안 된다.
     */
    private String sanitize(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        // 브라우저에 따라 전체 경로가 올 수 있으므로 마지막 세그먼트만 취한다
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        // Windows 금지 문자 + 제어 문자 치환
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        // 숨김 파일·빈 이름 방어
        if (name.isBlank() || name.startsWith(".")) {
            name = "file" + name;
        }
        // 과도하게 긴 이름은 확장자를 보존하며 자른다 (attachments.file_name varchar 한계 대비)
        if (name.length() > 100) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 ? name.substring(dot) : "";
            name = name.substring(0, 100 - ext.length()) + ext;
        }
        return name;
    }

    /** 같은 폴더에 같은 이름이 있으면 "이름 (1).ext", "이름 (2).ext" ... 로 비켜 간다 */
    private String uniquify(File dir, String name) {
        if (!new File(dir, name).exists()) return name;

        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!new File(dir, candidate).exists()) return candidate;
        }
    }
}
