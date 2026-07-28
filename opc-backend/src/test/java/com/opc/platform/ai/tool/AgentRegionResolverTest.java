package com.opc.platform.ai.tool;

import com.opc.platform.region.entity.Region;
import com.opc.platform.region.mapper.RegionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRegionResolverTest {

    @Test
    void resolvesOnlyAUniquePublicDirectoryRegion() {
        RegionMapper mapper = mock(RegionMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                region(3L, "武汉市", "city", 2L)
        ));

        AgentRegionMatch match = new AgentRegionResolver(mapper).resolve("武汉市");

        assertEquals(3L, match.regionId());
        assertEquals("武汉市", match.regionName());
        assertEquals("city", match.geographicLevel());
        assertEquals(2L, match.parentRegionId());
        assertEquals("exact_name", match.matchReason());
    }

    @Test
    void rejectsAmbiguousOrMissingRegionNames() {
        RegionMapper mapper = mock(RegionMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                region(3L, "武汉市", "city", 2L),
                region(4L, "武汉经济技术开发区", "district", 3L)
        ));

        AgentToolException exception = assertThrows(
                AgentToolException.class,
                () -> new AgentRegionResolver(mapper).resolve("武汉")
        );

        assertEquals("AMBIGUOUS_REGION", exception.getDiagnosticCode());
    }

    @Test
    void derivesParentAndNationalScopesFromTheConfirmedRegionTree() {
        RegionMapper mapper = mock(RegionMapper.class);
        when(mapper.selectById(3L)).thenReturn(region(3L, "Wuhan", "city", 2L));
        when(mapper.selectById(2L)).thenReturn(region(2L, "Hubei", "province", 1L));
        when(mapper.selectById(1L)).thenReturn(region(1L, "China", "country", null));
        AgentRegionResolver resolver = new AgentRegionResolver(mapper);

        assertEquals(List.of(3L), resolver.resolveScope(3L, "selected").regionIds());
        assertEquals(List.of(2L), resolver.resolveScope(3L, "parent").regionIds());
        assertEquals(List.of(1L), resolver.resolveScope(3L, "national").regionIds());
        assertEquals("cross_region_reference",
                resolver.resolveScope(3L, "cross_region_reference").scope());
    }

    @Test
    void provinceParentScopeFallsBackToTheNationalAncestorWithoutFailing() {
        RegionMapper mapper = mock(RegionMapper.class);
        when(mapper.selectById(2L)).thenReturn(region(2L, "Hubei", "province", 1L));
        when(mapper.selectById(1L)).thenReturn(region(1L, "China", "country", null));
        AgentRegionResolver resolver = new AgentRegionResolver(mapper);

        AgentRegionResolver.RegionScope parent = resolver.resolveScope(2L, "parent");
        AgentRegionResolver.RegionScope national = resolver.resolveScope(1L, "national");

        assertEquals("national", parent.scope());
        assertEquals(List.of(1L), parent.regionIds());
        assertEquals("national", national.scope());
        assertEquals(List.of(1L), national.regionIds());
    }

    @Test
    void rejectsARegionScopeThatIsNotInTheClosedCatalog() {
        RegionMapper mapper = mock(RegionMapper.class);
        when(mapper.selectById(3L)).thenReturn(region(3L, "Wuhan", "city", 2L));

        AgentToolException exception = assertThrows(
                AgentToolException.class,
                () -> new AgentRegionResolver(mapper).resolveScope(3L, "arbitrary_region")
        );

        assertEquals("INVALID_REGION_SCOPE", exception.getDiagnosticCode());
    }

    private Region region(Long id, String name, String level, Long parentId) {
        Region region = new Region();
        region.setId(id);
        region.setName(name);
        region.setLevel(level);
        region.setParentId(parentId);
        return region;
    }
}
