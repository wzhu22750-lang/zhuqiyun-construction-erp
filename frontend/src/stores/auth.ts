import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { get, post, TOKEN_KEY } from '../api/http'
import type { User, Bootstrap, Company, Row } from '../api/types'
export const useAuth = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const ready = ref(false)
  const company = ref<Company>({ name:'建筑企业管理平台', system_name:'筑企云', theme_color:'#2563EB' })
  const catalog = ref<{projects:Row[];materials:Row[];suppliers:Row[];warehouses:Row[];users:Row[];dictionaries:Record<string,Row[]>}>({ projects:[],materials:[],suppliers:[],warehouses:[],users:[],dictionaries:{} })
  const isAdmin = computed(() => user.value?.permissions?.includes('*') || false)
  const can = (permission: string) => isAdmin.value || Boolean(user.value?.permissions?.includes(permission))
  const hasMenu = (key: string) => { const menuKey = key === 'quotes' ? 'requests' : key; return Boolean(user.value?.menus?.some(menu => menu === '*' || menu.replace(/^\//,'') === menuKey)) }
  const applyBrand = () => {
    const configured = company.value.theme_color || '#2563EB'
    const color = /^#[0-9a-f]{6}$/i.test(configured) && configured.toLowerCase() !== '#0f766e' ? configured.toUpperCase() : '#2563EB'
    company.value.theme_color = color
    const style = document.documentElement.style
    style.setProperty('--el-color-primary', color)
    const channels = [1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16))
    const blend = (target:number, weight:number) => '#' + channels.map(channel => Math.round(channel + (target - channel) * weight).toString(16).padStart(2, '0')).join('')
    for (const level of [3, 5, 7, 8, 9]) style.setProperty(`--el-color-primary-light-${level}`, blend(255, level / 10))
    style.setProperty('--el-color-primary-dark-2', blend(0, 0.2))
    document.title = `${company.value.system_name} · 建筑企业管理平台`
  }
  async function refresh() {
    const result = await get<Bootstrap>('/bootstrap')
    user.value = result.user
    company.value = result.company
    catalog.value = { projects:result.projects || [], materials:result.materials || [], suppliers:result.suppliers || [], warehouses:result.warehouses || [], users:result.users || [], dictionaries:result.dictionaries || {} }
    ready.value = true; applyBrand()
  }
  async function login(username: string, password: string) {
    const result = await post<{token:string;user:User}>('/auth/login',{username,password})
    localStorage.setItem(TOKEN_KEY,result.token); user.value=result.user
    try { await refresh() } catch (error) { clear(); throw error }
  }
  function clear() { localStorage.removeItem(TOKEN_KEY); user.value=null; ready.value=false }
  async function logout() { try { await post('/auth/logout') } finally { clear() } }
  return {user,ready,company,catalog,isAdmin,can,hasMenu,refresh,login,logout,clear,applyBrand}
})
