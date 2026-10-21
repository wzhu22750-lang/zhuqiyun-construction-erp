<script setup lang="ts">
import {useRoute,useRouter} from 'vue-router'
import {firstAllowed} from '../router'
import {useAuth} from '../stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuth()
async function switchAccount(){await auth.logout().catch(()=>{});await router.replace('/login')}
</script>
<template><el-result icon="warning" :title="route.meta.forbidden?'暂无访问权限':'页面不存在'" :sub-title="route.meta.forbidden?'当前账号没有该菜单或操作权限，请联系管理员。':'请检查地址或返回工作空间。'"><template #extra><el-button type="primary" v-if="firstAllowed()!=='/forbidden'" @click="$router.push(firstAllowed())">返回工作空间</el-button><el-button @click="switchAccount">切换账号</el-button></template></el-result></template>
