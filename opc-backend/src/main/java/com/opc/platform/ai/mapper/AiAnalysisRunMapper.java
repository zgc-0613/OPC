package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAnalysisRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiAnalysisRunMapper extends BaseMapper<AiAnalysisRun> {

    @Select("""
            SELECT *
            FROM ai_analysis_runs
            WHERE user_id = #{userId}
              AND task_type = #{taskType}
              AND evidence_hash = #{evidenceHash}
              AND status = 'evidence_insufficient'
              AND ((case_id = #{caseId}) OR (case_id IS NULL AND #{caseId} IS NULL))
              AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 SECOND)
            ORDER BY id DESC
            LIMIT 1
            """)
    AiAnalysisRun findRecentEvidenceInsufficient(
            @Param("userId") Long userId,
            @Param("taskType") String taskType,
            @Param("caseId") Long caseId,
            @Param("evidenceHash") String evidenceHash
    );

    @Select("""
            SELECT COALESCE(SUM(total_tokens), 0)
            FROM ai_analysis_runs
            WHERE user_id = #{userId}
              AND status = 'completed'
              AND created_at >= CURRENT_DATE()
            """)
    Long sumCompletedTokensToday(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*)
            FROM ai_analysis_runs
            WHERE user_id = #{userId}
              AND status = 'running'
            """)
    int countRunningForUser(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM ai_analysis_runs
            WHERE session_id=#{sessionId} AND task_type='agent_research'
              AND status IN ('received','running')
            """)
    int countNonTerminalForSession(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT INTO ai_analysis_runs (
                user_id, task_type, case_id, session_id, user_message_id, idempotency_key,
                status, provider, model_id, current_stage, visible_progress,
                prompt_version, evidence_hash, reserved_tokens, started_at,
                deadline_at, heartbeat_at
            )
            SELECT
                #{run.userId}, #{run.taskType}, #{run.caseId}, #{run.sessionId},
                #{run.userMessageId}, #{run.idempotencyKey}, #{run.status},
                #{run.provider}, #{run.modelId}, #{run.currentStage}, #{run.visibleProgress},
                #{run.promptVersion}, #{run.evidenceHash},
                #{reservedTokens}, #{run.startedAt}, #{run.deadlineAt}, #{run.heartbeatAt}
            FROM DUAL
            WHERE #{dailyQuota} = 0 OR (
                SELECT COALESCE(SUM(
                    CASE WHEN status IN ('received','running')
                              OR settlement_status='provider_dispatched'
                         THEN reserved_tokens ELSE total_tokens END
                ), 0)
                FROM ai_analysis_runs
                WHERE user_id = #{run.userId}
                  AND created_at >= CURRENT_DATE()
            ) + #{reservedTokens} <= #{dailyQuota}
            """)
    @Options(useGeneratedKeys = true, keyProperty = "run.id")
    int reserve(
            @Param("run") AiAnalysisRun run,
            @Param("dailyQuota") long dailyQuota,
            @Param("reservedTokens") int reservedTokens
    );

    @Select("""
            SELECT * FROM ai_analysis_runs
            WHERE user_id=#{userId} AND task_type='agent_research' AND idempotency_key=#{idempotencyKey}
            ORDER BY id DESC LIMIT 1
            """)
    AiAnalysisRun findAgentByIdempotency(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Select("""
            SELECT * FROM ai_analysis_runs
            WHERE id=#{id} AND user_id=#{userId} AND task_type='agent_research'
            LIMIT 1
            """)
    AiAnalysisRun selectOwnedAgentRun(@Param("id") Long id, @Param("userId") Long userId);

    @Select("""
            SELECT * FROM ai_analysis_runs
            WHERE id=#{id} AND user_id=#{userId} AND task_type='agent_research'
            FOR UPDATE
            """)
    AiAnalysisRun selectOwnedAgentRunForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM ai_analysis_runs WHERE id=#{id} FOR UPDATE")
    AiAnalysisRun selectRunForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM ai_analysis_runs
            WHERE task_type='agent_research'
              AND execution_attempts < #{maxAttempts}
              AND (next_attempt_at IS NULL OR next_attempt_at <= #{now})
              AND (deadline_at IS NULL OR deadline_at >= #{now})
              AND (
                status='received'
                OR (status='running' AND lease_expires_at IS NOT NULL AND lease_expires_at < #{now})
              )
            ORDER BY COALESCE(next_attempt_at, created_at), id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    AiAnalysisRun selectClaimableAgentRunForUpdate(
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET status='running', current_stage='planning', visible_progress='正在分析需求',
                lease_owner=#{owner}, lease_expires_at=#{leaseExpiresAt}, heartbeat_at=#{now},
                execution_attempts=execution_attempts+1, next_attempt_at=NULL,
                last_recovery_reason=CASE WHEN status='running' THEN 'lease_expired' ELSE 'initial_claim' END,
                started_at=COALESCE(started_at, #{now})
            WHERE id=#{id} AND task_type='agent_research'
              AND execution_attempts < #{maxAttempts}
              AND (next_attempt_at IS NULL OR next_attempt_at <= #{now})
              AND (deadline_at IS NULL OR deadline_at >= #{now})
              AND (
                status='received'
                OR (status='running' AND lease_expires_at IS NOT NULL AND lease_expires_at < #{now})
              )
            """)
    int claimAgentRun(
            @Param("id") Long id,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("maxAttempts") int maxAttempts
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET heartbeat_at=#{now},
                lease_expires_at=LEAST(#{leaseExpiresAt},COALESCE(deadline_at,#{leaseExpiresAt}))
            WHERE id=#{id} AND task_type='agent_research' AND status='running'
              AND lease_owner=#{owner}
              AND (deadline_at IS NULL OR deadline_at >= #{now})
            """)
    int renewAgentLease(
            @Param("id") Long id,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET status=CASE WHEN deadline_at IS NOT NULL AND deadline_at < #{now}
                    THEN 'expired' ELSE 'failed' END,
                current_stage=CASE WHEN deadline_at IS NOT NULL AND deadline_at < #{now}
                    THEN 'expired' ELSE 'failed' END,
                visible_progress=CASE WHEN deadline_at IS NOT NULL AND deadline_at < #{now}
                    THEN '研究运行已过期' ELSE '研究运行恢复次数已用尽' END,
                error_type=CASE WHEN deadline_at IS NOT NULL AND deadline_at < #{now}
                    THEN 'TASK_TIMEOUT' ELSE 'RECOVERY_EXHAUSTED' END,
                diagnostic_code=CASE WHEN deadline_at IS NOT NULL AND deadline_at < #{now}
                    THEN 'AGENT_TIMEOUT' ELSE 'AGENT_RECOVERY_EXHAUSTED' END,
                prompt_tokens=CASE
                  WHEN settlement_status='provider_dispatched' AND total_tokens < reserved_tokens
                  THEN prompt_tokens + (reserved_tokens-total_tokens) ELSE prompt_tokens END,
                total_tokens=CASE WHEN settlement_status='provider_dispatched'
                  THEN GREATEST(total_tokens,reserved_tokens) ELSE total_tokens END,
                settlement_status=CASE WHEN settlement_status='provider_dispatched'
                  THEN 'settled_estimated' WHEN settlement_status='settled_actual'
                  THEN 'settled_actual' ELSE 'released_without_dispatch' END,
                settled_at=COALESCE(settled_at,#{now}), reserved_tokens=0,
                lease_owner=NULL, lease_expires_at=NULL, completed_at=#{now}, heartbeat_at=#{now},
                settlement_version=settlement_version+1,
                last_recovery_reason=CASE WHEN deadline_at IS NOT NULL AND deadline_at < #{now}
                  THEN 'deadline_expired' ELSE 'max_attempts_exhausted' END
            WHERE task_type='agent_research' AND status IN ('received','running')
              AND (
                (deadline_at IS NOT NULL AND deadline_at < #{now})
                OR (execution_attempts >= #{maxAttempts} AND (
                  status='received' OR lease_expires_at IS NULL OR lease_expires_at < #{now}
                ))
              )
            """)
    int finalizeUnrecoverableAgentRuns(
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts
    );

    @Select("""
            SELECT * FROM ai_analysis_runs
            WHERE session_id=#{sessionId} AND task_type='agent_research'
            ORDER BY id DESC LIMIT 1
            """)
    AiAnalysisRun selectLatestAgentRunForSession(@Param("sessionId") Long sessionId);

    @Update("""
            UPDATE ai_analysis_runs
            SET current_stage=#{stage}, visible_progress=#{progress}, heartbeat_at=#{now},
                step_count=#{stepCount}, tool_call_count=#{toolCallCount}
            WHERE id=#{id} AND status='running'
              AND (deadline_at IS NULL OR deadline_at >= #{now})
            """)
    int updateAgentStage(
            @Param("id") Long id,
            @Param("stage") String stage,
            @Param("progress") String progress,
            @Param("stepCount") int stepCount,
            @Param("toolCallCount") int toolCallCount,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET prompt_tokens=prompt_tokens+#{promptTokens},
                completion_tokens=completion_tokens+#{completionTokens},
                total_tokens=total_tokens+#{totalTokens},
                latency_ms=latency_ms+#{latencyMs},
                provider_request_id=#{providerRequestId}, finish_reason=#{finishReason},
                heartbeat_at=#{now}
            WHERE id=#{id} AND status='running'
              AND (deadline_at IS NULL OR deadline_at >= #{now})
            """)
    int recordAgentUsage(
            @Param("id") Long id,
            @Param("promptTokens") int promptTokens,
            @Param("completionTokens") int completionTokens,
            @Param("totalTokens") int totalTokens,
            @Param("latencyMs") long latencyMs,
            @Param("providerRequestId") String providerRequestId,
            @Param("finishReason") String finishReason,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET settlement_status='provider_dispatched', provider_dispatched_at=#{now},
                settlement_version=settlement_version+1
            WHERE id=#{id} AND task_type='agent_research' AND status='running'
              AND settlement_status IN ('reserved','settled_actual')
            """)
    int markAgentProviderDispatched(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_analysis_runs
            SET prompt_tokens=prompt_tokens+#{promptTokens},
                completion_tokens=completion_tokens+#{completionTokens},
                total_tokens=total_tokens+#{totalTokens},
                latency_ms=latency_ms+#{latencyMs},
                provider_request_id=#{providerRequestId}, finish_reason=#{finishReason},
                settlement_status='settled_actual', settled_at=#{now},
                reserved_tokens=CASE WHEN status='running' THEN reserved_tokens ELSE 0 END,
                settlement_version=settlement_version+1, heartbeat_at=#{now}
            WHERE id=#{id} AND task_type='agent_research'
              AND settlement_status='provider_dispatched'
            """)
    int settleAgentUsageActual(
            @Param("id") Long id,
            @Param("promptTokens") int promptTokens,
            @Param("completionTokens") int completionTokens,
            @Param("totalTokens") int totalTokens,
            @Param("latencyMs") long latencyMs,
            @Param("providerRequestId") String providerRequestId,
            @Param("finishReason") String finishReason,
            @Param("now") LocalDateTime now
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET prompt_tokens=(SELECT COALESCE(SUM(prompt_tokens),0) FROM ai_agent_provider_calls
                    WHERE analysis_run_id=#{id} AND settlement_status IN ('settled_actual','settled_estimated')),
                completion_tokens=(SELECT COALESCE(SUM(completion_tokens),0) FROM ai_agent_provider_calls
                    WHERE analysis_run_id=#{id} AND settlement_status IN ('settled_actual','settled_estimated')),
                total_tokens=(SELECT COALESCE(SUM(total_tokens),0) FROM ai_agent_provider_calls
                    WHERE analysis_run_id=#{id} AND settlement_status IN ('settled_actual','settled_estimated')),
                latency_ms=(SELECT COALESCE(SUM(latency_ms),0) FROM ai_agent_provider_calls
                    WHERE analysis_run_id=#{id} AND settlement_status IN ('settled_actual','settled_estimated')),
                settlement_status=CASE WHEN EXISTS (
                    SELECT 1 FROM ai_agent_provider_calls
                    WHERE analysis_run_id=#{id} AND settlement_status='settled_estimated'
                  ) THEN 'settled_estimated' ELSE 'settled_actual' END,
                settled_at=#{now}, reserved_tokens=0,
                settlement_version=settlement_version+1
            WHERE id=#{id} AND task_type='agent_research'
            """)
    int reconcileAgentProviderUsage(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_analysis_runs
            SET status=#{status}, current_stage=#{status}, visible_progress=#{status},
                prompt_tokens=#{promptTokens}, completion_tokens=#{completionTokens}, total_tokens=#{totalTokens},
                reserved_tokens=0, latency_ms=#{latencyMs}, provider_request_id=#{providerRequestId},
                finish_reason=#{finishReason}, step_count=#{stepCount}, tool_call_count=#{toolCallCount},
                result_json=#{resultJson}, completed_at=#{completedAt}, heartbeat_at=#{completedAt}
            WHERE id=#{id} AND status='running'
            """)
    int settleAgentCompleted(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("promptTokens") int promptTokens,
            @Param("completionTokens") int completionTokens,
            @Param("totalTokens") int totalTokens,
            @Param("latencyMs") long latencyMs,
            @Param("providerRequestId") String providerRequestId,
            @Param("finishReason") String finishReason,
            @Param("stepCount") int stepCount,
            @Param("toolCallCount") int toolCallCount,
            @Param("resultJson") String resultJson,
            @Param("completedAt") LocalDateTime completedAt
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET status=#{status}, current_stage=#{status}, visible_progress=#{progress},
                error_type=#{errorType}, diagnostic_code=#{diagnosticCode},
                prompt_tokens=CASE
                  WHEN settlement_status='provider_dispatched' AND total_tokens < reserved_tokens
                  THEN prompt_tokens + (reserved_tokens - total_tokens)
                  ELSE prompt_tokens END,
                total_tokens=CASE WHEN settlement_status='provider_dispatched'
                  THEN GREATEST(total_tokens, reserved_tokens) ELSE total_tokens END,
                settlement_status=CASE
                  WHEN settlement_status='provider_dispatched' THEN 'settled_estimated'
                  WHEN settlement_status='settled_actual' THEN 'settled_actual'
                  ELSE 'released_without_dispatch' END,
                settled_at=COALESCE(settled_at, #{completedAt}), reserved_tokens=0,
                lease_owner=NULL, lease_expires_at=NULL,
                settlement_version=settlement_version+1,
                step_count=#{stepCount}, tool_call_count=#{toolCallCount},
                completed_at=#{completedAt}, heartbeat_at=#{completedAt}
            WHERE id=#{id} AND status='running'
            """)
    int settleAgentFailed(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("progress") String progress,
            @Param("errorType") String errorType,
            @Param("diagnosticCode") String diagnosticCode,
            @Param("stepCount") int stepCount,
            @Param("toolCallCount") int toolCallCount,
            @Param("completedAt") LocalDateTime completedAt
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET status='cancelled', current_stage='cancelled', visible_progress='已取消运行',
                settlement_status=CASE WHEN settlement_status='provider_dispatched'
                    THEN 'provider_dispatched' ELSE 'released_without_dispatch' END,
                reserved_tokens=CASE WHEN settlement_status='provider_dispatched'
                    THEN reserved_tokens ELSE 0 END,
                settled_at=CASE WHEN settlement_status='provider_dispatched' THEN settled_at ELSE #{now} END,
                lease_owner=NULL, lease_expires_at=NULL,
                cancelled_at=#{now}, completed_at=#{now}, heartbeat_at=#{now},
                settlement_version=settlement_version+1
            WHERE id=#{id} AND user_id=#{userId} AND task_type='agent_research'
              AND status IN ('received','running')
            """)
    int cancelOwnedAgentRun(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_analysis_runs
            SET status = #{status},
                error_type = #{errorType},
                prompt_tokens = #{promptTokens},
                completion_tokens = #{completionTokens},
                total_tokens = #{totalTokens},
                reserved_tokens = 0,
                latency_ms = #{latencyMs},
                provider_request_id = #{providerRequestId},
                finish_reason = #{finishReason},
                response_hash = #{responseHash},
                diagnostic_code = #{diagnosticCode},
                result_json = #{resultJson},
                heartbeat_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND status = 'running'
              AND (deadline_at IS NULL OR deadline_at >= #{settledAt})
            """)
    int settle(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("errorType") String errorType,
            @Param("diagnosticCode") String diagnosticCode,
            @Param("promptTokens") int promptTokens,
            @Param("completionTokens") int completionTokens,
            @Param("totalTokens") int totalTokens,
            @Param("latencyMs") long latencyMs,
            @Param("providerRequestId") String providerRequestId,
            @Param("finishReason") String finishReason,
            @Param("responseHash") String responseHash,
            @Param("resultJson") String resultJson,
            @Param("settledAt") LocalDateTime settledAt
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET status = CASE WHEN task_type='agent_research' THEN 'expired' ELSE 'failed' END,
                current_stage = CASE WHEN task_type='agent_research' THEN 'expired' ELSE current_stage END,
                visible_progress = CASE WHEN task_type='agent_research' THEN '研究运行已过期' ELSE visible_progress END,
                error_type = 'TASK_TIMEOUT',
                diagnostic_code = CASE WHEN task_type='agent_research' THEN 'AGENT_TIMEOUT' ELSE diagnostic_code END,
                prompt_tokens = CASE WHEN total_tokens = 0 THEN LEAST(reserved_tokens, 2147483647) ELSE prompt_tokens END,
                total_tokens = CASE WHEN total_tokens = 0 THEN LEAST(reserved_tokens, 2147483647) ELSE total_tokens END,
                reserved_tokens = 0,
                heartbeat_at = #{now},
                completed_at = CASE WHEN task_type='agent_research' THEN #{now} ELSE completed_at END
            WHERE id = #{id}
              AND status = 'running'
              AND deadline_at IS NOT NULL
              AND deadline_at < #{now}
            """)
    int failExpiredRun(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_analysis_runs
            SET status = CASE WHEN task_type='agent_research' THEN 'expired' ELSE 'failed' END,
                current_stage = CASE WHEN task_type='agent_research' THEN 'expired' ELSE current_stage END,
                visible_progress = CASE WHEN task_type='agent_research' THEN '研究运行已过期' ELSE visible_progress END,
                error_type = 'TASK_TIMEOUT',
                diagnostic_code = CASE WHEN task_type='agent_research' THEN 'AGENT_TIMEOUT' ELSE diagnostic_code END,
                prompt_tokens = CASE WHEN total_tokens = 0 THEN LEAST(reserved_tokens, 2147483647) ELSE prompt_tokens END,
                total_tokens = CASE WHEN total_tokens = 0 THEN LEAST(reserved_tokens, 2147483647) ELSE total_tokens END,
                reserved_tokens = 0,
                heartbeat_at = #{now},
                completed_at = CASE WHEN task_type='agent_research' THEN #{now} ELSE completed_at END
            WHERE status = 'running'
              AND deadline_at IS NOT NULL
              AND deadline_at < #{now}
            """)
    int failExpiredRunning(@Param("now") LocalDateTime now);
}
