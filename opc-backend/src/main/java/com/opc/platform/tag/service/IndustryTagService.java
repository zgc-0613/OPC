package com.opc.platform.tag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.casetag.entity.CaseTag;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.policytag.entity.PolicyTag;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.tag.vo.IndustryResolution;
import com.opc.platform.tag.vo.IndustryTagVO;
import com.opc.platform.tagalias.entity.TagAlias;
import com.opc.platform.tagalias.mapper.TagAliasMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndustryTagService {

    private static final double FUZZY_THRESHOLD = 0.72;
    private final TagMapper tagMapper;
    private final TagAliasMapper aliasMapper;
    private final CaseTagMapper caseTagMapper;
    private final PolicyTagMapper policyTagMapper;

    public List<IndustryTagVO> listIndustries() {
        List<Tag> tags = industryTags();
        List<Long> industryTagIds = tags.stream().map(Tag::getId).toList();
        Map<Long, Long> caseCounts = safeCaseTags(industryTagIds).stream()
                .collect(Collectors.groupingBy(CaseTag::getTagId, Collectors.counting()));
        Map<Long, Long> policyCounts = safePolicyTags(industryTagIds).stream()
                .collect(Collectors.groupingBy(PolicyTag::getTagId, Collectors.counting()));
        return tags.stream()
                .map(tag -> new IndustryTagVO(
                        tag.getId(),
                        tag.getName(),
                        tag.getTagType(),
                        caseCounts.getOrDefault(tag.getId(), 0L),
                        policyCounts.getOrDefault(tag.getId(), 0L)
                ))
                .toList();
    }

    public IndustryResolution resolve(Long tagId, String rawText, boolean allowAi) {
        if (tagId != null) {
            Tag selected = tagMapper.selectById(tagId);
            if (isIndustry(selected)) {
                return resolved(selected, "tag_id", 1.0, false);
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所选行业标签不存在");
        }

        String normalized = normalize(rawText);
        if (normalized.isEmpty()) {
            return IndustryResolution.unresolved();
        }
        List<Tag> candidates = industryTags();
        Map<Long, Tag> byId = candidates.stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        for (Tag candidate : candidates) {
            if (normalize(candidate.getName()).equals(normalized)) {
                return resolved(candidate, "tag_name", 1.0, false);
            }
        }

        List<TagAlias> aliases = aliases();
        for (TagAlias alias : aliases) {
            if (normalized.equals(normalizedAlias(alias))) {
                Tag candidate = byId.get(alias.getTagId());
                if (candidate != null) {
                    return resolved(candidate, "alias", 0.98, false);
                }
            }
        }

        ScoredTag fuzzy = bestFuzzy(normalized, candidates, aliases, byId);
        if (fuzzy != null && fuzzy.score() >= FUZZY_THRESHOLD) {
            return resolved(fuzzy.tag(), "fuzzy", fuzzy.score(), true);
        }

        return IndustryResolution.unresolved();
    }

    public List<Long> relatedTagIds(Long industryTagId) {
        if (industryTagId == null) {
            return List.of();
        }
        Tag industry = tagMapper.selectById(industryTagId);
        if (!isIndustry(industry)) {
            return List.of();
        }
        var relatedNames = aliases().stream()
                .filter(alias -> Objects.equals(industryTagId, alias.getTagId()))
                .map(this::normalizedAlias)
                .collect(Collectors.toSet());
        relatedNames.add(normalize(industry.getName()));
        List<Tag> relatedTags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().in(Tag::getName, relatedNames)
        );
        return (relatedTags == null ? List.<Tag>of() : relatedTags).stream()
                .filter(tag -> relatedNames.contains(normalize(tag.getName())))
                .map(Tag::getId)
                .distinct()
                .toList();
    }

    private ScoredTag bestFuzzy(
            String input,
            List<Tag> candidates,
            List<TagAlias> aliases,
            Map<Long, Tag> byId
    ) {
        ScoredTag best = null;
        for (Tag candidate : candidates) {
            best = better(best, new ScoredTag(candidate, similarity(input, normalize(candidate.getName()))));
        }
        for (TagAlias alias : aliases) {
            Tag candidate = byId.get(alias.getTagId());
            if (candidate != null) {
                best = better(best, new ScoredTag(candidate, similarity(input, normalizedAlias(alias))));
            }
        }
        return best;
    }

    private ScoredTag better(ScoredTag current, ScoredTag candidate) {
        return current == null || candidate.score() > current.score() ? candidate : current;
    }

    private double similarity(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        if (left.contains(right) || right.contains(left)) {
            return 0.90;
        }
        int distance = editDistance(left, right);
        return Math.max(0, 1.0 - (double) distance / Math.max(left.length(), right.length()));
    }

    private int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            int[] current = new int[right.length() + 1];
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + cost
                );
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private IndustryResolution resolved(Tag tag, String method, double confidence, boolean confirmation) {
        return new IndustryResolution(
                tag.getId(), tag.getName(), tag.getTagType(), method, confidence, confirmation
        );
    }

    private List<Tag> industryTags() {
        List<Tag> values = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getIsIndustry, true)
                .orderByAsc(Tag::getSortOrder)
                .orderByAsc(Tag::getId));
        return values == null ? List.of() : values;
    }

    private List<TagAlias> aliases() {
        List<TagAlias> values = aliasMapper.selectList(new LambdaQueryWrapper<TagAlias>());
        return values == null ? List.of() : values;
    }

    private List<CaseTag> safeCaseTags(List<Long> tagIds) {
        if (tagIds.isEmpty()) return List.of();
        List<CaseTag> values = caseTagMapper.selectList(
                new LambdaQueryWrapper<CaseTag>().in(CaseTag::getTagId, tagIds)
        );
        return values == null ? List.of() : values;
    }

    private List<PolicyTag> safePolicyTags(List<Long> tagIds) {
        if (tagIds.isEmpty()) return List.of();
        List<PolicyTag> values = policyTagMapper.selectList(
                new LambdaQueryWrapper<PolicyTag>().in(PolicyTag::getTagId, tagIds)
        );
        return values == null ? List.of() : values;
    }

    private boolean isIndustry(Tag tag) {
        return tag != null && Boolean.TRUE.equals(tag.getIsIndustry());
    }

    private String normalizedAlias(TagAlias alias) {
        return StringUtils.hasText(alias.getNormalizedAlias())
                ? alias.getNormalizedAlias()
                : normalize(alias.getAlias());
    }

    public static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[\\s\\p{P}\\p{S}]+", "");
    }

    private record ScoredTag(Tag tag, double score) {
    }
}
