<script setup lang="ts">
import {usePagedResource} from '../composables/usePagedResource'
import DataTable,{type Column} from './DataTable.vue'
import ResourceState from './ResourceState.vue'
import ExchangeActions from './ExchangeActions.vue'
const props=defineProps<{entity:string;filters?:Record<string,any>;columns:Column[];destination?:string;printable?:boolean}>()
const listing=usePagedResource(()=>{const p=new URLSearchParams();for(const [k,v] of Object.entries(props.filters||{}))if(v!==null&&v!==undefined)p.set(k,String(v));return `/records/${props.entity}?${p}`})
</script>
<template><ResourceState :loading="listing.loading.value" :error="listing.error.value" @retry="listing.load"><DataTable :rows="listing.data.value" :remote="listing.paging" @page="listing.onPage" @size="listing.onSize" @search="listing.onSearch" :columns="columns"><template v-if="destination||printable" #actions="{row}"><el-button v-if="destination" link type="primary" @click="$router.push({path:destination.replace(':id',String(row.id)),query:{return_to:$route.fullPath}})">查看单据</el-button><ExchangeActions v-if="printable" :entity="entity" :print-id="row.id" print-only/></template></DataTable></ResourceState></template>
