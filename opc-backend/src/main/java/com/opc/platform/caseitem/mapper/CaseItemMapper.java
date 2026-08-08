package com.opc.platform.caseitem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.caseitem.entity.CaseItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public interface CaseItemMapper extends BaseMapper<CaseItem> {

    @Select("""
            SELECT COUNT(*)
            FROM case_items item
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
            FROM case_items item
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
            SELECT tag.id AS tagId, tag.name AS label, COUNT(DISTINCT item.id) AS value
            FROM tags tag
            INNER JOIN case_tags relation ON relation.tag_id=tag.id
            INNER JOIN case_items item ON item.id=relation.case_id
            INNER JOIN sources source ON source.id=item.source_id
            WHERE tag.is_industry=1
              AND item.status='published' AND item.ai_evidence_status='verified'
              AND source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)&lt;&gt;''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)&lt;&gt;''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
              <if test="tagIds != null and !tagIds.isEmpty()">
                AND tag.id IN
                <foreach collection="tagIds" item="tagId" open="(" separator="," close=")">#{tagId}</foreach>
              </if>
            GROUP BY tag.id, tag.name
            ORDER BY value DESC, tag.id ASC
            </script>
            """)
    List<AnalyticsIndustryRow> selectEligibleIndustryAnalyticsRows(@Param("tagIds") List<Long> tagIds);

    @Select("""
            <script>
            SELECT COUNT(DISTINCT item.id)
            FROM case_items item
            INNER JOIN sources source ON source.id=item.source_id
            INNER JOIN case_tags relation ON relation.case_id=item.id
            INNER JOIN tags tag ON tag.id=relation.tag_id AND tag.is_industry=1
            WHERE item.status='published' AND item.ai_evidence_status='verified'
              AND source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)&lt;&gt;''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)&lt;&gt;''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
              <if test="tagIds != null and !tagIds.isEmpty()">
                AND tag.id IN
                <foreach collection="tagIds" item="tagId" open="(" separator="," close=")">#{tagId}</foreach>
              </if>
            </script>
            """)
    long countEligibleIndustryTaggedCases(@Param("tagIds") List<Long> tagIds);

    @Select("SELECT COUNT(*) FROM tags WHERE is_industry=1")
    long countIndustryTaxonomyTags();

    @Select("""
            <script>
            SELECT id FROM tags WHERE is_industry=1
              <if test="tagIds != null and !tagIds.isEmpty()">
                AND id IN
                <foreach collection="tagIds" item="tagId" open="(" separator="," close=")">#{tagId}</foreach>
              </if>
            ORDER BY id
            </script>
            """)
    List<Long> selectApprovedIndustryTagIds(@Param("tagIds") List<Long> tagIds);

    @Select("""
            SELECT CONCAT(item.id, ':', COALESCE(item.evidence_revision,0), ':',
                          source.id, ':', COALESCE(source.evidence_revision,0), ':',
                          tag.id, ':', tag.name, ':', UNIX_TIMESTAMP(tag.updated_at), ':',
                          UNIX_TIMESTAMP(relation.created_at))
            FROM case_items item
            INNER JOIN sources source ON source.id=item.source_id
            INNER JOIN case_tags relation ON relation.case_id=item.id
            INNER JOIN tags tag ON tag.id=relation.tag_id AND tag.is_industry=1
            WHERE item.status='published' AND item.ai_evidence_status='verified'
              AND source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)<>''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)<>''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
            ORDER BY item.id, tag.id, source.id
            """)
    List<String> selectEligibleIndustryAnalyticsVersionStamps();

    @Select("""
            SELECT MAX(GREATEST(item.updated_at, source.updated_at, tag.updated_at, relation.created_at))
            FROM case_items item
            INNER JOIN sources source ON source.id=item.source_id
            INNER JOIN case_tags relation ON relation.case_id=item.id
            INNER JOIN tags tag ON tag.id=relation.tag_id AND tag.is_industry=1
            WHERE item.status='published' AND item.ai_evidence_status='verified'
              AND source.status='published' AND source.ai_evidence_status='verified'
              AND source.title IS NOT NULL AND TRIM(source.title)<>''
              AND source.publisher IS NOT NULL AND TRIM(source.publisher)<>''
              AND source.url IS NOT NULL
              AND (LOWER(TRIM(source.url)) LIKE 'http://%' OR LOWER(TRIM(source.url)) LIKE 'https://%')
            """)
    LocalDateTime selectEligibleIndustryAnalyticsLastUpdatedAt();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class AnalyticsIndustryRow {
        private Long tagId;
        private String label;
        private Long value;
    }

    @Select("SELECT * FROM case_items WHERE id = #{id} FOR UPDATE")
    CaseItem selectByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE case_items AS item
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
