package com.zhuqiyun.platform;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.Timestamp;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Internal SQL helper. Callers must apply entity permissions and project scope. */
@Component
public class Db {
    private final JdbcTemplate jdbc;
    private static final Map<String, Set<String>> FIELDS = new HashMap<>();
    static {
        allow("company", "name system_name theme_color");
        allow("roles", "code name permissions menus");
        allow("users", "tenant_id username password_hash name role_id enabled");
        allow("sessions", "token_hash user_id expires_at");
        allow("dictionaries", "tenant_id category label value sort_order");
        allow("projects", "tenant_id code name owner contractor manager_id location type contract_amount budget_amount start_date end_date progress status created_at");
        allow("project_progress", "tenant_id project_id progress note operator_id created_at");
        allow("suppliers", "tenant_id code name contact phone category status qualification qualification_expiry rating");
        allow("materials", "tenant_id code name specification unit category safety_stock reference_price");
        allow("warehouses", "tenant_id code name location");
        allow("purchase_requests", "tenant_id code project_id applicant_id required_date note status total_amount source_order_id created_at updated_at");
        allow("request_items", "tenant_id request_id material_id quantity unit_price amount");
        allow("approvals", "tenant_id request_id operator_id decision comment created_at");
        allow("quotes", "tenant_id request_id supplier_id delivery_days valid_until note total_amount selected selection_reason created_at");
        allow("quote_items", "tenant_id quote_id request_item_id unit_price amount");
        allow("purchase_orders", "tenant_id code project_id request_id quote_id supplier_id total_amount status buyer_id closed_amount close_reason closed_by closed_at replacement_request_id created_at");
        allow("order_items", "tenant_id order_id material_id request_item_id quantity received_quantity unit_price amount closed_quantity closed_amount");
        allow("receipts", "tenant_id code order_id project_id warehouse_id reference note amount operator_id created_at");
        allow("receipt_items", "tenant_id receipt_id order_item_id material_id quantity unit_price amount");
        allow("inventory", "tenant_id warehouse_id material_id quantity average_price stock_value");
        allow("issues", "tenant_id code project_id warehouse_id reference note amount operator_id created_at");
        allow("stock_movements", "tenant_id warehouse_id material_id project_id kind quantity unit_cost amount reference receipt_id issue_id issue_return_id purchase_return_id operator_id created_at");
        allow("costs", "tenant_id project_id category amount description incurred_date operator_id created_at");
        allow("contracts", "tenant_id code name type project_id supplier_id order_id amount signed_date start_date end_date status payment_plan attachment_name attachment_key created_at");
        allow("payables", "tenant_id order_id supplier_id project_id amount paid_amount");
        allow("payments", "tenant_id payable_id amount reference note operator_id created_at");
        allow("payment_applications", "tenant_id payable_id amount status note applicant_id payment_id reviewer_id reviewed_at review_comment created_at");
        allow("payment_application_records", "tenant_id application_id operator_id decision comment created_at");
        allow("issue_returns", "tenant_id code issue_id project_id warehouse_id reference reason amount operator_id created_at");
        allow("issue_return_items", "tenant_id return_id source_movement_id quantity amount");
        allow("purchase_returns", "tenant_id code receipt_id order_id project_id warehouse_id reference reason amount stock_amount variance_amount cost_id operator_id created_at");
        allow("purchase_return_items", "tenant_id return_id receipt_item_id quantity amount stock_amount");
        allow("supplier_refunds", "tenant_id payable_id amount reference note operator_id created_at");
        allow("audit_logs", "tenant_id operator_id action entity_type entity_id detail created_at");
    }
    private static void allow(String table, String fields) { FIELDS.put(table, Set.of(fields.split(" "))); }
    public Db(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public JdbcTemplate jdbc() { return jdbc; }
    public List<Map<String,Object>> list(String sql, Object... args) {
        return jdbc.query(sql, new ColumnMapRowMapper() {
            @Override protected Object getColumnValue(ResultSet rs, int index) throws SQLException {
                // MySQL TIMESTAMP is an instant; read its UTC session representation explicitly.
                if (rs.getMetaData().getColumnType(index) == Types.TIMESTAMP)
                    return rs.getTimestamp(index, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                return super.getColumnValue(rs, index);
            }
        }, args);
    }
    public Map<String,Object> one(String sql, Object... args) {
        List<Map<String,Object>> rows = list(sql, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return rows.get(0);
    }
    private LinkedHashMap<String,Object> checked(String table, Map<String,Object> data) {
        Set<String> allowed = FIELDS.get(table);
        if (allowed == null || data == null || data.isEmpty() || !allowed.containsAll(data.keySet()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不允许的表或字段");
        LinkedHashMap<String,Object> values = new LinkedHashMap<>(data);
        if (values.containsKey("tenant_id") && !"1".equals(String.valueOf(values.get("tenant_id"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不允许的公司范围");
        return values;
    }
    public long insert(String table, Map<String,Object> data) {
        LinkedHashMap<String,Object> values = checked(table, data);
        String sql = "INSERT INTO `" + table + "` (`" + String.join("`,`", values.keySet()) + "`) VALUES (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")";
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            int i = 1;
            for (Object value : values.values()) {
                if (value instanceof Timestamp timestamp)
                    statement.setTimestamp(i++, timestamp, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                else statement.setObject(i++, value);
            }
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("未生成主键");
        return key.longValue();
    }
    public void update(String table, long id, Map<String,Object> data) {
        LinkedHashMap<String,Object> values = checked(table, data);
        if (values.containsKey("tenant_id")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不可修改公司范围");
        String set = String.join(",", values.keySet().stream().map(k -> "`" + k + "`=?").toList());
        List<Object> args = new ArrayList<>(values.values()); args.add(id);
        String scope = FIELDS.get(table).contains("tenant_id") ? " AND tenant_id=1" : "";
        // MySQL may return zero for an unchanged value: check existence independently.
        one("SELECT id FROM `" + table + "` WHERE id=?" + scope, id);
        jdbc.update("UPDATE `" + table + "` SET " + set + " WHERE id=?" + scope, args.toArray());
    }
}
