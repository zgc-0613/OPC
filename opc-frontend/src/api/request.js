import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('opc_user_token') || sessionStorage.getItem('opc_user_token')
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
        const requestError = new Error(body.message || 'Request failed')
        requestError.businessCode = body.code
        requestError.response = { ...response, data: body }
        return Promise.reject(requestError)
      }
      return body.data
    }
    return body
  },
  (error) => {
    if (error?.response?.data?.code !== undefined) {
      error.businessCode = error.response.data.code
    }
    return Promise.reject(error)
  },
)

export default request
