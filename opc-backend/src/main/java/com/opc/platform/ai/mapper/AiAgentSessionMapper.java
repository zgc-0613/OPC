package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiAgentSessionMapper extends BaseMapper<AiAgentSession> {

    @Select("SELECT * FROM ai_agent_sessions WHERE id=#{id} AND user_id=#{userId} LIMIT 1")
    AiAgentSession selectOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM ai_agent_sessions WHERE id=#{id} AND user_id=#{userId} FOR UPDATE")
    AiAgentSession selectOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Update("""
            UPDATE ai_agent_sessions
            SET version=version+1, last_message_at=CURRENT_TIMESTAMP(6)
            WHERE id=#{id} AND user_id=#{userId} AND status='active'
            """)
    int touchActive(@Param("id") Long id, @Param("userId") Long userId);

    @Update("""
            UPDATE ai_agent_sessions
            SET research_context_json=#{contextJson}, version=version+1
            WHERE id=#{id} AND user_id=#{userId} AND status='active'
            """)
    int updateResearchContext(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("contextJson") String contextJson
    );
}
