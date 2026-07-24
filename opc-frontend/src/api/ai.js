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
