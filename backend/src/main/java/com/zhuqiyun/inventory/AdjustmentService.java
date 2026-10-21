package com.zhuqiyun.inventory;

import com.zhuqiyun.platform.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import static com.zhuqiyun.domain.Rules.*;

/** Append-only material corrections. Original receipts, issues and payments stay intact. */
@Service
public class AdjustmentService {
 private final Db db; private final Auth auth;
 public AdjustmentService(Db db,Auth auth){this.db=db;this.auth=auth;}
 private long number(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
 private String reference(Map<String,Object> body){String value=text(body,"reference");check(value.length()<=100,"凭证号不得超过100字符");return value;}
 private void lockWarehouse(long id){db.one("SELECT id FROM warehouses WHERE id=? AND tenant_id=1 FOR UPDATE",id);}
 private Map<String,Object> stock(long warehouse,long material){return db.one("SELECT * FROM inventory WHERE tenant_id=1 AND warehouse_id=? AND material_id=? FOR UPDATE",warehouse,material);}
 private void setStock(Map<String,Object> stock,BigDecimal quantity,BigDecimal value){
  check(quantity.signum()>=0&&value.signum()>=0&&(quantity.signum()>0||value.signum()==0),"库存数量或价值不一致");
  db.update("inventory",number(stock,"id"),row("quantity",quantity,"stock_value",value,"average_price",quantity.signum()==0?BigDecimal.ZERO:value.divide(quantity,6,RoundingMode.HALF_UP)));
 }
 private BigDecimal total(List<Map<String,Object>> rows,String key){return rows.stream().map(r->dec(r.get(key))).reduce(BigDecimal.ZERO,BigDecimal::add);}
 private BigDecimal allocated(BigDecimal quantity,BigDecimal originalQuantity,BigDecimal originalAmount,BigDecimal returnedQuantity,BigDecimal returnedAmount){
  BigDecimal remaining=originalQuantity.subtract(returnedQuantity);check(quantity.compareTo(remaining)<=0,"退回数量超过原单据可退数量");
  BigDecimal amount=quantity.compareTo(remaining)==0?originalAmount.subtract(returnedAmount):money(originalAmount.multiply(quantity).divide(originalQuantity,10,RoundingMode.HALF_UP));
  check(amount.signum()>=0&&returnedAmount.add(amount).compareTo(originalAmount)<=0,"分次退回金额超出原单据，请合并数量");return amount;
 }
 public List<Map<String,Object>> issues(){
  auth.require("inventory:read");List<Map<String,Object>> rows=db.list("SELECT i.*,p.name project_name,w.name warehouse_name,u.name operator_name FROM issues i JOIN projects p ON p.id=i.project_id JOIN warehouses w ON w.id=i.warehouse_id JOIN users u ON u.id=i.operator_id WHERE i.tenant_id=1 AND "+auth.projectFilter("p")+" ORDER BY i.id DESC");
  for(Map<String,Object> issue:rows)issue.put("items",db.list("SELECT s.id,s.material_id,m.name material_name,m.specification,m.unit,-s.quantity quantity,s.amount,COALESCE((SELECT SUM(r.quantity) FROM issue_return_items r WHERE r.source_movement_id=s.id),0) returned_quantity,-s.quantity-COALESCE((SELECT SUM(r.quantity) FROM issue_return_items r WHERE r.source_movement_id=s.id),0) returnable_quantity FROM stock_movements s JOIN materials m ON m.id=s.material_id WHERE s.issue_id=? AND s.kind='ISSUE' ORDER BY s.id",issue.get("id")));
  return rows;
 }
 public List<Map<String,Object>> issueReturns(){auth.require("inventory:read");return db.list("SELECT r.*,i.code issue_code,p.name project_name,w.name warehouse_name,u.name operator_name FROM issue_returns r JOIN issues i ON i.id=r.issue_id JOIN projects p ON p.id=r.project_id JOIN warehouses w ON w.id=r.warehouse_id JOIN users u ON u.id=r.operator_id WHERE r.tenant_id=1 AND "+auth.projectFilter("p")+" ORDER BY r.id DESC");}
 @Transactional public Map<String,Object> returnIssue(long issueId,Map<String,Object> body){
  auth.require("inventory:issue");Map<String,Object> initial=db.one("SELECT * FROM issues WHERE tenant_id=1 AND id=?",issueId);auth.project(number(initial,"project_id"));long warehouse=number(initial,"warehouse_id");lockWarehouse(warehouse);
  Map<String,Object> issue=db.one("SELECT * FROM issues WHERE tenant_id=1 AND id=? FOR UPDATE",issueId);String reference=reference(body),reason=text(body,"reason");
  check(db.list("SELECT id FROM issue_returns WHERE reference=?",reference).isEmpty(),"退料凭证号已使用");List<Map<String,Object>> lines=new ArrayList<>();Set<Long> seen=new HashSet<>();BigDecimal total=BigDecimal.ZERO;
  for(Map<String,Object> input:items(body)){
   long source=id(input,"source_id");unique(seen,source);BigDecimal qty=decimal(input,"quantity",3,true);
   Map<String,Object> flow=db.one("SELECT * FROM stock_movements WHERE id=? AND tenant_id=1 AND issue_id=? AND kind='ISSUE' FOR UPDATE",source,issueId);
   List<Map<String,Object>> previous=db.list("SELECT quantity,amount FROM issue_return_items WHERE source_movement_id=? FOR UPDATE",source);
   BigDecimal amount=allocated(qty,dec(flow.get("quantity")).negate(),dec(flow.get("amount")),total(previous,"quantity"),total(previous,"amount"));
   Map<String,Object> stock=stock(warehouse,number(flow,"material_id"));setStock(stock,dec(stock.get("quantity")).add(qty),dec(stock.get("stock_value")).add(amount));
   lines.add(row("source_movement_id",source,"material_id",flow.get("material_id"),"quantity",qty,"amount",amount));total=total.add(amount);
  }
  long id=db.insert("issue_returns",row("code",code("IR"),"issue_id",issueId,"project_id",issue.get("project_id"),"warehouse_id",warehouse,"reference",reference,"reason",reason,"amount",total,"operator_id",auth.id()));
  for(Map<String,Object> line:lines){db.insert("issue_return_items",row("return_id",id,"source_movement_id",line.get("source_movement_id"),"quantity",line.get("quantity"),"amount",line.get("amount")));
   db.insert("stock_movements",row("warehouse_id",warehouse,"material_id",line.get("material_id"),"project_id",issue.get("project_id"),"kind","ISSUE_RETURN","quantity",line.get("quantity"),"unit_cost",dec(line.get("amount")).divide(dec(line.get("quantity")),6,RoundingMode.HALF_UP),"amount",line.get("amount"),"reference",reference,"issue_id",issueId,"issue_return_id",id,"operator_id",auth.id()));}
  auth.audit("RETURN_ISSUE","issue_return",id,"原领料 "+issue.get("code")+" 退回成本 "+total+" 凭证 "+reference);return db.one("SELECT * FROM issue_returns WHERE id=?",id);
 }
 public List<Map<String,Object>> purchaseReturns(){auth.require("order:read");return db.list("SELECT r.*,o.code order_code,g.code receipt_code,p.name project_name,w.name warehouse_name,u.name operator_name FROM purchase_returns r JOIN purchase_orders o ON o.id=r.order_id JOIN receipts g ON g.id=r.receipt_id JOIN projects p ON p.id=r.project_id JOIN warehouses w ON w.id=r.warehouse_id JOIN users u ON u.id=r.operator_id WHERE r.tenant_id=1 AND "+auth.projectFilter("p")+" ORDER BY r.id DESC");}
 @Transactional public Map<String,Object> returnPurchase(long receiptId,Map<String,Object> body){
  auth.require("order:receive");Map<String,Object> receipt=db.one("SELECT * FROM receipts WHERE tenant_id=1 AND id=?",receiptId);auth.project(number(receipt,"project_id"));long order=number(receipt,"order_id"),warehouse=number(receipt,"warehouse_id");
  db.one("SELECT id FROM purchase_orders WHERE id=? AND tenant_id=1 FOR UPDATE",order);lockWarehouse(warehouse);
  Map<String,Object> payable=db.one("SELECT * FROM payables WHERE tenant_id=1 AND order_id=? FOR UPDATE",order);
  String reference=reference(body),reason=text(body,"reason");check(db.list("SELECT id FROM purchase_returns WHERE reference=?",reference).isEmpty(),"退货凭证号已使用");
  List<Map<String,Object>> lines=new ArrayList<>();Set<Long> seen=new HashSet<>();BigDecimal credit=BigDecimal.ZERO,stockTotal=BigDecimal.ZERO;
  for(Map<String,Object> input:items(body)){
   long source=id(input,"source_id");unique(seen,source);BigDecimal qty=decimal(input,"quantity",3,true);
   Map<String,Object> line=db.one("SELECT * FROM receipt_items WHERE tenant_id=1 AND id=? AND receipt_id=? FOR UPDATE",source,receiptId);
   List<Map<String,Object>> previous=db.list("SELECT quantity,amount FROM purchase_return_items WHERE receipt_item_id=? FOR UPDATE",source);
   BigDecimal amount=allocated(qty,dec(line.get("quantity")),dec(line.get("amount")),total(previous,"quantity"),total(previous,"amount"));
   Map<String,Object> stock=stock(warehouse,number(line,"material_id"));BigDecimal before=dec(stock.get("quantity")),value=dec(stock.get("stock_value"));check(qty.compareTo(before)<=0,"原仓库存不足，请先核查或办理项目退料");
   BigDecimal cost=qty.compareTo(before)==0?value:money(value.multiply(qty).divide(before,10,RoundingMode.HALF_UP));setStock(stock,before.subtract(qty),value.subtract(cost));
   lines.add(row("receipt_item_id",source,"material_id",line.get("material_id"),"quantity",qty,"amount",amount,"stock_amount",cost));credit=credit.add(amount);stockTotal=stockTotal.add(cost);
  }
  BigDecimal netAmount=dec(payable.get("amount")).subtract(credit);check(netAmount.signum()>=0,"退货应付冲减超过原验收应付");
  List<Map<String,Object>> reservations=db.list("SELECT amount FROM payment_applications WHERE payable_id=? AND tenant_id=1 AND status IN ('PENDING','APPROVED') FOR UPDATE",payable.get("id"));
  check(total(reservations,"amount").compareTo(netAmount.subtract(dec(payable.get("paid_amount"))).max(BigDecimal.ZERO))<=0,"退货会侵占付款申请占用，请先撤回相关申请");
  BigDecimal variance=stockTotal.subtract(credit);long id=db.insert("purchase_returns",row("code",code("SR"),"receipt_id",receiptId,"order_id",order,"project_id",receipt.get("project_id"),"warehouse_id",warehouse,"reference",reference,"reason",reason,"amount",credit,"stock_amount",stockTotal,"variance_amount",variance,"operator_id",auth.id()));
  if(variance.signum()!=0){long cost=db.insert("costs",row("project_id",receipt.get("project_id"),"category","RETURN_VARIANCE","amount",variance,"description","采购退货计价差额 "+reference,"incurred_date",LocalDate.now(),"operator_id",auth.id()));db.update("purchase_returns",id,row("cost_id",cost));}
  for(Map<String,Object> line:lines){db.insert("purchase_return_items",row("return_id",id,"receipt_item_id",line.get("receipt_item_id"),"quantity",line.get("quantity"),"amount",line.get("amount"),"stock_amount",line.get("stock_amount")));
   db.insert("stock_movements",row("warehouse_id",warehouse,"material_id",line.get("material_id"),"project_id",receipt.get("project_id"),"kind","PURCHASE_RETURN","quantity",dec(line.get("quantity")).negate(),"unit_cost",dec(line.get("stock_amount")).divide(dec(line.get("quantity")),6,RoundingMode.HALF_UP),"amount",line.get("stock_amount"),"reference",reference,"receipt_id",receiptId,"purchase_return_id",id,"operator_id",auth.id()));}
  db.update("payables",number(payable,"id"),row("amount",netAmount));auth.audit("RETURN_PURCHASE","purchase_return",id,"原入库 "+receipt.get("code")+" 应付冲减 "+credit+" 库存冲减 "+stockTotal+" 计价差额 "+variance);return db.one("SELECT * FROM purchase_returns WHERE id=?",id);
 }
 public List<Map<String,Object>> refunds(){auth.require("finance:read");return db.list("SELECT r.*,o.code order_code,p.name project_name,s.name supplier_name,u.name operator_name FROM supplier_refunds r JOIN payables a ON a.id=r.payable_id JOIN purchase_orders o ON o.id=a.order_id JOIN projects p ON p.id=a.project_id JOIN suppliers s ON s.id=a.supplier_id JOIN users u ON u.id=r.operator_id WHERE r.tenant_id=1 AND "+auth.projectFilter("p")+" ORDER BY r.id DESC");}
 @Transactional public Map<String,Object> refund(Map<String,Object> body){
  auth.require("finance:write");long payableId=id(body,"payable_id");Map<String,Object> payable=db.one("SELECT * FROM payables WHERE id=? AND tenant_id=1 FOR UPDATE",payableId);auth.project(number(payable,"project_id"));
  BigDecimal amount=decimal(body,"amount",2,true);check(amount.compareTo(dec(payable.get("paid_amount")).subtract(dec(payable.get("amount"))))<=0,"退款超过退货后待收退款金额");String reference=reference(body);check(db.list("SELECT id FROM supplier_refunds WHERE reference=?",reference).isEmpty(),"退款凭证号已登记");
  long id=db.insert("supplier_refunds",row("payable_id",payableId,"amount",amount,"reference",reference,"note",optional(body,"note"),"operator_id",auth.id()));db.update("payables",payableId,row("paid_amount",dec(payable.get("paid_amount")).subtract(amount)));auth.audit("REGISTER_SUPPLIER_REFUND","supplier_refund",id,"收到供应商退款 "+amount+" 凭证 "+reference);return db.one("SELECT * FROM supplier_refunds WHERE id=?",id);
 }
}
