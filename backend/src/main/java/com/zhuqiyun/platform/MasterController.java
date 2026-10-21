package com.zhuqiyun.platform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class MasterController {
    private final Db db; private final Auth auth;
    private static final Map<String,String> ALLOWED=Map.of(
        "suppliers","code name contact phone category status qualification qualification_expiry rating",
        "materials","code name specification unit category safety_stock reference_price",
        "warehouses","code name location");
    public MasterController(Db db,Auth auth) { this.db=db; this.auth=auth; }
    private Object rows(String table) { auth.require("master:read"); return db.list("SELECT * FROM "+table+" WHERE tenant_id=1 ORDER BY id DESC"); }
    @GetMapping("/suppliers") public CommonResult<?> suppliers() { return CommonResult.success(rows("suppliers")); }
    @GetMapping("/materials") public CommonResult<?> materials() { return CommonResult.success(rows("materials")); }
    @GetMapping("/warehouses") public CommonResult<?> warehouses() { return CommonResult.success(rows("warehouses")); }
    @GetMapping("/suppliers/{id}") public CommonResult<?> supplier(@PathVariable long id) {
        auth.require("master:read");
        var supplier=db.one("SELECT * FROM suppliers WHERE id=? AND tenant_id=1",id);
        String scope=auth.projectFilter("p");
        var quotes=db.list("SELECT q.*,r.code request_code,p.name project_name FROM quotes q JOIN purchase_requests r ON r.id=q.request_id AND r.tenant_id=1 JOIN projects p ON p.id=r.project_id WHERE q.tenant_id=1 AND q.supplier_id=? AND "+scope+" ORDER BY q.id DESC",id);
        var orders=db.list("SELECT o.*,p.name project_name FROM purchase_orders o JOIN projects p ON p.id=o.project_id WHERE o.tenant_id=1 AND o.supplier_id=? AND "+scope+" ORDER BY o.id DESC",id);
        var metrics=db.one("SELECT COALESCE(SUM(a.amount),0) payable_amount,COALESCE(SUM(a.paid_amount),0) paid_amount,COALESCE(SUM(GREATEST(a.amount-a.paid_amount,0)),0) outstanding_amount,COALESCE(SUM(GREATEST(a.paid_amount-a.amount,0)),0) refund_due_amount FROM payables a JOIN projects p ON p.id=a.project_id WHERE a.tenant_id=1 AND a.supplier_id=? AND "+scope,id);
        BigDecimal total=orders.stream().filter(o -> !"CANCELLED".equals(o.get("status"))).map(o -> new BigDecimal(o.get("total_amount").toString())).reduce(BigDecimal.ZERO,BigDecimal::add);
        metrics.put("order_amount",total); metrics.put("order_count",orders.size()); metrics.put("quote_count",quotes.size());
        return CommonResult.success(Map.of("supplier",supplier,"quotes",quotes,"orders",orders,"metrics",metrics));
    }
    @PostMapping("/suppliers") @Transactional public CommonResult<?> createSupplier(@RequestBody Map<String,Object> body) { return CommonResult.success(save("suppliers",null,body)); }
    @PutMapping("/suppliers/{id}") @Transactional public CommonResult<?> updateSupplier(@PathVariable long id,@RequestBody Map<String,Object> body) { return CommonResult.success(save("suppliers",id,body)); }
    @DeleteMapping("/suppliers/{id}") @Transactional public CommonResult<?> deleteSupplier(@PathVariable long id) { remove("suppliers",id,List.of("quotes","purchase_orders","contracts","payables"),"supplier_id"); return CommonResult.success(true); }
    @PostMapping("/materials") @Transactional public CommonResult<?> createMaterial(@RequestBody Map<String,Object> body) { return CommonResult.success(save("materials",null,body)); }
    @PutMapping("/materials/{id}") @Transactional public CommonResult<?> updateMaterial(@PathVariable long id,@RequestBody Map<String,Object> body) { return CommonResult.success(save("materials",id,body)); }
    @DeleteMapping("/materials/{id}") @Transactional public CommonResult<?> deleteMaterial(@PathVariable long id) { remove("materials",id,List.of("request_items","order_items","receipt_items","stock_movements","inventory"),"material_id"); return CommonResult.success(true); }
    @PostMapping("/warehouses") @Transactional public CommonResult<?> createWarehouse(@RequestBody Map<String,Object> body) { return CommonResult.success(save("warehouses",null,body)); }
    @PutMapping("/warehouses/{id}") @Transactional public CommonResult<?> updateWarehouse(@PathVariable long id,@RequestBody Map<String,Object> body) { return CommonResult.success(save("warehouses",id,body)); }
    private Map<String,Object> save(String table,Long id,Map<String,Object> body) {
        auth.require("master:write");
        Map<String,Object> values=Fields.only(body,ALLOWED.get(table));
        Map<String,Object> current=id==null?Map.of():db.one("SELECT * FROM "+table+" WHERE tenant_id=1 AND id=? FOR UPDATE",id);
        Map<String,Object> merged=new LinkedHashMap<>(current); merged.putAll(values);
        for(String field:List.of("code","name")) {
            String value=Fields.text(merged.get(field),field.equals("code")?50:table.equals("suppliers")?160:120,true);
            if(values.containsKey(field)||id==null) values.put(field,value);
        }
        Map<String,Integer> lengths=Map.of("contact",80,"phone",50,"category",60,"qualification",500,"specification",160,"unit",20,"location",160);
        for(var entry:lengths.entrySet()) if(values.containsKey(entry.getKey())) values.put(entry.getKey(),Fields.text(values.get(entry.getKey()),entry.getValue(),false));
        if(table.equals("suppliers")) {
            if(id==null) { values.putIfAbsent("status","ACTIVE"); values.putIfAbsent("rating",new BigDecimal("4.5")); }
            if(values.containsKey("status")) Fields.enumeration(values.get("status"),"ACTIVE","INACTIVE");
            if(values.containsKey("rating")) values.put("rating",Fields.decimal(values.get("rating"),1,new BigDecimal("5")));
            if(values.get("qualification_expiry")!=null) values.put("qualification_expiry",Fields.date(values.get("qualification_expiry")));
        }
        if(table.equals("materials")) {
            Fields.text(merged.get("unit"),20,true); Fields.text(merged.get("category"),60,true);
            if(id==null) { values.putIfAbsent("safety_stock",BigDecimal.ZERO); values.putIfAbsent("reference_price",BigDecimal.ZERO); }
            if(values.containsKey("safety_stock")) values.put("safety_stock",Fields.decimal(values.get("safety_stock"),3,new BigDecimal("999999999999999.999")));
            if(values.containsKey("reference_price")) values.put("reference_price",Fields.decimal(values.get("reference_price"),4,new BigDecimal("99999999999999.9999")));
        }
        long savedId;
        if(id==null) { values.put("tenant_id",1L); savedId=db.insert(table,values); }
        else { savedId=id; if(!values.isEmpty()) db.update(table,id,values); }
        auth.audit(id==null?"CREATE":"UPDATE",table,savedId,"维护基础档案 "+merged.get("code"));
        return db.one("SELECT * FROM "+table+" WHERE tenant_id=1 AND id=?",savedId);
    }
    private void remove(String table,long id,List<String> references,String column) {
        auth.require("master:write"); db.one("SELECT id FROM "+table+" WHERE id=? AND tenant_id=1 FOR UPDATE",id);
        for(String reference:references) if(!db.list("SELECT id FROM "+reference+" WHERE "+column+"=? LIMIT 1",id).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"档案已使用，不能删除");
        db.jdbc().update("DELETE FROM "+table+" WHERE id=? AND tenant_id=1",id); auth.audit("DELETE",table,id,"删除未使用档案");
    }
}
