package com.opc.platform.policy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.policy.entity.Policy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface PolicyMapper extends BaseMapper<Policy> {

    @Select("""
            SELECT COUNT(*)
            FROM policies item
            INNER JOIN sources source ON source.id=item.source_id
            WHERE item.status='published' AND item.ai_evidence_status='verified'
              AND source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)<>''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)<>''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
            """)
    long countEligibleAnalyticsRecords();

    @Select("""
            SELECT CONCAT(item.id, ':', COALESCE(item.evidence_revision,0), ':',
                          source.id, ':', COALESCE(source.evidence_revision,0))
            FROM policies item
            INNER JOIN sources source ON source.id=item.source_id
            WHERE item.status='published' AND item.ai_evidence_status='verified'
              AND source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)<>''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)<>''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
            ORDER BY item.id, source.id
            """)
    List<String> selectEligibleAnalyticsVersionStamps();

    @Select("""
            <script>
            SELECT item.id,
                   item.title,
                   item.publish_date AS publishDate,
                   item.region_id AS regionId,
                   region.parent_id AS parentId,
                   region.level AS regionLevel,
                   region.name AS regionName,
                   item.issuing_body AS issuingBody,
                   item.policy_type AS policyType,
                   item.ai_evidence_status AS aiEvidenceStatus,
                   item.updated_at AS updatedAt
            FROM policies item
            INNER JOIN sources source ON source.id=item.source_id
            LEFT JOIN regions region ON region.id=item.region_id
            WHERE item.status='published' AND item.ai_evidence_status='verified'
              AND source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)&lt;&gt;''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)&lt;&gt;''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
              <if test="regionId != null">
                AND item.region_id=#{regionId}
              </if>
            ORDER BY item.publish_date DESC, item.updated_at DESC, item.id DESC
            </script>
            """)
    List<AnalyticsPolicyRow> selectEligibleAnalyticsRows(@Param("regionId") Long regionId);

    @Select("""
            SELECT MAX(GREATEST(item.updated_at, source.updated_at))
            FROM policies item
            INNER JOIN sources source ON source.id=item.source_id
            WHERE item.status='published' AND item.ai_evidence_status='verified'
              AND source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)<>''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)<>''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
            """)
    LocalDateTime selectEligibleAnalyticsLastUpdatedAt();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class AnalyticsPolicyRow {
        private Long id;
        private String title;
        private LocalDate publishDate;
        private Long regionId;
        private Long parentId;
        private String regionLevel;
        private String regionName;
        private String issuingBody;
        private String policyType;
        private String aiEvidenceStatus;
        private LocalDateTime updatedAt;
    }

    @Select("SELECT * FROM policies WHERE id = #{id} FOR UPDATE")
    Policy selectByIdForUpdate(@Param("id") Long id);

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
              AND source.publisher IS NOT NULL AND TRIM(source.publisher) <> ''
              AND source.url IS NOT NULL AND TRIM(source.url) <> ''
            """)
    int verifyEvidenceWithEligibleSource(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") Long expectedVersion,
            @Param("expectedUpdatedAt") LocalDateTime expectedUpdatedAt
    );
}
