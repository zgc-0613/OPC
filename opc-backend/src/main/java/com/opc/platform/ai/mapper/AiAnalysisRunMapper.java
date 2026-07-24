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

    @Insert("""
            INSERT INTO ai_analysis_runs (
                user_id, task_type, case_id, status, provider, model_id,
                prompt_version, evidence_hash, reserved_tokens, started_at,
                deadline_at, heartbeat_at
            )
            SELECT
                #{run.userId}, #{run.taskType}, #{run.caseId}, 'running',
                #{run.provider}, #{run.modelId}, #{run.promptVersion}, #{run.evidenceHash},
                #{reservedTokens}, #{run.startedAt}, #{run.deadlineAt}, #{run.heartbeatAt}
            FROM DUAL
            WHERE #{dailyQuota} = 0 OR (
                SELECT COALESCE(SUM(
                    CASE WHEN status = 'running' THEN reserved_tokens ELSE total_tokens END
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
                result_json = #{resultJson},
                heartbeat_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int settle(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("errorType") String errorType,
            @Param("promptTokens") int promptTokens,
            @Param("completionTokens") int completionTokens,
            @Param("totalTokens") int totalTokens,
            @Param("latencyMs") long latencyMs,
            @Param("providerRequestId") String providerRequestId,
            @Param("resultJson") String resultJson
    );

    @Update("""
            UPDATE ai_analysis_runs
            SET status = 'failed',
                error_type = 'TASK_TIMEOUT',
                reserved_tokens = 0,
                heartbeat_at = #{now}
            WHERE status = 'running'
              AND deadline_at IS NOT NULL
              AND deadline_at < #{now}
            """)
    int failExpiredRunning(@Param("now") LocalDateTime now);
}
