package com.back.admin.org.dto;

import lombok.Data;

/** 학기 라벨 변경 본문 — { "term": "2026-2학기" } */
@Data
public class TermRenameRequest {
    private String term;
}
