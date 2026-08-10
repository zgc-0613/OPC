import request from './request'

export function getAnalyticsOverview() {
  return request.get('/analytics/overview')
}

export function getAnalyticsIndustries(industryTagIds = []) {
  return getAnalyticsResource('/analytics/industries', {
    metricId: 'industry.case_count',
    industryTagIds: Array.isArray(industryTagIds) ? industryTagIds : [],
  })
}

export function getAnalyticsTechnologies(filters = {}) {
  return getAnalyticsResource('/analytics/technologies', {
    metricId: 'technology.case_count',
    ...normalizeFilters(filters, 'technologyTagIds'),
  })
}

export function getAnalyticsRevenue(filters = {}) {
  return getAnalyticsResource('/analytics/revenue', {
    metricId: 'revenue.range_distribution',
    ...normalizeFilters(filters),
  })
}

export function getAnalyticsRegions(filters = {}) {
  return getAnalyticsResource('/analytics/regions', {
    metricId: 'region.case_count',
    regionRole: 'operation',
    ...normalizeFilters(filters),
  })
}

export function getAnalyticsTrends(filters = {}) {
  return getAnalyticsResource('/analytics/trends', {
    metricId: 'trend.policy_publish_time',
    ...normalizeFilters(filters),
  })
}

export function getAnalyticsDrilldown(filters = {}) {
  return getAnalyticsResource('/analytics/drilldown', normalizeFilters(filters))
}

function getAnalyticsResource(path, filters) {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value === null || value === undefined || value === '') return
    const values = Array.isArray(value) ? value : [value]
    values.forEach((item) => {
      if (item !== null && item !== undefined && item !== '') params.append(key, String(item))
    })
  })
  return request.get(path, { params })
}

function normalizeFilters(value, arrayKey) {
  if (Array.isArray(value) && arrayKey) return { [arrayKey]: value }
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}
