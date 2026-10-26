package com.opc.platform.platformsite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.platformsite.entity.PlatformSite;
import com.opc.platform.platformsite.mapper.PlatformSiteMapper;
import com.opc.platform.platformsite.vo.PlatformSiteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformSiteService {

    private final PlatformSiteMapper platformSiteMapper;

    public List<PlatformSiteVO> listPublishedSites() {
        List<PlatformSite> sites = platformSiteMapper.selectList(
                new LambdaQueryWrapper<PlatformSite>()
                        .eq(PlatformSite::getStatus, "published")
                        .orderByAsc(PlatformSite::getSortOrder)
                        .orderByAsc(PlatformSite::getId)
        );
        return sites.stream().map(this::toVO).toList();
    }

    private PlatformSiteVO toVO(PlatformSite site) {
        PlatformSiteVO vo = new PlatformSiteVO();
        vo.setId(site.getId());
        vo.setName(site.getName());
        vo.setUrl(site.getUrl());
        vo.setCategory(site.getCategory());
        vo.setOwner(site.getOwner());
        vo.setServices(site.getServices());
        vo.setSummary(site.getSummary());
        vo.setDomain(site.getDomain());
        vo.setLogoUrl(site.getFaviconUrl());
        vo.setSortOrder(site.getSortOrder());
        vo.setVerificationStatus(site.getVerificationStatus());
        return vo;
    }
}
