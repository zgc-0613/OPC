package com.opc.platform.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminAgentQualityMapper {

    @Select("""
            <script>
            SELECT r.requested_intent AS taskType, r.model_id AS model, r.prompt_version AS promptVersion,
                   r.status, COALESCE(r.diagnostic_code,'') AS diagnosticCode,
                   COUNT(*) AS runCount, COALESCE(SUM(r.total_tokens),0) AS totalTokens,
                   COALESCE(SUM(r.latency_ms),0) AS latencyMs,
                   COALESCE(SUM(r.tool_call_count),0) AS toolCallCount
            FROM ai_analysis_runs r
            WHERE r.task_type='agent_research'
            <if test="dateFrom != null">AND r.created_at &gt;= #{dateFrom}</if>
            <if test="dateTo != null">AND r.created_at &lt; #{dateTo}</if>
            <if test="taskType != null and taskType != ''">AND r.requested_intent=#{taskType}</if>
            <if test="model != null and model != ''">AND r.model_id=#{model}</if>
            <if test="promptVersion != null and promptVersion != ''">AND r.prompt_version=#{promptVersion}</if>
            GROUP BY r.requested_intent, r.model_id, r.prompt_version, r.status, r.diagnostic_code
            ORDER BY r.requested_intent, r.model_id, r.prompt_version, r.status, r.diagnostic_code
            </script>
            """)
    List<AdminAgentQualityRunRow> selectRunRows(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("taskType") String taskType,
            @Param("model") String model,
            @Param("promptVersion") String promptVersion
    );

    @Select("""
            <script>
            SELECT f.rating, f.reason, COUNT(*) AS feedbackCount
            FROM ai_agent_run_feedback f
            INNER JOIN ai_analysis_runs r ON r.id=f.run_id AND r.user_id=f.user_id
            WHERE r.task_type='agent_research'
            <if test="dateFrom != null">AND r.created_at &gt;= #{dateFrom}</if>
            <if test="dateTo != null">AND r.created_at &lt; #{dateTo}</if>
            <if test="taskType != null and taskType != ''">AND r.requested_intent=#{taskType}</if>
            <if test="model != null and model != ''">AND r.model_id=#{model}</if>
            <if test="promptVersion != null and promptVersion != ''">AND r.prompt_version=#{promptVersion}</if>
            GROUP BY f.rating, f.reason
            ORDER BY f.rating, f.reason
            </script>
            """)
    List<AdminAgentQualityFeedbackRow> selectFeedbackRows(
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("taskType") String taskType,
            @Param("model") String model,
            @Param("promptVersion") String promptVersion
    );
}
