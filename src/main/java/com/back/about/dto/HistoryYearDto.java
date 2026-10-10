package com.back.about.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 연혁 한 해 — 연도와 그 해의 항목들(순서대로) */
@Data
@AllArgsConstructor
public class HistoryYearDto {
    private Integer year;
    private List<HistoryItemDto> activities;
}
