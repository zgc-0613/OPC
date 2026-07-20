import request from './request'

export function getAdminUsers(params = {}) {
  return request.get('/admin/users', { params })
}

export function updateAdminUserStatus(id, status) {
  return request.patch(`/admin/users/${id}/status`, { status })
}

export function revokeAdminUserSessions(id) {
  return request.post(`/admin/users/${id}/revoke-sessions`)
}

export function deleteAdminUser(id) {
  return request.delete(`/admin/users/${id}`)
}

export function getMailSettings() {
  return request.get('/admin/settings/mail')
}

export function updateMailSettings(payload) {
  return request.put('/admin/settings/mail', payload)
}

export function testMailConnection(payload) {
  return request.post('/admin/settings/mail/test-connection', payload)
}

export function sendMailTest(payload) {
  return request.post('/admin/settings/mail/test-email', payload)
}

export function getCaptchaSettings() {
  return request.get('/admin/settings/captcha')
}

export function updateCaptchaSettings(payload) {
  return request.put('/admin/settings/captcha', payload)
}

export function getAdminRegistrationRequests(status = 'pending') {
  return request.get('/admin/registration-requests', { params: { status } })
}

export function approveAdminRegistration(id) {
  return request.post(`/admin/registration-requests/${id}/approve`)
}

export function rejectAdminRegistration(id) {
  return request.post(`/admin/registration-requests/${id}/reject`)
}

export function deleteAdminRegistrationRecord(id) {
  return request.delete(`/admin/registration-requests/${id}`)
}

export function getAdminAccounts() {
  return request.get('/admin/accounts')
}

export function deleteAdminAccount(id) {
  return request.delete(`/admin/accounts/${id}`)
}
