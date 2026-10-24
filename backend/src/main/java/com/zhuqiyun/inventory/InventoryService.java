package com.zhuqiyun.inventory;

import com.zhuqiyun.platform.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
import static com.zhuqiyun.domain.Rules.*;

/** Perpetual inventory with warehouse-level moving weighted average valuation. */
@Service
public class InventoryService {
 private final Db db; private final Auth auth;
 public InventoryService(Db db,Auth auth){this.db=db;this.auth=auth;}
 public List<Map<String,Object>> inventory(){auth.require("inventory:read");auth.require("inventory:company");var access=DataPolicy.flags(auth);return db.list("SELECT i.*,w.name warehouse_name,m.name material_name,m.specification,m.unit,m.safety_stock FROM inventory i JOIN warehouses w ON w.id=i.warehouse_id JOIN materials m ON m.id=i.material_id WHERE i.tenant_id=1 ORDER BY w.id,m.id LIMIT 200").stream().map(r->DataPolicy.inventory(r,access)).toList();}
 public List<Map<String,Object>> movements(){auth.require("inventory:read");var access=DataPolicy.flags(auth);return db.list("SELECT s.*,p.name project_name,w.name warehouse_name,m.name material_name,m.unit,u.name operator_name FROM stock_movements s LEFT JOIN projects p ON p.id=s.project_id JOIN warehouses w ON w.id=s.warehouse_id JOIN materials m ON m.id=s.material_id JOIN users u ON u.id=s.operator_id WHERE s.tenant_id=1 AND (("+auth.projectFilter("p")+")"+(auth.can("stocktake:read")&&auth.can("inventory:company")?" OR s.project_id IS NULL":"")+") ORDER BY s.id DESC LIMIT 200").stream().map(r->DataPolicy.inventory(r,access)).toList();}
 // Warehouse row is a coarse lock, also serializing first stock-row insertion and avoiding missing-row races.
 private void lockWarehouse(long id){db.one("SELECT * FROM warehouses WHERE id=? AND tenant_id=1 FOR UPDATE",id);}
 private Map<String,Object> stock(long warehouse,long material){List<Map<String,Object>> rows=db.list("SELECT * FROM inventory WHERE tenant_id=1 AND warehouse_id=? AND material_id=? FOR UPDATE",warehouse,material);if(rows.isEmpty()){long id=db.insert("inventory",row("warehouse_id",warehouse,"material_id",material));return db.one("SELECT * FROM inventory WHERE id=? FOR UPDATE",id);}return rows.get(0);}
 public BigDecimal sourceAvailable(long orderItem,long warehouse){
  BigDecimal received=db.list("SELECT i.quantity FROM receipt_items i JOIN receipts r ON r.id=i.receipt_id WHERE i.order_item_id=? AND r.warehouse_id=? FOR UPDATE",orderItem,warehouse).stream().map(r->dec(r.get("quantity"))).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal returned=db.list("SELECT i.quantity FROM purchase_return_items i JOIN receipt_items g ON g.id=i.receipt_item_id JOIN purchase_returns r ON r.id=i.return_id WHERE g.order_item_id=? AND r.warehouse_id=? FOR UPDATE",orderItem,warehouse).stream().map(r->dec(r.get("quantity"))).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal issued=db.list("SELECT quantity FROM stock_movements WHERE source_order_item_id=? AND warehouse_id=? AND kind IN ('ISSUE','ISSUE_RETURN') FOR UPDATE",orderItem,warehouse).stream().map(r->dec(r.get("quantity"))).reduce(BigDecimal.ZERO,BigDecimal::add);
  return received.subtract(returned).add(issued);
 }
 public List<Map<String,Object>> sources(long project,long warehouse,long material){
  auth.require("inventory:read");auth.require("order:read");auth.project(project);List<Map<String,Object>> rows=db.list("SELECT i.id,o.code order_code,m.unit FROM order_items i JOIN purchase_orders o ON o.id=i.order_id JOIN materials m ON m.id=i.material_id WHERE o.project_id=? AND i.material_id=? AND o.tenant_id=1 AND EXISTS(SELECT 1 FROM receipt_items g JOIN receipts r ON r.id=g.receipt_id WHERE g.order_item_id=i.id AND r.warehouse_id=?) ORDER BY o.id DESC",project,material,warehouse);
  for(var r:rows)r.put("available_quantity",sourceAvailable(((Number)r.get("id")).longValue(),warehouse));return rows.stream().filter(r->dec(r.get("available_quantity")).signum()>0).toList();
 }
 @Transactional public Map<String,Object> receive(long orderId,Map<String,Object> body){
  auth.require("order:receive");Map<String,Object> order=db.one("SELECT * FROM purchase_orders WHERE id=? AND tenant_id=1 FOR UPDATE",orderId);long project=((Number)order.get("project_id")).longValue();auth.project(project);check(Set.of("OPEN","PARTIAL").contains(order.get("status")),"订单状态不允许继续验收入库");
  long warehouse=id(body,"warehouse_id");String reference=text(body,"reference");lockWarehouse(warehouse);check(db.list("SELECT id FROM receipts WHERE reference=?",reference).isEmpty(),"收货凭证号已使用，请勿重复入库");
  List<Map<String,Object>> lines=new ArrayList<>();Set<Long> ids=new HashSet<>();BigDecimal total=BigDecimal.ZERO;
  for(Map<String,Object> item:items(body)){long line=id(item,"order_item_id");unique(ids,line);BigDecimal quantity=decimal(item,"quantity",3,true);Map<String,Object> source=db.one("SELECT * FROM order_items WHERE id=? AND order_id=? FOR UPDATE",line,orderId);BigDecimal remaining=dec(source.get("quantity")).subtract(dec(source.get("received_quantity")));check(quantity.compareTo(remaining)<=0,"验收数量超过订单剩余可收货数量");
   // Last receipt absorbs line rounding so accepted value can never exceed the committed line amount.
   BigDecimal accepted=dec(db.one("SELECT COALESCE(SUM(amount),0) amount FROM receipt_items WHERE order_item_id=?",line).get("amount"));BigDecimal amount=quantity.compareTo(remaining)==0?dec(source.get("amount")).subtract(accepted):money(quantity.multiply(dec(source.get("unit_price"))));check(amount.signum()>=0&&accepted.add(amount).compareTo(dec(source.get("amount")))<=0,"分批金额舍入超过订单金额，请合并本次收货数量");
   lines.add(row("order_item_id",line,"material_id",source.get("material_id"),"quantity",quantity,"unit_price",source.get("unit_price"),"amount",amount,"new_received",dec(source.get("received_quantity")).add(quantity)));total=total.add(amount);
  }
  long receipt=db.insert("receipts",row("code",code("GR"),"order_id",orderId,"project_id",project,"warehouse_id",warehouse,"reference",reference,"note",optional(body,"note"),"amount",total,"operator_id",auth.id()));
  for(Map<String,Object> line:lines){long material=((Number)line.get("material_id")).longValue();Map<String,Object> stock=stock(warehouse,material);BigDecimal quantity=dec(stock.get("quantity")).add(dec(line.get("quantity")));BigDecimal value=dec(stock.get("stock_value")).add(dec(line.get("amount")));BigDecimal average=value.divide(quantity,6,RoundingMode.HALF_UP);db.update("inventory",((Number)stock.get("id")).longValue(),row("quantity",quantity,"stock_value",value,"average_price",average));db.update("order_items",((Number)line.get("order_item_id")).longValue(),row("received_quantity",line.remove("new_received")));line.put("receipt_id",receipt);db.insert("receipt_items",line);db.insert("stock_movements",row("warehouse_id",warehouse,"material_id",material,"project_id",project,"kind","RECEIPT","quantity",line.get("quantity"),"unit_cost",line.get("unit_price"),"amount",line.get("amount"),"reference",reference,"receipt_id",receipt,"operator_id",auth.id()));}
  boolean complete=db.list("SELECT id FROM order_items WHERE order_id=? AND received_quantity<quantity",orderId).isEmpty();db.update("purchase_orders",orderId,row("status",complete?"COMPLETED":"PARTIAL"));Map<String,Object> payable=db.one("SELECT * FROM payables WHERE order_id=? FOR UPDATE",orderId);db.update("payables",((Number)payable.get("id")).longValue(),row("amount",dec(payable.get("amount")).add(total)));auth.audit("RECEIVE_STOCK","receipt",receipt,"验收入库 "+total+" 凭证 "+reference);return db.one("SELECT * FROM receipts WHERE id=?",receipt);
 }
 @Transactional public Map<String,Object> issue(Map<String,Object> body){
  auth.require("inventory:issue");long project=id(body,"project_id");auth.project(project);long warehouse=id(body,"warehouse_id");String reference=text(body,"reference");
  Set<Long> sourceOrders=new TreeSet<>();for(var item:items(body))if(item.get("order_item_id")!=null&&!item.get("order_item_id").toString().isBlank()){var source=db.one("SELECT o.id,o.project_id FROM order_items i JOIN purchase_orders o ON o.id=i.order_id WHERE i.id=? AND i.tenant_id=1",id(item,"order_item_id"));auth.project(((Number)source.get("project_id")).longValue());sourceOrders.add(((Number)source.get("id")).longValue());}
  for(long sourceOrder:sourceOrders)db.one("SELECT id FROM purchase_orders WHERE id=? FOR UPDATE",sourceOrder);
  lockWarehouse(warehouse);check(db.list("SELECT id FROM issues WHERE reference=?",reference).isEmpty(),"领料凭证号已使用，请勿重复出库");List<Map<String,Object>> lines=new ArrayList<>();Set<Long> ids=new HashSet<>();BigDecimal total=BigDecimal.ZERO;
  for(Map<String,Object> item:items(body)){long material=id(item,"material_id");unique(ids,material);db.one("SELECT id FROM materials WHERE id=? AND tenant_id=1",material);BigDecimal quantity=decimal(item,"quantity",3,true);Long orderItem=null;
   if(item.get("order_item_id")!=null&&!item.get("order_item_id").toString().isBlank()){
    orderItem=id(item,"order_item_id");var source=db.one("SELECT i.material_id,o.project_id FROM order_items i JOIN purchase_orders o ON o.id=i.order_id WHERE i.id=?",orderItem);check(((Number)source.get("material_id")).longValue()==material&&((Number)source.get("project_id")).longValue()==project,"领料来源的项目或材料不匹配");
    check(quantity.compareTo(sourceAvailable(orderItem,warehouse))<=0,"所选采购来源的未领用验收数量不足");
   }
   Map<String,Object> stock=stock(warehouse,material);BigDecimal before=dec(stock.get("quantity"));check(quantity.compareTo(before)<=0,"库存不足，禁止出库");BigDecimal value=dec(stock.get("stock_value"));BigDecimal amount=quantity.compareTo(before)==0?value:money(value.multiply(quantity).divide(before,10,RoundingMode.HALF_UP));BigDecimal remaining=before.subtract(quantity),residual=value.subtract(amount);BigDecimal average=remaining.signum()==0?BigDecimal.ZERO:residual.divide(remaining,6,RoundingMode.HALF_UP);db.update("inventory",((Number)stock.get("id")).longValue(),row("quantity",remaining,"stock_value",residual,"average_price",average));lines.add(row("warehouse_id",warehouse,"material_id",material,"project_id",project,"kind","ISSUE","source_order_item_id",orderItem,"quantity",quantity.negate(),"unit_cost",dec(stock.get("average_price")),"amount",amount,"reference",reference,"operator_id",auth.id()));total=total.add(amount);}
  long issue=db.insert("issues",row("code",code("MI"),"project_id",project,"warehouse_id",warehouse,"reference",reference,"note",optional(body,"note"),"amount",total,"operator_id",auth.id()));for(Map<String,Object> line:lines){line.put("issue_id",issue);db.insert("stock_movements",line);}auth.audit("ISSUE_STOCK","issue",issue,"项目材料领用成本 "+total+" 凭证 "+reference);return DataPolicy.inventory(db.one("SELECT * FROM issues WHERE id=?",issue),DataPolicy.flags(auth));
 }
}
