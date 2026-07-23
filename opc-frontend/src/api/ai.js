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
