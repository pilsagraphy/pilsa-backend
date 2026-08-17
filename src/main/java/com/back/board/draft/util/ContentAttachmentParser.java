package com.back.board.draft.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 본문(content)에서 참조 중인 첨부(이미지) attachment_id 집합을 뽑아내는 헬퍼.
 *
 * 리치 에디터는 본문 이미지를 <img src="/files/{attachmentId}"> 로 심는다. 이미지 URL 이
 * 소유자(draft/post)와 무관하게 안정적인 /files/{id} 형식이라, 발행으로 소유권이 바뀌어도
 * 본문 URL 이 깨지지 않고 여기서 곧바로 id 를 복원할 수 있다.
 *
 * 정규식은 HTML 태그 구조가 아니라 **URL 패턴 자체**(/files/{숫자})를 잡으므로,
 * <img src="...">, 마크다운 ![](...), 순수 URL 어느 표기로 와도 동일하게 동작한다.
 * (프론트가 마크다운/HTML 어느 쪽을 쓰더라도 재조정이 깨지지 않게 하기 위한 관용 처리)
 */
public final class ContentAttachmentParser {

    // /files/123 · /files/123.png · "/files/123?x=1" 등에서 123 만 캡처.
    // 앞은 경로 경계(따옴표/괄호/공백/시작), 뒤는 숫자 경계까지.
    private static final Pattern FILE_REF = Pattern.compile("/files/(\\d+)");

    private ContentAttachmentParser() {
    }

    /**
     * content 에서 참조하는 attachment_id 를 등장 순서대로(중복 제거) 반환한다.
     * content 가 null/빈 문자열이면 빈 집합.
     */
    public static Set<Long> extractAttachmentIds(String content) {
        Set<Long> ids = new LinkedHashSet<>();
        if (content == null || content.isEmpty()) {
            return ids;
        }
        Matcher m = FILE_REF.matcher(content);
        while (m.find()) {
            try {
                ids.add(Long.parseLong(m.group(1)));
            } catch (NumberFormatException ignored) {
                // 숫자 범위를 넘는 비정상 값은 무시 (참조로 치지 않음)
            }
        }
        return ids;
    }
}
