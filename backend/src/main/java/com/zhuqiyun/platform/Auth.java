package com.zhuqiyun.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Component
public class Auth {
    private final Db db;
    private final ObjectMapper json;
    public Auth(Db db, ObjectMapper json) { this.db = db; this.json = json; }
    public long id() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return userId;
    }
    public Map<String,Object> user() {
        // Deliberately re-read role permissions. Tokens carry no permission snapshot.
        List<Map<String,Object>> rows = db.list("SELECT u.id,u.tenant_id,u.username,u.name,u.role_id,r.code AS role,r.permissions,r.menus FROM users u JOIN roles r ON r.id=u.role_id WHERE u.id=? AND u.tenant_id=1 AND u.enabled=TRUE", id());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户已停用或不存在");
        Map<String,Object> user = new LinkedHashMap<>(rows.get(0));
        user.put("permissions", array(user.get("permissions")));
        user.put("menus", array(user.get("menus")));
        return user;
    }
    private List<String> array(Object value) {
        try { return json.readValue(String.valueOf(value), new TypeReference<List<String>>() {}); }
        catch (Exception e) { throw new IllegalStateException("角色配置格式无效", e); }
    }
    public void require(String permission) {
        var permissions = (List<?>) user().get("permissions");
        if (!permissions.contains("*") && !permissions.contains(permission))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限：" + permission);
    }
    public boolean can(String permission) { var p=(List<?>)user().get("permissions"); return p.contains("*")||p.contains(permission); }
    public boolean isManager() { return "manager".equals(user().get("role")); }
    public void project(long projectId) {
        Map<String,Object> project = db.one("SELECT id,manager_id FROM projects WHERE id=? AND tenant_id=1", projectId);
        if (isManager() && ((Number)project.get("manager_id")).longValue() != id())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该项目");
    }
    /** Alias is developer supplied and validated; user id is a database numeric id. */
    public String projectFilter(String alias) {
        if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid SQL alias");
        Map<String,Object> user = user();
        return alias + ".tenant_id=1" + ("manager".equals(user.get("role")) ? " AND " + alias + ".manager_id=" + ((Number)user.get("id")).longValue() : "");
    }
    public void audit(String action, String entityType, Long id, String detail) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("tenant_id",1L); data.put("operator_id", id()); data.put("action",action);
        data.put("entity_type",entityType); data.put("entity_id",id); data.put("detail",detail);
        db.insert("audit_logs", data);
    }
}
