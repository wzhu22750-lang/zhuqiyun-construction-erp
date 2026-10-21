package com.zhuqiyun.project;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhuqiyun.platform.Auth;
import com.zhuqiyun.platform.Db;
import com.zhuqiyun.platform.Fields;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ProjectService {
    private final Db db; private final Auth auth; private final ProjectMapper mapper;
    private static final String EDITABLE="code name owner contractor manager_id location type contract_amount budget_amount start_date end_date progress status";
    private static final BigDecimal MAX=new BigDecimal("999999999999999999.99");
    public ProjectService(Db db,Auth auth,ProjectMapper mapper) { this.db=db; this.auth=auth; this.mapper=mapper; }
    public List<Map<String,Object>> list() {
        auth.require("project:read");
        QueryWrapper<Project> query=new QueryWrapper<Project>().eq("tenant_id",1);
        if (auth.isManager()) query.eq("manager_id",auth.id());
        query.orderByDesc("id");
        return mapper.selectMaps(query);
    }
    public Map<String,Object> detail(long id) {
        auth.require("project:read"); auth.project(id);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("project",db.one("SELECT p.*,u.name manager_name FROM projects p JOIN users u ON u.id=p.manager_id AND u.tenant_id=1 WHERE p.id=? AND p.tenant_id=1",id));
        result.put("metrics",metrics(id));
        result.put("costs",db.list("SELECT c.*,u.name operator_name FROM costs c LEFT JOIN users u ON u.id=c.operator_id AND u.tenant_id=1 WHERE c.tenant_id=1 AND c.project_id=? ORDER BY c.id DESC",id));
        result.put("requests",db.list("SELECT r.*,u.name applicant_name FROM purchase_requests r JOIN users u ON u.id=r.applicant_id AND u.tenant_id=1 WHERE r.tenant_id=1 AND r.project_id=? ORDER BY r.id DESC",id));
        result.put("orders",db.list("SELECT o.*,s.name supplier_name FROM purchase_orders o JOIN suppliers s ON s.id=o.supplier_id AND s.tenant_id=1 WHERE o.tenant_id=1 AND o.project_id=? ORDER BY o.id DESC",id));
        result.put("contracts",db.list("SELECT * FROM contracts WHERE tenant_id=1 AND project_id=? ORDER BY id DESC",id));
        result.put("progress",db.list("SELECT pp.*,u.name operator_name FROM project_progress pp JOIN users u ON u.id=pp.operator_id AND u.tenant_id=1 WHERE pp.tenant_id=1 AND pp.project_id=? ORDER BY pp.id DESC",id));
        return result;
    }
    /** Internal reusable aggregation; still enforces object scope. */
    public Map<String,Object> metrics(long id) {
        auth.project(id);
        Map<String,Object> project=db.one("SELECT budget_amount,contract_amount FROM projects WHERE tenant_id=1 AND id=?",id);
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("request_amount",sum("SELECT COALESCE(SUM(total_amount),0) amount FROM purchase_requests WHERE tenant_id=1 AND project_id=? AND status<>'REJECTED'",id));
        m.put("order_amount",sum("SELECT COALESCE(SUM(o.total_amount-o.closed_amount-COALESCE((SELECT SUM(r.amount) FROM purchase_returns r WHERE r.order_id=o.id),0)),0) amount FROM purchase_orders o WHERE o.tenant_id=1 AND o.project_id=? AND status<>'CANCELLED'",id));
        m.put("receipt_amount",sum("SELECT COALESCE(SUM(amount),0) amount FROM receipts WHERE tenant_id=1 AND project_id=?",id));
        BigDecimal material=sum("SELECT COALESCE(SUM(CASE WHEN kind='ISSUE' THEN amount ELSE -amount END),0) amount FROM stock_movements WHERE tenant_id=1 AND project_id=? AND kind IN ('ISSUE','ISSUE_RETURN')",id);
        BigDecimal other=sum("SELECT COALESCE(SUM(amount),0) amount FROM costs WHERE tenant_id=1 AND project_id=?",id);
        BigDecimal actual=material.add(other), budget=number(project.get("budget_amount"));
        m.put("material_cost",material); m.put("other_cost",other); m.put("actual_cost",actual);
        m.put("paid_amount",sum("SELECT COALESCE(SUM(paid_amount),0) amount FROM payables WHERE tenant_id=1 AND project_id=?",id));
        m.put("purchase_return_amount",sum("SELECT COALESCE(SUM(amount),0) amount FROM purchase_returns WHERE tenant_id=1 AND project_id=?",id));
        m.put("refund_amount",sum("SELECT COALESCE(SUM(r.amount),0) amount FROM supplier_refunds r JOIN payables p ON p.id=r.payable_id WHERE r.tenant_id=1 AND p.project_id=?",id));
        m.put("refund_due_amount",sum("SELECT COALESCE(SUM(GREATEST(paid_amount-amount,0)),0) amount FROM payables WHERE tenant_id=1 AND project_id=?",id));
        m.put("budget_usage",budget.signum()==0?BigDecimal.ZERO:actual.multiply(new BigDecimal("100")).divide(budget,2,RoundingMode.HALF_UP));
        m.put("estimated_profit",number(project.get("contract_amount")).subtract(actual));
        return m;
    }
    public BigDecimal sum(String sql,Object... args) { return number(db.one(sql,args).get("amount")); }
    public static BigDecimal number(Object value) { return value==null?BigDecimal.ZERO:new BigDecimal(value.toString()); }
    private Map<String,Object> values(Map<String,Object> body,Map<String,Object> old) {
        Map<String,Object> changes=Fields.only(body,EDITABLE);
        Map<String,Object> merged=new LinkedHashMap<>(old); merged.putAll(changes);
        for (String field: List.of("code","name","owner","contractor")) {
            String v=Fields.text(merged.get(field),field.equals("code")?50:160,true);
            if(changes.containsKey(field) || old.isEmpty()) changes.put(field,v);
        }
        long manager=Fields.id(merged.get("manager_id"));
        db.one("SELECT u.id FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=? AND u.tenant_id=1 AND u.enabled=TRUE AND r.code IN ('manager','boss')",manager);
        if(auth.isManager() && manager!=auth.id()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"经理不能转移项目负责人");
        for(String field:List.of("contract_amount","budget_amount")) {
            BigDecimal v=Fields.decimal(merged.get(field),2,MAX);
            if(changes.containsKey(field)||old.isEmpty()) changes.put(field,v);
        }
        String start=Fields.date(merged.get("start_date")),end=Fields.date(merged.get("end_date"));
        Fields.check(start.compareTo(end)<=0,"结束日期不能早于开始日期");
        if(old.isEmpty()) { changes.putIfAbsent("progress",BigDecimal.ZERO); changes.putIfAbsent("status","PLANNING"); }
        if(changes.containsKey("progress")) changes.put("progress",Fields.decimal(changes.get("progress"),2,new BigDecimal("100")));
        if(changes.containsKey("status")) Fields.enumeration(changes.get("status"),"ACTIVE","PLANNING","COMPLETED","SUSPENDED");
        for(String field:List.of("location","type")) if(changes.containsKey(field)) changes.put(field,Fields.text(changes.get(field),field.equals("location")?200:60,false));
        return changes;
    }
    @Transactional public Map<String,Object> create(Map<String,Object> body) {
        auth.require("project:write");
        Map<String,Object> values=values(body,Map.of()); values.put("tenant_id",1L);
        long id=db.insert("projects",values);
        recordProgress(id,values.get("progress"),"创建项目");
        auth.audit("CREATE","project",id,"创建项目 "+values.get("code"));
        return db.one("SELECT * FROM projects WHERE tenant_id=1 AND id=?",id);
    }
    @Transactional public Map<String,Object> update(long id,Map<String,Object> body) {
        auth.require("project:write");
        Map<String,Object> old=db.one("SELECT * FROM projects WHERE tenant_id=1 AND id=? FOR UPDATE",id); auth.project(id);
        Map<String,Object> values=values(body,old);
        if(!values.isEmpty()) db.update("projects",id,values);
        if(values.containsKey("progress") && number(old.get("progress")).compareTo(number(values.get("progress")))!=0) recordProgress(id,values.get("progress"),"更新项目进度");
        auth.audit("UPDATE","project",id,"更新项目");
        return db.one("SELECT * FROM projects WHERE tenant_id=1 AND id=?",id);
    }
    @Transactional public Map<String,Object> progress(long id,Map<String,Object> body) {
        auth.require("project:write"); Fields.only(body,"progress note");
        db.one("SELECT id FROM projects WHERE tenant_id=1 AND id=? FOR UPDATE",id); auth.project(id);
        BigDecimal progress=Fields.decimal(body.get("progress"),2,new BigDecimal("100"));
        String note=Fields.text(body.get("note"),500,false);
        db.update("projects",id,Map.of("progress",progress)); recordProgress(id,progress,note);
        auth.audit("PROGRESS","project",id,"更新项目进度至 "+progress);
        return db.one("SELECT * FROM projects WHERE tenant_id=1 AND id=?",id);
    }
    private void recordProgress(long id,Object progress,String note) { db.insert("project_progress",Map.of("tenant_id",1L,"project_id",id,"progress",progress,"note",note,"operator_id",auth.id())); }
    @Transactional public void delete(long id) {
        auth.require("project:write"); db.one("SELECT id FROM projects WHERE tenant_id=1 AND id=? FOR UPDATE",id); auth.project(id);
        for(String table:List.of("purchase_requests","purchase_orders","receipts","issues","stock_movements","costs","contracts","payables")) {
            if(!db.list("SELECT id FROM "+table+" WHERE project_id=? LIMIT 1",id).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"项目已有业务单据，不能删除");
        }
        db.jdbc().update("DELETE FROM project_progress WHERE project_id=? AND tenant_id=1",id);
        db.jdbc().update("DELETE FROM projects WHERE id=? AND tenant_id=1",id);
        auth.audit("DELETE","project",id,"删除未使用项目");
    }
}
