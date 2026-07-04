package com.opc.platform.tag.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.tag.service.TagService;
import com.opc.platform.tag.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/tags")
public class TagController {

    private final TagService tagService;

    @GetMapping
    public Result<List<TagVO>> listTags(@RequestParam(required = false) String tagType) {
        return Result.success(tagService.listTags(tagType));
    }
}
