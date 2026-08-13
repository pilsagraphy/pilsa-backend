package com.back.sanction.dto;

import lombok.Data;

@Data
public class BanPolicyDto {
    private Long banPolicyId;
    private String code;
    private Integer warningNo;
    private String banType;
    private Integer banDays;
    private String description;
}
