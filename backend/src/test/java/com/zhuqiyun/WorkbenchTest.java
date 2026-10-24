package com.zhuqiyun;
import com.zhuqiyun.platform.*;
import com.zhuqiyun.procurement.ProcurementService;
import com.zhuqiyun.finance.FinanceService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.zhuqiyun.domain.Rules.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:workbench;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql,classpath:db/migration/V4__payment_application_approval.sql,classpath:db/migration/V5__procurement_adjustments.sql,classpath:db/migration/V6__scoped_read_permissions.sql,classpath:db/migration/V7__contract_payment_stages.sql,classpath:db/migration/V8__inventory_stocktakes.sql,classpath:db/migration/V9__explicit_issue_sources.sql,classpath:db/migration/V10__payment_plan_authority.sql,classpath:db/migration/V11__company_inventory_authority.sql,classpath:db/migration/V12__stocktake_versions.sql,classpath:db/migration/V13__import_batches.sql","app.seed=true"})
@Transactional
class WorkbenchTest {
 @Autowired Db db;@Autowired WorkbenchService service;@Autowired ProcurementService procurement;@Autowired FinanceService finance;
 void as(String role){long id=((Number)db.one("SELECT id FROM users WHERE username=?",role).get("id")).longValue();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 List<Map<String,Object>> groups(String role){as(role);return (List<Map<String,Object>>)service.view().get("groups");}
 long count(String role,String key){return ((Number)groups(role).stream().filter(g->key.equals(g.get("key"))).findFirst().orElseThrow().get("total")).longValue();}
 @Test void eachRoleHasFocusedRealTaskGroups(){for(String role:List.of("manager","buyer","warehouse","finance","boss")){var g=groups(role);assertTrue(g.size()>=3&&g.size()<=5,role);for(var group:g){assertNotNull(group.get("total"));for(var item:(List<Map<String,Object>>)group.get("items")){assertNotNull(item.get("path"));assertNotNull(item.get("code"));assertTrue(item.containsKey("project_name"));}}}}
 @Test void procurementDecisionRemovesApprovalAndCreatesBuyerTask(){long before=count("boss","purchase_approval"),quoteBefore=count("buyer","quote_needed");as("manager");var draft=procurement.save(null,row("project_id",1,"required_date",LocalDate.now().plusDays(3),"items",List.of(row("material_id",1,"quantity","1","unit_price","10"))));long id=((Number)draft.get("id")).longValue();procurement.submit(id);assertEquals(before+1,count("boss","purchase_approval"));as("boss");procurement.approve(id,row("decision","APPROVED","comment","核实通过"));assertEquals(before,count("boss","purchase_approval"));assertEquals(quoteBefore+1,count("buyer","quote_needed"));}
 @Test void paymentReviewAndExecutionChangeTheActualQueues(){long pending=count("finance","payment_pending"),approved=count("finance","payment_execution");long id=((Number)db.one("SELECT id FROM payment_applications WHERE status='PENDING' ORDER BY id LIMIT 1").get("id")).longValue();as("boss");finance.review(id,row("decision","APPROVED"));assertEquals(pending-1,count("finance","payment_pending"));assertEquals(approved+1,count("finance","payment_execution"));as("finance");finance.execute(id,row("reference",code("WB-PAY")));assertEquals(approved,count("finance","payment_execution"));}
 @Test void managerScopeAndUnknownDeadlinesAreNotInvented(){for(var g:groups("manager"))for(var item:(List<Map<String,Object>>)g.get("items"))assertNotEquals("科技产业园机电安装工程",item.get("project_name"));for(var g:groups("buyer"))for(var item:(List<Map<String,Object>>)g.get("items"))assertFalse(item.containsKey("overdue"));}
 @Test void revokedReadPermissionSuppressesActionGroup(){db.jdbc().update("UPDATE roles SET permissions=? WHERE code='boss'","[\"dashboard:read\",\"request:approve\",\"finance:approve\"]");assertTrue(groups("boss").isEmpty());}
}
