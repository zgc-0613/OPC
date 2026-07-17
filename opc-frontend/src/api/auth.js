import request from './request'

const ADMIN_PASSWORD = 'opc2026'
const ADMIN_AUTH_KEY = 'opc_admin_authenticated'
const USER_TOKEN_KEY = 'opc_user_token'
const USER_PROFILE_KEY = 'opc_user_profile'

export function isAdminAuthenticated() {
  return sessionStorage.getItem(ADMIN_AUTH_KEY) === 'true'
}

export function loginAdmin(password) {
  const success = password === ADMIN_PASSWORD
  if (success) {
    sessionStorage.setItem(ADMIN_AUTH_KEY, 'true')
  }
  return success
}

export function logoutAdmin() {
  sessionStorage.removeItem(ADMIN_AUTH_KEY)
}

export function getUserToken() {
  return sessionStorage.getItem(USER_TOKEN_KEY) || ''
}

export function getUserProfile() {
  const raw = sessionStorage.getItem(USER_PROFILE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function isUserAuthenticated() {
  return Boolean(getUserToken())
}

export function sendUserEmailCode(email) {
  return request.post('/auth/email-code', { email })
}

export async function verifyUserEmailCode(payload) {
  const user = await request.post('/auth/verify', payload)
  saveUserSession(user)
  return user
}

export async function fetchCurrentUser() {
  const user = await request.get('/auth/me')
  saveUserSession(user)
  return user
}

export async function logoutUser() {
  try {
    if (getUserToken()) {
      await request.post('/auth/logout')
    }
  } finally {
    clearUserSession()
  }
}

function saveUserSession(user) {
  if (!user?.token) {
    return
  }
  sessionStorage.setItem(USER_TOKEN_KEY, user.token)
  sessionStorage.setItem(USER_PROFILE_KEY, JSON.stringify({
    userId: user.userId,
    username: user.username,
    email: user.email,
    expiresAt: user.expiresAt,
  }))
}

function clearUserSession() {
  sessionStorage.removeItem(USER_TOKEN_KEY)
  sessionStorage.removeItem(USER_PROFILE_KEY)
}
