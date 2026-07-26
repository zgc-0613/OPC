package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentProfilePolicyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RegionMapper regions = mock(RegionMapper.class);
    private final TagMapper tags = mock(TagMapper.class);
    private final AgentProfilePolicy policy = new AgentProfilePolicy(objectMapper, regions, tags);

    @Test
    void canonicalProfileUsesVerifiedIndustryTagName() throws Exception {
        Region region = new Region();
        region.setId(1L);
        Tag tag = new Tag();
        tag.setId(7L);
        tag.setName("Artificial intelligence");
        tag.setIsIndustry(true);
        when(regions.selectById(1L)).thenReturn(region);
        when(tags.selectById(7L)).thenReturn(tag);

        String canonical = policy.canonicalJson(objectMapper.readTree("""
                {"industry":"untrusted","industryTagId":7,"regionId":1,
                 "ventureType":"solo_company","stage":"validation","budgetRange":"under_100k"}
                """));

        assertEquals("Artificial intelligence", objectMapper.readTree(canonical).path("industry").asText());
    }

    @Test
    void profileRejectsWrongTypesAndUnknownEnumValues() throws Exception {
        assertThrows(BusinessException.class, () -> policy.canonicalJson(
                objectMapper.readTree("{\"regionId\":\"1\"}")));
        assertThrows(BusinessException.class, () -> policy.canonicalJson(
                objectMapper.readTree("{\"resources\":[\"injected\"]}")));
        assertThrows(BusinessException.class, () -> policy.canonicalJson(
                objectMapper.readTree("{\"ventureType\":\"enterprise\"}")));
        assertThrows(BusinessException.class, () -> policy.canonicalJson(
                objectMapper.readTree("{\"stage\":\"mature\"}")));
        assertThrows(BusinessException.class, () -> policy.canonicalJson(
                objectMapper.readTree("{\"budgetRange\":\"unbounded\"}")));
    }

    @Test
    void profileRejectsUnknownRegionAndNonIndustryTag() throws Exception {
        Tag tag = new Tag();
        tag.setId(8L);
        tag.setIsIndustry(false);
        when(tags.selectById(8L)).thenReturn(tag);

        assertThrows(BusinessException.class, () -> policy.canonicalJson(
                objectMapper.readTree("{\"regionId\":999}")));
        assertThrows(BusinessException.class, () -> policy.canonicalJson(
                objectMapper.readTree("{\"industryTagId\":8}")));
    }
}
