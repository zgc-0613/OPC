package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAgentToolCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiAgentToolCallMapper extends BaseMapper<AiAgentToolCall> {

    @Select("""
            SELECT * FROM ai_agent_tool_calls
            WHERE analysis_run_id=#{runId}
            ORDER BY step_no ASC
            """)
    List<AiAgentToolCall> selectByRunId(@Param("runId") Long runId);
}
