<script setup lang="ts">
import {ref,onMounted,onBeforeUnmount,watch,nextTick} from 'vue'
import * as echarts from 'echarts/core'
import {BarChart,LineChart,PieChart} from 'echarts/charts'
import {GridComponent,TooltipComponent,LegendComponent} from 'echarts/components'
import {CanvasRenderer} from 'echarts/renderers'
import type {EChartsOption} from 'echarts'
echarts.use([BarChart,LineChart,PieChart,GridComponent,TooltipComponent,LegendComponent,CanvasRenderer])
const props=defineProps<{title:string;subtitle?:string;option:EChartsOption;empty?:boolean;height?:number}>()
const root=ref<HTMLElement>();let chart:echarts.ECharts|undefined,observer:ResizeObserver|undefined
const render=async()=>{await nextTick();if(!root.value||props.empty)return;if(!chart)chart=echarts.init(root.value);chart.setOption({...props.option,animation:false},true);chart.resize()}
onMounted(()=>{render();observer=new ResizeObserver(()=>chart?.resize());if(root.value)observer.observe(root.value)})
watch(()=>[props.option,props.empty],render,{deep:true})
onBeforeUnmount(()=>{observer?.disconnect();chart?.dispose()})
</script>
<template><section class="panel chart-panel"><div class="panel-heading"><div><h2>{{title}}</h2><p v-if="subtitle">{{subtitle}}</p></div><slot/></div><el-empty v-if="empty" description="暂无统计数据" :image-size="70" :style="{height:`${height||280}px`}"/><div v-show="!empty" ref="root" :style="{height:`${height||280}px`,width:'100%'}" role="img" :aria-label="title"/></section></template>
