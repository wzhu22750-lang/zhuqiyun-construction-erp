package com.zhuqiyun;
import com.zhuqiyun.platform.Db;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:readpolicy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql,classpath:db/migration/V4__payment_application_approval.sql,classpath:db/migration/V5__procurement_adjustments.sql,classpath:db/migration/V6__scoped_read_permissions.sql,classpath:db/migration/V7__contract_payment_stages.sql,classpath:db/migration/V8__inventory_stocktakes.sql,classpath:db/migration/V9__explicit_issue_sources.sql,classpath:db/migration/V10__payment_plan_authority.sql,classpath:db/migration/V11__company_inventory_authority.sql,classpath:db/migration/V12__stocktake_versions.sql,classpath:db/migration/V13__import_batches.sql","app.seed=true"})
@AutoConfigureMockMvc @Transactional
class ReadPolicyTest {
 @Autowired MockMvc http;@Autowired ObjectMapper json;@Autowired Db db;
 String token(String role)throws Exception{return json.readTree(http.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\""+role+"\",\"password\":\"Zhuqi2026!\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("token").asText();}
 String getData(String role,String path)throws Exception{return http.perform(get(path).header("Authorization","Bearer "+token(role))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
 @Test void projectReadDoesNotGrantCostsContractsOrCash()throws Exception{
  String body=getData("warehouse","/api/projects/1");var data=json.readTree(body).path("data");assertFalse(data.path("access").path("costs").asBoolean());assertFalse(data.path("project").has("budget_amount"));assertFalse(data.path("project").has("contract_amount"));assertTrue(data.path("costs").isEmpty());assertTrue(data.path("contracts").isEmpty());assertFalse(data.path("metrics").has("paid_amount"));assertFalse(data.path("metrics").has("material_cost"));assertFalse(data.path("metrics").has("registered_cost_balance"));
 }
 @Test void supplierMasterReadDoesNotGrantQuotesOrPayments()throws Exception{
  var data=json.readTree(getData("warehouse","/api/suppliers/1")).path("data");assertTrue(data.path("quotes").isEmpty());assertFalse(data.path("metrics").has("paid_amount"));assertFalse(data.path("metrics").has("payable_amount"));assertFalse(data.path("metrics").has("quote_count"));assertFalse(data.path("access").path("finance").asBoolean());
 }
 @Test void orderReadCannotExposeFinanceThroughDetailOrAudit()throws Exception{
  String body=getData("warehouse","/api/orders/1");var data=json.readTree(body).path("data");assertFalse(data.has("payable"));assertTrue(data.path("payments").isEmpty());assertTrue(data.path("refunds").isEmpty());for(var event:data.path("events"))assertFalse(Set.of("payment","supplier_refund","payment_application").contains(event.path("entity_type").asText()));
 }
 @Test void requestReadIsNotQuoteReadAndDirectFinanceIsDenied()throws Exception{
  String bearer="Bearer "+token("manager");http.perform(get("/api/requests/1/quotes").header("Authorization",bearer)).andExpect(status().isForbidden());
  http.perform(get("/api/payables").header("Authorization",bearer)).andExpect(status().isForbidden());var data=json.readTree(getData("manager","/api/requests/1")).path("data");assertTrue(data.path("quotes").isEmpty());assertFalse(data.path("access").path("quotes").asBoolean());
 }
 @Test void quantityReadersCannotGetActualInventoryValuation()throws Exception{
  var data=json.readTree(getData("warehouse","/api/inventory")).path("data");assertFalse(data.isEmpty());for(var row:data){assertTrue(row.has("quantity"));assertFalse(row.has("stock_value"));assertFalse(row.has("average_price"));}
 }
 @Test void projectManagerCannotReachAnotherProjectByDetailOrOrder()throws Exception{
  String bearer="Bearer "+token("manager");http.perform(get("/api/projects/3").header("Authorization",bearer)).andExpect(status().isForbidden());long order=((Number)db.one("SELECT id FROM purchase_orders WHERE project_id=3 ORDER BY id LIMIT 1").get("id")).longValue();http.perform(get("/api/orders/"+order).header("Authorization",bearer)).andExpect(status().isForbidden());
 }
 @Test void costOnlyRoleDoesNotGetContractOrPaymentMetrics()throws Exception{
  db.jdbc().update("UPDATE roles SET permissions=? WHERE code='finance'","[\"cost:read\",\"project:read\"]");var data=json.readTree(getData("finance","/api/costs")).path("data");for(var project:data){assertFalse(project.has("contract_amount"));assertTrue(project.path("metrics").has("actual_cost"));assertFalse(project.path("metrics").has("paid_amount"));assertFalse(project.path("metrics").has("registered_cost_balance"));}
 }
 @Test void financeReadDoesNotGrantOriginalOrderValue()throws Exception{
  db.jdbc().update("UPDATE roles SET permissions=? WHERE code='finance'","[\"finance:read\"]");
  for(String path:List.of("/api/payables","/api/records/payables")){var data=json.readTree(getData("finance",path)).path("data");if(data.has("items"))data=data.path("items");for(var r:data){assertTrue(r.has("amount"));assertFalse(r.has("order_amount"));}}
 }
 @Test void balanceNameDoesNotClaimFinalProfit()throws Exception{
  var m=json.readTree(getData("boss","/api/projects/1")).path("data").path("metrics");assertTrue(m.has("registered_cost_balance"));assertFalse(m.has("estimated_profit"));
 }
}
