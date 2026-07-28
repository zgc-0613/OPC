package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryResolution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentClarificationPolicyTest {

    @Test
    void detectsOnlyExplicitChangesToAnAlreadyConfirmedResearchBoundary() {
        RegionMapper regions = mock(RegionMapper.class);
        IndustryTagService industries = mock(IndustryTagService.class);
        Region hubei = new Region();
        hubei.setId(2L);
        hubei.setName("湖北省");
        Region guangdong = new Region();
        guangdong.setId(3L);
        guangdong.setName("广东省");
        when(regions.selectList(any())).thenReturn(java.util.List.of(hubei, guangdong));
        when(industries.resolve(isNull(), any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(new IndustryResolution(8L, "软件服务", "industry", "alias", 1, false));
        AgentClarificationPolicy policy = new AgentClarificationPolicy(
                new ObjectMapper(), regions, industries);
        String profile = "{\"regionId\":2,\"industryTagId\":7}";

        assertTrue(policy.changesResearchBoundary(profile, "请把研究地区改为广东省"));
        assertTrue(policy.changesResearchBoundary(profile, "请改到广东省继续研究"));
        assertTrue(policy.changesResearchBoundary(profile, "请将目标行业切换为软件服务"));
        assertTrue(policy.changesResearchBoundary(profile, "把软件服务换成电子商务"));
        assertFalse(policy.changesResearchBoundary(profile, "请比较广东省案例作为跨地区借鉴"));
    }

    @Test
    void runtimeProfileCannotReplaceTheConfirmedRegionOrIndustryBoundary() throws Exception {
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
        assertEquals(2L, profile.path("regionId").asLong());
        assertEquals(7L, profile.path("industryTagId").asLong());
        assertEquals("人工智能应用", profile.path("industry").asText());
        assertEquals("核验本地政策与案例", profile.path("goal").asText());
    }
}
