import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuth } from '../stores/auth'
import { TOKEN_KEY } from '../api/http'
export const navigation = [
  {path:'/dashboard',key:'dashboard',label:'经营驾驶舱',permission:'dashboard:read',icon:'DataAnalysis',group:'工作台'},
  {path:'/projects',key:'projects',label:'项目管理',permission:'project:read',icon:'OfficeBuilding',group:'项目与采购'},
  {path:'/requests',key:'requests',label:'采购申请',permission:'request:read',icon:'DocumentAdd',group:'项目与采购'},
  {path:'/quotes',key:'quotes',label:'询价与比价',permission:'request:read',icon:'TrendCharts',group:'项目与采购'},
  {path:'/orders',key:'orders',label:'采购订单',permission:'order:read',icon:'ShoppingCart',group:'项目与采购'},
  {path:'/suppliers',key:'suppliers',label:'供应商管理',permission:'master:read',icon:'Connection',group:'资源与仓储'},
  {path:'/materials',key:'materials',label:'材料档案',permission:'master:read',icon:'Box',group:'资源与仓储'},
  {path:'/warehouses',key:'warehouses',label:'仓库管理',permission:'master:read',icon:'House',group:'资源与仓储'},
  {path:'/inventory',key:'inventory',label:'库存与领料',permission:'inventory:read',icon:'Goods',group:'资源与仓储'},
  {path:'/costs',key:'costs',label:'成本费用',permission:'cost:read',icon:'PieChart',group:'合同与财务'},
  {path:'/contracts',key:'contracts',label:'合同管理',permission:'contract:read',icon:'Document',group:'合同与财务'},
  {path:'/finance',key:'finance',label:'应付与付款',permission:'finance:read',icon:'Wallet',group:'合同与财务'},
  {path:'/approvals',key:'approvals',label:'审批中心',permission:'request:approve',icon:'Checked',group:'协作与系统'},
  {path:'/system',key:'system',label:'系统管理',permission:'system:manage',icon:'Setting',group:'协作与系统'},
]
const components: Record<string,()=>Promise<unknown>> = {
  dashboard:()=>import('../views/Dashboard.vue'), projects:()=>import('../views/Projects.vue'), requests:()=>import('../views/Requests.vue'), quotes:()=>import('../views/Requests.vue'), orders:()=>import('../views/Orders.vue'), suppliers:()=>import('../views/MasterData.vue'), materials:()=>import('../views/MasterData.vue'), warehouses:()=>import('../views/MasterData.vue'), inventory:()=>import('../views/Inventory.vue'), costs:()=>import('../views/Costs.vue'), contracts:()=>import('../views/Contracts.vue'), finance:()=>import('../views/Finance.vue'), approvals:()=>import('../views/Approvals.vue'), system:()=>import('../views/System.vue'),
}
const children: RouteRecordRaw[] = navigation.map(n=>({path:n.path.slice(1),component:components[n.key] as any,meta:{...n}}))
children.push({path:'projects/:id',component:()=>import('../views/ProjectDetail.vue'),meta:{label:'项目详情',permission:'project:read',key:'projects'}})
children.push({path:'requests/:id',component:()=>import('../views/RequestDetail.vue'),meta:{label:'采购申请详情',permission:'request:read',key:'requests'}})
children.push({path:'orders/:id',component:()=>import('../views/OrderDetail.vue'),meta:{label:'采购订单详情',permission:'order:read',key:'orders'}})
const router=createRouter({history:createWebHistory(import.meta.env.BASE_URL),routes:[{path:'/login',component:()=>import('../views/Login.vue')},{path:'/',component:()=>import('../layouts/AppLayout.vue'),children},{path:'/forbidden',component:()=>import('../views/Unavailable.vue'),meta:{forbidden:true}},{path:'/:pathMatch(.*)*',component:()=>import('../views/Unavailable.vue')}]})
export const firstAllowed = () => { const auth=useAuth(); return navigation.find(n=>auth.can(n.permission)&&auth.hasMenu(n.key))?.path || '/forbidden' }
router.beforeEach(async to=>{
  const auth=useAuth()
  if(to.path==='/login') { if(!localStorage.getItem(TOKEN_KEY)) return true; if(!auth.ready) { try {await auth.refresh()} catch {auth.clear();return true} }; return firstAllowed() }
  if(!localStorage.getItem(TOKEN_KEY)) return {path:'/login',query:{redirect:to.fullPath}}
  if(!auth.ready) {try {await auth.refresh()} catch {auth.clear();return {path:'/login',query:{redirect:to.fullPath}}}}
  if(to.path==='/') return firstAllowed()
  if(to.meta.permission && (!auth.can(String(to.meta.permission))||!auth.hasMenu(String(to.meta.key)))) return '/forbidden'
  return true
})
export default router
