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

export function getResearchSessions() {
  return request.get('/ai/research/sessions')
}

export function getResearchSession(sessionId) {
  return request.get(`/ai/research/sessions/${sessionId}`)
}

export function archiveResearchSession(sessionId) {
  return request.delete(`/ai/research/sessions/${sessionId}`)
}

export function sendResearchMessage(sessionId, payload) {
  return request.post(`/ai/research/sessions/${sessionId}/messages`, payload, { timeout: 30000 })
}

export function getResearchRun(runId) {
  return request.get(`/ai/research/runs/${runId}`)
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
