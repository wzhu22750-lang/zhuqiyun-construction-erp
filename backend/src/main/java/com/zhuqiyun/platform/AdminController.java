package com.zhuqiyun.platform;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class AdminController {
    public static final Set<String> PERMISSIONS=Set.of("*","project:read","project:write","master:read","master:write","request:read","request:write","request:approve","quote:write","order:read","order:write","order:receive","inventory:read","inventory:issue","cost:read","cost:write","contract:read","contract:write","finance:read","finance:write","finance:approve","finance:record","dashboard:read","system:manage");
    public static final Set<String> MENUS=Set.of("dashboard","projects","suppliers","materials","warehouses","requests","orders","inventory","costs","contracts","finance","approvals","system","users","roles","company","dictionaries","audit","admin","payables","payments","payment-applications","stock-movements","audit-logs");
    private final Db db; private final Auth auth; private final PasswordEncoder encoder; private final ObjectMapper json;
    public AdminController(Db db,Auth auth,PasswordEncoder encoder,ObjectMapper json) { this.db=db; this.auth=auth; this.encoder=encoder; this.json=json; }
    @GetMapping("/admin/users") public CommonResult<?> users() { auth.require("system:manage"); return CommonResult.success(db.list("SELECT u.id,u.tenant_id,u.username,u.name,u.role_id,u.enabled,r.code role,r.name role_name FROM users u JOIN roles r ON r.id=u.role_id WHERE u.tenant_id=1 ORDER BY u.id")); }
    @PostMapping("/admin/users") @Transactional public CommonResult<?> create(@RequestBody Map<String,Object> body) { return CommonResult.success(saveUser(null,body)); }
    @PutMapping("/admin/users/{id}") @Transactional public CommonResult<?> update(@PathVariable long id,@RequestBody Map<String,Object> body) { return CommonResult.success(saveUser(id,body)); }
    private Map<String,Object> saveUser(Long id,Map<String,Object> body) {
        auth.require("system:manage"); Map<String,Object> values=Fields.only(body,"username name role_id role password enabled");
        Map<String,Object> old=id==null?Map.of():db.one("SELECT id,username,name,role_id,enabled FROM users WHERE tenant_id=1 AND id=? FOR UPDATE",id);
        Map<String,Object> merged=new LinkedHashMap<>(old); merged.putAll(values);
        for(String field:List.of("username","name")) {
            String value=Fields.text(merged.get(field),60,true);
            if(field.equals("username")) Fields.check(value.matches("[A-Za-z0-9_.@-]{1,60}"),"用户名格式无效");
            if(values.containsKey(field)||id==null) values.put(field,value);
        }
        if(values.containsKey("role")) {
            Fields.check(!values.containsKey("role_id"),"role 和 role_id 不能同时提供");
            var role=db.one("SELECT id FROM roles WHERE code=?",Fields.text(values.remove("role"),30,true));
            values.put("role_id",role.get("id")); merged.put("role_id",role.get("id"));
        }
        long roleId=Fields.id(merged.get("role_id")); db.one("SELECT id FROM roles WHERE id=?",roleId);
        if(id==null) values.putIfAbsent("enabled",true);
        if(values.containsKey("enabled")) Fields.bool(values.get("enabled"));
        if(values.containsKey("password")||id==null) {
            Object raw=values.remove("password");
            Fields.check(raw instanceof String,"密码不能为空");
            String password=(String)raw;
            Fields.check(password.length()>=8 && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"密码应为8到72字节");
            values.put("password_hash",encoder.encode(password));
        }
        if(id!=null && id==auth.id()) Fields.check(!Boolean.FALSE.equals(values.get("enabled")) && roleId==Fields.id(old.get("role_id")),"不能停用自己或修改自己的角色");
        long saved;
        if(id==null) { values.put("tenant_id",1L); saved=db.insert("users",values); }
        else { saved=id; if(!values.isEmpty()) db.update("users",id,values); if(values.containsKey("password_hash") || Boolean.FALSE.equals(values.get("enabled"))) db.jdbc().update("DELETE FROM sessions WHERE user_id=?",id); }
        auth.audit(id==null?"CREATE":"UPDATE","user",saved,"维护用户");
        return db.one("SELECT id,tenant_id,username,name,role_id,enabled FROM users WHERE tenant_id=1 AND id=?",saved);
    }
    @GetMapping("/admin/roles") public CommonResult<?> roles() {
        auth.require("system:manage"); var rows=db.list("SELECT * FROM roles ORDER BY id");
        for(var row:rows) { row.put("permissions",read(row.get("permissions"))); row.put("menus",read(row.get("menus"))); }
        return CommonResult.success(rows);
    }
    @PutMapping("/admin/roles/{id}") @Transactional public CommonResult<?> role(@PathVariable long id,@RequestBody Map<String,Object> body) {
        auth.require("system:manage"); var old=db.one("SELECT * FROM roles WHERE id=? FOR UPDATE",id);
        Map<String,Object> values=Fields.only(body,"permissions menus");
        if(values.containsKey("permissions")) {
            List<String> permissions=Fields.strings(values.get("permissions"),PERMISSIONS);
            if("admin".equals(old.get("code"))) Fields.check(permissions.contains("*")||permissions.contains("system:manage"),"管理员角色必须保留系统管理权限");
            values.put("permissions",write(permissions));
        }
        if(values.containsKey("menus")) values.put("menus",write(Fields.strings(values.get("menus"),MENUS)));
        if(!values.isEmpty()) db.update("roles",id,values);
        auth.audit("UPDATE","role",id,"更新角色权限和菜单");
        var result=db.one("SELECT * FROM roles WHERE id=?",id); result.put("permissions",read(result.get("permissions"))); result.put("menus",read(result.get("menus")));
        return CommonResult.success(result);
    }
    @PutMapping("/admin/company") @Transactional public CommonResult<?> company(@RequestBody Map<String,Object> body) {
        auth.require("system:manage"); Map<String,Object> values=Fields.only(body,"name system_name theme_color");
        if(values.containsKey("name")) values.put("name",Fields.text(values.get("name"),160,true));
        if(values.containsKey("system_name")) values.put("system_name",Fields.text(values.get("system_name"),80,true));
        if(values.containsKey("theme_color")) Fields.check(Fields.text(values.get("theme_color"),20,true).matches("#[0-9a-fA-F]{6}"),"主题色格式无效");
        if(!values.isEmpty()) db.update("company",1,values);
        auth.audit("UPDATE","company",1L,"维护公司设置"); return CommonResult.success(db.one("SELECT * FROM company WHERE id=1"));
    }
    @GetMapping("/admin/dictionaries") public CommonResult<?> dictionaries() { auth.require("system:manage"); return CommonResult.success(db.list("SELECT * FROM dictionaries WHERE tenant_id=1 ORDER BY category,sort_order,id")); }
    @PutMapping("/admin/dictionaries/{id}") @Transactional public CommonResult<?> dictionary(@PathVariable long id,@RequestBody Map<String,Object> body) {
        auth.require("system:manage"); Map<String,Object> values=Fields.only(body,"label value sort_order");
        if(values.containsKey("label")) values.put("label",Fields.text(values.get("label"),80,true));
        if(values.containsKey("value")) values.put("value",Fields.text(values.get("value"),50,true));
        if(values.containsKey("sort_order")) { long order; try { order=new java.math.BigDecimal(String.valueOf(values.get("sort_order"))).longValueExact(); } catch(Exception e) { throw new IllegalArgumentException("sort_order"); } Fields.check(order>=0 && order<=100000,"排序超出范围"); values.put("sort_order",order); }
        if(!values.isEmpty()) db.update("dictionaries",id,values);
        auth.audit("UPDATE","dictionary",id,"维护字典"); return CommonResult.success(db.one("SELECT * FROM dictionaries WHERE id=? AND tenant_id=1",id));
    }
    @GetMapping("/audit-logs") public CommonResult<?> audit() { auth.require("system:manage"); return CommonResult.success(db.list("SELECT a.*,u.name operator_name FROM audit_logs a LEFT JOIN users u ON u.id=a.operator_id AND u.tenant_id=1 WHERE a.tenant_id=1 ORDER BY a.id DESC LIMIT 500")); }
    private Object read(Object value) { try { return json.readValue(String.valueOf(value),List.class); } catch(Exception e) { throw new IllegalStateException("角色配置无效"); } }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalArgumentException("JSON格式无效"); } }
}
