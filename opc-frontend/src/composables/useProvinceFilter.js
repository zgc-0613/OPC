import { computed, ref } from 'vue'

export function resolveProvinceId(regionId, regions) {
  const byId = new Map((regions || []).map((region) => [Number(region.id), region]))
  const visited = new Set()
  let current = byId.get(Number(regionId))

  while (current && !visited.has(Number(current.id))) {
    const currentId = Number(current.id)
    visited.add(currentId)
    if (current.level === 'province') return currentId
    current = byId.get(Number(current.parentId))
  }
  return null
}

export function useProvinceFilter(items, regions) {
  const selectedProvinceId = ref('')
  const provinceOptions = computed(() => [...regions.value]
    .filter((region) => region.level === 'province')
    .sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
      || Number(left.id) - Number(right.id)))
  const filteredItems = computed(() => {
    if (!selectedProvinceId.value) return items.value
    const provinceId = Number(selectedProvinceId.value)
    return items.value.filter((item) => resolveProvinceId(item.regionId, regions.value) === provinceId)
  })

  return { filteredItems, provinceOptions, selectedProvinceId }
}
