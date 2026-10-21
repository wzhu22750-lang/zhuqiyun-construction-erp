package com.zhuqiyun;

import com.zhuqiyun.platform.*;
import com.zhuqiyun.procurement.ProcurementService;
import com.zhuqiyun.inventory.InventoryService;
import com.zhuqiyun.finance.FinanceService;
import com.zhuqiyun.project.ProjectService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.zhuqiyun.domain.Rules.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:erp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE", "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql,classpath:db/migration/V4__payment_application_approval.sql,classpath:db/migration/V5__procurement_adjustments.sql","app.seed=true"})
@Transactional
class BusinessFlowTest {
 @Autowired Db db; @Autowired ProcurementService procurement; @Autowired InventoryService inventory; @Autowired FinanceService finance; @Autowired ProjectService projects;
 long project,material,warehouse,supplier;
 @BeforeEach void setup(){as("admin");project=((Number)db.one("SELECT id FROM projects ORDER BY id LIMIT 1").get("id")).longValue();material=((Number)db.one("SELECT id FROM materials ORDER BY id LIMIT 1").get("id")).longValue();supplier=((Number)db.one("SELECT id FROM suppliers WHERE status='ACTIVE' ORDER BY id LIMIT 1").get("id")).longValue();warehouse=db.insert("warehouses",row("code",code("TESTWH"),"name","隔离测试仓","location","测试"));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void as(String username){long id=((Number)db.one("SELECT id FROM users WHERE username=?",username).get("id")).longValue();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of()));}
 Map<String,Object> draft(){as("manager");return procurement.save(null,row("project_id",project,"required_date",LocalDate.now().plusDays(15).toString(),"note","测试", "items",List.of(row("material_id",material,"quantity","10.000","unit_price","10.0000"))));}
 long idOf(Map<String,Object> m){return ((Number)m.get("id")).longValue();}
 Map<String,Object> order(){return order("10.0000");}
 Map<String,Object> order(String price){Map<String,Object> r=draft();long id=idOf(r);procurement.submit(id);as("boss");procurement.approve(id,row("decision","APPROVED","comment","审批同意"));as("buyer");Map<String,Object> q=procurement.quote(id,row("supplier_id",supplier,"delivery_days",3,"valid_until",LocalDate.now().plusDays(30).toString(),"items",List.of(row("request_item_id",idOf(((List<Map<String,Object>>)r.get("items")).get(0)),"unit_price",price))));return procurement.award(id,row("quote_id",q.get("id"),"reason","综合价格交期中标"));}
 Map<String,Object> receipt(Map<String,Object> order,String quantity,String ref){as("warehouse");long line=idOf(((List<Map<String,Object>>)order.get("items")).get(0));return inventory.receive(idOf(order),row("warehouse_id",warehouse,"reference",ref,"items",List.of(row("order_item_id",line,"quantity",quantity))));}
 void consume(String quantity,String ref){as("warehouse");inventory.issue(row("project_id",project,"warehouse_id",warehouse,"reference",ref,"items",List.of(row("material_id",material,"quantity",quantity))));}
 Map<String,Object> stock(){return db.one("SELECT * FROM inventory WHERE warehouse_id=? AND material_id=?",warehouse,material);}
 void amount(String expected,Object actual){assertEquals(0,new BigDecimal(expected).compareTo(dec(actual)));}
 @Test void fullFlowSeparatesInventoryCostAndCash(){as("admin");BigDecimal oldCost=dec(projects.metrics(project).get("material_cost"));BigDecimal oldPaid=dec(projects.metrics(project).get("paid_amount"));Map<String,Object> o=order();receipt(o,"6.000","FLOW-GR");amount("6",stock().get("quantity"));amount("60",stock().get("stock_value"));as("admin");amount(oldCost.toString(),projects.metrics(project).get("material_cost"));consume("2.000","FLOW-MI");amount("4",stock().get("quantity"));amount("40",stock().get("stock_value"));as("finance");long payable=idOf(db.one("SELECT * FROM payables WHERE order_id=?",o.get("id")));Map<String,Object> application=finance.apply(row("payable_id",payable,"amount","30.00"));as("boss");finance.review(idOf(application),row("decision","APPROVED"));as("finance");finance.execute(idOf(application),row("reference","FLOW-PAY"));as("admin");amount(oldCost.add(new BigDecimal("20")).toString(),projects.metrics(project).get("material_cost"));amount(oldPaid.add(new BigDecimal("30")).toString(),projects.metrics(project).get("paid_amount"));assertEquals("PARTIAL",procurement.orderDetail(idOf(o)).get("status"));}
 @Test void unapprovedCannotAward(){Map<String,Object> r=draft();as("buyer");assertThrows(ResponseStatusException.class,()->procurement.award(idOf(r),row("quote_id",1,"reason","违规")));}
 @Test void duplicateAwardIsRejected(){Map<String,Object> o=order();assertThrows(ResponseStatusException.class,()->procurement.award(((Number)o.get("request_id")).longValue(),row("quote_id",o.get("quote_id"),"reason","重复")));}
 @Test void overReceiptRollsBackAllLines(){Map<String,Object> o=order();assertThrows(ResponseStatusException.class,()->receipt(o,"11.000","OVER-GR"));assertTrue(db.list("SELECT id FROM receipts WHERE reference='OVER-GR'").isEmpty());assertTrue(db.list("SELECT id FROM inventory WHERE warehouse_id=?",warehouse).isEmpty());}
 @Test void insufficientStockCannotCreateIssue(){Map<String,Object> o=order();receipt(o,"3.000","INS-GR");assertThrows(ResponseStatusException.class,()->consume("4.000","INS-MI"));amount("3",stock().get("quantity"));assertTrue(db.list("SELECT id FROM issues WHERE reference='INS-MI'").isEmpty());}
 @Test void canceledOrderCannotReceive(){Map<String,Object> o=order();procurement.cancel(idOf(o),row("reason","需求取消"));assertThrows(ResponseStatusException.class,()->receipt(o,"1.000","CANCEL-GR"));}
 @Test void duplicateReceiptDoesNotChangeStock(){Map<String,Object> o=order();receipt(o,"3.000","DUP-GR");assertThrows(ResponseStatusException.class,()->receipt(o,"2.000","DUP-GR"));amount("3",stock().get("quantity"));}
 @Test void paymentsCannotExceedReceivedOrRepeat(){Map<String,Object> o=order();receipt(o,"6.000","PAY-GR");as("admin");long id=idOf(db.one("SELECT * FROM payables WHERE order_id=?",o.get("id")));assertThrows(ResponseStatusException.class,()->finance.payment(row("payable_id",id,"amount","60.01","reference","TOO-MUCH")));finance.payment(row("payable_id",id,"amount","20.00","reference","PAY-UNIQUE"));assertThrows(ResponseStatusException.class,()->finance.payment(row("payable_id",id,"amount","20.00","reference","PAY-UNIQUE")));amount("20",db.one("SELECT paid_amount FROM payables WHERE id=?",id).get("paid_amount"));}
 long payableFor(Map<String,Object> o){return idOf(db.one("SELECT * FROM payables WHERE order_id=?",o.get("id")));}
 Map<String,Object> apply(long payable,String amount){as("finance");return finance.apply(row("payable_id",payable,"amount",amount));}
 void approved(long id){as("boss");finance.review(id,row("decision","APPROVED"));as("finance");}
 void status(int code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(ResponseStatusException.class,action).getStatusCode().value());}
 @Test void paymentApplicationExecutesOnceAndReservesBalance(){
  Map<String,Object> o=order();receipt(o,"6.000","APP-GR");long payable=payableFor(o);Map<String,Object> app=apply(payable,"40.00");
  status(403,()->finance.payment(row("payable_id",payable,"amount","1.00","reference","APP-DIRECT")));
  status(409,()->finance.execute(idOf(app),row("reference","APP-EARLY")));
  as("admin");status(409,()->finance.payment(row("payable_id",payable,"amount","30.00","reference","APP-RESERVED")));
  approved(idOf(app));finance.execute(idOf(app),row("reference","APP-PAY"));status(409,()->finance.execute(idOf(app),row("reference","APP-AGAIN")));
  amount("40",db.one("SELECT paid_amount FROM payables WHERE id=?",payable).get("paid_amount"));
  assertEquals(List.of("SUBMITTED","APPROVED","PAID"),finance.applicationRecords(idOf(app)).stream().map(r->r.get("decision")).toList());
 }
 @Test void reviewPermissionsAndSeparationOfDuties(){
  Map<String,Object> o=order();receipt(o,"6.000","AUTH-APP-GR");long payable=payableFor(o);Map<String,Object> app=apply(payable,"20.00");
  status(403,()->finance.review(idOf(app),row("decision","APPROVED")));
  as("admin");Map<String,Object> own=finance.apply(row("payable_id",payable,"amount","10.00"));status(409,()->finance.review(idOf(own),row("decision","APPROVED")));
  // Even a role configured with both write and approve cannot review and execute the same request.
  db.jdbc().update("UPDATE roles SET permissions=? WHERE code='boss'","[\"finance:read\",\"finance:write\",\"finance:approve\"]");
  as("boss");finance.review(idOf(app),row("decision","APPROVED"));status(409,()->finance.execute(idOf(app),row("reference","AUTH-SELF-PAY")));
  as("finance");finance.execute(idOf(app),row("reference","AUTH-PAY"));
 }
 @Test void rejectionAndWithdrawalReleaseReservationWithoutPostingCash(){
  Map<String,Object> o=order();receipt(o,"6.000","REJECT-APP-GR");long payable=payableFor(o);Map<String,Object> first=apply(payable,"40.00");
  as("boss");status(409,()->finance.review(idOf(first),row("decision","REJECTED")));finance.review(idOf(first),row("decision","REJECTED","comment","凭证不全"));
  status(409,()->finance.review(idOf(first),row("decision","APPROVED")));
  Map<String,Object> second=apply(payable,"60.00");approved(idOf(second));finance.cancelApplication(idOf(second),row("reason","付款计划调整"));
  status(409,()->finance.execute(idOf(second),row("reference","WITHDRAWN-PAY")));status(409,()->finance.cancelApplication(idOf(second),row("reason","重复")));
  Map<String,Object> third=apply(payable,"60.00");finance.cancelApplication(idOf(third),row("reason","金额重报"));
  Map<String,Object> balance=finance.payables().stream().filter(a->idOf(a)==payable).findFirst().orElseThrow();amount("0",balance.get("reserved_amount"));amount("60",balance.get("available_amount"));amount("0",balance.get("paid_amount"));
  assertEquals(List.of("SUBMITTED","APPROVED","CANCELLED"),finance.applicationRecords(idOf(second)).stream().map(r->r.get("decision")).toList());
 }
 @Test void executionPreservesOtherReservationsAndDuplicateReferenceRollsBack(){
  Map<String,Object> o=order();receipt(o,"6.000","OTHER-APP-GR");long payable=payableFor(o);Map<String,Object> a=apply(payable,"20.00"),b=apply(payable,"30.00");approved(idOf(a));
  as("admin");status(409,()->finance.payment(row("payable_id",payable,"amount","10.01","reference","OTHER-OVER")));finance.payment(row("payable_id",payable,"amount","10.00","reference","OTHER-DIRECT"));
  as("finance");status(409,()->finance.execute(idOf(a),row("reference","OTHER-DIRECT")));assertEquals("APPROVED",db.one("SELECT status FROM payment_applications WHERE id=?",idOf(a)).get("status"));
  finance.execute(idOf(a),row("reference","OTHER-EXEC"));approved(idOf(b));finance.execute(idOf(b),row("reference","OTHER-LAST"));amount("60",db.one("SELECT paid_amount FROM payables WHERE id=?",payable).get("paid_amount"));
 }
 @Test void approvedApplicationRechecksContractLimitAtExecution(){
  Map<String,Object> o=order();receipt(o,"6.000","CONTRACT-APP-GR");long payable=payableFor(o);Map<String,Object> a=apply(payable,"40.00");approved(idOf(a));
  db.insert("contracts",row("code",code("LIMIT"),"name","付款限额测试","type","PURCHASE","project_id",project,"supplier_id",supplier,"order_id",o.get("id"),"amount",new BigDecimal("30"),"signed_date",LocalDate.now(),"start_date",LocalDate.now(),"end_date",LocalDate.now().plusDays(30),"status","ACTIVE"));
  status(409,()->finance.execute(idOf(a),row("reference","CONTRACT-OVER")));finance.cancelApplication(idOf(a),row("reason","按合同重新申请"));
  status(409,()->finance.apply(row("payable_id",payable,"amount","30.01")));apply(payable,"30.00");
 }
 @Test void scopedPaymentHistoryAndCancellationOwnership(){
  Map<String,Object> o=order();receipt(o,"6.000","SCOPE-APP-GR");Map<String,Object> a=apply(payableFor(o),"20.00");
  as("admin");Map<String,Object> other=finance.apply(row("payable_id",payableFor(o),"amount","10.00"));
  as("finance");status(409,()->finance.cancelApplication(idOf(other),row("reason","非本人")));
  as("admin");finance.cancelApplication(idOf(a),row("reason","管理员代撤"));
  db.jdbc().update("UPDATE roles SET permissions=? WHERE code='manager'","[\"finance:read\",\"finance:write\",\"finance:approve\"]");
  db.jdbc().update("UPDATE projects SET manager_id=(SELECT id FROM users WHERE username='boss') WHERE id=?",project);
  as("manager");status(403,()->finance.applicationRecords(idOf(a)));status(403,()->finance.cancelApplication(idOf(other),row("reason","越权")));status(403,()->finance.review(idOf(other),row("decision","APPROVED")));
 }
 @Test void rejectEditAndResubmit(){Map<String,Object> r=draft();procurement.submit(idOf(r));as("boss");procurement.approve(idOf(r),row("decision","REJECTED","comment","数量核实后重报"));as("manager");procurement.save(idOf(r),row("project_id",project,"required_date",LocalDate.now().plusDays(7).toString(),"items",List.of(row("material_id",material,"quantity","2.000","unit_price","10.0000"))));procurement.submit(idOf(r));assertEquals("PENDING",procurement.detail(idOf(r)).get("status"));assertEquals(3,db.list("SELECT * FROM approvals WHERE request_id=?",r.get("id")).size());}
 @Test void rolePermissionAndManagerScopeAreEnforced(){as("warehouse");assertThrows(ResponseStatusException.class,()->finance.payment(row("payable_id",1,"amount","1.00","reference","UNAUTH")));as("manager");long own=((Number)db.one("SELECT id FROM users WHERE username='manager'").get("id")).longValue();long other=db.insert("users",row("username",code("OTHER"),"name","另一个项目经理","password_hash","unused","role_id",db.one("SELECT id FROM roles WHERE code='manager'").get("id")));db.jdbc().update("UPDATE projects SET manager_id=? WHERE id=?",other,project);assertThrows(ResponseStatusException.class,()->projects.detail(project));assertNotEquals(own,other);}
 @Test void weightedAverageAndFullDepletionConserveMoney(){Map<String,Object> first=order("10.0000");receipt(first,"3.000","AVG-1");Map<String,Object> second=order("20.0000");receipt(second,"7.000","AVG-2");amount("170",stock().get("stock_value"));amount("17",stock().get("average_price"));consume("3.333","AVG-ISSUE-1");consume("6.667","AVG-ISSUE-2");amount("0",stock().get("quantity"));amount("0",stock().get("stock_value"));amount("170",db.one("SELECT SUM(amount) amount FROM stock_movements WHERE warehouse_id=? AND kind='ISSUE'",warehouse).get("amount"));}
}
