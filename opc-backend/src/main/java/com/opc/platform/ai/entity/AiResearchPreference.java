package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_research_preferences")
public class AiResearchPreference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Boolean memoryEnabled;
    private String commonRegion;
    private String commonIndustry;
    private String technologyDirection;
    private String ventureStage;
    private String budgetRange;
    private String teamCapabilities;
    private String existingResources;
    private String policyFocus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
