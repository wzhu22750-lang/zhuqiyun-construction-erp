<script setup lang="ts">
import {computed,reactive,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {ElMessage} from 'element-plus'
import type {Row} from '../api/types'
import {get,post} from '../api/http'
import {useAuth} from '../stores/auth'
import {useResource,useMutation,money,confirmAction} from '../composables/useResource'
import {usePagedResource} from '../composables/usePagedResource'
import ExchangeActions from '../components/ExchangeActions.vue'
import RecordSelect from '../components/RecordSelect.vue'
import PageHeader from '../components/PageHeader.vue'
import ResourceState from '../components/ResourceState.vue'
import DataTable from '../components/DataTable.vue'
import StatusTag from '../components/StatusTag.vue'
const route=useRoute(),auth=useAuth(),status=ref(String(route.query.status||'')),payables=usePagedResource('/records/payables'),payments=usePagedResource(()=>`/records/payments?id=${route.query.payment_id||''}`),applications=usePagedResource(()=>`/records/payment-applications?status=${status.value}&id=${route.query.application_id||''}`),refunds=usePagedResource(()=>`/records/supplier-refunds?id=${route.query.refund_id||''}`)
const tab=ref(String(route.query.tab||'payables')),dialog=ref(false),mode=ref<'payment'|'application'|'execute'|'refund'>('application'),selected=ref<Row>()
const form=reactive({payable_id:'' as any,amount:'',reference:'',note:''})
const reviewDialog=ref(false),decision=ref('APPROVED'),comment=ref(''),cancelDialog=ref(false),reason=ref('')
const historyDialog=ref(false),history=ref<Row[]>([]),historyLoading=ref(false),historyError=ref('')
const selectedPayable=ref<Row>()
const currentPayable=computed(()=>selectedPayable.value||payables.data.value.find(p=>String(p.id)===String(form.payable_id)))
const title=computed(()=>({payment:'直接登记付款',application:'发起付款申请',execute:'执行已批准付款',refund:'登记收到供应商退款'}[mode.value]))
const filteredApplications=computed(()=>applications.data.value.filter(a=>(!status.value||a.status===status.value)&&(!route.query.application_id||String(a.id)===String(route.query.application_id))))
const pendingCount=computed(()=>applications.data.value.filter(a=>a.status==='PENDING').length)
const approvedCount=computed(()=>applications.data.value.filter(a=>a.status==='APPROVED').length)
const {saving,run}=useMutation(reload)
async function reload(){await Promise.all([payables.load(),payments.load(),applications.load(),refunds.load()])}
function open(m:typeof mode.value,row?:Row){mode.value=m;selected.value=row;selectedPayable.value=m==='execute'?undefined:row;Object.assign(form,{payable_id:row?(m==='execute'?row.payable_id:row.id):'',amount:m==='execute'?String(row?.amount):m==='refund'&&row?String(row.refundable_amount):'',reference:'',note:''});dialog.value=true}
function canReview(row:Row){return auth.can('finance:approve')&&row.status==='PENDING'&&String(row.applicant_id)!==String(auth.user?.id)}
function canExecute(row:Row){return auth.can('finance:write')&&row.status==='APPROVED'&&String(row.reviewer_id)!==String(auth.user?.id)}
function canCancel(row:Row){return auth.can('finance:write')&&['PENDING','APPROVED'].includes(row.status)&&(auth.isAdmin||String(row.applicant_id)===String(auth.user?.id))}
function openReview(row:Row){selected.value=row;decision.value='APPROVED';comment.value='';reviewDialog.value=true}
function openCancel(row:Row){selected.value=row;reason.value='';cancelDialog.value=true}
async function review(){if(!selected.value)return;if(decision.value==='REJECTED'&&!comment.value.trim()){ElMessage.warning('请填写驳回原因');return}if(await run(()=>post(`/payment-applications/${selected.value!.id}/review`,{decision:decision.value,comment:comment.value}),'审批结果已保存'))reviewDialog.value=false}
async function cancel(){if(!selected.value)return;if(!reason.value.trim()){ElMessage.warning('请填写撤回原因');return}if(await run(()=>post(`/payment-applications/${selected.value!.id}/cancel`,{reason:reason.value}),'已撤回申请并释放占用金额'))cancelDialog.value=false}
async function showHistory(row:Row){selected.value=row;history.value=[];historyError.value='';historyDialog.value=true;await loadHistory()}
async function loadHistory(){if(!selected.value)return;historyLoading.value=true;historyError.value='';try{history.value=await get<Row[]>(`/payment-applications/${selected.value.id}/records`)}catch(e){historyError.value=e instanceof Error?e.message:'加载操作记录失败'}finally{historyLoading.value=false}}
async function save(){
  if(!form.payable_id||!Number.isFinite(Number(form.amount))||Number(form.amount)<=0){ElMessage.warning('请选择应付单并填写有效的正数金额');return}
  if(mode.value!=='application'&&!form.reference.trim()){ElMessage.warning(mode.value==='refund'?'请填写实际到账退款凭证号':'请填写实际付款凭证号');return}
  if(!await confirmAction(mode.value==='application'?'确认提交付款申请？提交后占用可申请余额，等待负责人审批。':mode.value==='refund'?'确认已实际收到供应商退款？请核对到账凭证。':'确认登记实际付款？请核对银行流水与付款凭证。'))return
  const path=mode.value==='execute'?`/payment-applications/${selected.value!.id}/pay`:mode.value==='application'?'/payment-applications':mode.value==='refund'?'/supplier-refunds':'/payments'
  const payload=mode.value==='execute'?{reference:form.reference,note:form.note}:mode.value==='application'?{payable_id:form.payable_id,amount:form.amount,note:form.note}:{...form}
  if(await run(()=>post(path,payload),mode.value==='application'?'付款申请已提交，等待审批':mode.value==='refund'?'供应商退款已登记':'付款已登记')){dialog.value=false;if(mode.value==='application')tab.value='applications';else if(mode.value==='refund')tab.value='refunds'}
}
watch(()=>route.query.tab,v=>{tab.value=String(v||'payables')})
watch(()=>route.query.status,v=>{status.value=String(v||'')})
const exportEntity=computed(()=>({payables:'payables',applications:'payment-applications',payments:'payments',refunds:'supplier-refunds'} as Record<string,string>)[tab.value]||'payables')
const exportFilters=computed(()=>{if(tab.value==='applications')return {q:applications.paging.q,status:status.value,id:route.query.application_id};if(tab.value==='payments')return {q:payments.paging.q,id:route.query.payment_id};if(tab.value==='refunds')return {q:refunds.paging.q,id:route.query.refund_id};return {q:payables.paging.q}})
</script>
<template>
  <PageHeader title="应付与付款" subtitle="按实际验收入库形成应付，审批通过后登记付款，退货后追踪退款，保留完整操作记录。">
    <ExchangeActions :entity="exportEntity" :filters="exportFilters"/><ExchangeActions v-if="tab==='payables'" entity="supplier-statement" export-label="导出累计对账" :filters="{q:payables.paging.q}"/><el-button :loading="payables.loading.value||payments.loading.value||applications.loading.value" @click="reload">刷新</el-button>
    <el-button v-if="auth.can('finance:record')" plain @click="open('payment')">直接登记付款</el-button>
    <el-button v-if="auth.can('finance:write')" plain @click="open('refund')">登记供应商退款</el-button>
    <el-button v-if="auth.can('finance:write')" type="primary" @click="open('application')">＋ 付款申请</el-button>
  </PageHeader>
  <div class="summary-strip"><div><span>本页待审批申请</span><b>{{pendingCount}}</b></div><div><span>本页已批准待付款</span><b>{{approvedCount}}</b></div><div><span>付款流程</span><strong>财务申请 → 负责人审批 → 财务执行</strong></div></div>
  <div class="notice-strip">余额口径 <span>未付余额 − 待审批 / 待付款占用，并受合同限额约束 = 可申请余额</span><b>驳回或撤回后释放占用</b></div>
  <section class="panel table-panel"><el-tabs v-model="tab">
    <el-tab-pane label="应付账款" name="payables"><ResourceState :loading="payables.loading.value" :error="payables.error.value" @retry="payables.load">
      <DataTable :remote="payables.paging" @page="payables.onPage" @size="payables.onSize" @search="payables.onSearch" :rows="payables.data.value" :action-width="290" :columns="[{key:'order_code',label:'采购订单',minWidth:175},{key:'project_name',label:'所属项目',minWidth:190},{key:'supplier_name',label:'应付供应商',minWidth:190},{key:'amount',label:'退货后应付（元）',money:true,minWidth:155},{key:'paid_amount',label:'净实付（元）',money:true,minWidth:155},{key:'outstanding_amount',label:'未付余额（元）',money:true,minWidth:155},{key:'refunded_amount',label:'累计退款（元）',money:true,minWidth:155},{key:'refundable_amount',label:'待收退款（元）',money:true,minWidth:155},{key:'reserved_amount',label:'申请占用（元）',money:true,minWidth:155},{key:'available_amount',label:'可申请余额（元）',money:true,minWidth:170}]">
        <template v-if="auth.can('finance:write')||auth.can('finance:record')" #actions="{row}"><el-button v-if="auth.can('finance:write')&&Number(row.refundable_amount)>0" link type="success" @click="open('refund',row)">登记收到退款</el-button><el-button v-if="auth.can('finance:write')" link type="primary" :disabled="Number(row.available_amount)<=0" @click="open('application',row)">申请付款</el-button><el-button v-if="auth.can('finance:record')" link type="primary" :disabled="Number(row.available_amount)<=0" @click="open('payment',row)">直接登记</el-button></template>
      </DataTable>
    </ResourceState></el-tab-pane>
    <el-tab-pane :label="`付款申请（本页待审批 ${pendingCount}）`" name="applications"><ResourceState :loading="applications.loading.value" :error="applications.error.value" @retry="applications.load">
      <DataTable :remote="applications.paging" @page="applications.onPage" @size="applications.onSize" @search="applications.onSearch" :rows="filteredApplications" :action-width="260" :columns="[{key:'id',label:'申请编号',width:100},{key:'order_code',label:'采购订单',minWidth:170},{key:'project_name',label:'所属项目',minWidth:190},{key:'supplier_name',label:'供应商',minWidth:180},{key:'amount',label:'申请金额（元）',money:true,minWidth:150},{key:'applicant_name',label:'申请人'},{key:'status',label:'状态',status:true},{key:'reviewer_name',label:'审批人'},{key:'review_comment',label:'审批意见',minWidth:180},{key:'reviewed_at',label:'审批时间',date:true,minWidth:170},{key:'note',label:'申请说明',minWidth:180},{key:'created_at',label:'申请时间',date:true,minWidth:170}]">
        <template #toolbar><el-select v-model="status" placeholder="全部状态" clearable style="width:160px"><el-option label="待审批" value="PENDING"/><el-option label="已批准待付款" value="APPROVED"/><el-option label="已驳回" value="REJECTED"/><el-option label="已撤回" value="CANCELLED"/><el-option label="已付款" value="PAID"/></el-select></template>
        <template #status="{row}"><el-tag v-if="row.status==='APPROVED'" type="success" round size="small">已批准待付款</el-tag><el-tag v-else-if="row.status==='CANCELLED'" type="info" round size="small">已撤回</el-tag><StatusTag v-else :value="row.status"/></template>
        <template #actions="{row}"><el-button v-if="canReview(row)" link type="primary" @click="openReview(row)">审批</el-button><el-button v-if="canExecute(row)" link type="primary" @click="open('execute',row)">执行付款</el-button><el-button v-if="canCancel(row)" link type="danger" @click="openCancel(row)">撤回</el-button><el-button link type="primary" @click="showHistory(row)">记录</el-button></template>
      </DataTable>
    </ResourceState></el-tab-pane>
    <el-tab-pane label="付款记录" name="payments"><ResourceState :loading="payments.loading.value" :error="payments.error.value" @retry="payments.load"><DataTable :remote="payments.paging" @page="payments.onPage" @size="payments.onSize" @search="payments.onSearch" :rows="payments.data.value" :columns="[{key:'reference',label:'付款凭证',minWidth:180},{key:'order_code',label:'采购订单',minWidth:170},{key:'supplier_name',label:'供应商',minWidth:190},{key:'amount',label:'实付金额（元）',money:true,minWidth:155},{key:'operator_name',label:'经办人'},{key:'note',label:'说明',minWidth:160},{key:'created_at',label:'付款时间',date:true,minWidth:170}]"/></ResourceState></el-tab-pane>
    <el-tab-pane label="供应商退款" name="refunds"><ResourceState :loading="refunds.loading.value" :error="refunds.error.value" @retry="refunds.load"><DataTable :remote="refunds.paging" @page="refunds.onPage" @size="refunds.onSize" @search="refunds.onSearch" :rows="refunds.data.value" :columns="[{key:'reference',label:'退款凭证',minWidth:180},{key:'order_code',label:'采购订单',minWidth:180},{key:'supplier_name',label:'退款供应商',minWidth:190},{key:'project_name',label:'所属项目',minWidth:190},{key:'amount',label:'实收退款（元）',money:true,minWidth:155},{key:'operator_name',label:'经办人'},{key:'note',label:'说明'},{key:'created_at',label:'退款时间',date:true,minWidth:170}]"/></ResourceState></el-tab-pane>
  </el-tabs></section>
  <el-dialog v-model="dialog" :title="title" width="650px" :close-on-click-modal="false">
    <el-alert v-if="mode==='payment'" title="直接登记用于已完成的线下付款记账，需要单独授权；请填写真实凭证号。" type="warning" :closable="false" class="mb"/>
    <el-form label-position="top"><el-form-item label="关联应付单 *"><RecordSelect v-model="form.payable_id" entity="payables" :disabled="mode==='execute'" @selected="selectedPayable=$event"/></el-form-item>
      <el-alert v-if="currentPayable" :title="mode==='refund'?`待收供应商退款：¥ ${money(currentPayable.refundable_amount)}`:`未付 ¥ ${money(currentPayable.outstanding_amount)} · 申请占用 ¥ ${money(currentPayable.reserved_amount)} · 可申请 ¥ ${money(currentPayable.available_amount)}`" type="info" :closable="false" class="mb"/>
      <el-form-item label="金额（元） *"><el-input v-model="form.amount" inputmode="decimal" :disabled="mode==='execute'" placeholder="填写本次金额"/></el-form-item><el-form-item v-if="mode!=='application'" :label="mode==='refund'?'到账退款凭证号 *':'付款凭证号 *'"><el-input v-model="form.reference" maxlength="100" :placeholder="mode==='refund'?'到账流水号或退款凭证，不可重复':'银行流水号或财务付款凭证，不可重复'"/></el-form-item><el-form-item label="说明"><el-input v-model="form.note" type="textarea" :rows="3" maxlength="1000"/></el-form-item>
    </el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{mode==='application'?'提交审批':mode==='refund'?'确认登记退款':'确认登记付款'}}</el-button></template>
  </el-dialog>
  <el-dialog v-model="reviewDialog" title="付款申请审批" width="580px" :close-on-click-modal="false"><div class="approval-summary"><b>申请 #{{selected?.id}} · {{selected?.order_code}}</b><p>{{selected?.supplier_name}} · 申请人 {{selected?.applicant_name}}</p><strong>¥ {{money(selected?.amount)}}</strong><p>{{selected?.note||'暂无申请说明'}}</p></div><el-form label-position="top"><el-form-item label="审批决定"><el-radio-group v-model="decision"><el-radio-button value="APPROVED">批准付款</el-radio-button><el-radio-button value="REJECTED">驳回申请</el-radio-button></el-radio-group></el-form-item><el-form-item label="审批意见（驳回必填）"><el-input v-model="comment" type="textarea" :rows="3" maxlength="1000"/></el-form-item></el-form><template #footer><el-button @click="reviewDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="review">确认审批</el-button></template></el-dialog>
  <el-dialog v-model="cancelDialog" title="撤回付款申请" width="520px" :close-on-click-modal="false"><el-alert title="撤回后释放占用金额，原申请和审批记录保留。如需付款，请重新申请。" type="info" :closable="false" class="mb"/><el-input v-model="reason" type="textarea" :rows="3" maxlength="1000" placeholder="填写撤回原因"/><template #footer><el-button @click="cancelDialog=false">返回</el-button><el-button type="danger" :loading="saving" @click="cancel">确认撤回</el-button></template></el-dialog>
  <el-dialog v-model="historyDialog" :title="`付款申请 #${selected?.id||''} 操作记录`" width="780px"><ResourceState :loading="historyLoading" :error="historyError" @retry="loadHistory"><DataTable :rows="history" :search="false" :columns="[{key:'decision',label:'操作',status:true},{key:'operator_name',label:'操作人'},{key:'comment',label:'说明',minWidth:240},{key:'created_at',label:'时间',date:true,minWidth:170}]"><template #decision="{row}"><span v-if="row.decision==='SUBMITTED'">提交申请</span><span v-else-if="row.decision==='CANCELLED'">撤回申请</span><StatusTag v-else :value="row.decision"/></template></DataTable><p v-if="!history.length" class="muted">历史演示申请可能早于操作记录功能，旧付款及申请数据保持原样。</p></ResourceState></el-dialog>
</template>
