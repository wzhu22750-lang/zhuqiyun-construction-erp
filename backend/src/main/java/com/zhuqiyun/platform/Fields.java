package com.zhuqiyun.platform;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Validation for this module's HTTP fields; never pass raw request maps into Db. */
public final class Fields {
    private Fields() {}
    public static void check(boolean condition,String message) { if (!condition) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    public static Map<String,Object> only(Map<String,Object> body,String fields) {
        check(body != null,"请求不能为空");
        Set<String> allowed = Set.of(fields.split(" "));
        check(allowed.containsAll(body.keySet()),"包含不允许修改的字段");
        return new LinkedHashMap<>(body);
    }
    public static String text(Object value,int max,boolean required) {
        check(value == null || value instanceof String,"文本格式无效");
        String s = value == null ? "" : ((String)value).trim();
        check((!required || !s.isEmpty()) && s.length() <= max,"必填文本缺失或过长");
        return s;
    }
    public static long id(Object value) {
        try { long id = new BigDecimal(String.valueOf(value)).longValueExact(); check(id>0,"编号必须为正整数"); return id; }
        catch (NumberFormatException | ArithmeticException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"编号格式无效"); }
    }
    public static BigDecimal decimal(Object value,int scale,BigDecimal max) {
        try {
            BigDecimal n = new BigDecimal(String.valueOf(value));
            check(n.signum()>=0 && n.compareTo(max)<=0 && n.stripTrailingZeros().scale()<=scale,"数值超出范围或精度");
            return n.setScale(scale);
        } catch (NumberFormatException | ArithmeticException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"金额或数量格式无效"); }
    }
    public static String date(Object value) {
        try { return LocalDate.parse(String.valueOf(value)).toString(); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"日期格式应为 yyyy-MM-dd"); }
    }
    public static void enumeration(Object value,String... allowed) { check(Arrays.asList(allowed).contains(value),"状态或类型无效"); }
    public static boolean bool(Object value) { check(value instanceof Boolean,"布尔值格式无效"); return (Boolean)value; }
    public static List<String> strings(Object value,Set<String> allowed) {
        check(value instanceof List<?>,"权限或菜单必须是数组");
        var result = new ArrayList<String>();
        for (Object v : (List<?>)value) { check(v instanceof String && allowed.contains(v),"权限或菜单值无效"); result.add((String)v); }
        return result.stream().distinct().toList();
    }
}
