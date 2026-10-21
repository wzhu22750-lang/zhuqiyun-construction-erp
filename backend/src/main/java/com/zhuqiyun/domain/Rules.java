package com.zhuqiyun.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Shared boundary validation; all business arithmetic remains decimal on the server. */
public final class Rules {
 private Rules() {}
 public static void check(boolean condition,String message) { if(!condition) throw new ResponseStatusException(HttpStatus.CONFLICT,message); }
 public static String text(Map<String,Object> m,String key) { Object v=m.get(key); String s=v==null?"":v.toString().trim(); if(s.isEmpty()||s.length()>1000) bad(key+"不能为空且不得超过1000字符"); return s; }
 public static String optional(Map<String,Object> m,String key) { Object v=m.get(key);String s=v==null?"":v.toString().trim(); if(s.length()>1000) bad(key+"过长");return s; }
 public static long id(Map<String,Object> m,String key) { try {long n=new BigDecimal(String.valueOf(m.get(key))).longValueExact();if(n<=0) throw new IllegalArgumentException();return n;} catch(Exception e) {bad(key+"必须为有效ID");return 0;} }
 public static BigDecimal dec(Object n) { return n==null?BigDecimal.ZERO:new BigDecimal(n.toString()); }
 public static BigDecimal decimal(Map<String,Object> m,String key,int scale,boolean positive) {try{BigDecimal n=dec(m.get(key)).setScale(scale,RoundingMode.UNNECESSARY); if(n.precision()>18||(positive?n.signum()<=0:n.signum()<0)) throw new IllegalArgumentException();return n;}catch(Exception e){bad(key+"必须是有效非负十进制数（小数位最多"+scale+"）");return BigDecimal.ZERO;}}
 public static BigDecimal money(BigDecimal n) {return n.setScale(2,RoundingMode.HALF_UP);}
 public static LocalDate date(Map<String,Object> m,String key) {try{return LocalDate.parse(text(m,key));}catch(Exception e){bad(key+"日期不正确");return null;}}
 @SuppressWarnings("unchecked") public static List<Map<String,Object>> items(Map<String,Object> m) {Object v=m.get("items");if(!(v instanceof List<?> list)||list.isEmpty()||list.size()>100) {bad("至少一条明细，最多100条");return List.of();} for(Object i:list) if(!(i instanceof Map)) bad("明细格式错误"); return (List<Map<String,Object>>)v;}
 public static void unique(Set<Long> ids,long id) {check(ids.add(id),"同一单据不允许重复明细");}
 public static Map<String,Object> row(Object...args) {Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<args.length;i+=2)m.put(args[i].toString(),args[i+1]);return m;}
 public static String code(String prefix) {return prefix+"-"+LocalDate.now().toString().replace("-","")+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();}
 public static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
