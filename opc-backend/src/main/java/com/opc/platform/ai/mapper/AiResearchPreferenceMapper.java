package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiResearchPreference;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiResearchPreferenceMapper extends BaseMapper<AiResearchPreference> {
    @Select("SELECT * FROM ai_research_preferences WHERE user_id=#{userId} LIMIT 1")
    AiResearchPreference selectByUserId(Long userId);

    @Delete("DELETE FROM ai_research_preferences WHERE user_id=#{userId}")
    int deleteByUserId(Long userId);
}
