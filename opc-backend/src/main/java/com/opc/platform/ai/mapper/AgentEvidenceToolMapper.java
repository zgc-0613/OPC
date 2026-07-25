package com.opc.platform.ai.mapper;

import com.opc.platform.ai.tool.AgentCaseSearchRow;
import com.opc.platform.ai.tool.AgentPolicySearchRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentEvidenceToolMapper {

    @Select("""
            <script>
            SELECT c.id AS caseId, c.title, r.name AS region, c.category,
                   c.summary, c.business_model AS businessModel, c.ai_tools AS aiTools, c.outcome,
                   c.source_id AS sourceId,
                   c.evidence_revision AS caseRevision,
                   s.evidence_revision AS sourceRevision,
                   c.updated_at AS caseUpdatedAt,
                   s.updated_at AS sourceUpdatedAt
            FROM case_items c
            JOIN sources s ON s.id=c.source_id
            LEFT JOIN regions r ON r.id=c.region_id
            WHERE c.status='published' AND c.ai_evidence_status='verified'
              AND s.status='published' AND s.ai_evidence_status='verified'
            <if test="regionId != null">AND c.region_id=#{regionId}</if>
            <if test="category != null and category != ''">AND c.category=#{category}</if>
            <if test="industryTagId != null">
              AND EXISTS (SELECT 1 FROM case_tags ct WHERE ct.case_id=c.id AND ct.tag_id=#{industryTagId})
            </if>
            <if test="industry != null and industry != ''">
              AND (c.title LIKE CONCAT('%',#{industry},'%')
                   OR c.tags LIKE CONCAT('%',#{industry},'%')
                   OR c.summary LIKE CONCAT('%',#{industry},'%'))
            </if>
            <if test="keywords != null and keywords != ''">
              AND (c.title LIKE CONCAT('%',#{keywords},'%')
                   OR c.summary LIKE CONCAT('%',#{keywords},'%')
                   OR c.business_model LIKE CONCAT('%',#{keywords},'%')
                   OR c.tags LIKE CONCAT('%',#{keywords},'%'))
            </if>
            ORDER BY c.updated_at DESC, c.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AgentCaseSearchRow> searchCases(
            @Param("regionId") Long regionId,
            @Param("industryTagId") Long industryTagId,
            @Param("industry") String industry,
            @Param("keywords") String keywords,
            @Param("category") String category,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT p.id AS policyId, p.title, p.policy_type AS policyType,
                   p.summary, p.support_measures AS supportMeasures,
                   p.applicability_mode AS applicabilityMode,
                   r.level AS geographicLevel, p.source_id AS sourceId,
                   p.evidence_revision AS policyRevision,
                   s.evidence_revision AS sourceRevision,
                   p.updated_at AS policyUpdatedAt,
                   s.updated_at AS sourceUpdatedAt
            FROM policies p
            JOIN sources s ON s.id=p.source_id
            LEFT JOIN regions r ON r.id=p.region_id
            WHERE p.status='published' AND p.ai_evidence_status='verified'
              AND s.status='published' AND s.ai_evidence_status='verified'
              AND p.region_id IN
              <foreach collection="regionIds" item="regionId" open="(" separator="," close=")">#{regionId}</foreach>
            <if test="industryTagId != null">
              AND (p.applicability_mode IN ('general','unclassified')
                   OR (p.applicability_mode='specific' AND EXISTS (
                       SELECT 1 FROM policy_industry_tags pit
                       WHERE pit.policy_id=p.id AND pit.industry_tag_id=#{industryTagId}
                   )))
            </if>
            <if test="industry != null and industry != ''">
              AND (p.applicability_mode IN ('general','unclassified')
                   OR p.title LIKE CONCAT('%',#{industry},'%')
                   OR p.tags LIKE CONCAT('%',#{industry},'%')
                   OR p.summary LIKE CONCAT('%',#{industry},'%'))
            </if>
            <if test="keywords != null and keywords != ''">
              AND (p.title LIKE CONCAT('%',#{keywords},'%')
                   OR p.summary LIKE CONCAT('%',#{keywords},'%')
                   OR p.key_points LIKE CONCAT('%',#{keywords},'%')
                   OR p.support_measures LIKE CONCAT('%',#{keywords},'%'))
            </if>
            ORDER BY FIELD(p.applicability_mode,'specific','general','unclassified'),
                     p.publish_date DESC, p.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AgentPolicySearchRow> searchPolicies(
            @Param("regionIds") List<Long> regionIds,
            @Param("industryTagId") Long industryTagId,
            @Param("industry") String industry,
            @Param("keywords") String keywords,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT c.id AS caseId, c.title, r.name AS region, c.category,
                   c.summary, c.business_model AS businessModel, c.ai_tools AS aiTools, c.outcome,
                   c.source_id AS sourceId,
                   c.evidence_revision AS caseRevision,
                   s.evidence_revision AS sourceRevision,
                   c.updated_at AS caseUpdatedAt,
                   s.updated_at AS sourceUpdatedAt
            FROM case_items c
            JOIN sources s ON s.id=c.source_id
            LEFT JOIN regions r ON r.id=c.region_id
            WHERE c.id IN
              <foreach collection="caseIds" item="caseId" open="(" separator="," close=")">#{caseId}</foreach>
              AND c.status='published' AND c.ai_evidence_status='verified'
              AND s.status='published' AND s.ai_evidence_status='verified'
            ORDER BY c.id
            </script>
            """)
    List<AgentCaseSearchRow> loadCases(@Param("caseIds") List<Long> caseIds);
}
