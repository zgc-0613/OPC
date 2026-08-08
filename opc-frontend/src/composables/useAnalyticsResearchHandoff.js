const HANDOFF_VERSION = 1
const KEY_PREFIX = 'opc_analytics_research_draft:user:'
const SUPPORTED_METRICS = new Set([
  'overview.verified_cases',
  'overview.verified_policies',
  'overview.verified_sources',
  'industry.case_count',
])

export function analyticsResearchDraftKey(userId) {
  return `${KEY_PREFIX}${String(userId || 'anonymous')}`
}

export function createAnalyticsResearchDraft(card, dataVersion) {
  const metricId = text(card?.metricId, 80)
  const metricLabel = text(card?.label, 120)
  const version = text(dataVersion, 128)
  if (!SUPPORTED_METRICS.has(metricId) || !metricLabel || !version) return null
  const selectedBucketIds = metricId === 'industry.case_count'
    ? normalizeIndustryBuckets([card?.bucketId])
    : []
  if (metricId === 'industry.case_count' && selectedBucketIds.length !== 1) return null
  const filters = metricId === 'industry.case_count'
    ? normalizeIndustryFilters({ industryTagId: card?.industryTagId }, selectedBucketIds)
    : {}
  if (!filters) return null

  return {
    version: HANDOFF_VERSION,
    metricId,
    metricLabel,
    dataVersion: version,
    filters,
    selectedBucketIds,
    userQuestion: `请基于“${metricLabel}”这一已核验数据指标，说明它对当前创业研究范围意味着什么；区分可确认事实、推断、风险和下一步行动。`,
  }
}

export function saveAnalyticsResearchDraft(storage, userId, draft) {
  const normalized = normalizeAnalyticsResearchDraft(draft)
  if (!normalized) return null
  storage.setItem(analyticsResearchDraftKey(userId), JSON.stringify(normalized))
  return normalized
}

export function readAnalyticsResearchDraft(storage, userId) {
  const key = analyticsResearchDraftKey(userId)
  try {
    const normalized = normalizeAnalyticsResearchDraft(JSON.parse(storage.getItem(key) || 'null'))
    if (normalized) return normalized
  } catch {
    // An interrupted or manually changed browser draft must not start research.
  }
  storage.removeItem(key)
  return null
}

export function clearAnalyticsResearchDraft(storage, userId) {
  storage.removeItem(analyticsResearchDraftKey(userId))
}

function normalizeAnalyticsResearchDraft(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null
  const metricId = text(value.metricId, 80)
  const metricLabel = text(value.metricLabel, 120)
  const dataVersion = text(value.dataVersion, 128)
  const userQuestion = text(value.userQuestion, 2000)
  if (value.version !== HANDOFF_VERSION || !SUPPORTED_METRICS.has(metricId) || !metricLabel || !dataVersion || !userQuestion) return null
  if (!Array.isArray(value.selectedBucketIds)) return null
  const selectedBucketIds = metricId === 'industry.case_count'
    ? normalizeIndustryBuckets(value.selectedBucketIds)
    : value.selectedBucketIds.length === 0 ? [] : null
  if (!selectedBucketIds || (metricId === 'industry.case_count' && selectedBucketIds.length !== 1)) return null
  const filters = metricId === 'industry.case_count'
    ? normalizeIndustryFilters(value.filters, selectedBucketIds)
    : isEmptyObject(value.filters) ? {} : null
  if (!filters) return null
  return { version: HANDOFF_VERSION, metricId, metricLabel, dataVersion, filters, selectedBucketIds, userQuestion }
}

function normalizeIndustryBuckets(values) {
  if (!Array.isArray(values) || values.length !== 1) return []
  const bucketId = text(values[0], 128)
  return /^industry:[1-9]\d*$/.test(bucketId) ? [bucketId] : []
}

function normalizeIndustryFilters(value, selectedBucketIds) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
    || Object.keys(value).length !== 1 || !Object.hasOwn(value, 'industryTagId')) return null
  const industryTagId = Number(value.industryTagId)
  if (!Number.isSafeInteger(industryTagId) || industryTagId <= 0
    || selectedBucketIds[0] !== `industry:${industryTagId}`) return null
  return { industryTagId }
}

function isEmptyObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0
}

function text(value, maxLength) {
  if (typeof value !== 'string') return ''
  const normalized = value.trim()
  return normalized.length > 0 && normalized.length <= maxLength ? normalized : ''
}
