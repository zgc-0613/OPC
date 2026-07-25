package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiAgentMessageMapper extends BaseMapper<AiAgentMessage> {

    @Select("SELECT COALESCE(MAX(sequence_no),0) FROM ai_agent_messages WHERE session_id=#{sessionId}")
    int maxSequence(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT * FROM ai_agent_messages
            WHERE session_id=#{sessionId}
            ORDER BY sequence_no DESC
            LIMIT #{limit}
            """)
    List<AiAgentMessage> selectRecent(
            @Param("sessionId") Long sessionId,
            @Param("limit") int limit
    );

    @Update("UPDATE ai_agent_messages SET run_id=#{runId} WHERE id=#{messageId} AND run_id IS NULL")
    int attachRun(@Param("messageId") Long messageId, @Param("runId") Long runId);

    @Select("""
            SELECT * FROM ai_agent_messages
            WHERE session_id=#{sessionId}
            ORDER BY sequence_no ASC
            LIMIT #{limit}
            """)
    List<AiAgentMessage> selectSessionMessages(
            @Param("sessionId") Long sessionId,
            @Param("limit") int limit
    );

    @Select("""
            SELECT * FROM ai_agent_messages
            WHERE run_id=#{runId} AND role='assistant' AND status='completed'
            ORDER BY sequence_no DESC LIMIT 1
            """)
    AiAgentMessage selectFinalByRun(@Param("runId") Long runId);
}
