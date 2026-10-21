<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {Row} from '../api/types'
import {money,number,dateTime} from '../composables/useResource'
import {useMediaQuery} from '../composables/useMediaQuery'
import StatusTag from './StatusTag.vue'
export interface Column {key:string;label:string;money?:boolean;number?:boolean;date?:boolean;status?:boolean;width?:number;minWidth?:number}
const props=withDefaults(defineProps<{rows:Row[];columns:Column[];search?:boolean;actionWidth?:number;rowKey?:string}>(),{search:true,actionWidth:190,rowKey:'id'})
const isMobile=useMediaQuery(),query=ref(''),page=ref(1),size=ref(isMobile.value?5:10)
const filtered=computed(()=>props.rows.filter(row=>!query.value||props.columns.some(c=>String(row[c.key]??'').toLowerCase().includes(query.value.toLowerCase()))))
const paged=computed(()=>filtered.value.slice((page.value-1)*size.value,page.value*size.value))
const titleColumn=computed(()=>props.columns[0])
const statusColumn=computed(()=>props.columns.find(c=>c.status&&c!==titleColumn.value))
const detailColumns=computed(()=>props.columns.filter(c=>c!==titleColumn.value&&c!==statusColumn.value))
watch([query,()=>props.rows,size],()=>page.value=1)
</script>
<template>
  <div class="data-table">
    <div v-if="search" class="table-toolbar"><el-input v-model="query" aria-label="搜索业务记录" placeholder="搜索单号、名称或关联信息" clearable style="max-width:310px"><template #prefix><span>⌕</span></template></el-input><span class="muted">共 {{filtered.length}} 条记录</span><div class="toolbar-end"><slot name="toolbar"/></div></div>
    <div v-if="isMobile" class="mobile-records">
      <el-empty v-if="!paged.length" description="暂无匹配的业务记录" :image-size="64"/>
      <article v-for="row in paged" :key="row[rowKey]" class="mobile-record" :data-row-id="row[rowKey]">
        <header class="mobile-record-header"><h3 v-if="titleColumn"><span class="mobile-record-caption">{{titleColumn.label}}</span><slot :name="titleColumn.key" :row="row"><span :class="{'numeric':titleColumn.money||titleColumn.number}">{{titleColumn.money?money(row[titleColumn.key]):titleColumn.number?number(row[titleColumn.key]):titleColumn.date?dateTime(row[titleColumn.key]):row[titleColumn.key]??'—'}}</span></slot></h3><slot v-if="statusColumn" :name="statusColumn.key" :row="row"><StatusTag :value="row[statusColumn.key]"/></slot></header>
        <dl class="mobile-record-fields"><div v-for="col in detailColumns" :key="col.key"><dt>{{col.label}}</dt><dd><slot :name="col.key" :row="row"><StatusTag v-if="col.status" :value="row[col.key]"/><span v-else-if="col.money" class="numeric">{{money(row[col.key])}}</span><span v-else-if="col.number" class="numeric">{{number(row[col.key])}}</span><span v-else-if="col.date">{{dateTime(row[col.key])}}</span><span v-else>{{row[col.key]??'—'}}</span></slot></dd></div></dl>
        <div v-if="$slots.actions" class="table-actions mobile-record-actions"><slot name="actions" :row="row"/></div>
      </article>
    </div>
    <el-table v-else :data="paged" :row-key="rowKey" stripe style="width:100%" empty-text="暂无匹配的业务记录"><el-table-column v-for="col in columns" :key="col.key" :prop="col.key" :label="col.label" :width="col.width" :min-width="col.minWidth||120" :align="col.money||col.number?'right':'left'" show-overflow-tooltip><template #default="{row}"><slot :name="col.key" :row="row"><StatusTag v-if="col.status" :value="row[col.key]"/><span v-else-if="col.money" class="numeric">{{money(row[col.key])}}</span><span v-else-if="col.number" class="numeric">{{number(row[col.key])}}</span><span v-else-if="col.date">{{dateTime(row[col.key])}}</span><span v-else>{{row[col.key]??'—'}}</span></slot></template></el-table-column><el-table-column v-if="$slots.actions" label="操作" fixed="right" :width="actionWidth"><template #default="{row}"><div class="table-actions"><slot name="actions" :row="row"/></div></template></el-table-column></el-table>
    <div class="table-footer"><span class="muted">共 {{filtered.length}} 条</span><el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[5,10,20,50]" :pager-count="isMobile?5:7" :total="filtered.length" :layout="isMobile?'prev, pager, next':'sizes, prev, pager, next'" size="small" background/></div>
  </div>
</template>
