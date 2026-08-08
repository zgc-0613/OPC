package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAnalyticsSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiAnalyticsSnapshotMapper extends BaseMapper<AiAnalyticsSnapshot> {

    @Select("""
            SELECT * FROM ai_analytics_snapshots
            WHERE user_id=#{userId} AND idempotency_key=#{idempotencyKey}
            LIMIT 1
            """)
    AiAnalyticsSnapshot findByUserAndIdempotency(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}
