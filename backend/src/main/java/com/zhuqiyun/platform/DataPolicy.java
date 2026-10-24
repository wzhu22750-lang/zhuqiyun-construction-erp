package com.zhuqiyun.platform;

import java.util.*;

/** A response contract shared by detail, list and export code. Empty sections + access=false mean denied, not zero business totals. */
public final class DataPolicy {
 private DataPolicy(){}
 public static Map<String,Boolean> flags(Auth auth){
  List<?> p=(List<?>)auth.user().get("permissions");Map<String,Boolean> a=new LinkedHashMap<>();
  for(var e:Map.ofEntries(Map.entry("projects","project:read"),Map.entry("requests","request:read"),Map.entry("quotes","quote:read"),Map.entry("orders","order:read"),Map.entry("inventory","inventory:read"),Map.entry("inventory_company","inventory:company"),Map.entry("costs","cost:read"),Map.entry("contracts","contract:read"),Map.entry("finance","finance:read"),Map.entry("stocktakes","stocktake:read"),Map.entry("stocktake_approve","stocktake:approve")).entrySet())a.put(e.getKey(),p.contains("*")||p.contains(e.getValue()));
  return a;
 }
 public static boolean yes(Map<String,Boolean> a,String key){return Boolean.TRUE.equals(a.get(key));}
 public static Map<String,Object> project(Map<String,Object> row,Map<String,Boolean> a){Map<String,Object> r=new LinkedHashMap<>(row);if(!yes(a,"contracts"))r.remove("contract_amount");if(!yes(a,"costs"))r.remove("budget_amount");return r;}
 public static Map<String,Object> inventory(Map<String,Object> row,Map<String,Boolean> a){Map<String,Object> r=new LinkedHashMap<>(row);if(!yes(a,"costs")&&!yes(a,"stocktake_approve"))for(String k:List.of("amount","unit_cost","stock_value","average_price","stock_amount","variance_amount","cost_id"))r.remove(k);return r;}
 @SuppressWarnings("unchecked") public static Map<String,Object> order(Map<String,Object> row,Map<String,Boolean> a){
  Map<String,Object> r=new LinkedHashMap<>(row);r.put("access",a);
  if(!yes(a,"finance")){for(String k:List.of("payable","paid_amount","outstanding_amount","refundable_amount","unpaid_amount","refunded_amount","available_amount","reserved_amount"))r.remove(k);r.put("payments",List.of());r.put("refunds",List.of());r.put("payment_applications",List.of());}
  if(!yes(a,"inventory"))r.put("receipts",List.of());
  if(r.get("returns") instanceof List<?> list)r.put("returns",list.stream().map(x->{Map<String,Object> v=new LinkedHashMap<>((Map<String,Object>)x);if(!yes(a,"costs"))for(String k:List.of("stock_amount","variance_amount","cost_id"))v.remove(k);return v;}).toList());
  if(r.get("events") instanceof List<?> events)r.put("events",events.stream().map(x->new LinkedHashMap<>((Map<String,Object>)x)).filter(e->{String type=Objects.toString(e.get("entity_type"),"");return switch(type){case "payment","supplier_refund","payment_application"->yes(a,"finance");case "purchase_request"->yes(a,"requests");case "quote"->yes(a,"quotes");default->true;};}).peek(e->{if("purchase_return".equals(e.get("entity_type"))&&!yes(a,"costs"))e.put("detail","办理采购退货");}).toList());
  return r;
 }
}
