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

    private Region region(Long id, String name, String level, Long parentId) {
        Region region = new Region();
        region.setId(id);
        region.setName(name);
        region.setLevel(level);
        region.setParentId(parentId);
        return region;
    }
}
