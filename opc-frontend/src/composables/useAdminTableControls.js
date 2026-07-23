import { computed, ref, watch } from 'vue'

function isEmptyValue(value) {
  return value === null || value === undefined || value === ''
}

function compareValues(left, right) {
  if (typeof left === 'number' && typeof right === 'number') {
    return left - right
  }

  return String(left).localeCompare(String(right), 'zh-CN', {
    numeric: true,
    sensitivity: 'base',
  })
}

export function useAdminTableControls(items, defaultSortKey = 'id') {
  const sortColumn = ref(null)
  const sortDirection = ref(null)
  const selectedIds = ref(new Set())

  const sortedItems = computed(() => {
    const column = sortColumn.value || defaultSortKey
    const direction = sortDirection.value || 'asc'

    return [...items.value].sort((leftItem, rightItem) => {
      const left = leftItem[column]
      const right = rightItem[column]
      const leftEmpty = isEmptyValue(left)
      const rightEmpty = isEmptyValue(right)

      if (leftEmpty !== rightEmpty) {
        return leftEmpty ? 1 : -1
      }

      const compared = leftEmpty ? 0 : compareValues(left, right)
      if (compared !== 0) {
        return direction === 'desc' ? -compared : compared
      }
      return Number(leftItem.id || 0) - Number(rightItem.id || 0)
    })
  })

  const allSelected = computed(() => (
    sortedItems.value.length > 0
    && sortedItems.value.every((item) => selectedIds.value.has(item.id))
  ))
  const someSelected = computed(() => selectedIds.value.size > 0 && !allSelected.value)
  const selectedCount = computed(() => selectedIds.value.size)

  function toggleSort(column) {
    if (sortColumn.value !== column) {
      sortColumn.value = column
      sortDirection.value = 'asc'
      return
    }

    if (sortDirection.value === 'asc') {
      sortDirection.value = 'desc'
      return
    }

    sortColumn.value = null
    sortDirection.value = null
  }

  function toggleAll(checked) {
    selectedIds.value = checked
      ? new Set(sortedItems.value.map((item) => item.id))
      : new Set()
  }

  function toggleRow(id, checked) {
    const next = new Set(selectedIds.value)
    if (checked) {
      next.add(id)
    } else {
      next.delete(id)
    }
    selectedIds.value = next
  }

  function replaceSelection(ids) {
    selectedIds.value = new Set(ids)
  }

  function clearSelection() {
    selectedIds.value = new Set()
  }

  watch(items, (currentItems) => {
    const availableIds = new Set(currentItems.map((item) => item.id))
    replaceSelection([...selectedIds.value].filter((id) => availableIds.has(id)))
  })

  return {
    allSelected,
    clearSelection,
    replaceSelection,
    selectedCount,
    selectedIds,
    someSelected,
    sortColumn,
    sortDirection,
    sortedItems,
    toggleAll,
    toggleRow,
    toggleSort,
  }
}
