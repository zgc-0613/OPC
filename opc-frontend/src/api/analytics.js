import request from './request'

export function getAnalyticsOverview() {
  return request.get('/analytics/overview')
}

export function getAnalyticsIndustries(industryTagIds = []) {
  const params = new URLSearchParams()
  params.append('metricId', 'industry.case_count')
  if (Array.isArray(industryTagIds) && industryTagIds.length) {
    industryTagIds.forEach((tagId) => params.append('industryTagIds', String(tagId)))
  }
  return request.get('/analytics/industries', { params })
}
