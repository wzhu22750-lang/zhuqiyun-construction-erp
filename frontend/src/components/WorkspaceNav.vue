<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import * as icons from '@element-plus/icons-vue'
import {navigation} from '../router'
import {useAuth} from '../stores/auth'
defineProps<{collapsed?:boolean}>()
const emit=defineEmits<{navigate:[]}>()
const auth=useAuth(),route=useRoute()
const menus=computed(()=>navigation.filter(n=>auth.can(n.permission)&&auth.hasMenu(n.key)))
const groups=computed(()=>Array.from(new Set(menus.value.map(n=>n.group))))
</script>
<template>
  <nav aria-label="业务功能导航">
    <div v-for="group in groups" :key="group" class="nav-group">
      <p v-if="!collapsed" class="nav-group-label">{{group}}</p>
      <router-link v-for="item in menus.filter(n=>n.group===group)" :key="item.path" :to="item.path" class="nav-item" :class="{active:route.meta.key===item.key}" :aria-current="route.meta.key===item.key?'page':undefined" :title="item.label" :aria-label="item.label" @click="emit('navigate')">
        <el-icon><component :is="(icons as any)[item.icon]"/></el-icon><span v-if="!collapsed">{{item.label}}</span>
      </router-link>
    </div>
  </nav>
</template>
