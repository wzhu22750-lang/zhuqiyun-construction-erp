import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Envelope } from './types'
export const TOKEN_KEY = 'zqy_token'
const http = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api', timeout: 25000 })
http.interceptors.request.use(config => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
http.interceptors.response.use(response => {
  if (response.config.responseType === 'blob') return response
  if (response.data?.code !== 0) {
    const error = new Error(response.data?.msg || '服务返回了无法识别的响应')
    ElMessage.error(error.message)
    return Promise.reject(error)
  }
  return response
}, error => {
  const message = error.response?.data?.msg || (error.code === 'ECONNABORTED' ? '请求超时，请重试' : error.response ? `请求失败（${error.response.status}）` : '无法连接服务，请检查后端是否已启动')
  if (error.response?.status === 401) {
    localStorage.removeItem(TOKEN_KEY)
    window.dispatchEvent(new Event('zqy:unauthorized'))
  }
  ElMessage.error(message)
  return Promise.reject(new Error(message))
})
export async function api<T>(path: string, config: AxiosRequestConfig = {}): Promise<T> {
  const response = await http.request<Envelope<T>>({ url: path, ...config })
  return response.data.data
}
export const get = <T>(path: string) => api<T>(path)
export const post = <T = unknown>(path: string, data: unknown = {}) => api<T>(path, {method:'POST',data})
export const put = <T = unknown>(path: string, data: unknown) => api<T>(path, {method:'PUT',data})
export const remove = (path: string) => api(path, {method:'DELETE'})
export async function downloadAttachment(id: string | number, name: string) {
  const response = await http.get(`/contracts/${id}/attachment`, { responseType: 'blob' })
  if (response.data.type.includes('json')) throw new Error('附件下载失败')
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = name || '合同附件'; anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
