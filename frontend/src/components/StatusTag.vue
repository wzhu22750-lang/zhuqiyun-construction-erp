<script setup lang="ts">
import {computed} from 'vue'
const props=defineProps<{value?:string}>()
export type TagType='primary'|'success'|'warning'|'danger'|'info'
const map:Record<string,[string,TagType]>={ACTIVE:['进行中','primary'],PLANNING:['筹备中','info'],COMPLETED:['已完成','success'],SUSPENDED:['已暂停','warning'],DRAFT:['草稿','info'],PENDING:['待审批','warning'],SUBMITTED:['待审批','warning'],APPROVED:['已批准','success'],REJECTED:['已驳回','danger'],ORDERED:['已下单','primary'],OPEN:['待收货','primary'],PARTIAL:['部分收货','warning'],CANCELLED:['已取消','info'],INACTIVE:['已停用','info'],PAID:['已付款','success'],RECEIPT:['入库','success'],ISSUE:['领料出库','warning'],MAIN:['总包合同','primary'],PURCHASE:['采购合同','primary'],SUBCONTRACT:['分包','warning'],LABOR:['人工','primary'],MACHINE:['机械','warning'],OTHER:['其他','info']}
const status=computed(()=>map[props.value||'']||[props.value||'—','info'])
</script>
<template><el-tag :type="status[1] as TagType" effect="light" round size="small">{{ status[0] }}</el-tag></template>
