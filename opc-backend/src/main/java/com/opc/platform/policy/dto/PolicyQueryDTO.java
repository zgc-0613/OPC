package com.opc.platform.policy.dto;

import lombok.Data;

@Data
public class PolicyQueryDTO {

    private String keyword;

    private Long regionId;

    private String policyType;

    private String status;
}
