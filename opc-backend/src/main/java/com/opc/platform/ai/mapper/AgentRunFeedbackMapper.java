package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AgentRunFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AgentRunFeedbackMapper extends BaseMapper<AgentRunFeedback> {

    @Select("SELECT * FROM ai_agent_run_feedback WHERE run_id=#{runId} AND user_id=#{userId} LIMIT 1")
    AgentRunFeedback selectOwned(@Param("runId") Long runId, @Param("userId") Long userId);

    @Update("UPDATE ai_agent_run_feedback SET rating=#{rating}, reason=#{reason}, comment_text=#{comment}, "
            + "revision=revision+1, updated_at=#{now} WHERE run_id=#{runId} AND user_id=#{userId} "
            + "AND revision=#{expectedRevision}")
    int updateCas(
            @Param("runId") Long runId,
            @Param("userId") Long userId,
            @Param("expectedRevision") Long expectedRevision,
            @Param("rating") String rating,
            @Param("reason") String reason,
            @Param("comment") String comment,
            @Param("now") LocalDateTime now
    );
}
