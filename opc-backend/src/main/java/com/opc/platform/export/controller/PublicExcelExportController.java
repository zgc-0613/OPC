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
@RequestMapping("/api/public/export")
public class PublicExcelExportController {

    private final ExcelExportService excelExportService;

    @GetMapping("/policies.xlsx")
    public void exportPolicies(HttpServletResponse response) throws IOException {
        excelExportService.exportPublishedPolicies(response);
    }
}
