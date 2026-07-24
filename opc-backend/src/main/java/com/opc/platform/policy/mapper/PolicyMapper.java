package com.opc.platform.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.policy.entity.Policy;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface PolicyMapper extends BaseMapper<Policy> {

    @Update("""
            UPDATE policies AS item
            INNER JOIN sources AS source ON source.id = item.source_id
            SET item.ai_evidence_status = 'verified',
                item.evidence_revision = item.evidence_revision + 1
            WHERE item.id = #{id}
              AND (item.ai_evidence_status = #{expectedStatus}
                   OR (#{expectedStatus} = 'legacy_unverified' AND item.ai_evidence_status IS NULL))
              AND item.evidence_revision = #{expectedVersion}
              AND item.updated_at = #{expectedUpdatedAt}
              AND item.status = 'published'
              AND source.status = 'published'
              AND source.ai_evidence_status = 'verified'
              AND source.title IS NOT NULL AND TRIM(source.title) <> ''
              AND source.url IS NOT NULL AND TRIM(source.url) <> ''
            """)
    int verifyEvidenceWithEligibleSource(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt
    );
}
