<script setup lang="ts">
import {ref,computed} from 'vue'
import {useRoute} from 'vue-router'
import type {Row} from '../api/types'
import {useAuth} from '../stores/auth'
import {useResource} from '../composables/useResource'
import {usePagedResource} from '../composables/usePagedResource'
import ExchangeActions from '../components/ExchangeActions.vue'
import PageHeader from '../components/PageHeader.vue'
import ResourceState from '../components/ResourceState.vue'
import DataTable from '../components/DataTable.vue'
import RequestEditor from '../components/RequestEditor.vue'
const route=useRoute(),auth=useAuth(),status=ref(String(route.query.status||'')),listing=usePagedResource(()=>`/records/requests?status=${status.value}&queue=${route.meta.key==='quotes'?'quotes':''}`),{data,loading,error,load,paging,onPage,onSize,onSearch}=listing,dialog=ref(false)
const isQuotes=computed(()=>route.meta.key==='quotes')
const rows=computed(()=>data.value.filter(r=>(!isQuotes.value||['APPROVED','ORDERED'].includes(r.status))&&(!status.value||r.status===status.value)))
</script>
<template><PageHeader :title="isQuotes?'询价与比价':'采购申请'" :subtitle="isQuotes?'按材料对比供应商报价，记录中选依据，生成采购订单。':'从现场需求出发，让每笔采购有计划、有审批、有追踪。'"><ExchangeActions entity="requests" :filters="{q:paging.q,status,queue:isQuotes?'quotes':''}"/><el-button @click="load" :loading="loading">刷新</el-button><el-button v-if="!isQuotes&&auth.can('request:write')" type="primary" @click="dialog=true">＋ 新建采购申请</el-button></PageHeader><div v-if="isQuotes" class="notice-strip">采购闭环 <span>需求审批</span> → <b>供应商询比价</b> → <span>中选下单</span> → <span>验收入库</span></div><section class="panel table-panel"><ResourceState :loading="loading" :error="error" @retry="load"><DataTable :remote="paging" @page="onPage" @size="onSize" @search="onSearch" :rows="rows" :columns="[{key:'code',label:'申请单号',minWidth:170},{key:'project_name',label:'所属项目',minWidth:200},{key:'applicant_name',label:'申请人',width:100},{key:'required_date',label:'要求到货',width:120},{key:'total_amount',label:'申请金额（元）',money:true,minWidth:155},{key:'status',label:'状态',status:true,width:110}]"><template #toolbar><el-select v-model="status" placeholder="全部状态" clearable style="width:140px"><el-option v-for="s in (isQuotes?[['APPROVED','已批准'],['ORDERED','已下单']]:[['DRAFT','草稿'],['PENDING','待审批'],['APPROVED','已批准'],['REJECTED','已驳回'],['ORDERED','已下单']])" :key="s[0]" :label="s[1]" :value="s[0]"/></el-select></template><template #code="{row}"><router-link :to="`/requests/${row.id}`" class="table-link">{{row.code}}</router-link></template><template #actions="{row}"><el-button link type="primary" @click="$router.push({path:`/requests/${row.id}`,query:isQuotes?{tab:'quotes'}:{}})">{{isQuotes?'询价 / 比价':'查看详情'}}</el-button></template></DataTable></ResourceState></section><RequestEditor v-model="dialog" @saved="load"/></template>
