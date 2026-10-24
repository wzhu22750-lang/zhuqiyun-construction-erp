package com.zhuqiyun.platform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OverviewController {
    private final Db db; private final Auth auth;private final WorkbenchService workbench;
    public OverviewController(Db db,Auth auth,WorkbenchService workbench) { this.db=db; this.auth=auth;this.workbench=workbench; }
    private boolean has(Map<String,Object> user,String permission) { var permissions=(List<?>)user.get("permissions"); return permissions.contains("*")||permissions.contains(permission); }
    @GetMapping("/bootstrap") public CommonResult<?> bootstrap() {
        Map<String,Object> user=auth.user();
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("user",user); data.put("company",db.one("SELECT id,name,system_name,theme_color FROM company WHERE id=1"));
        data.put("projects",has(user,"project:read")?db.list("SELECT p.*,u.name manager_name FROM projects p JOIN users u ON u.id=p.manager_id AND u.tenant_id=1 WHERE "+auth.projectFilter("p")+" ORDER BY p.id DESC LIMIT 100"):List.of());
        if(data.get("projects") instanceof List<?> projects){var access=DataPolicy.flags(auth);data.put("projects",projects.stream().map(p->DataPolicy.project((Map<String,Object>)p,access)).toList());}
        boolean master=has(user,"master:read");
        data.put("materials",master?db.list("SELECT * FROM materials WHERE tenant_id=1 ORDER BY id LIMIT 100"):List.of());
        data.put("suppliers",master?db.list("SELECT * FROM suppliers WHERE tenant_id=1 ORDER BY id LIMIT 100"):List.of());
        data.put("warehouses",master?db.list("SELECT * FROM warehouses WHERE tenant_id=1 ORDER BY id LIMIT 100"):List.of());
        data.put("users",has(user,"project:write")||has(user,"system:manage")?db.list("SELECT u.id,u.username,u.name,u.role_id,r.code role FROM users u JOIN roles r ON r.id=u.role_id WHERE u.tenant_id=1 AND u.enabled=TRUE ORDER BY u.id"):List.of());
        Map<String,List<Map<String,Object>>> dictionaries=new LinkedHashMap<>();
        for(var row:db.list("SELECT id,category,label,value,sort_order FROM dictionaries WHERE tenant_id=1 ORDER BY category,sort_order,id")) dictionaries.computeIfAbsent(row.get("category").toString(),k->new ArrayList<>()).add(row);
        data.put("dictionaries",dictionaries);
        return CommonResult.success(data);
    }
    /**
     * GET /api/dashboard?year=2026. Defaults to the server's current calendar year.
     * metrics.contract_amount sums project contract_amount only where project.start_date
     * falls in [January 1 of year, January 1 of year + 1), within the caller's project scope.
     * metrics.year identifies this annual contract amount; other metrics retain their
     * cumulative or current-state meanings from API-CONTRACT.md.
     */
    @GetMapping("/dashboard") public CommonResult<?> dashboard(@RequestParam(required=false) Integer year) {
        auth.require("dashboard:read"); String scope=auth.projectFilter("p");
        int selectedYear=year==null?LocalDate.now().getYear():year;
        Fields.check(selectedYear>=1000 && selectedYear<=9998,"年度应为1000到9998之间的整数");
        LocalDate start=LocalDate.of(selectedYear,1,1),end=start.plusYears(1);
        Map<String,Object> metrics=new LinkedHashMap<>(db.one("SELECT COALESCE(SUM(CASE WHEN p.status='ACTIVE' THEN 1 ELSE 0 END),0) active_projects,COALESCE(SUM(CASE WHEN p.start_date>=? AND p.start_date<? THEN p.contract_amount ELSE 0 END),0) contract_amount,COALESCE(SUM(ROUND(p.contract_amount*p.progress/100,2)),0) completed_value,COALESCE(SUM(p.budget_amount),0) budget_amount FROM projects p WHERE "+scope,start,end));
        metrics.put("year",selectedYear);
        BigDecimal material=amount("SELECT COALESCE(SUM(CASE WHEN m.kind='ISSUE' THEN m.amount ELSE -m.amount END),0) amount FROM stock_movements m JOIN projects p ON p.id=m.project_id WHERE m.tenant_id=1 AND m.kind IN ('ISSUE','ISSUE_RETURN') AND "+scope);
        BigDecimal other=amount("SELECT COALESCE(SUM(c.amount),0) amount FROM costs c JOIN projects p ON p.id=c.project_id WHERE c.tenant_id=1 AND "+scope);
        metrics.put("actual_cost",material.add(other));
        metrics.put("purchase_amount",amount("SELECT COALESCE(SUM(o.total_amount-o.closed_amount-COALESCE((SELECT SUM(r.amount) FROM purchase_returns r WHERE r.order_id=o.id),0)),0) amount FROM purchase_orders o JOIN projects p ON p.id=o.project_id WHERE o.tenant_id=1 AND o.status<>'CANCELLED' AND "+scope));
        metrics.put("pending_requests",db.one("SELECT COUNT(*) n FROM purchase_requests r JOIN projects p ON p.id=r.project_id WHERE r.tenant_id=1 AND r.status IN ('SUBMITTED','PENDING') AND "+scope).get("n"));
        metrics.put("outstanding_amount",amount("SELECT COALESCE(SUM(GREATEST(a.amount-a.paid_amount,0)),0) amount FROM payables a JOIN projects p ON p.id=a.project_id WHERE a.tenant_id=1 AND "+scope));
        Map<String,Object> data=new LinkedHashMap<>(); data.put("metrics",metrics);
        metrics.put("refund_due_amount",amount("SELECT COALESCE(SUM(GREATEST(a.paid_amount-a.amount,0)),0) amount FROM payables a JOIN projects p ON p.id=a.project_id WHERE a.tenant_id=1 AND "+scope));
        data.put("budget_vs_cost",db.list("SELECT p.name,p.budget_amount budget,(COALESCE((SELECT SUM(CASE WHEN m.kind='ISSUE' THEN m.amount ELSE -m.amount END) FROM stock_movements m WHERE m.tenant_id=1 AND m.project_id=p.id AND m.kind IN ('ISSUE','ISSUE_RETURN')),0)+COALESCE((SELECT SUM(c.amount) FROM costs c WHERE c.tenant_id=1 AND c.project_id=p.id),0)) cost FROM projects p WHERE "+scope+" ORDER BY p.id"));
        data.put("monthly_purchases",db.list("SELECT DATE_FORMAT(o.created_at,'%Y-%m') month,SUM(o.total_amount-o.closed_amount-COALESCE((SELECT SUM(r.amount) FROM purchase_returns r WHERE r.order_id=o.id),0)) amount FROM purchase_orders o JOIN projects p ON p.id=o.project_id WHERE o.tenant_id=1 AND o.status<>'CANCELLED' AND "+scope+" GROUP BY DATE_FORMAT(o.created_at,'%Y-%m') ORDER BY month"));
        var composition=new ArrayList<Map<String,Object>>(); composition.add(Map.of("name","材料","value",material));
        for(var row:db.list("SELECT c.category name,SUM(c.amount) value FROM costs c JOIN projects p ON p.id=c.project_id WHERE c.tenant_id=1 AND "+scope+" GROUP BY c.category")) {
            row.put("name",Map.of("LABOR","人工","MACHINE","机械","SUBCONTRACT","分包","OTHER","其他","RETURN_VARIANCE","退货计价差额").getOrDefault(row.get("name").toString(),row.get("name").toString())); composition.add(row);
        }
        data.put("cost_composition",composition);
        data.put("project_progress",db.list("SELECT p.name,p.progress FROM projects p WHERE "+scope+" ORDER BY p.id"));
        data.put("order_status",db.list("SELECT o.status name,COUNT(*) value FROM purchase_orders o JOIN projects p ON p.id=o.project_id WHERE o.tenant_id=1 AND "+scope+" GROUP BY o.status"));
        data.put("todos",db.list("SELECT r.id,r.code,r.status,r.total_amount,p.name project_name,r.required_date FROM purchase_requests r JOIN projects p ON p.id=r.project_id WHERE r.tenant_id=1 AND r.status IN ('SUBMITTED','PENDING') AND "+scope+" ORDER BY r.required_date,r.id LIMIT 20"));
        data.put("recent_activity",db.list("SELECT a.*,u.name operator_name FROM audit_logs a LEFT JOIN users u ON u.id=a.operator_id AND u.tenant_id=1 WHERE a.tenant_id=1"+(auth.isManager()?" AND a.operator_id="+auth.id():"")+" ORDER BY a.id DESC LIMIT 12"));
        var access=DataPolicy.flags(auth);data.put("access",access);data.put("workbench",workbench.view());
        if(!DataPolicy.yes(access,"projects")){metrics.remove("active_projects");data.put("project_progress",List.of());}
        if(!DataPolicy.yes(access,"contracts")){metrics.remove("contract_amount");metrics.remove("completed_value");}
        if(!DataPolicy.yes(access,"costs")){metrics.remove("actual_cost");metrics.remove("budget_amount");data.put("budget_vs_cost",List.of());data.put("cost_composition",List.of());}
        if(!DataPolicy.yes(access,"orders")){metrics.remove("purchase_amount");data.put("monthly_purchases",List.of());data.put("order_status",List.of());}
        if(!DataPolicy.yes(access,"requests")){metrics.remove("pending_requests");data.put("todos",List.of());}
        if(!DataPolicy.yes(access,"finance")){metrics.remove("outstanding_amount");metrics.remove("refund_due_amount");}
        if(!auth.can("system:manage"))data.put("recent_activity",List.of());
        return CommonResult.success(data);
    }
    private BigDecimal amount(String sql) { return new BigDecimal(db.one(sql).get("amount").toString()); }
}
