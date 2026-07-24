export const DEFAULT_EVIDENCE_QUERY = Object.freeze({
  keyword: '',
  itemType: '',
  evidenceStatus: 'legacy_unverified',
  reviewability: 'all',
  sourceId: '',
  sort: 'updated_desc',
  page: 1,
  size: 20,
})

export function evidenceItemKey(item) {
  return `${item.itemType}:${item.itemId}`
}

export function groupEvidenceBySource(items = []) {
  const groups = new Map()
  for (const item of items) {
    const sourceId = item.sourceId || (item.itemType === 'source' ? item.itemId : null)
    const key = sourceId ? `source:${sourceId}` : 'source:missing'
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        sourceId,
        source: null,
        title: item.sourceTitle || '未关联来源',
        items: [],
      })
    }
    const group = groups.get(key)
    if (item.itemType === 'source') {
      group.source = item
      group.title = item.title || group.title
    } else {
      group.items.push(item)
    }
  }
  return [...groups.values()].sort((left, right) => {
    if (left.source && !right.source) return -1
    if (!left.source && right.source) return 1
    return String(left.title).localeCompare(String(right.title), 'zh-CN')
  })
}

export function readEvidenceQuery(routeQuery = {}) {
  const page = Math.max(1, Number(routeQuery.page || DEFAULT_EVIDENCE_QUERY.page))
  const sourceId = routeQuery.source ? Number(routeQuery.source) : ''
  return {
    ...DEFAULT_EVIDENCE_QUERY,
    keyword: String(routeQuery.q || ''),
    itemType: String(routeQuery.type || ''),
    evidenceStatus: String(routeQuery.status || DEFAULT_EVIDENCE_QUERY.evidenceStatus),
    reviewability: String(routeQuery.reviewability || DEFAULT_EVIDENCE_QUERY.reviewability),
    sourceId: Number.isFinite(sourceId) ? sourceId : '',
    sort: String(routeQuery.sort || DEFAULT_EVIDENCE_QUERY.sort),
    page,
  }
}

export function writeEvidenceQuery(query, selectedKey = '') {
  const values = {
    q: query.keyword || undefined,
    type: query.itemType || undefined,
    status: query.evidenceStatus || undefined,
    reviewability: query.reviewability !== 'all' ? query.reviewability : undefined,
    source: query.sourceId || undefined,
    sort: query.sort !== 'updated_desc' ? query.sort : undefined,
    page: Number(query.page) > 1 ? Number(query.page) : undefined,
    selected: selectedKey || undefined,
  }
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined))
}

export function buildEvidenceDecisionPayload(detail, evidenceStatus, options = {}) {
  return {
    evidenceStatus,
    expectedEvidenceStatus: detail.evidenceStatus,
    expectedUpdatedAt: detail.updatedAt,
    expectedVersion: detail.version,
    reason: String(options.reason || '').trim() || undefined,
    notes: String(options.notes || '').trim() || undefined,
    cascade: Boolean(options.cascade),
  }
}

export function buildEvidenceBatchPayload(items, selectedKeys, evidenceStatus, options = {}) {
  return {
    items: items
      .filter((item) => selectedKeys.has(evidenceItemKey(item)))
      .map((item) => ({
        itemType: item.itemType,
        itemId: item.itemId,
        expectedEvidenceStatus: item.evidenceStatus,
        expectedUpdatedAt: item.updatedAt,
        expectedVersion: item.version,
      })),
    evidenceStatus,
    reason: String(options.reason || '').trim() || undefined,
    notes: String(options.notes || '').trim() || undefined,
    cascade: Boolean(options.cascade),
  }
}

export function nextEvidenceItem(items, currentKey) {
  if (!items.length) return null
  const index = items.findIndex((item) => evidenceItemKey(item) === currentKey)
  if (index < 0) return items[0]
  return items[index + 1] || items[index - 1] || null
}

export function reconcileEvidenceSelection(items, selectedKeys) {
  const visibleKeys = new Set(items.map(evidenceItemKey))
  return new Set([...selectedKeys].filter((key) => visibleKeys.has(key)))
}

export function clampEvidencePage(page, total, size) {
  return Math.min(Math.max(1, Number(page) || 1), Math.max(1, Math.ceil(Number(total || 0) / Number(size || 20))))
}

export function buildEvidenceEditPayload(itemType, content = {}) {
  const expectedEvidenceRevision = Number(content.evidenceRevision ?? 0)
  const expectedUpdatedAt = content.updatedAt || undefined
  const ignored = new Set([
    'id', 'createdAt', 'updatedAt', 'evidenceRevision', 'aiEvidenceStatus', 'regionName', 'sourceTitle',
  ])
  const payload = Object.fromEntries(
    Object.entries(content).filter(([key]) => !ignored.has(key)),
  )
  if (itemType === 'case' || itemType === 'policy') {
    payload.regionId = Number(payload.regionId)
    payload.sourceId = Number(payload.sourceId)
  }
  payload.expectedEvidenceRevision = expectedEvidenceRevision
  payload.expectedUpdatedAt = expectedUpdatedAt
  return payload
}

export function hydrateEvidenceEditForm(content = {}) {
  const seen = new WeakMap()

  function cloneValue(value) {
    if (value === null || typeof value !== 'object') return value
    if (seen.has(value)) return seen.get(value)

    const clone = Array.isArray(value) ? [] : {}
    seen.set(value, clone)
    for (const [key, nestedValue] of Object.entries(value)) {
      clone[key] = cloneValue(nestedValue)
    }
    return clone
  }

  return cloneValue(content)
}

export function resolveEvidencePane(isCompact, selectedKey) {
  return isCompact && selectedKey ? 'detail' : 'queue'
}

export function isLatestEvidenceRequest(requestId, latestRequestId) {
  return requestId === latestRequestId
}
