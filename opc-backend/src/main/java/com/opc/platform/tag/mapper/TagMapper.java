package com.opc.platform.tag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.tag.entity.Tag;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TagMapper extends BaseMapper<Tag> {

    @Select("""
            SELECT
                (SELECT COUNT(*) FROM case_tags WHERE tag_id = #{tagId})
              + (SELECT COUNT(*) FROM policy_tags WHERE tag_id = #{tagId})
              + (SELECT COUNT(*) FROM policy_industry_tags WHERE industry_tag_id = #{tagId})
            """)
    long countCaseOrPolicyReferences(@Param("tagId") Long tagId);
}
