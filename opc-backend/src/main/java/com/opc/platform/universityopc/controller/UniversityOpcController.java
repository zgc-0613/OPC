package com.opc.platform.universityopc.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.universityopc.mapper.UniversityOpcMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/university-opc")
public class UniversityOpcController {

    private final UniversityOpcMapper universityOpcMapper;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.success(universityOpcMapper.selectPublicRecords());
    }
}
