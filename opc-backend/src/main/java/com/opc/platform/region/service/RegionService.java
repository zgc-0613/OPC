package com.opc.platform.region.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.region.vo.RegionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionMapper regionMapper;

    public List<RegionVO> listRegions() {
        List<Region> regions = regionMapper.selectList(
                new LambdaQueryWrapper<Region>()
                        .orderByAsc(Region::getSortOrder)
                        .orderByAsc(Region::getId)
        );

        return regions.stream()
                .map(this::toVO)
                .toList();
    }

    private RegionVO toVO(Region region) {
        RegionVO vo = new RegionVO();
        vo.setId(region.getId());
        vo.setName(region.getName());
        vo.setLevel(region.getLevel());
        vo.setParentId(region.getParentId());
        vo.setSortOrder(region.getSortOrder());
        return vo;
    }
}