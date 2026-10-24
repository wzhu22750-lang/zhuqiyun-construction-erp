import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './style.css'
import './mobile.css'
import App from './App.vue'
import router from './router'
import { useAuth } from './stores/auth'
// Recover tabs holding content-hashed chunks from before a local upgrade, without a reload loop.
window.addEventListener('vite:preloadError',(event)=>{
 const last=Number(sessionStorage.getItem('zqy:asset-reload')||0)
 if(Date.now()-last>60000){event.preventDefault();sessionStorage.setItem('zqy:asset-reload',String(Date.now()));window.location.reload()}
})
const app=createApp(App)
app.use(createPinia()).use(router).use(ElementPlus,{locale:zhCn})
window.addEventListener('zqy:unauthorized',()=>{useAuth().clear();if(router.currentRoute.value.path!=='/login') router.replace({path:'/login',query:{redirect:router.currentRoute.value.fullPath}})})
app.mount('#app')
