import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('opc_user_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const adminToken = localStorage.getItem('opc_admin_token') || sessionStorage.getItem('opc_admin_token')
  if (adminToken) {
    config.headers['X-Admin-Token'] = adminToken
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 200) {
        return Promise.reject(new Error(body.message || 'Request failed'))
      }
      return body.data
    }
    return body
  },
  (error) => Promise.reject(error),
)

export default request
