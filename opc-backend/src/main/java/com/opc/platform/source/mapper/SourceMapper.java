package com.opc.platform.source.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.source.entity.Source;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SourceMapper extends BaseMapper<Source> {

    @Select("""
            SELECT COUNT(*) FROM sources source
            WHERE source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)<>''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)<>''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
            """)
    long countEligibleAnalyticsRecords();

    @Select("""
            SELECT CONCAT(source.id, ':', COALESCE(source.evidence_revision,0))
            FROM sources source
            WHERE source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)<>''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)<>''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
            ORDER BY source.id
            """)
    List<String> selectEligibleAnalyticsVersionStamps();

    @Select("SELECT * FROM sources WHERE id = #{id} FOR UPDATE")
    Source selectByIdForUpdate(@Param("id") Long id);
}
