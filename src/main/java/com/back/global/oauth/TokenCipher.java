package com.back.global.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 구글 refresh token 암호화 (AES-256-GCM).
 *
 * refresh token 은 사실상 무기한 유효한 자격증명이라 DB 에 평문으로 두면 안 된다.
 * DB 가 통째로 유출돼도 암호화 키(환경변수)가 없으면 쓸 수 없게 한다.
 *
 * 저장 형식: [IV 12바이트][암호문+GCM태그]  → varbinary(512)
 * GCM 을 쓰는 이유는 무결성까지 같이 검증되기 때문이다(변조된 값이면 복호화가 실패한다).
 */
@Component
@RequiredArgsConstructor
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;      // GCM 권장 IV 길이
    private static final int TAG_LENGTH_BIT = 128;

    private final GoogleProperties properties;
    private final SecureRandom random = new SecureRandom();

    public byte[] encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // IV 를 앞에 붙여 함께 저장한다 (IV 는 비밀이 아니어도 되지만 복호화에 필요하다)
            byte[] result = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("구글 토큰 암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(byte[] stored) {
        if (stored == null || stored.length <= IV_LENGTH) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(stored, 0, iv, 0, IV_LENGTH);

            byte[] cipherText = new byte[stored.length - IV_LENGTH];
            System.arraycopy(stored, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherText), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 키를 교체했거나 값이 변조된 경우. 토큰을 되살릴 방법이 없으므로 재연동을 유도해야 한다.
            throw new IllegalStateException("구글 토큰 복호화에 실패했습니다. 재연동이 필요합니다.", e);
        }
    }

    private SecretKeySpec secretKey() {
        byte[] key = Base64.getDecoder().decode(properties.getTokenCipherKey());
        if (key.length != 32) {
            throw new IllegalStateException("google.token.cipher-key 는 Base64 로 인코딩된 32바이트여야 합니다. (현재 " + key.length + "바이트)");
        }
        return new SecretKeySpec(key, "AES");
    }
}
