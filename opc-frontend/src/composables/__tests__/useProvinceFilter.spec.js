import { ref } from 'vue'
import { describe, expect, it } from 'vitest'

import { resolveProvinceId, useProvinceFilter } from '@/composables/useProvinceFilter'

const regions = [
  { id: 1, name: '中国', level: 'country', parentId: null, sortOrder: 1 },
  { id: 33, name: '浙江省', level: 'province', parentId: 1, sortOrder: 33 },
  { id: 3301, name: '杭州市', level: 'city', parentId: 33, sortOrder: 1 },
  { id: 330106, name: '西湖区', level: 'district', parentId: 3301, sortOrder: 1 },
  { id: 44, name: '广东省', level: 'province', parentId: 1, sortOrder: 44 },
]

describe('useProvinceFilter', () => {
  it('rolls city and district records up to their province', () => {
    expect(resolveProvinceId(330106, regions)).toBe(33)
    expect(resolveProvinceId(3301, regions)).toBe(33)
    expect(resolveProvinceId(44, regions)).toBe(44)
  })

  it('filters records by province while retaining the complete list by default', () => {
    const items = ref([{ id: 1, regionId: 330106 }, { id: 2, regionId: 44 }])
    const filter = useProvinceFilter(items, ref(regions))

    expect(filter.filteredItems.value.map((item) => item.id)).toEqual([1, 2])
    filter.selectedProvinceId.value = '33'
    expect(filter.filteredItems.value.map((item) => item.id)).toEqual([1])
    expect(filter.provinceOptions.value.map((item) => item.id)).toEqual([33, 44])
  })
})
