package com.opc.platform.casetag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_tags")
public class CaseTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long caseId;

    private Long tagId;

    private LocalDateTime createdAt;
}
