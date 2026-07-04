package com.opc.platform.tag.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.tag.dto.TagCreateDTO;
import com.opc.platform.tag.dto.TagUpdateDTO;
import com.opc.platform.tag.service.TagService;
import com.opc.platform.tag.vo.TagVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tags")
public class TagAdminController {

    private final TagService tagService;

    @PostMapping
    public Result<TagVO> createTag(@Valid @RequestBody TagCreateDTO dto) {
        return Result.success(tagService.createTag(dto));
    }

    @PutMapping("/{id}")
    public Result<TagVO> updateTag(@PathVariable Long id, @Valid @RequestBody TagUpdateDTO dto) {
        return Result.success(tagService.updateTag(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success();
    }
}
