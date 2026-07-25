package com.opc.platform.ai.mapper;

import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import com.opc.platform.ai.service.EvidenceUrlPolicy;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EvidenceReviewQueueSqlProvider {

    public String selectPage(Map<String, Object> parameters) {
        EvidenceReviewQueryDTO query = (EvidenceReviewQueryDTO) parameters.get("query");
        return baseQuery(query)
                + orderBy(query.getSort())
                + " LIMIT #{limit} OFFSET #{offset}";
    }

    public String count(Map<String, Object> parameters) {
        EvidenceReviewQueryDTO query = (EvidenceReviewQueryDTO) parameters.get("query");
        return "SELECT COUNT(*) FROM (" + filteredUnion(query) + ") evidence_queue";
    }

    private String baseQuery(EvidenceReviewQueryDTO query) {
        return "SELECT * FROM (" + filteredUnion(query) + ") evidence_queue";
    }

    private String filteredUnion(EvidenceReviewQueryDTO query) {
        List<String> branches = new ArrayList<>();
        if (!StringUtils.hasText(query.getItemType()) || "case".equals(query.getItemType())) {
            branches.add(caseBranch(query));
        }
        if (!StringUtils.hasText(query.getItemType()) || "policy".equals(query.getItemType())) {
            branches.add(policyBranch(query));
        }
        if (!StringUtils.hasText(query.getItemType()) || "source".equals(query.getItemType())) {
            branches.add(sourceBranch(query));
        }
        String union = String.join(" UNION ALL ", branches);
        if (!StringUtils.hasText(query.getReviewability()) || "all".equals(query.getReviewability())) {
            return union;
        }
        return "SELECT * FROM (" + union + ") reviewability_queue WHERE reviewable = "
                + ("reviewable".equals(query.getReviewability()) ? "1" : "0");
    }

    private String caseBranch(EvidenceReviewQueryDTO query) {
        String complete = "(NULLIF(TRIM(c.title), '') IS NOT NULL AND NULLIF(TRIM(c.summary), '') IS NOT NULL)";
        return "SELECT 'case' AS item_type, c.id AS item_id, c.title, c.status AS publication_status, "
                + "COALESCE(c.ai_evidence_status, 'legacy_unverified') AS evidence_status, c.source_id, "
                + "s.title AS source_title, s.status AS source_status, "
                + "COALESCE(s.ai_evidence_status, 'legacy_unverified') AS source_evidence_status, "
                + "s.publisher AS source_publisher, s.url AS source_url, "
                + complete + " AS content_complete, "
                + reviewableExpression("c.status", complete) + " AS reviewable, "
                + "COALESCE(c.evidence_revision, 0) AS version, c.updated_at "
                + "FROM case_items c LEFT JOIN sources s ON s.id = c.source_id"
                + whereClause(query, "c", "c.source_id", "c.title", "c.summary", "c.tags");
    }

    private String policyBranch(EvidenceReviewQueryDTO query) {
        String complete = "(NULLIF(TRIM(p.title), '') IS NOT NULL AND NULLIF(TRIM(p.summary), '') IS NOT NULL "
                + "AND NULLIF(TRIM(p.issuing_body), '') IS NOT NULL)";
        return "SELECT 'policy' AS item_type, p.id AS item_id, p.title, p.status AS publication_status, "
                + "COALESCE(p.ai_evidence_status, 'legacy_unverified') AS evidence_status, p.source_id, "
                + "s.title AS source_title, s.status AS source_status, "
                + "COALESCE(s.ai_evidence_status, 'legacy_unverified') AS source_evidence_status, "
                + "s.publisher AS source_publisher, s.url AS source_url, "
                + complete + " AS content_complete, "
                + reviewableExpression("p.status", complete) + " AS reviewable, "
                + "COALESCE(p.evidence_revision, 0) AS version, p.updated_at "
                + "FROM policies p LEFT JOIN sources s ON s.id = p.source_id"
                + whereClause(query, "p", "p.source_id", "p.title", "p.summary", "p.tags");
    }

    private String sourceBranch(EvidenceReviewQueryDTO query) {
        String complete = sourceCompleteExpression("s");
        return "SELECT 'source' AS item_type, s.id AS item_id, s.title, s.status AS publication_status, "
                + "COALESCE(s.ai_evidence_status, 'legacy_unverified') AS evidence_status, s.id AS source_id, "
                + "s.title AS source_title, s.status AS source_status, "
                + "COALESCE(s.ai_evidence_status, 'legacy_unverified') AS source_evidence_status, "
                + "s.publisher AS source_publisher, s.url AS source_url, "
                + complete + " AS content_complete, "
                + "(s.status = 'published' AND " + complete + ") AS reviewable, "
                + "COALESCE(s.evidence_revision, 0) AS version, s.updated_at FROM sources s"
                + whereClause(query, "s", "s.id", "s.title", "s.publisher", "s.notes");
    }

    private String reviewableExpression(String publicationColumn, String contentComplete) {
        return "(" + publicationColumn + " = 'published' AND " + contentComplete
                + " AND s.id IS NOT NULL AND s.status = 'published'"
                + " AND COALESCE(s.ai_evidence_status, 'legacy_unverified') = 'verified'"
                + " AND " + sourceCompleteExpression("s") + ")";
    }

    private String sourceCompleteExpression(String alias) {
        return "(NULLIF(TRIM(" + alias + ".title), '') IS NOT NULL"
                + " AND NULLIF(TRIM(" + alias + ".publisher), '') IS NOT NULL"
                + " AND " + EvidenceUrlPolicy.sqlPredicate(alias + ".url") + ")";
    }

    private String whereClause(
            EvidenceReviewQueryDTO query,
            String alias,
            String sourceColumn,
            String... keywordColumns
    ) {
        List<String> filters = new ArrayList<>();
        if (StringUtils.hasText(query.getEvidenceStatus())) {
            if ("legacy_unverified".equals(query.getEvidenceStatus())) {
                filters.add("(" + alias + ".ai_evidence_status = #{query.evidenceStatus} OR "
                        + alias + ".ai_evidence_status IS NULL)");
            } else {
                filters.add(alias + ".ai_evidence_status = #{query.evidenceStatus}");
            }
        }
        if (query.getSourceId() != null) {
            filters.add(sourceColumn + " = #{query.sourceId}");
        }
        if (StringUtils.hasText(query.getKeyword())) {
            List<String> likes = new ArrayList<>();
            for (String column : keywordColumns) {
                likes.add(column + " LIKE CONCAT('%', #{query.keyword}, '%')");
            }
            filters.add("(" + String.join(" OR ", likes) + ")");
        }
        return filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
    }

    private String orderBy(String requested) {
        return switch (requested == null ? "updated_desc" : requested) {
            case "updated_asc" -> " ORDER BY updated_at ASC, item_type ASC, item_id ASC";
            case "title_asc" -> " ORDER BY title ASC, item_type ASC, item_id ASC";
            case "title_desc" -> " ORDER BY title DESC, item_type DESC, item_id DESC";
            default -> " ORDER BY updated_at DESC, item_type DESC, item_id DESC";
        };
    }
}
