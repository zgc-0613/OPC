package com.opc.platform.policyindustrytag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("policy_industry_tags")
public class PolicyIndustryTag {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long policyId;
    private Long industryTagId;
    private LocalDateTime createdAt;
}
