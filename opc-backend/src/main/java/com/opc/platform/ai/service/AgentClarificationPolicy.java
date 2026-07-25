package com.opc.platform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.tag.service.IndustryTagService;
import com.opc.platform.tag.vo.IndustryResolution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class AgentClarificationPolicy {

    static final int MAX_CLARIFICATION_TURNS = 3;

    private final ObjectMapper objectMapper;
    private final RegionMapper regionMapper;
    private final IndustryTagService industryTagService;

    @Autowired
    public AgentClarificationPolicy(
            ObjectMapper objectMapper,
            RegionMapper regionMapper,
            IndustryTagService industryTagService
    ) {
        this.objectMapper = objectMapper;
        this.regionMapper = regionMapper;
        this.industryTagService = industryTagService;
    }

    AgentClarificationPolicy(ObjectMapper objectMapper) {
        this(objectMapper, null, null);
    }

    public AgentClarificationDecision evaluate(String profileJson, String contextJson, String content) {
        try {
            ObjectNode profile = object(profileJson);
            ObjectNode context = object(contextJson);
            ObjectNode resolved = context.has("resolvedFields") && context.path("resolvedFields").isObject()
                    ? (ObjectNode) context.path("resolvedFields").deepCopy()
                    : objectMapper.createObjectNode();
            Set<String> priorPending = stringSet(context.path("pendingFields"));

            if (priorPending.contains("region")) resolveRegionAnswer(resolved, content);
            if (priorPending.contains("industry")) resolveIndustryAnswer(resolved, content);
            mergeVerifiedProfile(resolved, profile);
            if (!StringUtils.hasText(resolved.path("researchGoal").asText())
                    && priorPending.isEmpty() && StringUtils.hasText(content) && content.trim().length() >= 6) {
                resolved.put("researchGoal", bounded(content.trim(), AgentSessionService.MAX_USER_MESSAGE_LENGTH));
            }

            List<String> pending = pendingFields(resolved);
            int turns = Math.max(0, context.path("clarificationTurns").asInt(0));
            if (!pending.isEmpty() && turns >= MAX_CLARIFICATION_TURNS) {
                ObjectNode updated = context(resolved, pending, turns, context.path("lastClarificationQuestion").asText(""));
                return new AgentClarificationDecision(updated.toString(), null, true);
            }
            String question = pending.isEmpty() ? null : questionFor(pending.get(0));
            int updatedTurns = question == null ? turns : turns + 1;
            ObjectNode updated = context(resolved, pending, updatedTurns, question == null ? "" : question);
            return new AgentClarificationDecision(updated.toString(), question, false);
        } catch (BusinessException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "研究画像或澄清上下文格式无效");
        }
    }

    public String question(String profileJson, String content) {
        return evaluate(profileJson, null, content).question();
    }

    private void resolveRegionAnswer(ObjectNode resolved, String content) {
        if (!StringUtils.hasText(content) || regionMapper == null) return;
        String input = normalize(content);
        List<Region> regions = regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .orderByAsc(Region::getId));
        List<Region> matches = (regions == null ? List.<Region>of() : regions).stream()
                .filter(region -> {
                    String name = normalize(region.getName());
                    return !name.isEmpty() && (input.equals(name) || input.contains(name));
                })
                .toList();
        if (matches.size() == 1) putRegion(resolved, matches.get(0));
    }

    private void resolveIndustryAnswer(ObjectNode resolved, String content) {
        if (!StringUtils.hasText(content) || industryTagService == null) return;
        IndustryResolution industry = industryTagService.resolve(null, content, false);
        if (industry.tagId() != null && !industry.requiresConfirmation()) putIndustry(resolved, industry);
    }

    private void mergeVerifiedProfile(ObjectNode resolved, ObjectNode profile) {
        if (!resolved.path("regionId").isIntegralNumber() && profile.path("regionId").isIntegralNumber()
                && regionMapper != null) {
            Region region = regionMapper.selectById(profile.path("regionId").asLong());
            if (region != null) putRegion(resolved, region);
        }
        if (!resolved.path("industryTagId").isIntegralNumber() && industryTagService != null) {
            Long tagId = profile.path("industryTagId").isIntegralNumber()
                    ? profile.path("industryTagId").asLong() : null;
            String industryText = profile.path("industry").asText(null);
            IndustryResolution industry = industryTagService.resolve(tagId, industryText, false);
            if (industry.tagId() != null && !industry.requiresConfirmation()) putIndustry(resolved, industry);
        }
        if (!StringUtils.hasText(resolved.path("researchGoal").asText())) {
            String goal = profile.path("goal").asText("").trim();
            if (StringUtils.hasText(goal)) resolved.put("researchGoal", bounded(goal, 1000));
        }
    }

    private List<String> pendingFields(ObjectNode resolved) {
        List<String> pending = new ArrayList<>();
        if (!resolved.path("regionId").isIntegralNumber()) pending.add("region");
        if (!resolved.path("industryTagId").isIntegralNumber()) pending.add("industry");
        if (!StringUtils.hasText(resolved.path("researchGoal").asText())) pending.add("researchGoal");
        return pending;
    }

    private ObjectNode context(ObjectNode resolved, List<String> pending, int turns, String question) {
        ObjectNode context = objectMapper.createObjectNode();
        context.set("resolvedFields", resolved);
        ArrayNode pendingNode = context.putArray("pendingFields");
        pending.forEach(pendingNode::add);
        context.put("clarificationTurns", turns);
        context.put("lastClarificationQuestion", question == null ? "" : question);
        return context;
    }

    private void putRegion(ObjectNode resolved, Region region) {
        resolved.put("regionId", region.getId());
        resolved.put("regionName", region.getName());
    }

    private void putIndustry(ObjectNode resolved, IndustryResolution industry) {
        resolved.put("industryTagId", industry.tagId());
        resolved.put("industry", industry.name());
    }

    private String questionFor(String field) {
        return switch (field) {
            case "region" -> "您希望这次研究聚焦哪个具体地区？";
            case "industry" -> "您希望研究哪个行业或具体业务方向？";
            default -> "您这次最希望验证的研究目标是什么？";
        };
    }

    private ObjectNode object(String value) throws JsonProcessingException {
        if (!StringUtils.hasText(value)) return objectMapper.createObjectNode();
        JsonNode node = objectMapper.readTree(value);
        if (node == null || !node.isObject()) throw new JsonProcessingException("not an object") { };
        return (ObjectNode) node;
    }

    private Set<String> stringSet(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node != null && node.isArray()) node.forEach(item -> values.add(item.asText("")));
        return values;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase()
                .replaceAll("[\\s\\p{P}\\p{S}]", "")
                .replaceAll("(壮族自治区|回族自治区|维吾尔自治区|自治区|特别行政区|省|市|地区)$", "");
    }

    private String bounded(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
