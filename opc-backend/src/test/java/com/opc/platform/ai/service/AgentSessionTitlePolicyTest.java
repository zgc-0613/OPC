package com.opc.platform.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSessionTitlePolicyTest {

    private final AgentSessionTitlePolicy policy = new AgentSessionTitlePolicy();

    @Test
    void removesMarkdownAndWhitespaceFromTheFirstQuestion() {
        assertEquals("研究湖北人工智能创业机会", policy.fromFirstQuestion("## 研究\n**湖北** 人工智能创业机会"));
    }

    @Test
    void truncatesByUnicodeCodePointWithoutSplittingSupplementaryCharacters() {
        String title = policy.fromFirstQuestion("😀".repeat(50));

        assertEquals(40, title.codePointCount(0, title.length()));
        assertTrue(title.endsWith("😀"));
    }
}
