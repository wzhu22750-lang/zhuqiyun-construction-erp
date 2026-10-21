package com.zhuqiyun.platform;

import com.zhuqiyun.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh seed: project costs use consumption + expenses, never receipts; all payable and stock ledgers balance. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:platform_seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE", "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql,classpath:db/migration/V4__payment_application_approval.sql,classpath:db/migration/V5__procurement_adjustments.sql","app.seed=true"})
@Transactional
class DemoFixtureTest {
    @Autowired Db db;
    @Autowired ProjectService projects;
    @Test void installationIsActuallyOverBudgetWithMatchingMainContractAndManagerIsolation() {
        Map<String,Object> p=db.one("SELECT * FROM projects WHERE code='PRJ-2026-003'");
        long id=((Number)p.get("id")).longValue();
        long admin=((Number)db.one("SELECT id FROM users WHERE username='admin'").get("id")).longValue();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin,null,List.of()));
        try {
            Map<String,Object> metrics=projects.metrics(id);
            amount("1800000",p.get("contract_amount")); amount("1450000",p.get("budget_amount"));
            amount("1575000",metrics.get("other_cost"));
            assertTrue(decimal(metrics.get("material_cost")).signum()>0,"Actual issued materials must remain linked");
            assertTrue(decimal(metrics.get("actual_cost")).compareTo(decimal(p.get("budget_amount")))>0);
            assertTrue(decimal(metrics.get("budget_usage")).compareTo(new BigDecimal("100"))>0);
            amount(p.get("contract_amount").toString(),db.one("SELECT amount FROM contracts WHERE code='CT-2026-003'").get("amount"));
            assertNotEquals(db.one("SELECT id FROM users WHERE username='manager'").get("id"),p.get("manager_id"));
            int year=LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear();
            assertTrue(db.list("SELECT start_date FROM projects").stream().allMatch(row -> java.sql.Date.valueOf(row.get("start_date").toString()).toLocalDate().getYear()==year));
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void seedFinancialAndInventoryLedgersConserveAmounts() {
        for(var payable:db.list("SELECT * FROM payables")) {
            Object id=payable.get("order_id");
            amount(payable.get("amount").toString(),db.one("SELECT SUM(amount) value_total FROM receipts WHERE order_id=?",id).get("value_total"));
            amount(payable.get("paid_amount").toString(),db.one("SELECT SUM(amount) value_total FROM payments WHERE payable_id=?",payable.get("id")).get("value_total"));
            assertTrue(decimal(payable.get("paid_amount")).compareTo(decimal(payable.get("amount")))<=0);
        }
        for(var stock:db.list("SELECT * FROM inventory")) {
            var ledger=db.one("SELECT SUM(quantity) quantity, SUM(CASE WHEN kind='RECEIPT' THEN amount ELSE -amount END) stock_value FROM stock_movements WHERE warehouse_id=? AND material_id=?",stock.get("warehouse_id"),stock.get("material_id"));
            amount(stock.get("quantity").toString(),ledger.get("quantity"));
            amount(stock.get("stock_value").toString(),ledger.get("stock_value"));
        }
    }
    private BigDecimal decimal(Object value) { return new BigDecimal(value.toString()); }
    private void amount(String expected,Object value) { assertEquals(0,new BigDecimal(expected).compareTo(decimal(value))); }
}
