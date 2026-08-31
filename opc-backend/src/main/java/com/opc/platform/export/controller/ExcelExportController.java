package com.opc.platform.export.controller;

import com.opc.platform.export.service.ExcelExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/export")
public class ExcelExportController {

    private final ExcelExportService excelExportService;

    @GetMapping("/sources.xlsx")
    public void exportSources(HttpServletResponse response) throws IOException {
        excelExportService.exportSources(response);
    }

    @GetMapping("/policies.xlsx")
    public void exportPolicies(HttpServletResponse response) throws IOException {
        excelExportService.exportPolicies(response);
    }

    @GetMapping("/cases.xlsx")
    public void exportCases(HttpServletResponse response) throws IOException {
        excelExportService.exportCases(response);
    }

    @GetMapping("/paper-dataset.xlsx")
    public void exportPaperDataset(HttpServletResponse response) throws IOException {
        excelExportService.exportPaperDataset(response);
    }
}
