package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAnalysisRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
