package com.zhuqiyun.finance;

import com.zhuqiyun.platform.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import static com.zhuqiyun.domain.Rules.*;

@Service
public class PaymentPlanService {
 private final Db db;private final Auth auth;private final ObjectMapper json;
 public PaymentPlanService(Db db,Auth auth,ObjectMapper json){this.db=db;this.auth=auth;this.json=json;}
 private long n(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
 public Map<String,Object> contract(long id){auth.require("contract:read");var c=db.one("SELECT * FROM contracts WHERE id=? AND tenant_id=1",id);auth.project(n(c,"project_id"));return c;}
 public Map<String,Object> lockHeader(Long id,Long nextOrder){Map<String,Object> before=id==null?null:contract(id);Set<Long> orders=new TreeSet<>();if(nextOrder!=null)orders.add(nextOrder);if(before!=null&&before.get("order_id")!=null)orders.add(n(before,"order_id"));for(long order:orders)db.one("SELECT id FROM purchase_orders WHERE id=? AND tenant_id=1 FOR UPDATE",order);for(long order:orders)db.one("SELECT id FROM payables WHERE order_id=? FOR UPDATE",order);if(id==null)return null;var c=db.one("SELECT * FROM contracts WHERE id=? AND tenant_id=1 FOR UPDATE",id);check(Objects.equals(before.get("order_id"),c.get("order_id")),"合同关联已变更，请刷新");return c;}
 private Map<String,Object> lock(long id){return lockHeader(id,null);}
 private List<Map<String,Object>> normalize(Object input){
  if(!(input instanceof List<?> rows)||rows.size()>100)bad("付款计划应为数组且最多100行");List<Map<String,Object>> result=new ArrayList<>();Set<Long> seen=new HashSet<>();
  for(Object raw:(List<?>)input){if(!(raw instanceof Map<?,?>))bad("计划行格式无效");Map<String,Object> r=(Map<String,Object>)raw;
   String name=Objects.toString(r.getOrDefault("stage_name",r.getOrDefault("stage",r.get("name"))),"").trim();check(!name.isBlank()&&name.length()<=120,"付款阶段名称必填且不得超过120字");
   if(!r.containsKey("amount")||r.get("amount")==null||r.get("amount").toString().isBlank()||r.containsKey("percentage"))bad("百分比或缺失金额的旧计划不能自动推算，请保留原文后人工确认");BigDecimal amount=decimal(r,"amount",2,false);Object date=r.getOrDefault("due_date",r.get("date"));LocalDate due=date==null||date.toString().isBlank()?null:date(row("date",date),"date");
   var line=row("stage_name",name,"due_date",due,"amount",amount,"note",optional(r,"note"),"sort_order",result.size());if(r.get("id")!=null&&!r.get("id").toString().isBlank()){long id=id(r,"id");unique(seen,id);line.put("id",id);}result.add(line);
  }return result;
 }
 public List<Map<String,Object>> parse(String raw){try{return normalize(json.readValue(raw==null||raw.isBlank()?"[]":raw,List.class));}catch(org.springframework.web.server.ResponseStatusException e){throw e;}catch(Exception e){bad("旧付款计划不是可识别数组，原文已保留");return List.of();}}
 private String encode(Object value){try{String result=json.writeValueAsString(value);check(result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=60000,"计划内容不得超过60KB");return result;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("计划序列化失败");}}
 private BigDecimal net(long stage){return net(stage,true);}
 private BigDecimal net(long stage,boolean lock){return db.list("SELECT amount,payment_id FROM contract_payment_allocations WHERE stage_id=? AND voided=FALSE"+(lock?" FOR UPDATE":""),stage).stream().map(a->a.get("payment_id")!=null?dec(a.get("amount")):dec(a.get("amount")).negate()).reduce(BigDecimal.ZERO,BigDecimal::add);}
 private boolean migrated(Map<String,Object> c){return Boolean.TRUE.equals(c.get("plan_migrated"))||"1".equals(Objects.toString(c.get("plan_migrated")));}
 @Transactional(readOnly=true) public Map<String,Object> view(long id){
  var c=contract(id);String raw=Objects.toString(c.get("payment_plan"),"[]"),warning=null;boolean unsupported=false;List<Map<String,Object>> rows;
  if(migrated(c))rows=db.list("SELECT * FROM contract_payment_stages WHERE contract_id=? ORDER BY sort_order,id",id);else{try{rows=parse(raw);}catch(Exception e){rows=new ArrayList<>();unsupported=true;warning="旧计划无法完整转换："+(e instanceof org.springframework.web.server.ResponseStatusException reason?reason.getReason():"内容格式无法识别")+"。不会静默覆盖，请核实原文后重建。";}}
  BigDecimal planned=rows.stream().map(r->dec(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);if(planned.compareTo(dec(c.get("amount")))>0)warning="旧计划金额超过合同，请人工核实后保存，原文仍保留。";
  for(var r:rows){boolean history=r.get("id")!=null&&!db.list("SELECT id FROM contract_payment_allocations WHERE stage_id=? LIMIT 1",r.get("id")).isEmpty();if(auth.can("finance:read"))r.put("has_history",history);r.put("can_delete",r.get("id")==null||auth.can("finance:read")&&!history);if(auth.can("finance:read")){BigDecimal actual=r.get("id")==null?BigDecimal.ZERO:net(((Number)r.get("id")).longValue(),false);r.put("linked_net_paid",actual);r.put("unlinked_plan_amount",dec(r.get("amount")).subtract(actual));Object due=r.get("due_date");r.put("due_for_review",due!=null&&!LocalDate.parse(due.toString()).isAfter(LocalDate.now())&&dec(r.get("amount")).compareTo(actual)>0);}}
  var result=row("contract_id",id,"order_id",c.get("order_id"),"contract_amount",c.get("amount"),"version",c.get("plan_version"),"migrated",migrated(c),"stages",rows,"planned_total",unsupported?null:planned,"unassigned_amount",unsupported?null:dec(c.get("amount")).subtract(planned),"allow_unassigned",true,"legacy_warning",warning,"legacy_raw",migrated(c)?null:raw,"actuals_supported",c.get("order_id")!=null,"access",DataPolicy.flags(auth));
  result.put("history",db.list("SELECT h.id,h.created_at,u.name operator_name FROM contract_plan_history h JOIN users u ON u.id=h.operator_id WHERE h.contract_id=? ORDER BY h.id DESC LIMIT 20",id));
  if(auth.can("finance:read")){
   result.put("allocations",db.list("SELECT a.*,s.stage_name,u.name operator_name,COALESCE(p.reference,r.reference) source_reference,CASE WHEN a.payment_id IS NOT NULL THEN 'PAYMENT' ELSE 'REFUND' END source_type FROM contract_payment_allocations a JOIN contract_payment_stages s ON s.id=a.stage_id JOIN users u ON u.id=a.operator_id LEFT JOIN payments p ON p.id=a.payment_id LEFT JOIN supplier_refunds r ON r.id=a.refund_id WHERE a.contract_id=? ORDER BY a.id DESC LIMIT 20",id));
   if(c.get("order_id")!=null){result.put("payments",db.list("SELECT p.*,p.amount-COALESCE((SELECT SUM(a.amount) FROM contract_payment_allocations a WHERE a.payment_id=p.id AND a.voided=FALSE),0) unallocated_amount FROM payments p JOIN payables v ON v.id=p.payable_id WHERE v.order_id=? ORDER BY p.id DESC LIMIT 20",c.get("order_id")));result.put("refunds",db.list("SELECT r.*,r.amount-COALESCE((SELECT SUM(a.amount) FROM contract_payment_allocations a WHERE a.refund_id=r.id AND a.voided=FALSE),0) unallocated_amount FROM supplier_refunds r JOIN payables v ON v.id=r.payable_id WHERE v.order_id=? ORDER BY r.id DESC LIMIT 20",c.get("order_id")));}
  }return result;
 }
 public void validateTotal(long id,BigDecimal amount){var c=contract(id);List<Map<String,Object>> rows=migrated(c)?db.list("SELECT amount FROM contract_payment_stages WHERE contract_id=?",id):parse(Objects.toString(c.get("payment_plan"),"[]"));check(rows.stream().map(r->dec(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(amount)<=0,"合同金额不能小于已维护的付款计划总额");}
 @Transactional public Map<String,Object> save(long id,Map<String,Object> body){
  auth.require("contract:plan");auth.require("contract:write");auth.require("finance:read");var c=lock(id);long version;try{version=new BigDecimal(Objects.toString(body.get("expected_version"))).longValueExact();}catch(Exception e){bad("请提供计划版本号");return null;}check(version==n(c,"plan_version"),"付款计划已被修改，请刷新后重试");
  var rows=normalize(body.get("stages"));BigDecimal planned=rows.stream().map(r->dec(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);check(planned.compareTo(dec(c.get("amount")))<=0,"计划总额不能超过合同金额，允许未分配余额");
  if(!migrated(c)){try{var old=parse(Objects.toString(c.get("payment_plan"),"[]"));check(old.stream().map(r->dec(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(dec(c.get("amount")))<=0,"旧计划金额超限");}catch(Exception e){check(Boolean.TRUE.equals(body.get("replace_legacy")),"旧计划无法确认，必须明确勾选保留原文并重建");}}
  var old=db.list("SELECT * FROM contract_payment_stages WHERE contract_id=? FOR UPDATE",id);Set<Long> keep=new HashSet<>();for(var r:rows)if(r.get("id")!=null){long sid=n(r,"id");db.one("SELECT id FROM contract_payment_stages WHERE id=? AND contract_id=?",sid,id);keep.add(sid);check(net(sid).compareTo(dec(r.get("amount")))<=0,"阶段金额不能小于已关联净付款");}
  for(var r:old)if(!keep.contains(n(r,"id")))check(db.list("SELECT id FROM contract_payment_allocations WHERE stage_id=? LIMIT 1",r.get("id")).isEmpty(),"有付款关联历史的阶段不能删除，请保留历史");
  db.jdbc().update("UPDATE contract_payment_stages SET sort_order=-sort_order-1 WHERE contract_id=?",id);
  for(var r:old)if(!keep.contains(n(r,"id")))db.jdbc().update("DELETE FROM contract_payment_stages WHERE id=?",r.get("id"));
  for(var r:rows){Map<String,Object> values=new LinkedHashMap<>(r);Object stageId=values.remove("id");values.put("contract_id",id);if(stageId==null)db.insert("contract_payment_stages",values);else db.update("contract_payment_stages",((Number)stageId).longValue(),values);}
  String encoded=encode(rows);db.insert("contract_plan_history",row("contract_id",id,"before_json",Objects.toString(c.get("payment_plan"),""),"after_json",encoded,"operator_id",auth.id()));db.update("contracts",id,row("payment_plan",encoded,"plan_migrated",true,"plan_version",version+1));auth.audit("SAVE_PAYMENT_PLAN","contract",id,"保存付款计划 "+rows.size()+" 阶段，总额 "+planned);return row("id",id,"saved",true);
 }
 @Transactional public Map<String,Object> allocate(long id,Map<String,Object> body){
  auth.require("finance:write");auth.require("finance:read");var c=lock(id);check(c.get("order_id")!=null,"当前合同没有可核对的采购付款来源");long stage=id(body,"stage_id"),source=id(body,"source_id");var s=db.one("SELECT * FROM contract_payment_stages WHERE id=? AND contract_id=? FOR UPDATE",stage,id);String type=text(body,"source_type"),reference=text(body,"reference");check(reference.length()<=100,"关联操作号过长");check(Set.of("PAYMENT","REFUND").contains(type),"关联来源类型无效");BigDecimal amount=decimal(body,"amount",2,true);
  String table=type.equals("PAYMENT")?"payments":"supplier_refunds",column=type.equals("PAYMENT")?"payment_id":"refund_id";var actual=db.one("SELECT t.* FROM "+table+" t JOIN payables v ON v.id=t.payable_id WHERE t.id=? AND t.tenant_id=1 AND v.order_id=? FOR UPDATE",source,c.get("order_id"));BigDecimal used=db.list("SELECT amount FROM contract_payment_allocations WHERE "+column+"=? AND voided=FALSE FOR UPDATE",source).stream().map(r->dec(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);check(amount.compareTo(dec(actual.get("amount")).subtract(used))<=0,"关联金额超过该实际记录的未分配金额");
  BigDecimal after=net(stage).add(type.equals("PAYMENT")?amount:amount.negate());check(after.signum()>=0&&after.compareTo(dec(s.get("amount")))<=0,"阶段关联净付款不能小于零或超过计划金额");check(db.list("SELECT id FROM contract_payment_allocations WHERE reference=?",reference).isEmpty(),"本次关联已提交，请刷新记录");
  long aid=db.insert("contract_payment_allocations",row("contract_id",id,"stage_id",stage,"payment_id",type.equals("PAYMENT")?source:null,"refund_id",type.equals("REFUND")?source:null,"amount",amount,"reference",reference,"operator_id",auth.id()));db.update("contracts",id,row("plan_version",n(c,"plan_version")+1));auth.audit("ALLOCATE_CONTRACT_PAYMENT","contract",id,"关联 "+type+" #"+source+" 至阶段 "+s.get("stage_name")+" 金额 "+amount);return row("id",id,"saved",true);
 }
 @Transactional public Map<String,Object> voidAllocation(long id,long allocation,Map<String,Object> body){
  auth.require("finance:write");auth.require("finance:read");var c=lock(id);var a=db.one("SELECT * FROM contract_payment_allocations WHERE id=? AND contract_id=? FOR UPDATE",allocation,id);check(!Boolean.TRUE.equals(a.get("voided"))&&!"1".equals(Objects.toString(a.get("voided"))),"该关联已取消");var stage=db.one("SELECT * FROM contract_payment_stages WHERE id=? FOR UPDATE",a.get("stage_id"));BigDecimal after=net(n(a,"stage_id")).add(a.get("payment_id")!=null?dec(a.get("amount")).negate():dec(a.get("amount")));check(after.signum()>=0&&after.compareTo(dec(stage.get("amount")))<=0,"取消后阶段净额不合法，请先核对其他关联或计划");String reason=text(body,"reason");db.update("contract_payment_allocations",allocation,row("voided",true,"voided_by",auth.id(),"voided_at",Timestamp.from(Instant.now()),"void_reason",reason));db.update("contracts",id,row("plan_version",n(c,"plan_version")+1));auth.audit("VOID_PAYMENT_ALLOCATION","contract",id,"取消关联 #"+allocation+" 原因 "+reason);return row("id",id,"saved",true);
 }
}
