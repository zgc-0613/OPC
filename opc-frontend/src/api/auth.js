import request from './request'

const ADMIN_TOKEN_KEY = 'opc_admin_token'
const ADMIN_EXPIRY_KEY = 'opc_admin_expires_at'
const ADMIN_USERNAME_KEY = 'opc_admin_username'
const USER_TOKEN_KEY = 'opc_user_token'
const USER_PROFILE_KEY = 'opc_user_profile'

export function isAdminAuthenticated() {
  const token = getAdminSessionValue(ADMIN_TOKEN_KEY)
  const expiresAt = getAdminSessionValue(ADMIN_EXPIRY_KEY)
  if (!token || !expiresAt || new Date(expiresAt).getTime() <= Date.now()) {
    clearAdminSession()
    return false
  }
  return true
}

export function getAdminUsername() {
  return getAdminSessionValue(ADMIN_USERNAME_KEY)
}

export async function loginAdmin(username, password) {
  const session = await request.post('/admin/auth/login', { username, password })
  localStorage.setItem(ADMIN_TOKEN_KEY, session.token)
  localStorage.setItem(ADMIN_EXPIRY_KEY, session.expiresAt)
  localStorage.setItem(ADMIN_USERNAME_KEY, session.username)
  return session
}

export function submitAdminRegistrationRequest(username, password) {
  return request.post('/admin/auth/register-request', { username, password })
}

export async function logoutAdmin() {
  try {
    if (getAdminSessionValue(ADMIN_TOKEN_KEY)) {
      await request.post('/admin/auth/logout')
    }
  } finally {
    clearAdminSession()
  }
}

export function getUserToken() {
  return getUserSessionValue(USER_TOKEN_KEY)
}

export function getUserProfile() {
  const raw = getUserSessionValue(USER_PROFILE_KEY)
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

export function getAltchaConfig() {
  return request.get('/auth/altcha/config')
}

export function sendUserEmailCode(email, altcha = '') {
  return request.post('/auth/email-code', { email, altcha })
}

export async function loginUser(identifier, password) {
  const user = await request.post('/auth/login', { identifier, password })
  saveUserSession(user)
  return user
}

export async function registerUser(payload) {
  const user = await request.post('/auth/register', payload)
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

  localStorage.setItem(USER_TOKEN_KEY, user.token)
  localStorage.setItem(USER_PROFILE_KEY, JSON.stringify({
    userId: user.userId,
    username: user.username,
    email: user.email,
    expiresAt: user.expiresAt,
  }))
  sessionStorage.removeItem(USER_TOKEN_KEY)
  sessionStorage.removeItem(USER_PROFILE_KEY)
}

function clearUserSession() {
  for (const key of [USER_TOKEN_KEY, USER_PROFILE_KEY]) {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  }
}

function getUserSessionValue(key) {
  const persistentValue = localStorage.getItem(key)
  if (persistentValue) {
    return persistentValue
  }

  const legacyValue = sessionStorage.getItem(key) || ''
  if (legacyValue) {
    localStorage.setItem(key, legacyValue)
    sessionStorage.removeItem(key)
  }
  return legacyValue
}

function clearAdminSession() {
  for (const key of [ADMIN_TOKEN_KEY, ADMIN_EXPIRY_KEY, ADMIN_USERNAME_KEY]) {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  }
}

function getAdminSessionValue(key) {
  const persistentValue = localStorage.getItem(key)
  if (persistentValue) {
    return persistentValue
  }

  const legacyValue = sessionStorage.getItem(key) || ''
  if (legacyValue) {
    localStorage.setItem(key, legacyValue)
    sessionStorage.removeItem(key)
  }
  return legacyValue
}
