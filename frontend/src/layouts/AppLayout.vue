<script setup lang="ts">
import {ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import * as icons from '@element-plus/icons-vue'
import {firstAllowed} from '../router'
import {useAuth} from '../stores/auth'
import {useMediaQuery} from '../composables/useMediaQuery'
import WorkspaceNav from '../components/WorkspaceNav.vue'
const auth=useAuth(),route=useRoute(),router=useRouter(),collapsed=ref(false),mobileOpen=ref(false),isMobile=useMediaQuery()
const roles:Record<string,string>={admin:'系统管理员',boss:'企业负责人',manager:'项目经理',buyer:'采购专员',warehouse:'仓库管理员',finance:'财务专员'}
watch(()=>route.fullPath,()=>{mobileOpen.value=false})
watch(isMobile,mobile=>{if(!mobile)mobileOpen.value=false})
async function logout(){await auth.logout().catch(()=>{});router.replace('/login')}
function toggleNavigation(){if(isMobile.value)mobileOpen.value=!mobileOpen.value;else collapsed.value=!collapsed.value}
</script>
<template>
  <div class="app-shell" :class="{'is-collapsed':collapsed&&!isMobile}">
    <aside v-if="!isMobile" class="sidebar">
      <router-link :to="firstAllowed()" class="brand"><span class="brand-symbol">筑</span><span v-if="!collapsed" class="brand-copy" :title="auth.company.system_name">{{auth.company.system_name}}<small>CONSTRUCTION CLOUD</small></span></router-link>
      <div v-if="!collapsed" class="workspace-label"><span class="workspace-dot"/> 企业管理工作空间</div>
      <WorkspaceNav :collapsed="collapsed"/>
      <div class="sidebar-footer"><el-icon><icons.Lock/></el-icon><span v-if="!collapsed">企业数据 · 角色授权访问</span></div>
    </aside>
    <el-drawer v-if="isMobile" v-model="mobileOpen" direction="ltr" size="min(86vw, 320px)" title="功能导航" class="mobile-navigation">
      <template #header><div class="mobile-nav-brand"><span class="brand-symbol">筑</span><div><b>{{auth.company.system_name}}</b><small>{{auth.company.name}}</small></div></div></template>
      <div id="mobile-navigation-menu"><WorkspaceNav @navigate="mobileOpen=false"/></div>
      <template #footer><span class="mobile-nav-account">{{auth.user?.name}} · {{roles[auth.user?.role||'']}}</span></template>
    </el-drawer>
    <div class="main-shell">
      <header class="topbar">
        <div class="topbar-left">
          <el-button text circle :aria-label="isMobile?'打开功能导航':'折叠或展开导航'" :aria-expanded="isMobile?mobileOpen:!collapsed" :aria-controls="isMobile?'mobile-navigation-menu':undefined" @click="toggleNavigation"><el-icon :size="22"><icons.Menu v-if="isMobile"/><icons.Expand v-else-if="collapsed"/><icons.Fold v-else/></el-icon></el-button>
          <span class="breadcrumb"><span class="breadcrumb-prefix">工作空间 <span>/</span></span><strong>{{route.meta.label}}</strong></span>
        </div>
        <div class="topbar-right"><span class="company-name">{{auth.company.name}}</span><el-divider direction="vertical"/>
          <el-dropdown trigger="click" @command="logout"><button type="button" class="user-menu" :aria-label="`用户菜单：${auth.user?.name||''}`"><span class="avatar">{{auth.user?.name?.slice(0,1)}}</span><div><b>{{auth.user?.name}}</b><small>{{roles[auth.user?.role||'']||auth.user?.role}}</small></div><el-icon><icons.ArrowDown/></el-icon></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
      </header>
      <main class="page-content"><router-view :key="route.path"/></main>
      <footer class="app-footer">{{auth.company.system_name}} · 建筑企业一体化管理 <span>项目有序 · 经营有数</span></footer>
    </div>
  </div>
</template>
