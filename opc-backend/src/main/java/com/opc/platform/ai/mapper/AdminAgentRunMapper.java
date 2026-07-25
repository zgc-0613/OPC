package com.opc.platform.ai.mapper;

import com.opc.platform.ai.vo.AdminAgentRunRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminAgentRunMapper {

    @Select("""
            SELECT r.id AS runId,
                   CASE WHEN u.username IS NULL THEN CONCAT('user#',r.user_id)
                        WHEN CHAR_LENGTH(u.username) <= 2 THEN CONCAT(LEFT(u.username,1),'***')
                        ELSE CONCAT(LEFT(u.username,2),'***',RIGHT(u.username,1)) END AS maskedUser,
                   r.session_id AS sessionId, r.status, r.provider, r.model_id AS model,
                   r.step_count AS modelRounds, r.tool_call_count AS toolCallCount,
                   r.prompt_tokens AS promptTokens, r.completion_tokens AS completionTokens,
                   r.total_tokens AS totalTokens, r.latency_ms AS latencyMs,
                   r.finish_reason AS finishReason, r.diagnostic_code AS diagnosticCode,
                   r.provider_request_id AS requestId, r.created_at AS createdAt,
                   r.completed_at AS completedAt
            FROM ai_analysis_runs r
            LEFT JOIN platform_users u ON u.id=r.user_id
            WHERE r.task_type='agent_research'
            ORDER BY r.id DESC
            LIMIT #{limit}
            """)
    List<AdminAgentRunRowVO> selectRecent(@Param("limit") int limit);

    @Select("""
            SELECT r.id AS runId,
                   CASE WHEN u.username IS NULL THEN CONCAT('user#',r.user_id)
                        WHEN CHAR_LENGTH(u.username) <= 2 THEN CONCAT(LEFT(u.username,1),'***')
                        ELSE CONCAT(LEFT(u.username,2),'***',RIGHT(u.username,1)) END AS maskedUser,
                   r.session_id AS sessionId, r.status, r.provider, r.model_id AS model,
                   r.step_count AS modelRounds, r.tool_call_count AS toolCallCount,
                   r.prompt_tokens AS promptTokens, r.completion_tokens AS completionTokens,
                   r.total_tokens AS totalTokens, r.latency_ms AS latencyMs,
                   r.finish_reason AS finishReason, r.diagnostic_code AS diagnosticCode,
                   r.provider_request_id AS requestId, r.created_at AS createdAt,
                   r.completed_at AS completedAt
            FROM ai_analysis_runs r
            LEFT JOIN platform_users u ON u.id=r.user_id
            WHERE r.id=#{runId} AND r.task_type='agent_research'
            LIMIT 1
            """)
    AdminAgentRunRowVO selectRun(@Param("runId") Long runId);
}
