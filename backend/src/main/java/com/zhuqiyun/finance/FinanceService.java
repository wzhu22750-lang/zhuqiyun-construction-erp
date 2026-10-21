package com.zhuqiyun.finance;

import com.zhuqiyun.platform.*;
import com.zhuqiyun.project.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
import static com.zhuqiyun.domain.Rules.*;

@Service
public class FinanceService {
 private final Db db;private final Auth auth;private final ProjectService projects;private final ObjectMapper json;
 public FinanceService(Db db,Auth auth,ProjectService projects,ObjectMapper json){this.db=db;this.auth=auth;this.projects=projects;this.json=json;}
 public List<Map<String,Object>> costs(){auth.require("cost:read");List<Map<String,Object>> result=db.list("SELECT p.* FROM projects p WHERE "+auth.projectFilter("p")+" ORDER BY p.id");for(Map<String,Object> p:result){long id=((Number)p.get("id")).longValue();p.put("metrics",projects.metrics(id));p.put("expenses",db.list("SELECT c.*,u.name operator_name FROM costs c JOIN users u ON u.id=c.operator_id WHERE c.project_id=? ORDER BY c.id DESC",id));p.put("composition",db.list("SELECT category,SUM(amount) amount FROM costs WHERE tenant_id=1 AND project_id=? GROUP BY category",id));}return result;}
 @Transactional public Map<String,Object> cost(Map<String,Object> body){auth.require("cost:write");long project=id(body,"project_id");auth.project(project);String category=text(body,"category");check(Set.of("LABOR","MACHINE","SUBCONTRACT","OTHER").contains(category),"费用类型无效，材料成本应通过领料归集");long id=db.insert("costs",row("project_id",project,"category",category,"amount",decimal(body,"amount",2,true),"description",text(body,"description"),"incurred_date",date(body,"incurred_date"),"operator_id",auth.id()));auth.audit("RECORD_COST","cost",id,text(body,"description"));return db.one("SELECT * FROM costs WHERE id=?",id);}
 public List<Map<String,Object>> contracts(){auth.require("contract:read");return db.list("SELECT c.*,p.name project_name,s.name supplier_name,o.code order_code,COALESCE(a.paid_amount,0) paid_amount,c.amount-COALESCE(a.paid_amount,0) unpaid_amount FROM contracts c JOIN projects p ON p.id=c.project_id LEFT JOIN suppliers s ON s.id=c.supplier_id LEFT JOIN purchase_orders o ON o.id=c.order_id LEFT JOIN payables a ON a.order_id=c.order_id WHERE c.tenant_id=1 AND "+auth.projectFilter("p")+" ORDER BY c.id DESC");}
 public Map<String,Object> contract(long id){Map<String,Object> c=db.one("SELECT * FROM contracts WHERE id=? AND tenant_id=1",id);auth.project(((Number)c.get("project_id")).longValue());return c;}
 @Transactional public Map<String,Object> saveContract(Long contractId,Map<String,Object> body){auth.require("contract:write");if(contractId!=null)contract(contractId);long project=id(body,"project_id");auth.project(project);String type=text(body,"type"),status=text(body,"status");check(Set.of("MAIN","PURCHASE","SUBCONTRACT").contains(type),"合同类型无效");check(Set.of("ACTIVE","COMPLETED","DRAFT").contains(status),"合同状态无效");Long supplier=nullableId(body,"supplier_id"),order=nullableId(body,"order_id");if(supplier!=null)db.one("SELECT id FROM suppliers WHERE id=? AND tenant_id=1",supplier);if("PURCHASE".equals(type))check(supplier!=null,"采购合同必须关联供应商");if(order!=null){Map<String,Object> o=db.one("SELECT * FROM purchase_orders WHERE id=? AND tenant_id=1",order);check(((Number)o.get("project_id")).longValue()==project,"采购订单与合同项目不一致");check(supplier!=null&&((Number)o.get("supplier_id")).longValue()==supplier,"合同供应商与订单不一致");check("PURCHASE".equals(type),"只有采购合同可关联采购订单");check(!"CANCELLED".equals(o.get("status")),"取消订单不能关联合同");check(db.list("SELECT id FROM contracts WHERE order_id=? AND id<>?",order,contractId==null?0:contractId).isEmpty(),"此订单已关联合同，避免重复展示付款");}
  var start=date(body,"start_date");var end=date(body,"end_date");check(!end.isBefore(start),"履约结束日不能早于开始日");String plan=optional(body,"payment_plan");if(!plan.isEmpty()){try{check(json.readTree(plan).isArray(),"付款计划应为JSON数组");}catch(java.io.IOException e){bad("付款计划格式无效");}}
  BigDecimal amount=decimal(body,"amount",2,true);if(order!=null){Map<String,Object> payable=db.one("SELECT * FROM payables WHERE order_id=?",order);check(amount.compareTo(dec(payable.get("paid_amount")))>=0,"合同金额不能低于已付款金额");}
  Map<String,Object> values=row("code",text(body,"code"),"name",text(body,"name"),"type",type,"project_id",project,"supplier_id",supplier,"order_id",order,"amount",amount,"signed_date",date(body,"signed_date"),"start_date",start,"end_date",end,"status",status,"payment_plan",plan);
  if(contractId==null)contractId=db.insert("contracts",values);else {Map<String,Object> old=contract(contractId);if(old.get("order_id")!=null&& !Objects.equals(nullableId(old,"order_id"),order))check(dec(db.one("SELECT paid_amount FROM payables WHERE order_id=?",old.get("order_id")).get("paid_amount")).signum()==0,"已有付款的合同不能更换订单");db.update("contracts",contractId,values);}auth.audit("SAVE_CONTRACT","contract",contractId,values.get("name").toString());return contract(contractId);
 }
 private Long nullableId(Map<String,Object> b,String key){return b.get(key)==null||b.get(key).toString().isBlank()?null:id(b,key);}
 @Transactional public void deleteContract(long id){auth.require("contract:write");Map<String,Object> c=contract(id);if(c.get("order_id")!=null)check(dec(db.one("SELECT paid_amount FROM payables WHERE order_id=?",c.get("order_id")).get("paid_amount")).signum()==0,"已有付款记录的合同不能删除");db.jdbc().update("DELETE FROM contracts WHERE id=? AND tenant_id=1",id);auth.audit("DELETE_CONTRACT","contract",id,c.get("code").toString());}
 public List<Map<String,Object>> payables(){
  auth.require("finance:read");
  List<Map<String,Object>> rows=db.list("SELECT a.*,a.amount-a.paid_amount outstanding_amount,p.name project_name,s.name supplier_name,o.code order_code,o.total_amount order_amount FROM payables a JOIN projects p ON p.id=a.project_id JOIN suppliers s ON s.id=a.supplier_id JOIN purchase_orders o ON o.id=a.order_id WHERE a.tenant_id=1 AND "+auth.projectFilter("p")+" ORDER BY a.id DESC");
  for(Map<String,Object> a:rows){BigDecimal reserved=reserved(((Number)a.get("id")).longValue(),0);a.put("reserved_amount",reserved);a.put("available_amount",available(a,0,false));}
  return rows;
 }
 public List<Map<String,Object>> payments(){auth.require("finance:read");return db.list("SELECT t.*,p.name project_name,s.name supplier_name,o.code order_code,u.name operator_name FROM payments t JOIN payables a ON a.id=t.payable_id JOIN projects p ON p.id=a.project_id JOIN suppliers s ON s.id=a.supplier_id JOIN purchase_orders o ON o.id=a.order_id JOIN users u ON u.id=t.operator_id WHERE t.tenant_id=1 AND "+auth.projectFilter("p")+" ORDER BY t.id DESC");}
 private Map<String,Object> payable(long id){Map<String,Object> a=db.one("SELECT * FROM payables WHERE id=? AND tenant_id=1 FOR UPDATE",id);auth.project(((Number)a.get("project_id")).longValue());return a;}
 private BigDecimal reserved(long payableId,long excludedApplication){return reserved(payableId,excludedApplication,false);}
 private BigDecimal reserved(long payableId,long excludedApplication,boolean lock){
  // Locking reads see current committed reservations even under MySQL REPEATABLE READ.
  return db.list("SELECT amount FROM payment_applications WHERE tenant_id=1 AND payable_id=? AND status IN ('PENDING','APPROVED') AND id<>?"+(lock?" FOR UPDATE":""),payableId,excludedApplication).stream().map(r->dec(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);
 }
 private BigDecimal available(Map<String,Object> payable,long excludedApplication){return available(payable,excludedApplication,true);}
 private BigDecimal available(Map<String,Object> payable,long excludedApplication,boolean lock){
  BigDecimal limit=dec(payable.get("amount"));
  List<Map<String,Object>> contracts=db.list("SELECT amount FROM contracts WHERE tenant_id=1 AND order_id=?",payable.get("order_id"));
  if(!contracts.isEmpty())limit=limit.min(dec(contracts.get(0).get("amount")));
  return limit.subtract(dec(payable.get("paid_amount"))).subtract(reserved(((Number)payable.get("id")).longValue(),excludedApplication,lock)).max(BigDecimal.ZERO);
 }
 /** Direct bookkeeping is an explicit administrator permission; ordinary finance uses approved applications. */
 @Transactional public Map<String,Object> payment(Map<String,Object> body){auth.require("finance:record");return postPayment(payable(id(body,"payable_id")),decimal(body,"amount",2,true),text(body,"reference"),optional(body,"note"),0);}
 private Map<String,Object> postPayment(Map<String,Object> payable,BigDecimal amount,String reference,String note,long excludedApplication){
  long id=((Number)payable.get("id")).longValue();
  check(amount.compareTo(available(payable,excludedApplication))<=0,"付款超过可付余额（其他申请已预留，且不可超过合同金额）");
  check(reference.length()<=100,"付款凭证号不得超过100字符");
  check(db.list("SELECT id FROM payments WHERE reference=?",reference).isEmpty(),"付款流水号已登记，禁止重复计入");
  long payment=db.insert("payments",row("payable_id",id,"amount",amount,"reference",reference,"note",note,"operator_id",auth.id()));
  db.update("payables",id,row("paid_amount",dec(payable.get("paid_amount")).add(amount)));
  auth.audit("REGISTER_PAYMENT","payment",payment,"付款 "+amount+" 流水 "+reference);
  return db.one("SELECT * FROM payments WHERE id=?",payment);
 }
 public List<Map<String,Object>> applications(){auth.require("finance:read");return db.list("SELECT t.*,p.name project_name,s.name supplier_name,o.code order_code,u.name applicant_name,v.name reviewer_name FROM payment_applications t JOIN payables a ON a.id=t.payable_id JOIN projects p ON p.id=a.project_id JOIN suppliers s ON s.id=a.supplier_id JOIN purchase_orders o ON o.id=a.order_id JOIN users u ON u.id=t.applicant_id LEFT JOIN users v ON v.id=t.reviewer_id WHERE t.tenant_id=1 AND "+auth.projectFilter("p")+" ORDER BY t.id DESC");}
 private Map<String,Object> application(long id){Map<String,Object> a=db.one("SELECT t.*,p.project_id FROM payment_applications t JOIN payables p ON p.id=t.payable_id WHERE t.id=? AND t.tenant_id=1",id);auth.project(((Number)a.get("project_id")).longValue());return a;}
 // All mutations lock payable before application, so review/cancel/pay share the reservation lock.
 private Map<String,Object> lockApplication(long id){return db.one("SELECT * FROM payment_applications WHERE id=? AND tenant_id=1 FOR UPDATE",id);}
 private void record(long id,String decision,String comment){db.insert("payment_application_records",row("application_id",id,"operator_id",auth.id(),"decision",decision,"comment",comment));auth.audit("PAYMENT_"+decision,"payment_application",id,comment);}
 public List<Map<String,Object>> applicationRecords(long id){auth.require("finance:read");application(id);return db.list("SELECT r.*,u.name operator_name FROM payment_application_records r JOIN users u ON u.id=r.operator_id WHERE r.application_id=? AND r.tenant_id=1 ORDER BY r.id",id);}
 @Transactional public Map<String,Object> apply(Map<String,Object> body){
  auth.require("finance:write");long payableId=id(body,"payable_id");Map<String,Object> payable=payable(payableId);BigDecimal amount=decimal(body,"amount",2,true);
  check(amount.compareTo(available(payable,0))<=0,"申请金额超过可申请余额（含待审批、待付款占用及合同限额）");
  long id=db.insert("payment_applications",row("payable_id",payableId,"amount",amount,"note",optional(body,"note"),"applicant_id",auth.id()));record(id,"SUBMITTED","申请付款 "+amount);return application(id);
 }
 @Transactional public Map<String,Object> review(long id,Map<String,Object> body){
  auth.require("finance:approve");Map<String,Object> initial=application(id);Map<String,Object> payable=payable(((Number)initial.get("payable_id")).longValue());Map<String,Object> a=lockApplication(id);
  check("PENDING".equals(a.get("status")),"只有待审批付款申请可审批");check(((Number)a.get("applicant_id")).longValue()!=auth.id(),"申请人与审批人必须不同");
  String decision=text(body,"decision"),comment=optional(body,"comment");check(Set.of("APPROVED","REJECTED").contains(decision),"审批结论无效");if("REJECTED".equals(decision))check(!comment.isBlank(),"驳回必须填写原因");
  if("APPROVED".equals(decision))check(dec(a.get("amount")).compareTo(available(payable,id))<=0,"余额或合同限额变化，请驳回后重新申请");
  db.update("payment_applications",id,row("status",decision,"reviewer_id",auth.id(),"reviewed_at",java.sql.Timestamp.from(java.time.Instant.now()),"review_comment",comment));record(id,decision,comment);return application(id);
 }
 @Transactional public Map<String,Object> cancelApplication(long id,Map<String,Object> body){
  auth.require("finance:write");Map<String,Object> initial=application(id);payable(((Number)initial.get("payable_id")).longValue());Map<String,Object> a=lockApplication(id);
  check(((Number)a.get("applicant_id")).longValue()==auth.id()||((List<?>)auth.user().get("permissions")).contains("*"),"只能撤回本人付款申请");
  check(Set.of("PENDING","APPROVED").contains(a.get("status")),"只有待审批或待付款申请可撤回");String reason=text(body,"reason");db.update("payment_applications",id,row("status","CANCELLED"));record(id,"CANCELLED",reason);return application(id);
 }
 @Transactional public Map<String,Object> execute(long id,Map<String,Object> body){
  auth.require("finance:write");Map<String,Object> initial=application(id);Map<String,Object> payable=payable(((Number)initial.get("payable_id")).longValue());Map<String,Object> a=lockApplication(id);
  check("APPROVED".equals(a.get("status")),"只有已批准的付款申请可执行");check(a.get("reviewer_id")!=null&&((Number)a.get("reviewer_id")).longValue()!=auth.id(),"审批人与付款执行人必须不同");
  Map<String,Object> payment=postPayment(payable,dec(a.get("amount")),text(body,"reference"),optional(body,"note"),id);
  db.update("payment_applications",id,row("status","PAID","payment_id",payment.get("id")));record(id,"PAID","付款凭证 "+payment.get("reference"));return payment;
 }
 @Transactional public void attachment(long id,String name,String key){auth.require("contract:write");contract(id);db.update("contracts",id,row("attachment_name",name,"attachment_key",key));auth.audit("UPLOAD_CONTRACT","contract",id,name);}
}
