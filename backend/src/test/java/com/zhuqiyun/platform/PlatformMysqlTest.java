package com.zhuqiyun.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhuqiyun.project.ProjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt in only with an EMPTY task-owned zqy_platform_validation_* MySQL schema.
 * Exercises real Flyway V1-V3, fresh seeding, original-fixture migration, custom-data guards,
 * idempotence, UTC sessions, direct JDBC/Db timestamps, and unchanged financial ledgers.
 * ZQY_PLATFORM_TEST_MYSQL_URL / USER / PASSWORD are supplied only to the test process.
 */
@EnabledIfEnvironmentVariable(named="ZQY_PLATFORM_TEST_MYSQL_URL",matches="jdbc:mysql://[^/]+/zqy_platform_validation_[a-z0-9_]+[?].*")
@SpringBootTest(properties={"spring.datasource.url=${ZQY_PLATFORM_TEST_MYSQL_URL}","spring.datasource.driver-class-name=org.mariadb.jdbc.Driver","spring.datasource.username=${ZQY_PLATFORM_TEST_MYSQL_USER}","spring.datasource.password=${ZQY_PLATFORM_TEST_MYSQL_PASSWORD}","spring.flyway.enabled=true","spring.sql.init.mode=never","app.seed=true"})
@DirtiesContext
@AutoConfigureMockMvc
class PlatformMysqlTest {
    @Autowired Db db;
    @Autowired MockMvc http;
    @Autowired HikariDataSource dataSource;
    @Autowired ObjectMapper json;
    @Autowired ProjectMapper projectMapper;
    @Test void realMysqlTimezoneAndGuardedMigration() throws Exception {
        String driver=db.jdbc().execute((ConnectionCallback<String>)connection -> connection.getMetaData().getDriverVersion());
        assertEquals("3.5.8",driver);
        System.out.println("Platform JDBC driver: MariaDB "+driver);
        assertTrue(dataSource.getJdbcUrl().contains("connectionTimeZone=UTC"));
        assertFalse(dataSource.getJdbcUrl().contains("serverTimezone="));
        assertEquals("+00:00",db.one("SELECT @@session.time_zone zone").get("zone"));
        Object project=db.one("SELECT id FROM projects WHERE code='PRJ-2026-003'").get("id");
        amount("1800000",db.one("SELECT contract_amount amount FROM projects WHERE id=?",project).get("amount"));
        amount("1575000",db.one("SELECT SUM(amount) amount FROM costs WHERE project_id=?",project).get("amount"));
        Map<String,Object> ledgers=ledgers();
        Map<String,Object> originalDates=db.one("SELECT start_date,end_date,created_at FROM projects WHERE id=?",project);
        var mapped=projectMapper.selectById(((Number)project).longValue());
        assertEquals(((Timestamp)originalDates.get("created_at")).toInstant(),mapped.getCreatedAt().toInstant(ZoneOffset.UTC));
        assertEquals(((java.sql.Date)originalDates.get("start_date")).toLocalDate(),mapped.getStartDate());
        assertEquals(json.writeValueAsString(originalDates.get("created_at")),json.writeValueAsString(mapped.getCreatedAt()));
        restoreOldFixture(project);
        // Every independently customized part must opt the whole fixture out, not partially repair it.
        List<String> customizations=List.of(
            "UPDATE company SET name='客户自定义公司' WHERE id=1",
            "UPDATE projects SET budget_amount=28000000 WHERE code='PRJ-2026-003'",
            "UPDATE projects SET manager_id=(SELECT id FROM users WHERE username='manager') WHERE code='PRJ-2026-003'",
            "UPDATE costs SET amount=495001 WHERE project_id="+project+" AND category='LABOR'",
            "UPDATE costs SET description='客户修改人工费' WHERE project_id="+project+" AND category='LABOR'",
            "UPDATE contracts SET amount=36000001 WHERE code='CT-2026-003'",
            "INSERT INTO costs(tenant_id,project_id,category,amount,description,incurred_date,operator_id) SELECT 1,"+project+",'OTHER',1,'客户新增费用',CURRENT_DATE,id FROM users WHERE username='finance'"
        );
        for(String customization:customizations) {
            db.jdbc().update(customization);
            Map<String,Object> before=snapshot(project);
            migrate();
            assertEquals(before,snapshot(project),"V3 must preserve customized fixture: "+customization);
            restoreOldFixture(project);
        }
        migrate();
        amount("1800000",db.one("SELECT contract_amount amount FROM projects WHERE id=?",project).get("amount"));
        amount("1450000",db.one("SELECT budget_amount amount FROM projects WHERE id=?",project).get("amount"));
        amount("1575000",db.one("SELECT SUM(amount) amount FROM costs WHERE project_id=?",project).get("amount"));
        amount("1800000",db.one("SELECT amount FROM contracts WHERE code='CT-2026-003'").get("amount"));
        BigDecimal actual=dec(db.one("SELECT SUM(amount) amount FROM costs WHERE project_id=?",project).get("amount"))
            .add(dec(db.one("SELECT SUM(amount) amount FROM stock_movements WHERE kind='ISSUE' AND project_id=?",project).get("amount")));
        assertTrue(actual.compareTo(new BigDecimal("1450000"))>0);
        assertTrue(actual.compareTo(new BigDecimal("1800000"))<0,"Over budget but still within the contract");
        System.out.println("Platform fixture: contract=1800000.00, budget=1450000.00, expenses=1575000.00, actual="+actual);
        assertEquals(originalDates,db.one("SELECT start_date,end_date,created_at FROM projects WHERE id=?",project));
        assertEquals(ledgers,ledgers(),"Stock, receipts, payables and payments must not change");
        Map<String,Object> once=snapshot(project); migrate(); assertEquals(once,snapshot(project));
        verifyTimestampsAcrossJvmZones();
        verifyApiInstants(project, originalDates);
    }
    private void verifyApiInstants(Object project, Map<String,Object> originalDates) throws Exception {
        String token="platform-timezone-regression";
        db.jdbc().update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",SecurityConfig.tokenHash(token),1L,Timestamp.from(Instant.now().plusSeconds(3600)));
        String response=http.perform(get("/api/projects").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        boolean found=false;
        for(var row:json.readTree(response).path("data")) if(row.path("id").asLong()==((Number)project).longValue()) {
            assertEquals(((Timestamp)originalDates.get("created_at")).toInstant(),Instant.parse(row.path("created_at").asText()));
            found=true;
        }
        assertTrue(found,"Project API must return the seeded project and its UTC instant");
        String audit=http.perform(get("/api/audit-logs").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(json.readTree(audit).path("data").isEmpty());
        for(var row:json.readTree(audit).path("data")) assertTrue(row.path("created_at").asText().endsWith("Z"));
        db.jdbc().update("DELETE FROM sessions WHERE token_hash=?",SecurityConfig.tokenHash(token));
        Map<String,Object> clock=db.one("SELECT @@session.time_zone zone,CURRENT_TIMESTAMP current_time_utc,UTC_TIMESTAMP clock_utc,UNIX_TIMESTAMP(CURRENT_TIMESTAMP) epoch");
        assertEquals(((Timestamp)clock.get("clock_utc")).toInstant(),((Timestamp)clock.get("current_time_utc")).toInstant());
        assertEquals(((Timestamp)clock.get("current_time_utc")).toInstant().getEpochSecond(),((Number)clock.get("epoch")).longValue());
        System.out.println("Platform MySQL clock: zone="+clock.get("zone")+", current_timestamp/API instant="+((Timestamp)clock.get("current_time_utc")).toInstant()+", epoch="+clock.get("epoch"));
    }
    private void migrate() {
        db.jdbc().execute((ConnectionCallback<Void>)connection->{
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V3__demo_installation_budget_overrun.sql")); return null;
        });
    }
    private Map<String,Object> ledgers() {
        return Map.of("stock",db.list("SELECT * FROM inventory ORDER BY id"),"movements",db.list("SELECT * FROM stock_movements ORDER BY id"),
            "payables",db.list("SELECT * FROM payables ORDER BY id"),"payments",db.list("SELECT * FROM payments ORDER BY id"),
            "receipts",db.list("SELECT * FROM receipts ORDER BY id"));
    }
    private Map<String,Object> snapshot(Object project) {
        return Map.of("project",db.one("SELECT * FROM projects WHERE id=?",project),"costs",db.list("SELECT * FROM costs WHERE project_id=? ORDER BY id",project),
            "company",db.one("SELECT * FROM company WHERE id=1"),"contracts",db.list("SELECT * FROM contracts WHERE project_id=? ORDER BY id",project));
    }
    private void restoreOldFixture(Object project) {
        db.jdbc().update("UPDATE company SET name='华建工程建设有限公司' WHERE id=1");
        db.jdbc().update("UPDATE projects SET contract_amount=36000000,budget_amount=29000000,manager_id=(SELECT id FROM users WHERE username='boss') WHERE id=?",project);
        db.jdbc().update("UPDATE contracts SET amount=36000000 WHERE code='CT-2026-003'");
        db.jdbc().update("DELETE FROM costs WHERE project_id=? AND description='客户新增费用'",project);
        String[] categories={"LABOR","MACHINE","SUBCONTRACT","OTHER"};
        String[] amounts={"495000","144000","660000","37500"};
        String[] descriptions={"现场工人工资","塔吊及挖机租赁","土建专业分包进度款","现场临设及水电费"};
        for(int i=0;i<4;i++) db.jdbc().update("UPDATE costs SET amount=?,description=? WHERE project_id=? AND category=?",new BigDecimal(amounts[i]),descriptions[i],project,categories[i]);
    }
    private void verifyTimestampsAcrossJvmZones() throws Exception {
        TimeZone original=TimeZone.getDefault();
        try {
            for(String zone:List.of("UTC","Asia/Shanghai","America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                try(HikariDataSource pool=new HikariDataSource()) {
                    pool.setDriverClassName("org.mariadb.jdbc.Driver"); pool.setJdbcUrl(dataSource.getJdbcUrl());
                    pool.setUsername(dataSource.getUsername()); pool.setPassword(dataSource.getPassword()); pool.setMaximumPoolSize(1);
                    Db scoped=new Db(new JdbcTemplate(pool));
                    Instant instant=Instant.parse("2026-10-20T16:56:00Z");
                    long id=scoped.insert("audit_logs",Map.of("operator_id",1L,"action","TIME_TEST","entity_type","test","created_at",Timestamp.from(instant)));
                    var row=scoped.one("SELECT created_at,UNIX_TIMESTAMP(created_at) epoch FROM audit_logs WHERE id=?",id);
                    assertEquals(instant,((Timestamp)row.get("created_at")).toInstant());
                    assertEquals(instant.getEpochSecond(),((Number)row.get("epoch")).longValue());
                    assertEquals("\"2026-10-20T16:56:00Z\"",json.writeValueAsString(row.get("created_at")));
                    assertEquals(instant,scoped.jdbc().queryForObject("SELECT created_at FROM audit_logs WHERE id=?",Timestamp.class,id).toInstant());
                    scoped.jdbc().update("UPDATE audit_logs SET created_at=? WHERE id=?",Timestamp.from(instant.plusSeconds(60)),id);
                    assertEquals(instant.plusSeconds(60),((Timestamp)scoped.one("SELECT created_at FROM audit_logs WHERE id=?",id).get("created_at")).toInstant());
                    Instant expiry=Instant.now().plus(24,ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
                    String tokenHash=switch(zone) { case "UTC" -> "a"; case "Asia/Shanghai" -> "b"; default -> "c"; };
                    scoped.jdbc().update("INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)",tokenHash.repeat(64),1L,Timestamp.from(expiry));
                    var session=scoped.one("SELECT expires_at,expires_at>CURRENT_TIMESTAMP valid FROM sessions WHERE token_hash=?",tokenHash.repeat(64));
                    assertEquals(expiry,((Timestamp)session.get("expires_at")).toInstant());
                    assertEquals(1,((Number)session.get("valid")).intValue());
                    long defaultId=scoped.insert("audit_logs",Map.of("operator_id",1L,"action","TIME_DEFAULT","entity_type","test"));
                    var now=(Timestamp)scoped.one("SELECT created_at FROM audit_logs WHERE id=?",defaultId).get("created_at");
                    assertTrue(Math.abs(Instant.now().getEpochSecond()-now.toInstant().getEpochSecond())<10);
                }
            }
        } finally { TimeZone.setDefault(original); }
    }
    private BigDecimal dec(Object value) { return new BigDecimal(value.toString()); }
    private void amount(String expected,Object value) { assertEquals(0,new BigDecimal(expected).compareTo(dec(value))); }
}
