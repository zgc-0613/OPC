const ADMIN_PASSWORD = 'opc2026'
const AUTH_KEY = 'opc_admin_authenticated'

export function isAdminAuthenticated() {
  return sessionStorage.getItem(AUTH_KEY) === 'true'
}

export function loginAdmin(password) {
  const success = password === ADMIN_PASSWORD
  if (success) {
    sessionStorage.setItem(AUTH_KEY, 'true')
  }
  return success
}

export function logoutAdmin() {
  sessionStorage.removeItem(AUTH_KEY)
}
