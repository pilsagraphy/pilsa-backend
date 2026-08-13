package com.back.global.util;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Component
public class FileStorageUtil {

    public String save(MultipartFile file, String relativeDir, @Nullable String oldFilePath) {
        if (file.isEmpty()) return null;

        try {
            String basePath = new File("").getAbsolutePath();
            String fullDirPath = basePath + File.separator + relativeDir + File.separator;

            File dir = new File(fullDirPath);
            if (!dir.exists()) dir.mkdirs(); // 폴더 없으면 자동으로 만들어줌

            String originalFilename = file.getOriginalFilename();
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
            String savedName = UUID.randomUUID() + ext; // 파일 이름이 안 겹치게 중복 방지

            File targetFile = new File(fullDirPath + savedName);
            file.transferTo(targetFile); // 실제 파일을 하드디스크에 저장하고

            return "/" + relativeDir + "/" + savedName; // DB에 저장할 경로 반환
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 중 오류 발생", e);
        }
    }
}
