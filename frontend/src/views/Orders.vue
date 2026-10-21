<script setup lang="ts">
import {ref,computed} from 'vue'
import type {Row} from '../api/types'
import {useResource} from '../composables/useResource'
import PageHeader from '../components/PageHeader.vue'
import ResourceState from '../components/ResourceState.vue'
import DataTable from '../components/DataTable.vue'
const {data,loading,error,load}=useResource<Row[]>('/orders',[]),status=ref('')
const rows=computed(()=>data.value.filter(r=>!status.value||r.status===status.value))
</script>
<template><PageHeader title="采购订单" subtitle="追踪订单履约、分批验收与应付，确保采购交付可追溯。"><el-button :loading="loading" @click="load">刷新</el-button></PageHeader><section class="panel table-panel"><ResourceState :error="error" :loading="loading" @retry="load"><DataTable :rows="rows" :columns="[{key:'code',label:'订单编号',minWidth:180},{key:'project_name',label:'所属项目',minWidth:190},{key:'supplier_name',label:'供应商',minWidth:180},{key:'total_amount',label:'订单金额（元）',money:true,minWidth:155},{key:'status',label:'履约状态',status:true,width:120},{key:'created_at',label:'下单时间',date:true,minWidth:170}]"><template #toolbar><el-select v-model="status" clearable placeholder="全部状态" style="width:140px"><el-option label="待收货" value="OPEN"/><el-option label="部分收货" value="PARTIAL"/><el-option label="已完成" value="COMPLETED"/><el-option label="已取消" value="CANCELLED"/></el-select></template><template #code="{row}"><router-link :to="`/orders/${row.id}`" class="table-link">{{row.code}}</router-link></template><template #actions="{row}"><el-button link type="primary" @click="$router.push(`/orders/${row.id}`)">订单详情 / 验收</el-button></template></DataTable></ResourceState></section></template>
