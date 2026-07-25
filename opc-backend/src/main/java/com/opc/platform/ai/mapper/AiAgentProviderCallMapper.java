package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAgentProviderCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiAgentProviderCallMapper extends BaseMapper<AiAgentProviderCall> {

    @Update("""
            UPDATE ai_agent_provider_calls
            SET settlement_status='settled_actual', prompt_tokens=#{promptTokens},
                completion_tokens=#{completionTokens}, total_tokens=#{totalTokens},
                latency_ms=#{latencyMs}, provider_request_id=#{providerRequestId},
                finish_reason=#{finishReason}, settled_at=#{settledAt}
            WHERE id=#{id} AND settlement_status='provider_dispatched'
            """)
    int settleActual(
            @Param("id") Long id,
            @Param("promptTokens") int promptTokens,
            @Param("completionTokens") int completionTokens,
            @Param("totalTokens") int totalTokens,
            @Param("latencyMs") long latencyMs,
            @Param("providerRequestId") String providerRequestId,
            @Param("finishReason") String finishReason,
            @Param("settledAt") LocalDateTime settledAt
    );

    @Select("SELECT * FROM ai_agent_provider_calls WHERE id=#{id} FOR UPDATE")
    AiAgentProviderCall selectForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE ai_agent_provider_calls
            SET settlement_status='settled_actual', prompt_tokens=#{promptTokens},
                completion_tokens=#{completionTokens}, total_tokens=#{totalTokens},
                latency_ms=#{latencyMs}, provider_request_id=#{providerRequestId},
                finish_reason=#{finishReason}, settled_at=#{settledAt}
            WHERE id=#{id} AND settlement_status='settled_estimated'
            """)
    int replaceEstimateWithActual(
            @Param("id") Long id,
            @Param("promptTokens") int promptTokens,
            @Param("completionTokens") int completionTokens,
            @Param("totalTokens") int totalTokens,
            @Param("latencyMs") long latencyMs,
            @Param("providerRequestId") String providerRequestId,
            @Param("finishReason") String finishReason,
            @Param("settledAt") LocalDateTime settledAt
    );

    @Update("""
            UPDATE ai_agent_provider_calls
            SET settlement_status='settled_estimated', prompt_tokens=reserved_tokens,
                completion_tokens=0, total_tokens=reserved_tokens, settled_at=#{settledAt}
            WHERE analysis_run_id=#{runId} AND settlement_status='provider_dispatched'
            """)
    int markDispatchedEstimated(
            @Param("runId") Long runId,
            @Param("settledAt") LocalDateTime settledAt
    );
}
