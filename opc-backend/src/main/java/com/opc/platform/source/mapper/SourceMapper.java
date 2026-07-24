package com.opc.platform.source.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.source.entity.Source;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SourceMapper extends BaseMapper<Source> {

    @Select("SELECT * FROM sources WHERE id = #{id} FOR UPDATE")
    Source selectByIdForUpdate(@Param("id") Long id);
}
