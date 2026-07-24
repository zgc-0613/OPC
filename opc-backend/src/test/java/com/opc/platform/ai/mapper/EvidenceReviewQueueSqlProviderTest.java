package com.opc.platform.ai.mapper;

import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceReviewQueueSqlProviderTest {

    @Test
    void pageQuerySortsTheCompleteUnionBeforeApplyingLimit() {
        EvidenceReviewQueryDTO query = new EvidenceReviewQueryDTO();
        query.setSort("title_asc");
        query.setSize(2);
        String sql = new EvidenceReviewQueueSqlProvider().selectPage(
                new HashMap<>(java.util.Map.of("query", query, "limit", 2, "offset", 2))
        );

        assertTrue(sql.contains("UNION ALL"));
        assertTrue(sql.contains("ORDER BY title ASC"));
        assertTrue(sql.indexOf("ORDER BY title ASC") > sql.indexOf("UNION ALL"));
        assertTrue(sql.endsWith("LIMIT #{limit} OFFSET #{offset}"));
    }

    @Test
    void countQueryAppliesReviewabilityAfterUnion() {
        EvidenceReviewQueryDTO query = new EvidenceReviewQueryDTO();
        query.setReviewability("reviewable");
        String sql = new EvidenceReviewQueueSqlProvider().count(
                new HashMap<>(java.util.Map.of("query", query))
        );

        assertTrue(sql.contains("UNION ALL"));
        assertTrue(sql.contains("WHERE reviewable = 1"));
    }
}
