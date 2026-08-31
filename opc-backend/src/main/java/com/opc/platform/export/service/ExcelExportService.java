package com.opc.platform.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.casetag.entity.CaseTag;
import com.opc.platform.casetag.mapper.CaseTagMapper;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policyindustrytag.entity.PolicyIndustryTag;
import com.opc.platform.policyindustrytag.mapper.PolicyIndustryTagMapper;
import com.opc.platform.policytag.entity.PolicyTag;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.tagalias.entity.TagAlias;
import com.opc.platform.tagalias.mapper.TagAliasMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private static final String EXCEL_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PUBLISHED_STATUS = "published";

    private final SourceMapper sourceMapper;

    private final PolicyMapper policyMapper;

    private final CaseItemMapper caseItemMapper;

    private final RegionMapper regionMapper;

    private final TagMapper tagMapper;

    private final TagAliasMapper tagAliasMapper;

    private final PolicyTagMapper policyTagMapper;

    private final PolicyIndustryTagMapper policyIndustryTagMapper;

    private final CaseTagMapper caseTagMapper;

    public void exportPaperDataset(HttpServletResponse response) throws IOException {
        List<Source> sources = safeList(sourceMapper.selectList(new LambdaQueryWrapper<Source>()
                .orderByAsc(Source::getId)));
        List<Policy> policies = safeList(policyMapper.selectList(new LambdaQueryWrapper<Policy>()
                .orderByAsc(Policy::getId)));
        List<CaseItem> cases = safeList(caseItemMapper.selectList(new LambdaQueryWrapper<CaseItem>()
                .orderByAsc(CaseItem::getId)));
        List<Region> regions = safeList(regionMapper.selectList(new LambdaQueryWrapper<Region>()
                .orderByAsc(Region::getSortOrder)
                .orderByAsc(Region::getId)));
        List<Tag> tags = safeList(tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .orderByAsc(Tag::getId)));
        List<TagAlias> aliases = safeList(tagAliasMapper.selectList(new LambdaQueryWrapper<TagAlias>()
                .orderByAsc(TagAlias::getId)));
        List<PolicyTag> policyTags = safeList(policyTagMapper.selectList(new LambdaQueryWrapper<PolicyTag>()
                .orderByAsc(PolicyTag::getId)));
        List<PolicyIndustryTag> policyIndustryTags = safeList(policyIndustryTagMapper.selectList(
                new LambdaQueryWrapper<PolicyIndustryTag>().orderByAsc(PolicyIndustryTag::getId)));
        List<CaseTag> caseTags = safeList(caseTagMapper.selectList(new LambdaQueryWrapper<CaseTag>()
                .orderByAsc(CaseTag::getId)));

        Map<Long, Source> sourceMap = sources.stream().collect(Collectors.toMap(Source::getId, Function.identity()));
        Map<Long, Region> regionMap = regions.stream().collect(Collectors.toMap(Region::getId, Function.identity()));

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);
            List<AuditRow> audits = new ArrayList<>();

            writeManifestSheet(workbook, headerStyle, wrapStyle, sources, policies, cases);
            writePaperSourcesSheet(workbook, headerStyle, wrapStyle, sources, audits);
            writePaperPoliciesSheet(workbook, headerStyle, wrapStyle, policies, sourceMap, regionMap, audits);
            writePaperCasesSheet(workbook, headerStyle, wrapStyle, cases, sourceMap, regionMap, audits);
            writeRegionsSheet(workbook, headerStyle, wrapStyle, regions, regionMap);
            writeTagsSheet(workbook, headerStyle, wrapStyle, tags);
            writeTagAliasesSheet(workbook, headerStyle, wrapStyle, aliases);
            writePolicyTagsSheet(workbook, headerStyle, wrapStyle, policyTags);
            writePolicyIndustryTagsSheet(workbook, headerStyle, wrapStyle, policyIndustryTags);
            writeCaseTagsSheet(workbook, headerStyle, wrapStyle, caseTags);
            writeAuditSheet(workbook, headerStyle, wrapStyle, audits);

            writeWorkbook(response, workbook, "findopc-paper-dataset-" + LocalDate.now() + ".xlsx");
        }
    }

    public void exportSources(HttpServletResponse response) throws IOException {
        List<Source> sources = sourceMapper.selectList(new LambdaQueryWrapper<Source>()
                .orderByDesc(Source::getAccessedAt)
                .orderByDesc(Source::getId));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("sources");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);
            writeHeader(sheet, headerStyle, List.of(
                    "ID", "Title", "Source Type", "Publisher", "URL", "Local File",
                    "Accessed At", "Status", "Notes"
            ));

            int rowIndex = 1;
            for (Source source : sources) {
                Row row = sheet.createRow(rowIndex++);
                writeCells(row, wrapStyle, Arrays.asList(
                        source.getId(),
                        source.getTitle(),
                        source.getSourceType(),
                        source.getPublisher(),
                        source.getUrl(),
                        source.getLocalFile(),
                        source.getAccessedAt(),
                        source.getStatus(),
                        source.getNotes()
                ));
            }

            finishSheet(sheet, 9);
            writeWorkbook(response, workbook, "sources.xlsx");
        }
    }

    public void exportPolicies(HttpServletResponse response) throws IOException {
        exportPolicies(response, false);
    }

    public void exportPublishedPolicies(HttpServletResponse response) throws IOException {
        exportPolicies(response, true);
    }

    private void exportPolicies(HttpServletResponse response, boolean publishedOnly) throws IOException {
        LambdaQueryWrapper<Policy> query = new LambdaQueryWrapper<>();
        if (publishedOnly) {
            query.eq(Policy::getStatus, PUBLISHED_STATUS);
        }
        query.orderByDesc(Policy::getPublishDate)
                .orderByDesc(Policy::getId);

        List<Policy> policies = policyMapper.selectList(query);
        Map<Long, Region> regionMap = loadRegionMap(policies.stream().map(Policy::getRegionId).collect(Collectors.toSet()));
        Map<Long, Source> sourceMap = loadSourceMap(policies.stream().map(Policy::getSourceId).collect(Collectors.toSet()));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("policies");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);
            writeHeader(sheet, headerStyle, List.of(
                    "ID", "Title", "Region ID", "Region Name", "Issuing Body", "Document No",
                    "Publish Date", "Effective Date", "Valid Period", "Source ID", "Source Title",
                    "Policy Level", "Policy Type", "Summary", "Key Points", "Support Measures",
                    "Tags", "Original URL", "Evidence URL", "Local File", "Accessed At",
                    "Status", "Reviewer"
            ));

            int rowIndex = 1;
            for (Policy policy : policies) {
                Region region = regionMap.get(policy.getRegionId());
                Source source = sourceMap.get(policy.getSourceId());
                Row row = sheet.createRow(rowIndex++);
                writeCells(row, wrapStyle, Arrays.asList(
                        policy.getId(),
                        policy.getTitle(),
                        policy.getRegionId(),
                        region == null ? null : region.getName(),
                        policy.getIssuingBody(),
                        policy.getDocumentNo(),
                        policy.getPublishDate(),
                        policy.getEffectiveDate(),
                        policy.getValidPeriod(),
                        policy.getSourceId(),
                        source == null ? null : source.getTitle(),
                        policy.getPolicyLevel(),
                        policy.getPolicyType(),
                        policy.getSummary(),
                        policy.getKeyPoints(),
                        policy.getSupportMeasures(),
                        policy.getTags(),
                        policy.getOriginalUrl(),
                        policy.getEvidenceUrl(),
                        policy.getLocalFile(),
                        policy.getAccessedAt(),
                        policy.getStatus(),
                        policy.getReviewer()
                ));
            }

            finishSheet(sheet, 23);
            writeWorkbook(response, workbook, "policies.xlsx");
        }
    }

    public void exportCases(HttpServletResponse response) throws IOException {
        List<CaseItem> caseItems = caseItemMapper.selectList(new LambdaQueryWrapper<CaseItem>()
                .orderByDesc(CaseItem::getAccessedAt)
                .orderByDesc(CaseItem::getId));
        Map<Long, Region> regionMap = loadRegionMap(caseItems.stream().map(CaseItem::getRegionId).collect(Collectors.toSet()));
        Map<Long, Source> sourceMap = loadSourceMap(caseItems.stream().map(CaseItem::getSourceId).collect(Collectors.toSet()));

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("cases");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);
            writeHeader(sheet, headerStyle, List.of(
                    "ID", "Title", "Article Title", "Region ID", "Region Name", "Category", "Subcategory", "Actor Name",
                    "Source ID", "Source Title", "Summary", "Business Model", "AI Tools",
                    "Outcome", "Tags", "Original URL", "Local File", "Accessed At",
                    "Status", "Reviewer"
            ));

            int rowIndex = 1;
            for (CaseItem caseItem : caseItems) {
                Region region = regionMap.get(caseItem.getRegionId());
                Source source = sourceMap.get(caseItem.getSourceId());
                Row row = sheet.createRow(rowIndex++);
                writeCells(row, wrapStyle, Arrays.asList(
                        caseItem.getId(),
                        caseItem.getTitle(),
                        caseItem.getArticleTitle(),
                        caseItem.getRegionId(),
                        region == null ? null : region.getName(),
                        caseItem.getCategory(),
                        caseItem.getSubcategory(),
                        caseItem.getActorName(),
                        caseItem.getSourceId(),
                        source == null ? null : source.getTitle(),
                        caseItem.getSummary(),
                        caseItem.getBusinessModel(),
                        caseItem.getAiTools(),
                        caseItem.getOutcome(),
                        caseItem.getTags(),
                        caseItem.getOriginalUrl(),
                        caseItem.getLocalFile(),
                        caseItem.getAccessedAt(),
                        caseItem.getStatus(),
                        caseItem.getReviewer()
                ));
            }

            finishSheet(sheet, 20);
            writeWorkbook(response, workbook, "cases.xlsx");
        }
    }

    private void writeManifestSheet(
            Workbook workbook,
            CellStyle headerStyle,
            CellStyle wrapStyle,
            List<Source> sources,
            List<Policy> policies,
            List<CaseItem> cases
    ) {
        Sheet sheet = workbook.createSheet("README");
        writeHeader(sheet, headerStyle, List.of("Item", "Value"));
        List<List<Object>> rows = List.of(
                Arrays.asList("Dataset", "findopc.online paper dataset snapshot"),
                Arrays.asList("Snapshot date", LocalDate.now()),
                Arrays.asList("Source records", sources.size()),
                Arrays.asList("Policy records", policies.size()),
                Arrays.asList("Case records", cases.size()),
                Arrays.asList("Scope", "Current website sources, policies and cases; university data is not included in this phase."),
                Arrays.asList("Completeness rule", "Every database record is exported. Missing fields are listed in data_audit; blanks are never treated as complete."),
                Arrays.asList("Paper eligibility", "published + verified + complete required fields + linked source published and verified"),
                Arrays.asList("Evidence statuses", "legacy_unverified / verified / excluded"),
                Arrays.asList("Important limitation", "Student status is not currently stored as a structured case field. Foreign cases must be added under a separately defined country/comparability schema."),
                Arrays.asList("Raw data rule", "Do not edit this snapshot in place. Create a separate analysis copy and retain this file as the reproducible cutoff snapshot.")
        );
        int rowIndex = 1;
        for (List<Object> values : rows) {
            writeCells(sheet.createRow(rowIndex++), wrapStyle, values);
        }
        finishSheet(sheet, 2);
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 100 * 256);
    }

    private void writePaperSourcesSheet(
            Workbook workbook,
            CellStyle headerStyle,
            CellStyle wrapStyle,
            List<Source> sources,
            List<AuditRow> audits
    ) {
        Sheet sheet = workbook.createSheet("sources_full");
        writeHeader(sheet, headerStyle, List.of(
                "ID", "Title", "Source Type", "Publisher", "URL", "Local File", "Accessed At",
                "Notes", "Status", "Evidence Status", "Evidence Revision", "Created At", "Updated At",
                "Required Complete", "Missing Required", "Missing Recommended", "Paper Eligible"
        ));
        int rowIndex = 1;
        for (Source source : sources) {
            AuditRow audit = auditSource(source);
            audits.add(audit);
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    source.getId(), source.getTitle(), source.getSourceType(), source.getPublisher(), source.getUrl(),
                    source.getLocalFile(), source.getAccessedAt(), source.getNotes(), source.getStatus(),
                    source.getAiEvidenceStatus(), source.getEvidenceRevision(), source.getCreatedAt(), source.getUpdatedAt(),
                    audit.requiredComplete(), audit.missingRequired(), audit.missingRecommended(), audit.paperEligible()
            ));
        }
        finishSheet(sheet, 17);
    }

    private void writePaperPoliciesSheet(
            Workbook workbook,
            CellStyle headerStyle,
            CellStyle wrapStyle,
            List<Policy> policies,
            Map<Long, Source> sourceMap,
            Map<Long, Region> regionMap,
            List<AuditRow> audits
    ) {
        Sheet sheet = workbook.createSheet("policies_full");
        writeHeader(sheet, headerStyle, List.of(
                "ID", "Title", "Region ID", "Country", "Region Path", "Issuing Body", "Document No",
                "Publish Date", "Effective Date", "Valid Period", "Source ID", "Source Title", "Source Publisher",
                "Source URL", "Source Status", "Source Evidence Status", "Policy Level", "Policy Type",
                "Applicability Mode", "Summary", "Key Points", "Support Measures", "Tags", "Original URL",
                "Evidence URL", "Local File", "Accessed At", "Status", "Reviewer", "Evidence Status",
                "Evidence Revision", "Created At", "Updated At", "Required Complete", "Missing Required",
                "Missing Recommended", "Paper Eligible"
        ));
        int rowIndex = 1;
        for (Policy policy : policies) {
            Source source = sourceMap.get(policy.getSourceId());
            AuditRow audit = auditPolicy(policy, source, regionMap);
            audits.add(audit);
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    policy.getId(), policy.getTitle(), policy.getRegionId(), regionCountry(policy.getRegionId(), regionMap),
                    regionPath(policy.getRegionId(), regionMap), policy.getIssuingBody(), policy.getDocumentNo(),
                    policy.getPublishDate(), policy.getEffectiveDate(), policy.getValidPeriod(), policy.getSourceId(),
                    source == null ? null : source.getTitle(), source == null ? null : source.getPublisher(),
                    source == null ? null : source.getUrl(), source == null ? null : source.getStatus(),
                    source == null ? null : source.getAiEvidenceStatus(), policy.getPolicyLevel(), policy.getPolicyType(),
                    policy.getApplicabilityMode(), policy.getSummary(), policy.getKeyPoints(), policy.getSupportMeasures(),
                    policy.getTags(), policy.getOriginalUrl(), policy.getEvidenceUrl(), policy.getLocalFile(),
                    policy.getAccessedAt(), policy.getStatus(), policy.getReviewer(), policy.getAiEvidenceStatus(),
                    policy.getEvidenceRevision(), policy.getCreatedAt(), policy.getUpdatedAt(), audit.requiredComplete(),
                    audit.missingRequired(), audit.missingRecommended(), audit.paperEligible()
            ));
        }
        finishSheet(sheet, 37);
    }

    private void writePaperCasesSheet(
            Workbook workbook,
            CellStyle headerStyle,
            CellStyle wrapStyle,
            List<CaseItem> cases,
            Map<Long, Source> sourceMap,
            Map<Long, Region> regionMap,
            List<AuditRow> audits
    ) {
        Sheet sheet = workbook.createSheet("cases_full");
        writeHeader(sheet, headerStyle, List.of(
                "ID", "Title", "Article Title", "Region ID", "Country", "Region Path", "Category", "Subcategory", "Actor Name", "Source ID",
                "Source Title", "Source Publisher", "Source URL", "Source Status", "Source Evidence Status",
                "Summary", "Business Model", "AI Tools", "Outcome", "Tags", "Original URL", "Local File",
                "Accessed At", "Status", "Reviewer", "Evidence Status", "Evidence Revision", "Created At",
                "Updated At", "Required Complete", "Missing Required", "Missing Recommended", "Paper Eligible"
        ));
        int rowIndex = 1;
        for (CaseItem item : cases) {
            Source source = sourceMap.get(item.getSourceId());
            AuditRow audit = auditCase(item, source, regionMap);
            audits.add(audit);
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    item.getId(), item.getTitle(), item.getArticleTitle(), item.getRegionId(), regionCountry(item.getRegionId(), regionMap),
                    regionPath(item.getRegionId(), regionMap), item.getCategory(), item.getSubcategory(), item.getActorName(), item.getSourceId(),
                    source == null ? null : source.getTitle(), source == null ? null : source.getPublisher(),
                    source == null ? null : source.getUrl(), source == null ? null : source.getStatus(),
                    source == null ? null : source.getAiEvidenceStatus(), item.getSummary(), item.getBusinessModel(),
                    item.getAiTools(), item.getOutcome(), item.getTags(), item.getOriginalUrl(), item.getLocalFile(),
                    item.getAccessedAt(), item.getStatus(), item.getReviewer(), item.getAiEvidenceStatus(),
                    item.getEvidenceRevision(), item.getCreatedAt(), item.getUpdatedAt(), audit.requiredComplete(),
                    audit.missingRequired(), audit.missingRecommended(), audit.paperEligible()
            ));
        }
        finishSheet(sheet, 33);
    }

    private void writeRegionsSheet(
            Workbook workbook,
            CellStyle headerStyle,
            CellStyle wrapStyle,
            List<Region> regions,
            Map<Long, Region> regionMap
    ) {
        Sheet sheet = workbook.createSheet("regions");
        writeHeader(sheet, headerStyle, List.of(
                "ID", "Name", "Level", "Parent ID", "Country", "Full Path", "Sort Order", "Created At", "Updated At"
        ));
        int rowIndex = 1;
        for (Region region : regions) {
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    region.getId(), region.getName(), region.getLevel(), region.getParentId(),
                    regionCountry(region.getId(), regionMap), regionPath(region.getId(), regionMap),
                    region.getSortOrder(), region.getCreatedAt(), region.getUpdatedAt()
            ));
        }
        finishSheet(sheet, 9);
    }

    private void writeTagsSheet(Workbook workbook, CellStyle headerStyle, CellStyle wrapStyle, List<Tag> tags) {
        Sheet sheet = workbook.createSheet("tags");
        writeHeader(sheet, headerStyle, List.of(
                "ID", "Name", "Tag Type", "Is Industry", "Sort Order", "Created At", "Updated At"
        ));
        int rowIndex = 1;
        for (Tag tag : tags) {
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    tag.getId(), tag.getName(), tag.getTagType(), tag.getIsIndustry(), tag.getSortOrder(),
                    tag.getCreatedAt(), tag.getUpdatedAt()
            ));
        }
        finishSheet(sheet, 7);
    }

    private void writeTagAliasesSheet(
            Workbook workbook, CellStyle headerStyle, CellStyle wrapStyle, List<TagAlias> aliases
    ) {
        Sheet sheet = workbook.createSheet("tag_aliases");
        writeHeader(sheet, headerStyle, List.of(
                "ID", "Tag ID", "Alias", "Normalized Alias", "Created At", "Updated At"
        ));
        int rowIndex = 1;
        for (TagAlias alias : aliases) {
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    alias.getId(), alias.getTagId(), alias.getAlias(), alias.getNormalizedAlias(),
                    alias.getCreatedAt(), alias.getUpdatedAt()
            ));
        }
        finishSheet(sheet, 6);
    }

    private void writePolicyTagsSheet(
            Workbook workbook, CellStyle headerStyle, CellStyle wrapStyle, List<PolicyTag> relations
    ) {
        Sheet sheet = workbook.createSheet("policy_tags");
        writeHeader(sheet, headerStyle, List.of("ID", "Policy ID", "Tag ID", "Created At"));
        int rowIndex = 1;
        for (PolicyTag relation : relations) {
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    relation.getId(), relation.getPolicyId(), relation.getTagId(), relation.getCreatedAt()
            ));
        }
        finishSheet(sheet, 4);
    }

    private void writePolicyIndustryTagsSheet(
            Workbook workbook, CellStyle headerStyle, CellStyle wrapStyle, List<PolicyIndustryTag> relations
    ) {
        Sheet sheet = workbook.createSheet("policy_industry_tags");
        writeHeader(sheet, headerStyle, List.of("ID", "Policy ID", "Industry Tag ID", "Created At"));
        int rowIndex = 1;
        for (PolicyIndustryTag relation : relations) {
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    relation.getId(), relation.getPolicyId(), relation.getIndustryTagId(), relation.getCreatedAt()
            ));
        }
        finishSheet(sheet, 4);
    }

    private void writeCaseTagsSheet(
            Workbook workbook, CellStyle headerStyle, CellStyle wrapStyle, List<CaseTag> relations
    ) {
        Sheet sheet = workbook.createSheet("case_tags");
        writeHeader(sheet, headerStyle, List.of("ID", "Case ID", "Tag ID", "Created At"));
        int rowIndex = 1;
        for (CaseTag relation : relations) {
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    relation.getId(), relation.getCaseId(), relation.getTagId(), relation.getCreatedAt()
            ));
        }
        finishSheet(sheet, 4);
    }

    private void writeAuditSheet(
            Workbook workbook, CellStyle headerStyle, CellStyle wrapStyle, List<AuditRow> audits
    ) {
        Sheet sheet = workbook.createSheet("data_audit");
        writeHeader(sheet, headerStyle, List.of(
                "Record Type", "Record ID", "Title", "Required Complete", "Missing Required",
                "Missing Recommended", "Publication Status", "Evidence Status", "Linked Source Status",
                "Linked Source Evidence Status", "Paper Eligible", "Exclusion Reason"
        ));
        int rowIndex = 1;
        for (AuditRow audit : audits) {
            writeCells(sheet.createRow(rowIndex++), wrapStyle, Arrays.asList(
                    audit.recordType(), audit.recordId(), audit.title(), audit.requiredComplete(),
                    audit.missingRequired(), audit.missingRecommended(), audit.publicationStatus(),
                    audit.evidenceStatus(), audit.sourceStatus(), audit.sourceEvidenceStatus(),
                    audit.paperEligible(), audit.exclusionReason()
            ));
        }
        finishSheet(sheet, 12);
    }

    private AuditRow auditSource(Source source) {
        List<String> required = missingFields(fields(
                "title", source.getTitle(),
                "source_type", source.getSourceType(),
                "publisher", source.getPublisher(),
                "url", source.getUrl(),
                "accessed_at", source.getAccessedAt(),
                "status", source.getStatus(),
                "ai_evidence_status", source.getAiEvidenceStatus()
        ));
        List<String> recommended = missingFields(fields(
                "local_file", source.getLocalFile(),
                "notes", source.getNotes()
        ));
        boolean eligible = required.isEmpty()
                && PUBLISHED_STATUS.equals(source.getStatus())
                && "verified".equals(source.getAiEvidenceStatus());
        return auditRow("source", source.getId(), source.getTitle(), required, recommended,
                source.getStatus(), source.getAiEvidenceStatus(), null, null, eligible);
    }

    private AuditRow auditPolicy(Policy policy, Source source, Map<Long, Region> regionMap) {
        List<String> required = missingFields(fields(
                "title", policy.getTitle(),
                "region_id", regionMap.get(policy.getRegionId()),
                "issuing_body", policy.getIssuingBody(),
                "publish_date", policy.getPublishDate(),
                "source_id", source,
                "policy_level", policy.getPolicyLevel(),
                "policy_type", policy.getPolicyType(),
                "summary", policy.getSummary(),
                "original_url", policy.getOriginalUrl(),
                "accessed_at", policy.getAccessedAt(),
                "status", policy.getStatus(),
                "ai_evidence_status", policy.getAiEvidenceStatus()
        ));
        if (isBlank(policy.getKeyPoints()) && isBlank(policy.getSupportMeasures())) {
            required.add("key_points_or_support_measures");
        }
        List<String> recommended = missingFields(fields(
                "document_no", policy.getDocumentNo(),
                "effective_date", policy.getEffectiveDate(),
                "valid_period", policy.getValidPeriod(),
                "applicability_mode", policy.getApplicabilityMode(),
                "evidence_url", policy.getEvidenceUrl(),
                "tags", policy.getTags()
        ));
        boolean eligible = required.isEmpty()
                && PUBLISHED_STATUS.equals(policy.getStatus())
                && "verified".equals(policy.getAiEvidenceStatus())
                && sourceEligible(source);
        return auditRow("policy", policy.getId(), policy.getTitle(), required, recommended,
                policy.getStatus(), policy.getAiEvidenceStatus(), source == null ? null : source.getStatus(),
                source == null ? null : source.getAiEvidenceStatus(), eligible);
    }

    private AuditRow auditCase(CaseItem item, Source source, Map<Long, Region> regionMap) {
        List<String> required = missingFields(fields(
                "title", item.getTitle(),
                "article_title", item.getArticleTitle(),
                "region_id", regionMap.get(item.getRegionId()),
                "category", item.getCategory(),
                "subcategory", item.getSubcategory(),
                "actor_name", item.getActorName(),
                "source_id", source,
                "summary", item.getSummary(),
                "business_model", item.getBusinessModel(),
                "ai_tools", item.getAiTools(),
                "original_url", item.getOriginalUrl(),
                "accessed_at", item.getAccessedAt(),
                "status", item.getStatus(),
                "ai_evidence_status", item.getAiEvidenceStatus()
        ));
        List<String> recommended = missingFields(fields(
                "outcome", item.getOutcome(),
                "tags", item.getTags()
        ));
        recommended.add("student_status_not_structured");
        boolean eligible = required.isEmpty()
                && PUBLISHED_STATUS.equals(item.getStatus())
                && "verified".equals(item.getAiEvidenceStatus())
                && sourceEligible(source);
        return auditRow("case", item.getId(), item.getTitle(), required, recommended,
                item.getStatus(), item.getAiEvidenceStatus(), source == null ? null : source.getStatus(),
                source == null ? null : source.getAiEvidenceStatus(), eligible);
    }

    private AuditRow auditRow(
            String type, Long id, String title, List<String> required, List<String> recommended,
            String publicationStatus, String evidenceStatus, String sourceStatus,
            String sourceEvidenceStatus, boolean eligible
    ) {
        List<String> reasons = new ArrayList<>();
        if (!required.isEmpty()) reasons.add("missing_required_fields");
        if (!PUBLISHED_STATUS.equals(publicationStatus)) reasons.add("not_published");
        if (!"verified".equals(evidenceStatus)) reasons.add("not_verified");
        if (!"source".equals(type) && !PUBLISHED_STATUS.equals(sourceStatus)) reasons.add("source_not_published");
        if (!"source".equals(type) && !"verified".equals(sourceEvidenceStatus)) reasons.add("source_not_verified");
        return new AuditRow(type, id, title, required.isEmpty(), String.join(";", required),
                String.join(";", recommended), publicationStatus, evidenceStatus, sourceStatus,
                sourceEvidenceStatus, eligible, String.join(";", reasons));
    }

    private List<String> missingFields(Map<String, Object> fields) {
        return fields.entrySet().stream()
                .filter(entry -> isBlank(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<String, Object> fields(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Field entries must be key-value pairs");
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            fields.put((String) entries[index], entries[index + 1]);
        }
        return fields;
    }

    private boolean isBlank(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private boolean sourceEligible(Source source) {
        return source != null
                && PUBLISHED_STATUS.equals(source.getStatus())
                && "verified".equals(source.getAiEvidenceStatus());
    }

    private String regionPath(Long regionId, Map<Long, Region> regionMap) {
        List<String> names = new ArrayList<>();
        Set<Long> visited = new java.util.HashSet<>();
        Region current = regionMap.get(regionId);
        while (current != null && visited.add(current.getId())) {
            names.add(0, current.getName());
            current = regionMap.get(current.getParentId());
        }
        return String.join(" / ", names);
    }

    private String regionCountry(Long regionId, Map<Long, Region> regionMap) {
        Region current = regionMap.get(regionId);
        Region last = current;
        Set<Long> visited = new java.util.HashSet<>();
        while (current != null && visited.add(current.getId())) {
            last = current;
            current = regionMap.get(current.getParentId());
        }
        return last == null ? "" : last.getName();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record AuditRow(
            String recordType,
            Long recordId,
            String title,
            boolean requiredComplete,
            String missingRequired,
            String missingRecommended,
            String publicationStatus,
            String evidenceStatus,
            String sourceStatus,
            String sourceEvidenceStatus,
            boolean paperEligible,
            String exclusionReason
    ) {
    }

    private Map<Long, Region> loadRegionMap(Set<Long> regionIds) {
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        return regionMapper.selectBatchIds(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
    }

    private Map<Long, Source> loadSourceMap(Set<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
        return sourceMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(Source::getId, Function.identity()));
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createWrapStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, List<String> headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeCells(Row row, CellStyle style, List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellStyle(style);
            Object value = values.get(i);
            if (value == null) {
                cell.setCellValue("");
            } else if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof LocalDate localDate) {
                cell.setCellValue(localDate.toString());
            } else {
                cell.setCellValue(value.toString());
            }
        }
    }

    private void finishSheet(Sheet sheet, int columnCount) {
        sheet.createFreezePane(0, 1);
        for (int i = 0; i < columnCount; i++) {
            sheet.setColumnWidth(i, 22 * 256);
        }
    }

    private void writeWorkbook(HttpServletResponse response, Workbook workbook, String filename) throws IOException {
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(EXCEL_CONTENT_TYPE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
        response.setHeader("Cache-Control", "no-store");
        workbook.write(response.getOutputStream());
        response.flushBuffer();
    }
}
