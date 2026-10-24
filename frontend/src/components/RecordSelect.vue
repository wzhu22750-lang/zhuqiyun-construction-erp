<script setup lang="ts">
import {ref,computed,watch} from 'vue'
import {get} from '../api/http'
import {usePagedResource} from '../composables/usePagedResource'
import type {Row} from '../api/types'
import {useAuth} from '../stores/auth'
const props=withDefaults(defineProps<{modelValue:any;entity:string;filters?:Record<string,any>;disabled?:boolean;placeholder?:string}>(),{filters:()=>({}),placeholder:'输入编号或名称搜索'})
const emit=defineEmits<{'update:modelValue':[any];change:[any];selected:[Row|undefined]}>(),auth=useAuth(),selected=ref<Row>()
const listing=usePagedResource(()=>{const p=new URLSearchParams();for(const [k,v] of Object.entries(props.filters))if(v!==''&&v!==null&&v!==undefined)p.set(k,String(v));return `/records/${props.entity}?${p}`})
const options=computed(()=>selected.value&&!listing.data.value.some(r=>String(r.id)===String(selected.value?.id))?[selected.value,...listing.data.value]:listing.data.value)
const label=(r:Row)=>`${r.code||r.username||r.reference||''} · ${r.name||r.order_code||r.reference||r.id}${r.specification?' · '+r.specification:''}${r.unallocated_amount!==undefined?' · 未关联 ¥ '+r.unallocated_amount:''}`
function cache(r:Row){const catalogs=auth.catalog as any;if(Array.isArray(catalogs[props.entity])&&!catalogs[props.entity].some((p:Row)=>String(p.id)===String(r.id)))catalogs[props.entity].push(r)}
function changed(id:any){selected.value=options.value.find(r=>String(r.id)===String(id));if(selected.value)cache(selected.value);emit('update:modelValue',id);emit('change',id);emit('selected',selected.value)}
watch(()=>props.modelValue,async id=>{if(!id){selected.value=undefined;return}selected.value=options.value.find(r=>String(r.id)===String(id));if(!selected.value)try{const page=await get<{items:Row[]}>(`/records/${props.entity}?id=${id}&size=1`);selected.value=page.items[0];if(selected.value)cache(selected.value)}catch{}},{immediate:true})
</script>
<template><el-select :model-value="modelValue" filterable remote clearable :disabled="disabled" :placeholder="placeholder" :remote-method="listing.onSearch" :loading="listing.loading.value" @change="changed"><el-option v-for="r in options" :key="r.id" :label="label(r)" :value="r.id" :disabled="r.unallocated_amount!==undefined&&Number(r.unallocated_amount)<=0"/><template #empty><p v-if="listing.error.value" role="alert">{{listing.error.value}} <el-button link type="primary" @click="listing.load">重试</el-button></p><p v-else class="muted">暂无匹配记录，请输入编号或名称搜索</p></template><template #footer><span class="muted">搜索匹配 {{listing.paging.total}} 项，显示前 {{listing.data.value.length}} 项；可继续输入缩小范围</span></template></el-select></template>
