package com.opc.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Immutable, user-owned server reconstruction of an analytics view. */
@Data
@TableName("ai_analytics_snapshots")
public class AiAnalyticsSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String idempotencyKey;
    private String requestHash;
    private String metricId;
    @TableField("normalized_filters_json")
    private String filtersJson;
    private String selectedDimension;
    private String selectedBucketIdsJson;
    private String dataVersion;
    @TableField("snapshot_json")
    private String payloadJson;
    private String snapshotHash;
    private Long runId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
