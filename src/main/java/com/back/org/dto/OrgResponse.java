package com.back.org.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 조직 전체 — 역대 회장(기수 순) + 각 기수의 학기별 임원진.
 * 소개 페이지 조직도는 마지막 기수의 마지막 학기(currentTerm)를 그린다.
 */
@Data
@AllArgsConstructor
public class OrgResponse {
    private String currentTerm;
    private List<OrgPresidentDto> presidents;
}
