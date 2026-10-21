package com.zhuqiyun.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Demo fixture, single MySQL advisory lock + one database transaction, never repairs existing companies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoSeed implements ApplicationRunner {
    private final Db db; private final PasswordEncoder passwords; private final ObjectMapper json; private final TransactionTemplate tx;
    private final boolean enabled; private final String demoPassword;
    public DemoSeed(Db db,PasswordEncoder passwords,ObjectMapper json,TransactionTemplate tx,@Value("${app.seed:true}") boolean enabled,@Value("${app.demo-password:Zhuqi2026!}") String demoPassword) {
        this.db=db; this.passwords=passwords; this.json=json; this.tx=tx; this.enabled=enabled; this.demoPassword=demoPassword;
    }
    @Override public void run(ApplicationArguments args) {
        if(!enabled) return;
        tx.executeWithoutResult(status -> {
            db.jdbc().execute((ConnectionCallback<Void>) connection -> {
                String product=connection.getMetaData().getDatabaseProductName();
                boolean mysql=product.toLowerCase(Locale.ROOT).contains("mysql");
                if(mysql) {
                    try(var statement=connection.prepareStatement("SELECT GET_LOCK('zhuqiyun_demo_seed_v1',60)")) {
                        try(var rs=statement.executeQuery()) { if(!rs.next() || rs.getInt(1)!=1) throw new IllegalStateException("无法获取演示种子锁"); }
                    }
                }
                try {
                    // Lock is session owned. Commit before releasing it; see transaction synchronization below.
                    if(mysql) org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCompletion(int completionStatus) {
                            try(var statement=connection.prepareStatement("SELECT RELEASE_LOCK('zhuqiyun_demo_seed_v1')")) { statement.execute(); }
                            catch(Exception e) { throw new IllegalStateException("无法释放演示种子锁",e); }
                        }
                    });
                    if(!db.list("SELECT id FROM company LIMIT 1").isEmpty()) return null;
                    seed();
                    return null;
                } catch(RuntimeException e) { throw e; }
            });
        });
    }
    private Map<String,Object> row(Object... pairs) {
        Map<String,Object> map=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) map.put((String)pairs[i],pairs[i+1]);
        return map;
    }
    private long add(String table,Object... pairs) { var map=row(pairs); map.put("tenant_id",1L); return db.insert(table,map); }
    private String encode(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private BigDecimal money(String value) { return new BigDecimal(value).setScale(2,RoundingMode.HALF_UP); }
    private void seed() {
        Fields.check(demoPassword.length()>=8 && demoPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,"演示密码应为8到72字节");
        db.jdbc().update("INSERT INTO company(id,name,system_name,theme_color) VALUES(1,?,?,?)","华建工程建设有限公司","筑企云 · 建筑企业经营平台","#2563EB");
        String[] roleCodes={"admin","boss","manager","buyer","warehouse","finance"};
        String[] roleNames={"系统管理员","企业负责人","项目经理","采购专员","仓库管理员","财务专员"};
        Map<String,List<String>> permissions=new LinkedHashMap<>();
        permissions.put("admin",List.of("*"));
        permissions.put("boss",List.of("dashboard:read","project:read","project:write","master:read","request:read","request:approve","order:read","inventory:read","cost:read","contract:read","contract:write","finance:read","finance:approve"));
        permissions.put("manager",List.of("dashboard:read","project:read","project:write","master:read","request:read","request:write","order:read","inventory:read","cost:read","cost:write","contract:read"));
        permissions.put("buyer",List.of("dashboard:read","project:read","master:read","master:write","request:read","quote:write","order:read","order:write","inventory:read","contract:read","contract:write"));
        permissions.put("warehouse",List.of("dashboard:read","project:read","master:read","request:read","order:read","order:receive","inventory:read","inventory:issue"));
        permissions.put("finance",List.of("dashboard:read","project:read","master:read","request:read","order:read","cost:read","cost:write","contract:read","contract:write","finance:read","finance:write"));
        Map<String,List<String>> menus=Map.of(
            "admin",List.of("dashboard","projects","requests","approvals","orders","inventory","costs","contracts","finance","suppliers","materials","warehouses","system"),
            "boss",List.of("dashboard","projects","requests","approvals","orders","inventory","costs","contracts","finance","suppliers"),
            "manager",List.of("dashboard","projects","requests","orders","inventory","costs","contracts"),
            "buyer",List.of("dashboard","projects","requests","orders","inventory","contracts","suppliers","materials"),
            "warehouse",List.of("dashboard","projects","orders","inventory","materials","warehouses"),
            "finance",List.of("dashboard","projects","orders","costs","contracts","finance"));
        Map<String,Long> users=new LinkedHashMap<>();
        for(int i=0;i<roleCodes.length;i++) {
            String code=roleCodes[i];
            long role=db.insert("roles",row("code",code,"name",roleNames[i],"permissions",encode(permissions.get(code)),"menus",encode(menus.get(code))));
            long user=add("users","username",code,"name",roleNames[i],"role_id",role,"password_hash",passwords.encode(demoPassword),"enabled",true);
            users.put(code,user);
        }
        long manager=users.get("manager"),buyer=users.get("buyer"),warehouse=users.get("warehouse"),finance=users.get("finance"),boss=users.get("boss");
        String[][] dictionaries={
            {"project_type","商业建筑","商业建筑"},{"project_type","住宅工程","住宅"},{"project_type","机电安装工程","机电安装"},
            {"material_category","钢材","钢材"},{"material_category","水泥","水泥"},{"material_category","砂石","砂石"},{"material_category","管材","管材"},{"material_category","电气","电气"},
            {"supplier_category","钢材供应","钢材"},{"supplier_category","建材供应","建材"},{"supplier_category","机电设备","机电"},
            {"cost_category","人工","LABOR"},{"cost_category","机械","MACHINE"},{"cost_category","分包","SUBCONTRACT"},{"cost_category","其他","OTHER"},
            {"unit","吨","吨"},{"unit","立方米","m³"},{"unit","米","米"},{"unit","个","个"},{"unit","台","台"}};
        for(int i=0;i<dictionaries.length;i++) add("dictionaries","category",dictionaries[i][0],"label",dictionaries[i][1],"value",dictionaries[i][2],"sort_order",i);
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        long[] projects=new long[3];
        String[] names={"滨江商务中心建设项目","城南住宅小区二期工程","科技产业园机电安装工程"};
        for(int i=0;i<3;i++) {
            long projectManager=i==2?boss:manager;
            projects[i]=add("projects","code","PRJ-2026-00"+(i+1),"name",names[i],"owner",List.of("滨江商务置业有限公司","城南置业有限公司","科技产业园投资发展有限公司").get(i),"contractor","华建工程建设有限公司","manager_id",projectManager,
                "location",List.of("杭州市滨江区商务中心地块","杭州市萧山区城南住宅二期地块","杭州市余杭区科技产业园").get(i),"type",List.of("商业建筑","住宅","机电安装").get(i),"contract_amount",money(List.of("28000000","12500000","1800000").get(i)),"budget_amount",money(List.of("22000000","9800000","1450000").get(i)),
                "start_date",withinYear(today,120-i*20),"end_date",today.plusDays(180+i*60),"progress",new BigDecimal(List.of("46.50","68.00","25.00").get(i)),"status","ACTIVE");
            add("project_progress","project_id",projects[i],"progress",new BigDecimal("10.00"),"note","完成开工准备与场地交接","operator_id",projectManager,"created_at",Timestamp.from(today.minusDays(70).atTime(10,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant()));
            add("project_progress","project_id",projects[i],"progress",new BigDecimal(List.of("46.50","68.00","25.00").get(i)),"note","本月工程进度确认","operator_id",projectManager);
        }
        long[] suppliers=new long[10];
        String[] supplierNames={"华东钢铁供应有限公司","恒基建材贸易有限公司","绿城水泥有限公司","中联砂石供应站","安通管业有限公司","德力电气设备有限公司","金盾五金贸易有限公司","远航机电设备有限公司","宏达建筑材料有限公司","启明消防设备有限公司"};
        for(int i=0;i<10;i++) suppliers[i]=add("suppliers","code",String.format("SUP-%03d",i+1),"name",supplierNames[i],"contact","联系人"+(i+1),"phone","1380000"+String.format("%04d",i+1),"category",i<4?"建材":i<7?"机电":"综合","status","ACTIVE","qualification","营业执照及供货资质已归档","qualification_expiry",today.plusMonths(9+i),"rating",new BigDecimal("4."+(i%5)));
        long[] materials=new long[40];
        String[] materialNames={"螺纹钢筋","盘螺","普通硅酸盐水泥","机制砂","碎石","给水管","电力电缆","配电箱","镀锌钢管","防水卷材"};
        String[] specs={"HRB400E Φ16","HRB400E Φ8","P.O 42.5","中砂 II类","5-25mm","PE DN110","YJV 4×35","三级配电","DN50","SBS 4mm"};
        String[] units={"吨","吨","吨","m³","m³","米","米","台","米","m²"};
        String[] categories={"钢材","钢材","水泥","砂石","砂石","管材","电气","电气","管材","防水"};
        String[] prices={"3600","3750","420","135","118","42","86","680","32","28"};
        for(int i=0;i<40;i++) materials[i]=add("materials","code",String.format("MAT-%03d",i+1),"name",materialNames[i%10]+(i<10?"":" · "+(i/10+1)+"型"),"specification",specs[i%10],"unit",units[i%10],"category",categories[i%10],"safety_stock",new BigDecimal(i%10<3?"10.000":"50.000"),"reference_price",new BigDecimal(prices[i%10]).setScale(4));
        long central=add("warehouses","code","WH-001","name","集团中心仓","location","杭州市临平区物流园");
        add("warehouses","code","WH-002","name","滨江项目现场仓","location","滨江商务中心建设项目驻地");
        seedBusiness(projects,suppliers,materials,central,manager,buyer,warehouse,finance,boss,today);
        add("audit_logs","operator_id",users.get("admin"),"action","DEMO_SEED","entity_type","company","entity_id",1L,"detail","演示数据初始化：3项目、10供应商、40材料、15申请、10订单、8合同；库存、应付和付款同事务生成");
    }
    private void seedBusiness(long[] projects,long[] suppliers,long[] materials,long warehouseId,long manager,long buyer,long warehouse,long finance,long boss,LocalDate today) {
        List<Long> orderIds=new ArrayList<>();
        for(int i=0;i<15;i++) {
            long project=projects[i%3], supplier=suppliers[i%10];
            String state=i<10||i==12?"APPROVED":i<12?"PENDING":i==13?"DRAFT":"REJECTED";
            Timestamp created=Timestamp.from(today.minusMonths(4-i%5).withDayOfMonth(5+i).atTime(9,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant());
            long request=add("purchase_requests","code",String.format("REQ-2026-%03d",i+1),"project_id",project,"applicant_id",manager,"required_date",today.plusDays(7+i),"note","施工阶段材料采购 · "+(i+1),"status",state,"total_amount",BigDecimal.ZERO,"created_at",created);
            List<Map<String,Object>> lines=new ArrayList<>(); BigDecimal requestTotal=BigDecimal.ZERO;
            for(int j=0;j<3;j++) {
                long material=materials[(i*3+j)%40];
                BigDecimal price=new BigDecimal(db.one("SELECT reference_price FROM materials WHERE id=?",material).get("reference_price").toString());
                BigDecimal quantity=new BigDecimal(12+i*2+j*5).setScale(3);
                BigDecimal amount=quantity.multiply(price).setScale(2,RoundingMode.HALF_UP); requestTotal=requestTotal.add(amount);
                long item=add("request_items","request_id",request,"material_id",material,"quantity",quantity,"unit_price",price,"amount",amount);
                lines.add(row("request_item_id",item,"material_id",material,"quantity",quantity,"unit_price",price));
            }
            db.update("purchase_requests",request,Map.of("total_amount",requestTotal));
            if(!state.equals("DRAFT")) {
                add("approvals","request_id",request,"operator_id",manager,"decision","SUBMITTED","comment","已核对施工计划，提交采购审批","created_at",created);
                if(state.equals("APPROVED")||state.equals("REJECTED")) add("approvals","request_id",request,"operator_id",boss,"decision",state,"comment",state.equals("APPROVED")?"预算内采购，同意执行":"需求重复，请调整采购计划","created_at",created);
            }
            if(!state.equals("APPROVED")) continue;
            long chosen=0; BigDecimal chosenTotal=BigDecimal.ZERO;
            List<Map<String,Object>> chosenLines=new ArrayList<>();
            for(int q=0;q<2;q++) {
                long quotedSupplier=q==0?supplier:suppliers[(i+1)%10];
                List<Map<String,Object>> quotedLines=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
                for(Map<String,Object> line:lines) {
                    BigDecimal price=((BigDecimal)line.get("unit_price")).multiply(new BigDecimal(q==0?"0.98":"1.025")).setScale(4,RoundingMode.HALF_UP);
                    BigDecimal amount=((BigDecimal)line.get("quantity")).multiply(price).setScale(2,RoundingMode.HALF_UP);
                    Map<String,Object> quotedLine=new LinkedHashMap<>(line); quotedLine.put("unit_price",price); quotedLine.put("amount",amount); quotedLines.add(quotedLine); total=total.add(amount);
                }
                boolean selected=i<10 && q==0;
                long quote=add("quotes","request_id",request,"supplier_id",quotedSupplier,"delivery_days",3+q*2,"valid_until",today.plusDays(30),"note","含税运输到场价","total_amount",total,"selected",selected,"selection_reason",selected?"综合比价，价格及交货期满足要求":null,"created_at",created);
                for(Map<String,Object> line:quotedLines) add("quote_items","quote_id",quote,"request_item_id",line.get("request_item_id"),"unit_price",line.get("unit_price"),"amount",line.get("amount"));
                if(q==0) { chosen=quote; chosenTotal=total; chosenLines=quotedLines; }
            }
            if(i>=10) continue;
            boolean received=i<8, complete=i<4;
            long order=add("purchase_orders","code",String.format("PO-2026-%03d",i+1),"project_id",project,"request_id",request,"quote_id",chosen,"supplier_id",supplier,"total_amount",chosenTotal,"status",complete?"COMPLETED":received?"PARTIAL":"OPEN","buyer_id",buyer,"created_at",created);
            orderIds.add(order);
            List<Map<String,Object>> receiptLines=new ArrayList<>(); BigDecimal acceptedTotal=BigDecimal.ZERO;
            for(Map<String,Object> line:chosenLines) {
                BigDecimal quantity=(BigDecimal)line.get("quantity"), price=(BigDecimal)line.get("unit_price");
                BigDecimal receivedQuantity=received?(complete?quantity:quantity.multiply(new BigDecimal("0.60")).setScale(3,RoundingMode.HALF_UP)):new BigDecimal("0.000");
                long item=add("order_items","order_id",order,"material_id",line.get("material_id"),"request_item_id",line.get("request_item_id"),"quantity",quantity,"received_quantity",receivedQuantity,"unit_price",price,"amount",line.get("amount"));
                if(received) {
                    BigDecimal amount=receivedQuantity.multiply(price).setScale(2,RoundingMode.HALF_UP); acceptedTotal=acceptedTotal.add(amount);
                    receiptLines.add(row("order_item_id",item,"material_id",line.get("material_id"),"quantity",receivedQuantity,"unit_price",price,"amount",amount));
                }
            }
            if(received) {
                String reference=String.format("SEED-REC-%03d",i+1);
                long receipt=add("receipts","code",String.format("GRN-2026-%03d",i+1),"order_id",order,"project_id",project,"warehouse_id",warehouseId,"reference",reference,"note","按实到数量验收合格入库","amount",acceptedTotal,"operator_id",warehouse,"created_at",created);
                for(Map<String,Object> line:receiptLines) {
                    long material=((Number)line.get("material_id")).longValue();
                    BigDecimal quantity=(BigDecimal)line.get("quantity"),price=(BigDecimal)line.get("unit_price"),amount=(BigDecimal)line.get("amount");
                    add("receipt_items","receipt_id",receipt,"order_item_id",line.get("order_item_id"),"material_id",material,"quantity",quantity,"unit_price",price,"amount",amount);
                    var existing=db.list("SELECT * FROM inventory WHERE tenant_id=1 AND warehouse_id=? AND material_id=?",warehouseId,material);
                    BigDecimal newQuantity=quantity,newValue=amount;
                    if(!existing.isEmpty()) { newQuantity=newQuantity.add(decimal(existing.get(0).get("quantity"))); newValue=newValue.add(decimal(existing.get(0).get("stock_value"))); }
                    var inventory=row("quantity",newQuantity,"average_price",newValue.divide(newQuantity,6,RoundingMode.HALF_UP),"stock_value",newValue);
                    if(existing.isEmpty()) { inventory.put("warehouse_id",warehouseId); inventory.put("material_id",material); inventory.put("tenant_id",1L); db.insert("inventory",inventory); }
                    else db.update("inventory",((Number)existing.get(0).get("id")).longValue(),inventory);
                    add("stock_movements","warehouse_id",warehouseId,"material_id",material,"project_id",project,"kind","RECEIPT","quantity",quantity,"unit_cost",price,"amount",amount,"reference",reference,"receipt_id",receipt,"operator_id",warehouse,"created_at",created);
                }
                seedIssue(receiptLines,project,warehouseId,warehouse,i,created);
                BigDecimal paid=acceptedTotal.multiply(new BigDecimal(i==0?"1.00":i%2==0?"0.40":"0.65")).setScale(2,RoundingMode.HALF_UP);
                long payable=add("payables","order_id",order,"supplier_id",supplier,"project_id",project,"amount",acceptedTotal,"paid_amount",paid);
                long payment=add("payments","payable_id",payable,"amount",paid,"reference",String.format("SEED-PAY-%03d",i+1),"note","银行转账支付已验收货款","operator_id",finance,"created_at",created);
                add("payment_applications","payable_id",payable,"amount",paid,"status","PAID","note","到货验收后申请付款","applicant_id",buyer,"payment_id",payment,"created_at",created);
                BigDecimal outstanding=acceptedTotal.subtract(paid);
                if(outstanding.signum()>0) add("payment_applications","payable_id",payable,"amount",outstanding.divide(new BigDecimal("2"),2,RoundingMode.HALF_UP),"status","PENDING","note","余款分期申请","applicant_id",buyer);
            }
        }
        for(int i=0;i<3;i++) {
            String[] categories={"LABOR","MACHINE","SUBCONTRACT","OTHER"};
            String[] values={"165000.00","48000.00","220000.00","12500.00"};
            // Project 3 demonstrates a real overrun: 1,575,000 expenses + issued materials > 1,450,000 budget.
            String[] installationValues={"600000.00","180000.00","720000.00","75000.00"};
            List<String> descriptions=i==2?List.of("机电安装班组人工费","吊装及调试设备租赁","机电安装专业分包进度款","现场临设及水电费")
                :List.of("现场工人工资","塔吊及挖机租赁","土建专业分包进度款","现场临设及水电费");
            for(int j=0;j<4;j++) add("costs","project_id",projects[i],"category",categories[j],"amount",i==2?money(installationValues[j]):money(values[j]).multiply(BigDecimal.valueOf(i+1)),"description",descriptions.get(j),"incurred_date",today.minusDays(8+j),"operator_id",finance);
        }
        for(int i=0;i<8;i++) {
            long project=projects[i%3]; boolean main=i<3,purchase=i>=5;
            BigDecimal amount=main?decimal(db.one("SELECT contract_amount FROM projects WHERE id=?",project).get("contract_amount")):purchase?decimal(db.one("SELECT total_amount FROM purchase_orders WHERE id=?",orderIds.get(i-5)).get("total_amount")):money("880000.00");
            if(purchase) project=projects[(i-5)%3];
            add("contracts","code",String.format("CT-2026-%03d",i+1),"name",main?"建设工程施工总承包合同":purchase?"建筑材料采购合同":"专业工程分包合同","type",main?"MAIN":purchase?"PURCHASE":"SUBCONTRACT","project_id",project,"supplier_id",main?null:purchase?suppliers[(i-5)%10]:suppliers[i%10],"order_id",purchase?orderIds.get(i-5):null,"amount",amount,
                "signed_date",withinYear(today,60-i*3),"start_date",withinYear(today,55-i*3),"end_date",today.plusDays(180),"status","ACTIVE","payment_plan",encode(List.of(Map.of("stage","验收付款","percentage",100))));
        }
    }
    /** Annual dashboard uses project start_date. Keep demo projects/contracts in the current year even in January. */
    private LocalDate withinYear(LocalDate today,int daysAgo) {
        LocalDate date=today.minusDays(daysAgo),firstDay=today.withDayOfYear(1);
        return date.isBefore(firstDay)?firstDay:date;
    }
    private BigDecimal decimal(Object value) { return new BigDecimal(value.toString()); }
    private void seedIssue(List<Map<String,Object>> receiptLines,long project,long warehouseId,long operator,int sequence,Timestamp created) {
        BigDecimal total=BigDecimal.ZERO; List<Map<String,Object>> movements=new ArrayList<>();
        for(int j=0;j<2;j++) {
            var line=receiptLines.get(j); long material=((Number)line.get("material_id")).longValue();
            var inventory=db.one("SELECT * FROM inventory WHERE tenant_id=1 AND warehouse_id=? AND material_id=?",warehouseId,material);
            BigDecimal quantity=((BigDecimal)line.get("quantity")).multiply(new BigDecimal("0.25")).setScale(3,RoundingMode.HALF_UP);
            BigDecimal unitCost=decimal(inventory.get("average_price"));
            BigDecimal amount=decimal(inventory.get("stock_value")).multiply(quantity).divide(decimal(inventory.get("quantity")),2,RoundingMode.HALF_UP);
            BigDecimal remaining=decimal(inventory.get("quantity")).subtract(quantity),value=decimal(inventory.get("stock_value")).subtract(amount);
            Fields.check(remaining.signum()>=0 && value.signum()>=0,"演示领料超库存");
            db.update("inventory",((Number)inventory.get("id")).longValue(),row("quantity",remaining,"stock_value",value,"average_price",remaining.signum()==0?BigDecimal.ZERO:value.divide(remaining,6,RoundingMode.HALF_UP)));
            total=total.add(amount); movements.add(row("material_id",material,"quantity",quantity,"unit_cost",unitCost,"amount",amount));
        }
        String reference=String.format("SEED-ISS-%03d",sequence+1);
        long issue=add("issues","code",String.format("ISS-2026-%03d",sequence+1),"project_id",project,"warehouse_id",warehouseId,"reference",reference,"note","现场施工班组领料","amount",total,"operator_id",operator,"created_at",created);
        for(var movement:movements) {
            movement.putAll(row("warehouse_id",warehouseId,"project_id",project,"kind","ISSUE","quantity",decimal(movement.get("quantity")).negate(),"reference",reference,"issue_id",issue,"operator_id",operator,"created_at",created,"tenant_id",1L));
            db.insert("stock_movements",movement);
        }
    }
}
