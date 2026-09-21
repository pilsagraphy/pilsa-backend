package com.back.global.util;

/**
 * 마크다운 본문을 알림 미리보기·구글 캘린더·ICS 에 실을 **평문**으로 바꾼다.
 *
 * 글 본문과 일정 세부 사항은 편집기(Tiptap)가 마크다운으로 저장한다. 그대로 실으면 `**굵게**`, `## 제목`,
 * `<span style="…">` 이 글자로 보인다. 완전한 파서 대신 자주 쓰는 표기만 걷어내는 정도면 충분하다 (2026-09-21).
 */
public final class MarkdownText {

    private MarkdownText() {
    }

    public static String toPlain(String markdown) {
        if (markdown == null) return "";
        String s = markdown;
        s = s.replaceAll("(?s)```.*?```", " ");                 // 코드 블록
        s = s.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ");        // 이미지
        s = s.replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1");        // 링크 → 글자만
        s = s.replaceAll("<[^>]+>", "");                          // HTML 태그(색 span 등)
        s = s.replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "");           // 제목 기호
        s = s.replaceAll("(?m)^\\s*(?:[-*+]|\\d+\\.)\\s+", "• ");   // 목록 기호
        s = s.replaceAll("(?m)^\\s*>\\s?", "");                    // 인용
        s = s.replaceAll("(?m)^\\s*(?:---|\\*\\*\\*|___)\\s*$", ""); // 수평선
        s = s.replaceAll("(\\*{1,3}|_{1,3}|~~|`)(.+?)\\1", "$2");   // 굵게·기울임·취소·코드
        s = s.replaceAll("\\\\([\\\\`*_\\[\\]~#])", "$1");         // 이스케이프 풀기
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.strip();
    }

    /** 한 줄 미리보기 — 줄바꿈을 공백으로 접고 max 자를 넘으면 … */
    public static String preview(String markdown, int max) {
        String plain = toPlain(markdown).replaceAll("\\s+", " ").strip();
        if (plain.length() <= max) return plain;
        return plain.substring(0, Math.max(0, max - 1)) + "…";
    }
}
