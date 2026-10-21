import { ref, onMounted } from 'vue'
import { get, post, put, remove } from '../api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuth } from '../stores/auth'
export function useResource<T>(path: string | (()=>string), initial: T) {
  const data = ref<T>(initial)
  const loading = ref(false)
  const error = ref('')
  let requestId = 0
  async function load() {
    const current = ++requestId; loading.value = true; error.value = ''
    try { const result = await get<T>(typeof path === 'function' ? path() : path); if (current===requestId) data.value=result as any }
    catch (e) { if(current===requestId) error.value=e instanceof Error ? e.message : '加载失败' }
    finally { if(current===requestId) loading.value=false }
  }
  onMounted(load)
  return {data,loading,error,load}
}
export function useMutation(refresh?: ()=>Promise<unknown>) {
  const saving = ref(false)
  async function run(work: ()=>Promise<unknown>, message='操作成功') {
    if(saving.value) return false
    saving.value=true
    try { await work(); ElMessage.success(message); if(refresh) await refresh(); return true }
    catch { return false }
    finally { saving.value=false }
  }
  return {saving,run}
}
export async function confirmAction(message: string) {
  try { await ElMessageBox.confirm(message,'操作确认',{confirmButtonText:'确认',cancelButtonText:'取消',type:'warning'}); return true } catch { return false }
}
export const money = (value: unknown) => value===null || value===undefined || value==='' ? '—' : Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2}) : '—'
export const number = (value: unknown, digits=3) => value===null || value===undefined || value==='' ? '—' : Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN',{maximumFractionDigits:digits}) : '—'
const shanghaiTime = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
})
export const dateTime = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '—'
  const text = String(value).trim()
  // Business DATE fields have no timezone; legacy zone-less timestamps represent UTC.
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return text
  const normalized = text.replace(' ', 'T')
  const instant = new Date(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(normalized) ? normalized : `${normalized}Z`)
  if (!Number.isFinite(instant.getTime())) return '—'
  const parts = Object.fromEntries(shanghaiTime.formatToParts(instant).map(part => [part.type, part.value]))
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}
export const today = () => { const d=new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }
export const nameOf = (list: {id:string|number;name?:string}[],id:unknown) => list.find(r=>String(r.id)===String(id))?.name || '—'
export async function saveEntity(path:string,id:unknown,payload:unknown) { await (id ? put(`${path}/${id}`,payload) : post(path,payload)); await useAuth().refresh() }
export async function deleteEntity(path:string,id:unknown) { if(!await confirmAction('确认删除该档案？有关联业务时服务端将拒绝删除。')) return false; await remove(`${path}/${id}`); await useAuth().refresh(); return true }
