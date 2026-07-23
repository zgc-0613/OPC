import { computed, onBeforeUnmount, reactive } from 'vue'

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

export function useResizableColumns(storageKey, columns) {
  const definitions = new Map(columns.map((column) => [column.key, column]))
  const storedWidths = loadStoredWidths(storageKey)
  const columnWidths = reactive(Object.fromEntries(columns.map((column) => {
    const stored = Number(storedWidths[column.key])
    const width = Number.isFinite(stored) ? stored : column.width
    return [column.key, clamp(width, column.minWidth || 64, column.maxWidth || 720)]
  })))
  const tableWidth = computed(() => (
    columns.reduce((total, column) => total + columnWidths[column.key], 0)
  ))
  const columnPercentages = computed(() => Object.fromEntries(columns.map((column) => [
    column.key,
    (columnWidths[column.key] / tableWidth.value) * 100,
  ])))
  let resizeState = null

  function startResize(event, columnKey) {
    const definition = definitions.get(columnKey)
    if (!definition || definition.resizable === false || event.button > 0) {
      return
    }

    const partner = getResizePartner(columnKey)
    if (!partner) {
      return
    }

    resizeState = {
      columnKey,
      partnerKey: partner.key,
      startX: event.clientX,
      startWidth: columnWidths[columnKey],
      partnerStartWidth: columnWidths[partner.key],
    }
    document.body.classList.add('is-resizing-admin-column')
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', finishResize, { once: true })
    window.addEventListener('pointercancel', finishResize, { once: true })
  }

  function handlePointerMove(event) {
    if (!resizeState) {
      return
    }
    applyResizeDelta(
      resizeState.columnKey,
      resizeState.partnerKey,
      event.clientX - resizeState.startX,
      resizeState.startWidth,
      resizeState.partnerStartWidth,
    )
  }

  function resizeBy(columnKey, delta) {
    const definition = definitions.get(columnKey)
    if (!definition || definition.resizable === false) {
      return
    }
    const partner = getResizePartner(columnKey)
    if (!partner) {
      return
    }
    applyResizeDelta(
      columnKey,
      partner.key,
      delta,
      columnWidths[columnKey],
      columnWidths[partner.key],
    )
    persistWidths()
  }

  function getResizePartner(columnKey) {
    const index = columns.findIndex((column) => column.key === columnKey)
    return columns[index + 1]?.adjustable === false ? null : columns[index + 1] || null
  }

  function applyResizeDelta(columnKey, partnerKey, requestedDelta, startWidth, partnerStartWidth) {
    const definition = definitions.get(columnKey)
    const partner = definitions.get(partnerKey)
    const minimumDelta = Math.max(
      (definition.minWidth || 64) - startWidth,
      partnerStartWidth - (partner.maxWidth || 720),
    )
    const maximumDelta = Math.min(
      (definition.maxWidth || 720) - startWidth,
      partnerStartWidth - (partner.minWidth || 64),
    )
    const delta = clamp(requestedDelta, minimumDelta, maximumDelta)
    columnWidths[columnKey] = startWidth + delta
    columnWidths[partnerKey] = partnerStartWidth - delta
  }

  function finishResize() {
    resizeState = null
    document.body.classList.remove('is-resizing-admin-column')
    window.removeEventListener('pointermove', handlePointerMove)
    window.removeEventListener('pointerup', finishResize)
    window.removeEventListener('pointercancel', finishResize)
    persistWidths()
  }

  function persistWidths() {
    try {
      localStorage.setItem(storageKey, JSON.stringify({ ...columnWidths }))
    } catch {
      // Column resizing still works when persistent storage is unavailable.
    }
  }

  onBeforeUnmount(finishResize)

  return {
    columnPercentages,
    columnWidths,
    resizeBy,
    startResize,
    tableWidth,
  }
}

function loadStoredWidths(storageKey) {
  try {
    return JSON.parse(localStorage.getItem(storageKey) || '{}')
  } catch {
    return {}
  }
}
