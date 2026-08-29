package com.back.auth.social.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 구글 계정 연결 상태 (마이페이지). */
@Getter
@AllArgsConstructor
public class GoogleLinkStatusResponse {
    private boolean linked;
    private String googleEmail;
    private String linkedAt;

    public static GoogleLinkStatusResponse notLinked() {
        return new GoogleLinkStatusResponse(false, null, null);
    }
}
