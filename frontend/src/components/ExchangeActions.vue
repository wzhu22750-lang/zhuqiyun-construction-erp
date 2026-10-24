<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {ElMessage} from 'element-plus'
import {post,downloadFile,binary} from '../api/http'
import {useMutation,confirmAction} from '../composables/useResource'
import DataTable from './DataTable.vue'
interface ImportPreview {
 hash:string;valid:boolean;count:number;valid_count:number;error_count:number
 columns:Record<string,string>
 rows:{row:number;data:Record<string,unknown>;input?:Record<string,unknown>;errors:string[]}[]
}
const props=withDefaults(defineProps<{entity:string;filters?:Record<string,any>;allowImport?:boolean;exportLabel?:string;printId?:string|number;printOnly?:boolean}>(),{filters:()=>({}),exportLabel:'导出 Excel'})
const labels:Record<string,string>={suppliers:'供应商',materials:'材料',projects:'项目',warehouses:'仓库',requests:'采购申请',orders:'采购订单',contracts:'合同',inventory:'实时库存','stock-movements':'出入库流水',receipts:'入库单',issues:'领料单','issue-returns':'退料单',stocktakes:'库存盘点',payables:'应付账款','payment-applications':'付款申请',payments:'付款记录','supplier-refunds':'供应商退款','supplier-statement':'供应商累计对账','cost-overview':'项目成本总览',expenses:'费用明细'}
const title=computed(()=>labels[props.entity]||'业务数据')
const emit=defineEmits<{changed:[]}>(),dialog=ref(false),preview=ref<ImportPreview>(),file=ref<File>(),loading=ref(false),checking=ref(false),error=ref(''),fileInput=ref<HTMLInputElement>(),printUrl=ref(''),printDialog=ref(false),frame=ref<HTMLIFrameElement>()
const {saving,run}=useMutation()
const previewRows=computed(()=>preview.value?.rows.map(row=>({...row,id:row.row}))||[])
let previewSequence=0
const suffix=()=>{const params=new URLSearchParams();Object.entries(props.filters).forEach(([k,v])=>{if(v!==undefined&&v!==null&&v!=='')params.set(k,String(v))});return `?${params}`}
async function download(template=false){loading.value=true;try{const day=new Date().toLocaleDateString('sv-SE');await downloadFile(`/exchange/${template?'template':'export'}/${props.entity}${template?'':suffix()}`,`${title.value}${template?'导入模板':`-${day}`}.xlsx`)}catch(e){ElMessage.error(e instanceof Error?e.message:'下载失败')}finally{loading.value=false}}
function resetImport(){previewSequence++;preview.value=undefined;file.value=undefined;error.value='';checking.value=false;if(fileInput.value)fileInput.value.value=''}
function openImport(){resetImport();dialog.value=true}
function select(event:Event){previewSequence++;preview.value=undefined;error.value='';file.value=undefined;const candidate=(event.target as HTMLInputElement).files?.[0];if(!candidate)return;if(!candidate.name.toLowerCase().endsWith('.xlsx')){error.value='请选择 .xlsx 格式的 Excel 文件';return}if(candidate.size>2*1024*1024){error.value='文件超过 2MB，请拆分后导入';return}file.value=candidate}
async function check(){if(!file.value)return;const sequence=++previewSequence;checking.value=true;error.value='';preview.value=undefined;try{const fd=new FormData();fd.append('file',file.value);const result=await post<ImportPreview>(`/exchange/preview/${props.entity}`,fd);if(sequence===previewSequence)preview.value=result}catch(e){if(sequence===previewSequence)error.value=e instanceof Error?e.message:'预览失败'}finally{if(sequence===previewSequence)checking.value=false}}
async function commit(){if(!file.value||!preview.value?.valid||checking.value)return;const selected=file.value,checked=preview.value,entity=props.entity;if(!await confirmAction(`确认新增 ${checked.count} 条${title.value}档案？已有编号不会覆盖。`))return;if(file.value!==selected||preview.value!==checked||props.entity!==entity)return;await run(async()=>{const fd=new FormData();fd.append('file',selected);fd.append('expected_hash',checked.hash);await post(`/exchange/import/${entity}`,fd);dialog.value=false;emit('changed')},`成功导入 ${checked.count} 条${title.value}档案`)}
function previewValues(row:ImportPreview['rows'][number]){return Object.entries(row.input||row.data).map(([key,value])=>`${(preview.value?.columns[key]||key).replace(/\s*\[[^\]]+\]$/,'')}：${value}`).join(' · ')}
async function print(){loading.value=true;try{const blob=await binary(`/exchange/print/${props.entity}/${props.printId||0}${suffix()}`);if(printUrl.value)URL.revokeObjectURL(printUrl.value);printUrl.value=URL.createObjectURL(blob);printDialog.value=true}catch(e){ElMessage.error(e instanceof Error?e.message:'打印加载失败')}finally{loading.value=false}}
watch(dialog,value=>{if(!value)resetImport()})
watch(()=>props.entity,()=>{dialog.value=false;resetImport()})
onBeforeUnmount(()=>{previewSequence++;if(printUrl.value)URL.revokeObjectURL(printUrl.value)})
</script>
<template>
 <el-button v-if="!printOnly" :loading="loading" @click="download()">{{exportLabel}}</el-button>
 <el-button v-if="allowImport" @click="openImport">导入 Excel</el-button>
 <el-button v-if="printId||entity==='supplier-statement'" :loading="loading" @click="print">A4 打印</el-button>
 <el-dialog v-model="dialog" :title="`导入 Excel · ${title}档案`" width="960px" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
  <el-alert :closable="false" type="info" class="mb" title="仅新增，不覆盖已有档案。每批最多 300 行 / 2MB，所有行通过校验后整批写入。"/>
  <ol class="import-steps"><li>下载模板，保留表头填写档案</li><li>选择文件，预览并逐行校验</li><li>全部通过后确认导入</li></ol>
  <div class="import-controls"><el-button :loading="loading" @click="download(true)">下载 Excel 模板</el-button><label class="import-file">选择 xlsx 文件 <input ref="fileInput" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" :disabled="checking||saving" @change="select"/></label><el-button :disabled="!file||saving" :loading="checking" @click="check">预览并逐行校验</el-button></div>
  <p v-if="entity==='materials'||entity==='warehouses'" class="field-hint">本次仅建立档案；材料参考价和仓库档案不会生成库存或出入库记录。</p>
  <p v-if="error" class="import-error" role="alert">{{error}}</p>
  <template v-if="preview">
   <div class="import-summary" aria-live="polite"><strong>共 {{preview.count}} 行</strong><span>通过 {{preview.valid_count}} 行</span><span :class="{'import-error':preview.error_count>0}">错误 {{preview.error_count}} 行</span></div>
   <el-alert class="mb" :closable="false" :type="preview.valid?'success':'error'" :title="preview.valid?'全部通过，可以确认导入':'尚未写入任何档案。请修改错误行，重新选择文件并预览。'"/>
   <DataTable :rows="previewRows" row-key="row" :columns="[{key:'row',label:'Excel 行号',width:110},{key:'data',label:'待新增档案',minWidth:380},{key:'errors',label:'逐行校验结果',minWidth:240}]"><template #data="{row}"><div class="import-values">{{previewValues(row)}}</div></template><template #errors="{row}"><span :style="{color:row.errors.length?'var(--el-color-danger)':'var(--el-color-success)'}">{{row.errors.join('；')||'通过'}}</span></template></DataTable>
  </template>
  <template #footer><el-button :disabled="saving" @click="dialog=false">关闭</el-button><el-button type="primary" :disabled="!preview?.valid||checking" :loading="saving" @click="commit">确认导入{{preview?.valid?` ${preview.count} 行`:''}}</el-button></template>
 </el-dialog>
 <el-dialog v-model="printDialog" title="A4 打印预览" width="1050px" destroy-on-close><iframe ref="frame" :src="printUrl" title="A4业务单据" style="width:100%;height:68vh;border:1px solid #ddd"/><p class="field-hint">打印仅包含业务单据。可在浏览器打印对话框选择 A4 或保存为 PDF。</p><template #footer><el-button @click="printDialog=false">关闭</el-button><el-button type="primary" @click="frame?.contentWindow?.print()">打印 / 保存 PDF</el-button></template></el-dialog>
</template>
<style scoped>
.import-controls,.import-summary{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:16px 0}.import-file{display:flex;gap:10px;align-items:center;max-width:100%;flex-wrap:wrap}.import-file input{max-width:100%}.import-steps{padding-left:24px;color:var(--el-text-color-regular);line-height:1.9}.import-error{color:var(--el-color-danger)}.import-values{white-space:normal;overflow-wrap:anywhere}@media(max-width:767px){.import-controls>*{max-width:100%}.import-file{width:100%}.import-file input{width:100%}}
</style>
