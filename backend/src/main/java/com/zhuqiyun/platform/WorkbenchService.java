package com.zhuqiyun.platform;

import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import static com.zhuqiyun.domain.Rules.*;

@Service
public class WorkbenchService {
 private final Db db;private final Auth auth;
 public WorkbenchService(Db db,Auth auth){this.db=db;this.auth=auth;}
 private boolean menu(Map<String,Object> u,String key){return ((List<?>)u.get("menus")).stream().anyMatch(v->"*".equals(v)||key.equals(v.toString().replaceFirst("^/","")));}
 private void group(List<Map<String,Object>> groups,Map<String,Object> user,String key,String title,String permission,String menu,String path,String sql,Object...args){
  if(!auth.can(permission)||!menu(user,menu))return;
  if(Set.of("quote_needed","award_needed","purchase_approval","rejected_requests").contains(key)&&!auth.can("request:read"))return;
  if(key.equals("award_needed")&&(!auth.can("quote:read")||!auth.can("order:read")))return;
  if(key.equals("payment_approval")&&!auth.can("finance:read"))return;
  if(key.equals("plan_due")&&!auth.can("contract:read"))return;
  if(Set.of("low_stock","stocktakes","stocktake_approval").contains(key)&&!auth.can("inventory:company"))return;
  if(key.equals("stocktake_approval")&&!auth.can("stocktake:read"))return;
  if(key.equals("budget_risks")&&!auth.can("project:read"))return;
  long count=((Number)db.one("SELECT COUNT(*) total FROM ("+sql+") work_items",args).get("total")).longValue();
  List<Map<String,Object>> rows=db.list(sql+" ORDER BY waiting_since,id LIMIT 5",args);
  for(var r:rows){String target=Objects.toString(r.remove("target"),path);r.put("path",target);Object since=r.get("waiting_since");if(since instanceof java.sql.Timestamp t)r.put("waiting_days",Math.max(0,Duration.between(t.toInstant(),Instant.now()).toDays()));Object due=r.get("due_date");if(due!=null)r.put("overdue",LocalDate.parse(due.toString()).isBefore(LocalDate.now()));}
  groups.add(row("key",key,"title",title,"total",count,"items",rows,"path",path));
 }
 public Map<String,Object> view(){
  auth.require("dashboard:read");var user=auth.user();String role=user.get("role").toString(),scope=auth.projectFilter("p");List<Map<String,Object>> groups=new ArrayList<>();List<String> metrics=new ArrayList<>(),charts=new ArrayList<>();
  String request="SELECT r.id,r.code,p.name project_name,r.total_amount amount,r.status,CASE WHEN EXISTS(SELECT 1 FROM approvals a WHERE a.request_id=r.id AND a.decision=CASE WHEN r.status='PENDING' THEN 'SUBMITTED' ELSE r.status END) THEN CASE r.status WHEN 'PENDING' THEN '提交后' WHEN 'APPROVED' THEN '审批后' WHEN 'REJECTED' THEN '驳回后' ELSE '创建后' END ELSE '创建后' END waiting_label,COALESCE((SELECT MAX(a.created_at) FROM approvals a WHERE a.request_id=r.id AND a.decision=CASE WHEN r.status='PENDING' THEN 'SUBMITTED' ELSE r.status END),r.created_at) waiting_since,CONCAT('/requests/',r.id) target FROM purchase_requests r JOIN projects p ON p.id=r.project_id WHERE r.tenant_id=1 AND "+scope;
  String orders="SELECT o.id,o.code,p.name project_name,o.total_amount-o.closed_amount-COALESCE((SELECT SUM(r.amount) FROM purchase_returns r WHERE r.order_id=o.id),0) amount,o.status,o.created_at waiting_since,CONCAT('/orders/',o.id) target FROM purchase_orders o JOIN projects p ON p.id=o.project_id WHERE o.tenant_id=1 AND "+scope;
  String applications="SELECT a.id,CONCAT('付款申请 #',a.id) code,p.name project_name,a.amount,a.status,CASE WHEN a.status='APPROVED' AND a.reviewed_at IS NOT NULL THEN '批准后' ELSE '提交后' END waiting_label,CASE WHEN a.status='APPROVED' THEN COALESCE(a.reviewed_at,a.created_at) ELSE a.created_at END waiting_since,CONCAT('/finance?tab=applications&application_id=',a.id) target FROM payment_applications a JOIN payables v ON v.id=a.payable_id JOIN projects p ON p.id=v.project_id WHERE a.tenant_id=1 AND "+scope;
  String risks="SELECT p.id,p.code,p.name project_name,COALESCE((SELECT SUM(CASE WHEN m.kind='ISSUE' THEN m.amount ELSE -m.amount END) FROM stock_movements m WHERE m.project_id=p.id AND m.kind IN ('ISSUE','ISSUE_RETURN')),0)+COALESCE((SELECT SUM(c.amount) FROM costs c WHERE c.project_id=p.id),0) amount,p.created_at waiting_since,CONCAT('/projects/',p.id) target FROM projects p WHERE "+scope+" AND p.budget_amount<(COALESCE((SELECT SUM(CASE WHEN m.kind='ISSUE' THEN m.amount ELSE -m.amount END) FROM stock_movements m WHERE m.project_id=p.id AND m.kind IN ('ISSUE','ISSUE_RETURN')),0)+COALESCE((SELECT SUM(c.amount) FROM costs c WHERE c.project_id=p.id),0))";
  if(Set.of("manager","admin").contains(role)){
   group(groups,user,"my_projects","所属进行中项目","project:read","projects","/projects", "SELECT p.id,p.code,p.name project_name,p.status,p.created_at waiting_since,CONCAT('/projects/',p.id) target FROM projects p WHERE "+scope+" AND p.status IN ('ACTIVE','PLANNING')");
   group(groups,user,"rejected_requests","被驳回待修改","request:read","requests","/requests?status=REJECTED",request+" AND r.status='REJECTED' AND r.applicant_id=?",auth.id());
   group(groups,user,"project_procurement","项目采购进展","order:read","orders","/orders",orders+" AND o.status IN ('OPEN','PARTIAL')");
   group(groups,user,"budget_risks","预算风险","cost:read","projects","/projects",risks);
   metrics.addAll(List.of("active_projects","budget_amount","actual_cost"));charts.add("budget");charts.add("composition");
  }
  if(role.equals("buyer")){
   String unawarded=" AND r.status='APPROVED' AND NOT EXISTS(SELECT 1 FROM purchase_orders o WHERE o.request_id=r.id)";
   group(groups,user,"quote_needed","已批准待询价","quote:read","requests","/quotes",request+unawarded+" AND (SELECT COUNT(DISTINCT q.supplier_id) FROM quotes q WHERE q.request_id=r.id)<2");
   group(groups,user,"award_needed","待比较并下单","order:write","requests","/quotes",request+unawarded+" AND (SELECT COUNT(DISTINCT q.supplier_id) FROM quotes q WHERE q.request_id=r.id)>=2");
   group(groups,user,"delivery_followup","待跟进到货","order:read","orders","/orders",orders+" AND o.status IN ('OPEN','PARTIAL')");
   metrics.add("purchase_amount");charts.addAll(List.of("purchases","order_status"));
  }
  if(role.equals("warehouse")){
   group(groups,user,"receipts","待收货订单","order:read","orders","/orders",orders+" AND o.status IN ('OPEN','PARTIAL')");
   group(groups,user,"low_stock","低库存材料","inventory:read","inventory","/inventory?low_only=true","SELECT i.id,m.code,m.name project_name,m.unit,i.quantity,i.safety_stock_hint,NULL waiting_since,CONCAT('/inventory?warehouse_id=',i.warehouse_id,'&material_id=',i.material_id) target FROM (SELECT v.*,m.safety_stock safety_stock_hint FROM inventory v JOIN materials m ON m.id=v.material_id) i JOIN materials m ON m.id=i.material_id WHERE i.tenant_id=1 AND i.quantity<m.safety_stock");
   group(groups,user,"stocktakes","进行中的盘点","stocktake:read","inventory","/inventory?tab=stocktakes","SELECT s.id,s.code,w.name project_name,s.status,s.created_at waiting_since,CONCAT('/inventory?tab=stocktakes&stocktake_id=',s.id) target FROM stocktakes s JOIN warehouses w ON w.id=s.warehouse_id WHERE s.tenant_id=1 AND s.applicant_id=? AND s.status IN ('DRAFT','PENDING','REJECTED')",auth.id());
  }
  if(role.equals("finance")){
   group(groups,user,"payment_pending","待审批付款","finance:read","finance","/finance?tab=applications&status=PENDING",applications+" AND a.status='PENDING'");
   group(groups,user,"payment_execution","已批准待执行","finance:read","finance","/finance?tab=applications&status=APPROVED",applications+" AND a.status='APPROVED'");
   group(groups,user,"plan_due","付款计划到期提醒","finance:read","contracts","/contracts","SELECT s.id,CONCAT(c.code,' · ',s.stage_name) code,p.name project_name,s.amount,s.due_date,s.due_date waiting_since,CONCAT('/contracts?contract_id=',c.id) target FROM contract_payment_stages s JOIN contracts c ON c.id=s.contract_id JOIN projects p ON p.id=c.project_id WHERE c.tenant_id=1 AND c.status='ACTIVE' AND s.due_date IS NOT NULL AND s.due_date<=? AND s.amount>COALESCE((SELECT SUM(CASE WHEN a.payment_id IS NOT NULL THEN a.amount ELSE -a.amount END) FROM contract_payment_allocations a WHERE a.stage_id=s.id AND a.voided=FALSE),0) AND "+scope,LocalDate.now());
   metrics.add("outstanding_amount");
  }
  if(Set.of("boss","admin").contains(role)){
   group(groups,user,"purchase_approval","待审批采购","request:approve","requests","/approvals",request+" AND r.status='PENDING' AND r.applicant_id<>?",auth.id());
   group(groups,user,"payment_approval","待审批付款","finance:approve","finance","/finance?tab=applications&status=PENDING",applications+" AND a.status='PENDING' AND a.applicant_id<>?",auth.id());
   if(!role.equals("admin"))group(groups,user,"budget_risks","超预算项目","cost:read","projects","/projects",risks);
   group(groups,user,"stocktake_approval","待审核盘点","stocktake:approve","inventory","/inventory?tab=stocktakes&status=PENDING","SELECT s.id,s.code,w.name project_name,s.status,COALESCE(s.submitted_at,s.created_at) waiting_since,CONCAT('/inventory?tab=stocktakes&stocktake_id=',s.id) target FROM stocktakes s JOIN warehouses w ON w.id=s.warehouse_id WHERE s.tenant_id=1 AND s.status='PENDING' AND s.applicant_id<>?",auth.id());
   metrics=new ArrayList<>(List.of("active_projects","contract_amount","actual_cost","outstanding_amount"));charts=new ArrayList<>(List.of("budget","composition","purchases","order_status"));
  }
  // The admin workspace focuses on at most five groups; all operations remain available in navigation.
  if(groups.size()>5)groups=new ArrayList<>(groups.subList(groups.size()-5,groups.size()));
  return row("role",role,"groups",groups,"metric_keys",metrics,"chart_keys",charts,"as_of",Instant.now());
 }
}
