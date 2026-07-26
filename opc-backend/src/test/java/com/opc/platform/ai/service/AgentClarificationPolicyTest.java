package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentClarificationPolicyTest {

    @Test
    void runtimeProfileMergesVerifiedContextWithoutDroppingTheResearchProfile() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentClarificationPolicy policy = new AgentClarificationPolicy(objectMapper);

        String merged = policy.runtimeProfile(
                """
                {"ventureType":"solo_company","regionId":2,"industryTagId":7,"industry":"人工智能应用",
                 "stage":"validation","budgetRange":"under_100k","goal":"验证付费需求","resources":"产品原型"}
                """,
                """
                {"resolvedFields":{"regionId":3,"regionName":"武汉市","industryTagId":8,
                 "industry":"软件服务","researchGoal":"核验本地政策与案例"},
                 "pendingFields":[],"clarificationTurns":0,"lastClarificationQuestion":""}
                """
        );

        var profile = objectMapper.readTree(merged);
        assertEquals("solo_company", profile.path("ventureType").asText());
        assertEquals("validation", profile.path("stage").asText());
        assertEquals("under_100k", profile.path("budgetRange").asText());
        assertEquals("产品原型", profile.path("resources").asText());
        assertEquals(3L, profile.path("regionId").asLong());
        assertEquals("武汉市", profile.path("regionName").asText());
        assertEquals(8L, profile.path("industryTagId").asLong());
        assertEquals("软件服务", profile.path("industry").asText());
        assertEquals("核验本地政策与案例", profile.path("goal").asText());
    }
}
