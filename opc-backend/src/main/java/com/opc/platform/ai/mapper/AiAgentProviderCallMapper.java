package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAgentProviderCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiAgentProviderCallMapper extends BaseMapper<AiAgentProviderCall> {

    @Insert("""
            INSERT INTO ai_agent_provider_calls (
              analysis_run_id,round_no,internal_request_id,settlement_status,reserved_tokens,
              prompt_tokens,completion_tokens,total_tokens,latency_ms,dispatched_at
            )
            SELECT #{call.analysisRunId},#{call.roundNo},#{call.internalRequestId},#{call.settlementStatus},
                   #{call.reservedTokens},#{call.promptTokens},#{call.completionTokens},#{call.totalTokens},
                   #{call.latencyMs},#{call.dispatchedAt}
            FROM ai_analysis_runs r
            WHERE r.id=#{call.analysisRunId} AND r.task_type='agent_research' AND r.status='running'
              AND r.lease_owner=#{leaseOwner} AND r.execution_attempts=#{executionAttempt}
              AND r.lease_expires_at IS NOT NULL AND r.lease_expires_at >= #{now}
              AND (r.deadline_at IS NULL OR r.deadline_at >= #{now})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "call.id")
    int insertGuardedFenced(
            @Param("call") AiAgentProviderCall call,
            @Param("leaseOwner") String leaseOwner,
            @Param("executionAttempt") int executionAttempt,
            @Param("now") LocalDateTime now
    );

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

    @Update("""
            UPDATE ai_agent_provider_calls pc
            JOIN ai_analysis_runs r ON r.id=pc.analysis_run_id
            SET pc.settlement_status='settled_actual', pc.prompt_tokens=#{promptTokens},
                pc.completion_tokens=#{completionTokens}, pc.total_tokens=#{totalTokens},
                pc.latency_ms=#{latencyMs}, pc.provider_request_id=#{providerRequestId},
                pc.finish_reason=#{finishReason}, pc.settled_at=#{settledAt}
            WHERE pc.id=#{id} AND pc.settlement_status='provider_dispatched'
              AND r.task_type='agent_research' AND r.status='running'
              AND r.lease_owner=#{leaseOwner} AND r.execution_attempts=#{executionAttempt}
              AND r.lease_expires_at IS NOT NULL AND r.lease_expires_at >= #{settledAt}
              AND (r.deadline_at IS NULL OR r.deadline_at >= #{settledAt})
            """)
    int settleActualFenced(
            @Param("id") Long id,
            @Param("leaseOwner") String leaseOwner,
            @Param("executionAttempt") int executionAttempt,
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

    @Update("""
            UPDATE ai_agent_provider_calls pc
            JOIN ai_analysis_runs r ON r.id=pc.analysis_run_id
            SET pc.settlement_status='settled_estimated', pc.prompt_tokens=pc.reserved_tokens,
                pc.completion_tokens=0, pc.total_tokens=pc.reserved_tokens, pc.settled_at=#{settledAt}
            WHERE pc.analysis_run_id=#{runId} AND pc.settlement_status='provider_dispatched'
              AND r.task_type='agent_research' AND r.status='running'
              AND r.lease_owner=#{leaseOwner} AND r.execution_attempts=#{executionAttempt}
              AND r.lease_expires_at IS NOT NULL AND r.lease_expires_at >= #{settledAt}
              AND (r.deadline_at IS NULL OR r.deadline_at >= #{settledAt})
            """)
    int markDispatchedEstimatedFenced(
            @Param("runId") Long runId,
            @Param("leaseOwner") String leaseOwner,
            @Param("executionAttempt") int executionAttempt,
            @Param("settledAt") LocalDateTime settledAt
    );

    @Select("SELECT COALESCE(MAX(round_no),0) FROM ai_agent_provider_calls WHERE analysis_run_id=#{runId}")
    int selectMaxRoundNo(@Param("runId") Long runId);
}
