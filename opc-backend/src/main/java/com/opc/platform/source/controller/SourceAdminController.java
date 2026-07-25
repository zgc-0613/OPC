package com.opc.platform.source.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.source.dto.SourceCreateDTO;
import com.opc.platform.source.dto.SourceUpdateDTO;
import com.opc.platform.source.service.SourceService;
import com.opc.platform.source.vo.SourceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

import static com.opc.platform.adminauth.AdminAuthInterceptor.AUTHENTICATED_ADMIN_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sources")
public class SourceAdminController {

    private final SourceService sourceService;

    @GetMapping
    public Result<List<SourceVO>> listSources() {
        return Result.success(sourceService.listSources());
    }

    @PostMapping
    public Result<SourceVO> createSource(@Valid @RequestBody SourceCreateDTO dto) {
        return Result.success(sourceService.createSource(dto));
    }

    @PutMapping("/{id}")
    public Result<SourceVO> updateSource(
            @PathVariable Long id,
            @Valid @RequestBody SourceUpdateDTO dto,
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        return Result.success(sourceService.updateSource(id, dto, admin));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSource(
            @PathVariable Long id,
            @RequestParam Long expectedEvidenceRevision,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedUpdatedAt
    ) {
        sourceService.deleteSource(id, expectedEvidenceRevision, expectedUpdatedAt);
        return Result.success();
    }
}
