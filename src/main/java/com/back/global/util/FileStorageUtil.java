package com.back.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

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

            String fileUrl = "/" + relativeDir + "/" + savedName;
            // 트랜잭션이 롤백되면 DB 행이 사라지는데 파일만 남으면 어떤 배치도 못 찾는 영구 고아가 된다
            // (정리 배치는 attachments 행 기준으로 파일을 찾는다) → 롤백 시 방금 저장한 파일을 보상 삭제
            registerRollbackCleanup(fileUrl);
            return fileUrl; // DB에 저장할 경로 반환
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류 발생", e);
        }
    }

    /** 현재 트랜잭션이 롤백되면 방금 저장한 파일을 지운다 (트랜잭션 밖에서 저장했으면 아무것도 안 한다) */
    private void registerRollbackCleanup(String fileUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.info("트랜잭션 롤백 — 업로드 파일 보상 삭제: {}", fileUrl);
                    delete(fileUrl);
                }
            }
        });
    }

    /**
     * 물리 파일 삭제를 **트랜잭션 커밋 후**로 미룬다.
     * 트랜잭션 안에서 즉시 지우면, 이후 예외로 롤백될 때 DB 행(소프트삭제 취소 등)은 살아나는데
     * 파일은 이미 사라져 "정상 첨부인데 다운로드는 영원히 404"가 된다.
     * 트랜잭션이 없으면(배치 밖 등) 즉시 삭제한다. 삭제 실패는 delete() 규칙대로 로그만.
     */
    public void deleteAfterCommit(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return;
        }
        List<String> urls = List.copyOf(fileUrls);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            urls.forEach(this::delete);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                urls.forEach(FileStorageUtil.this::delete);
            }
        });
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
            if (!isInside(target, base)) {
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
            if (!isInside(target, base)) {
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
                && isInside(dir, base)
                && !dir.equals(base)) {
            String[] children = dir.list();
            if (children == null || children.length > 0 || !dir.delete()) {
                break; // 파일이 남아 있거나 삭제 실패면 중단
            }
            dir = dir.getParentFile();
        }
    }

    /**
     * target 이 base 디렉터리 안(자기 자신 포함)에 있는가.
     * 문자열 접두사 비교는 형제 디렉터리(uploads-secret 등)를 통과시키므로
     * 경로 세그먼트 단위로 비교한다 (Path.startsWith).
     */
    private boolean isInside(File target, File base) {
        return target.toPath().startsWith(base.toPath());
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
