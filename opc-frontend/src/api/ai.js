import request from './request'

export function getAiCapabilities() {
  return request.get('/ai/capabilities')
}

export function analyzeCase(caseId, userQuestion = '') {
  const payload = { caseId: Number(caseId) }
  if (userQuestion.trim()) {
    payload.userQuestion = userQuestion.trim()
  }
  return request.post('/ai/case-analysis', payload, { timeout: 120000 })
}

export function getEntrepreneurshipAdvice(payload) {
  return request.post('/ai/entrepreneurship-advice', payload, { timeout: 120000 })
}

export function checkEntrepreneurshipReadiness(payload) {
  return request.post('/ai/entrepreneurship-readiness', payload, { timeout: 45000 })
}

export function resolveIndustryWithAi(industry) {
  return request.post('/ai/industry-resolution', { industry: industry.trim() }, { timeout: 45000 })
}

export function createResearchSession(payload) {
  return request.post('/ai/research/sessions', payload)
}

export function startResearchSession(payload) {
  return request.post('/ai/research/sessions/start', payload, { timeout: 30000 })
}

export function startResearchFromAnalytics(payload) {
  return request.post('/ai/research/from-analytics', payload, { timeout: 30000 })
}

export function getResearchSessions() {
  return request.get('/ai/research/sessions')
}

export function getResearchHistory({ scope = 'active', q = '', cursor, limit = 30 } = {}) {
  return request.get('/ai/research/sessions/history', {
    params: { scope, q, cursor: cursor || undefined, limit },
  })
}

export function getResearchSession(sessionId) {
  return request.get(`/ai/research/sessions/${sessionId}`)
}

export function archiveResearchSession(sessionId) {
  return request.delete(`/ai/research/sessions/${sessionId}`)
}

export function updateResearchSession(sessionId, payload) {
  return request.patch(`/ai/research/sessions/${sessionId}`, payload)
}

export function archiveResearchSessionExplicit(sessionId) {
  return request.post(`/ai/research/sessions/${sessionId}/archive`)
}

export function unarchiveResearchSession(sessionId) {
  return request.post(`/ai/research/sessions/${sessionId}/unarchive`)
}

export function trashResearchSession(sessionId) {
  return request.post(`/ai/research/sessions/${sessionId}/trash`)
}

export function restoreResearchSession(sessionId) {
  return request.post(`/ai/research/sessions/${sessionId}/restore`)
}

export function permanentlyDeleteResearchSession(sessionId) {
  return request.delete(`/ai/research/sessions/${sessionId}/permanent`)
}

export function getResearchMessages(sessionId, { beforeSequence, limit = 50 } = {}) {
  return request.get(`/ai/research/sessions/${sessionId}/messages`, {
    params: { beforeSequence: beforeSequence || undefined, limit },
  })
}

export function getResearchUsage() {
  return request.get('/ai/research/usage')
}

export function getResearchPreferences() {
  return request.get('/ai/research/preferences')
}

export function updateResearchPreferences(payload) {
  return request.patch('/ai/research/preferences', payload)
}

export function clearResearchPreferences() {
  return request.delete('/ai/research/preferences')
}

export function sendResearchMessage(sessionId, payload) {
  return request.post(`/ai/research/sessions/${sessionId}/messages`, payload, { timeout: 30000 })
}

export function getResearchRun(runId) {
  return request.get(`/ai/research/runs/${runId}`)
}

export function getResearchBranchMaterial(runId) {
  return request.get(`/ai/research/runs/${runId}/branch-material`)
}

export function getResearchRunEvidence(runId) {
  return request.get(`/ai/research/runs/${runId}/evidence`)
}

export function getResearchRunFeedback(runId) {
  return request.get(`/ai/research/runs/${runId}/feedback`)
}

export function updateResearchRunFeedback(runId, payload) {
  return request.put(`/ai/research/runs/${runId}/feedback`, payload)
}

export function getResearchReports({ scope = 'active', q = '', cursor, limit = 30 } = {}) {
  return request.get('/ai/research/reports/page', {
    params: { scope, q, cursor: cursor || undefined, limit },
  })
}

export function saveResearchReport(sessionId, payload) {
  return request.post(`/ai/research/sessions/${sessionId}/reports`, payload)
}

export function updateResearchReport(reportId, payload) {
  return request.patch(`/ai/research/reports/${reportId}`, payload)
}

export function trashResearchReport(reportId, payload) {
  return request.post(`/ai/research/reports/${reportId}/trash`, payload)
}

export function restoreResearchReport(reportId, payload) {
  return request.post(`/ai/research/reports/${reportId}/restore`, payload)
}

export function permanentlyDeleteResearchReport(reportId, payload) {
  return request.delete(`/ai/research/reports/${reportId}/permanent`, { data: payload })
}

export function exportResearchReport(reportId, format = 'markdown') {
  return request.get(`/ai/research/reports/${reportId}/export`, {
    params: { format },
    responseType: 'blob',
  })
}

export function cancelResearchRun(runId) {
  return request.post(`/ai/research/runs/${runId}/cancel`)
}

export function getAdminAgentRuns(limit = 50) {
  return request.get('/admin/ai-agent-runs', { params: { limit } })
}

export function getAdminAgentRun(runId) {
  return request.get(`/admin/ai-agent-runs/${runId}`)
}

export function getAdminResearchQuality(params = {}) {
  return request.get('/admin/ai/research/quality', { params })
}
