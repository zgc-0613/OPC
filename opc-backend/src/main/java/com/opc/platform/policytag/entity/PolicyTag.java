package com.opc.platform.policytag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("policy_tags")
public class PolicyTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long policyId;

    private Long tagId;

    private LocalDateTime createdAt;
}
