<script setup lang="ts">
import {ref,computed} from 'vue'
import type {Row} from '../api/types'
import {useResource,useMutation,deleteEntity} from '../composables/useResource'
import {useAuth} from '../stores/auth'
import PageHeader from '../components/PageHeader.vue'
import ResourceState from '../components/ResourceState.vue'
import DataTable from '../components/DataTable.vue'
import EntityDialog from '../components/EntityDialog.vue'
import {projectFields} from './projectFields'
const auth=useAuth(),{data,loading,error,load}=useResource<Row[]>('/projects',[]),dialog=ref(false),selected=ref<Row>(),status=ref('')
const rows=computed(()=>data.value.filter(r=>!status.value||r.status===status.value).map(row=>({...row,manager_name:row.manager_name||auth.catalog.users.find(user=>String(user.id)===String(row.manager_id))?.name||'—'}))),fields=computed(()=>projectFields(auth.catalog.users))
const {saving,run}=useMutation(load)
function edit(row?:Row){selected.value=row;dialog.value=true}
async function drop(row:Row){await run(()=>deleteEntity('/projects',row.id),'档案已处理')}
</script>
<template><PageHeader title="项目管理" subtitle="汇聚项目全周期信息，掌握预算、采购和履约进展。"><el-button @click="load" :loading="loading">刷新</el-button><el-button v-if="auth.can('project:write')" type="primary" @click="edit()">＋ 新建项目</el-button></PageHeader><div class="summary-strip"><div><span>项目总数</span><b>{{data.length}}</b></div><div><span>在建项目</span><b>{{data.filter(r=>r.status==='ACTIVE').length}}</b></div><div><span>筹备项目</span><b>{{data.filter(r=>r.status==='PLANNING').length}}</b></div><div><span>已完成项目</span><b>{{data.filter(r=>r.status==='COMPLETED').length}}</b></div></div><section class="panel table-panel"><ResourceState :loading="loading" :error="error" @retry="load"><DataTable :rows="rows" :columns="[{key:'code',label:'项目编号',minWidth:145},{key:'name',label:'项目名称',minWidth:200},{key:'manager_name',label:'项目经理'},{key:'contract_amount',label:'合同金额（元）',money:true,minWidth:160},{key:'budget_amount',label:'预算（元）',money:true,minWidth:150},{key:'progress',label:'工程进度',width:150},{key:'status',label:'状态',status:true,width:100}]" :action-width="210"><template #toolbar><el-select v-model="status" clearable placeholder="全部状态" style="width:140px"><el-option label="进行中" value="ACTIVE"/><el-option label="筹备中" value="PLANNING"/><el-option label="已完成" value="COMPLETED"/><el-option label="已暂停" value="SUSPENDED"/></el-select></template><template #name="{row}"><router-link :to="`/projects/${row.id}`" class="table-link">{{row.name}}</router-link></template><template #progress="{row}"><el-progress :percentage="Math.max(0,Math.min(100,Number(row.progress||0)))" :stroke-width="5"/></template><template #actions="{row}"><el-button link type="primary" @click="$router.push(`/projects/${row.id}`)">详情</el-button><el-button v-if="auth.can('project:write')" link type="primary" @click="edit(row)">编辑</el-button><el-button v-if="auth.can('project:write')" link type="danger" :disabled="saving" @click="drop(row)">删除</el-button></template></DataTable></ResourceState></section><EntityDialog v-model="dialog" :title="selected?'编辑项目':'新建项目'" endpoint="/projects" :record="selected" :fields="fields" :defaults="{status:'PLANNING',progress:0}" @saved="load"/></template>
