package com.back.auth.social.exception;

import com.back.global.oauth.GoogleIntegrationException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 구글 인증은 됐는데 그 구글 계정에 연결된 회원이 없다.
 *
 * 단순 실패가 아니라 "이제 로그인하면 연결해 주겠다"는 다음 단계로 이어지므로,
 * 콜백이 그 안내에 필요한 것들을 함께 들고 간다.
 */
@Getter
public class GoogleAccountNotLinkedException extends GoogleIntegrationException {

    /** 연결 대기 토큰 — 쿠키로 브라우저에 묶어 내려간다 */
    private final String pendingToken;

    /** 구글 계정 이메일과 같은 이메일로 가입된 회원이 있는가 (안내 문구 분기용) */
    private final boolean emailMatched;

    /** 화면에 보여줄 마스킹된 구글 이메일 (q***7@gmail.com) */
    private final String maskedEmail;

    public GoogleAccountNotLinkedException(String pendingToken, boolean emailMatched, String maskedEmail) {
        super("연결된 계정이 없습니다. 로그인하면 이 구글 계정을 연결할 수 있습니다.", HttpStatus.CONFLICT);
        this.pendingToken = pendingToken;
        this.emailMatched = emailMatched;
        this.maskedEmail = maskedEmail;
    }
}
