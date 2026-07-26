package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiAgentToolCallMapper extends BaseMapper<AiAgentToolCall> {

    @Insert("""
            INSERT INTO ai_agent_tool_calls (
              analysis_run_id,step_no,tool_name,arguments_json,status,evidence_count,latency_ms
            )
            SELECT #{call.analysisRunId},#{call.stepNo},#{call.toolName},#{call.argumentsJson},
                   #{call.status},#{call.evidenceCount},#{call.latencyMs}
            FROM ai_analysis_runs r
            JOIN ai_agent_sessions s ON s.id=r.session_id
            WHERE r.id=#{call.analysisRunId} AND r.task_type='agent_research' AND r.status='running'
              AND ((r.lease_owner=#{leaseOwner}) OR (r.lease_owner IS NULL AND #{leaseOwner} IS NULL))
              AND s.purged_at IS NULL
              AND r.session_content_generation=s.content_generation
            """)
    @Options(useGeneratedKeys = true, keyProperty = "call.id")
    int insertGuarded(@Param("call") AiAgentToolCall call, @Param("leaseOwner") String leaseOwner);

    @Update("""
            UPDATE ai_agent_tool_calls tc
            JOIN ai_analysis_runs r ON r.id=tc.analysis_run_id
            JOIN ai_agent_sessions s ON s.id=r.session_id
            SET tc.arguments_json=#{call.argumentsJson},
                tc.result_summary_json=#{call.resultSummaryJson},
                tc.status=#{call.status}, tc.evidence_hash=#{call.evidenceHash},
                tc.evidence_count=#{call.evidenceCount}, tc.latency_ms=#{call.latencyMs},
                tc.diagnostic_code=#{call.diagnosticCode}, tc.started_at=#{call.startedAt},
                tc.completed_at=#{call.completedAt}
            WHERE tc.id=#{call.id} AND r.task_type='agent_research' AND r.status='running'
              AND ((r.lease_owner=#{leaseOwner}) OR (r.lease_owner IS NULL AND #{leaseOwner} IS NULL))
              AND s.purged_at IS NULL
              AND r.session_content_generation=s.content_generation
            """)
    int updateGuarded(@Param("call") AiAgentToolCall call, @Param("leaseOwner") String leaseOwner);

    @Select("""
            SELECT * FROM ai_agent_tool_calls
            WHERE analysis_run_id=#{runId}
            ORDER BY step_no ASC
            """)
    List<AiAgentToolCall> selectByRunId(@Param("runId") Long runId);

    @Update("""
            UPDATE ai_agent_tool_calls tc
            JOIN ai_analysis_runs r ON r.id=tc.analysis_run_id
            SET tc.arguments_json=JSON_OBJECT(), tc.result_summary_json=JSON_OBJECT(),
                tc.evidence_hash=NULL
            WHERE r.session_id=#{sessionId} AND r.task_type='agent_research'
            """)
    int purgeSessionContent(@Param("sessionId") Long sessionId);
}
