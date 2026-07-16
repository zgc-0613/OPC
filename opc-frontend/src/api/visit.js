import request from './request'

export function recordVisit(payload) {
  return request.post('/public/visits', payload)
}

export function getVisitSummary() {
  return request.get('/public/visits/summary')
}

export function getVisitRankings(params = {}) {
  return request.get('/public/visits/rankings', { params })
}

export function getVisitTrend(params = {}) {
  return request.get('/public/visits/trend', { params })
}