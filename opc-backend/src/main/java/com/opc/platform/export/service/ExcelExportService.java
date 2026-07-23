package com.opc.platform.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
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
import java.util.Arrays;
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
                    "ID", "Title", "Region ID", "Region Name", "Category", "Actor Name",
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
                        caseItem.getRegionId(),
                        region == null ? null : region.getName(),
                        caseItem.getCategory(),
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

            finishSheet(sheet, 18);
            writeWorkbook(response, workbook, "cases.xlsx");
        }
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
