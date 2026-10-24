package com.zhuqiyun;
import com.zhuqiyun.platform.*;import com.zhuqiyun.exchange.*;import org.apache.poi.ss.usermodel.*;import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.web.server.ResponseStatusException;import java.util.*;import java.io.*;import static org.junit.jupiter.api.Assertions.*;import static com.zhuqiyun.domain.Rules.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:exchange;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql,classpath:db/migration/V4__payment_application_approval.sql,classpath:db/migration/V5__procurement_adjustments.sql,classpath:db/migration/V6__scoped_read_permissions.sql,classpath:db/migration/V7__contract_payment_stages.sql,classpath:db/migration/V8__inventory_stocktakes.sql,classpath:db/migration/V9__explicit_issue_sources.sql,classpath:db/migration/V10__payment_plan_authority.sql,classpath:db/migration/V11__company_inventory_authority.sql,classpath:db/migration/V12__stocktake_versions.sql,classpath:db/migration/V13__import_batches.sql","app.seed=true"})
class ExchangeTest {
 @Autowired Db db;@Autowired ImportService imports;@Autowired ExportService exports;@Autowired RecordQueryService query;
 void as(String role){long id=((Number)db.one("SELECT id FROM users WHERE username=?",role).get("id")).longValue();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of()));}
 @BeforeEach void setup(){as("admin");}@AfterEach void clear(){SecurityContextHolder.clearContext();}
 byte[] file(String entity,List<Map<String,String>> rows)throws Exception{try(var book=new XSSFWorkbook(new ByteArrayInputStream(imports.template(entity)));var out=new ByteArrayOutputStream()){var sheet=book.getSheet("导入数据");int index=1;for(var input:rows){var r=sheet.createRow(index++);int col=0;for(String key:ImportService.columns(entity).keySet()){if(input.containsKey(key))r.createCell(col).setCellValue(input.get(key));col++;}}book.write(out);return out.toByteArray();}}
 String hash(String kind,byte[] bytes){return imports.preview(kind,bytes).get("hash").toString();}
 void status(int code,org.junit.jupiter.api.function.Executable action){assertEquals(code,assertThrows(ResponseStatusException.class,action).getStatusCode().value());}
 @Test void invalidBatchDoesNotWriteAnyRowAndDuplicateImportNeverOverwrites()throws Exception{String a=code("IMPORT"),b=code("BAD");byte[] bad=file("materials",List.of(Map.of("code",a,"name","合法行","unit","吨","category","钢材"),Map.of("code",b,"name","错误行","unit","吨","category","钢材","reference_price","1.00001")));var preview=imports.preview("materials",bad);assertEquals(false,preview.get("valid"));status(400,()->imports.commit("materials",bad,preview.get("hash").toString()));assertTrue(db.list("SELECT id FROM materials WHERE code IN (?,?)",a,b).isEmpty());byte[] valid=file("materials",List.of(Map.of("code",a,"name","合法行","unit","吨","category","钢材")));imports.commit("materials",valid,hash("materials",valid));status(400,()->imports.commit("materials",valid,hash("materials",valid)));assertEquals(1,db.list("SELECT id FROM materials WHERE code=?",a).size());}
 @Test void previewHashAndDatabaseRevalidationProtectAgainstChangedFilesAndLateDuplicates()throws Exception{String code=code("LATE");byte[] original=file("suppliers",List.of(Map.of("code",code,"name","新供应商")));String hash=hash("suppliers",original);byte[] other=file("suppliers",List.of(Map.of("code",code("OTHER"),"name","其他")));status(400,()->imports.commit("suppliers",other,hash));db.insert("suppliers",row("code",code,"name","晚到记录"));status(400,()->imports.commit("suppliers",original,hash));assertEquals("晚到记录",db.one("SELECT name FROM suppliers WHERE code=?",code).get("name"));}
 @Test void duplicateNumbersDatesUnitsAndFormulaCellsHaveRowErrors()throws Exception{String code=code("DUP");byte[] bytes=file("suppliers",List.of(Map.of("code",code,"name","甲","qualification_expiry","2026-99-01"),Map.of("code",code,"name","乙")));var p=imports.preview("suppliers",bytes);assertEquals(false,p.get("valid"));assertTrue(((List<Map<String,Object>>)p.get("rows")).stream().allMatch(r->!((List<?>)r.get("errors")).isEmpty()));byte[] material=file("materials",List.of(Map.of("code",code,"name","缺单位","category","钢材")));assertEquals(false,imports.preview("materials",material).get("valid"));try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes));var out=new ByteArrayOutputStream()){book.getSheet("导入数据").getRow(1).getCell(1).setCellFormula("1+1");book.write(out);var result=imports.preview("suppliers",out.toByteArray());assertTrue(((List<?>)((List<Map<String,Object>>)result.get("rows")).get(0).get("errors")).toString().contains("公式"));}}
 @Test void projectImportRespectsManagerAssignmentAndUserExistence()throws Exception{as("manager");var input=new HashMap<String,String>(Map.of("code",code("PRJIMP"),"name","项目","owner","业主","contractor","承建","manager_username","boss","contract_amount","100","budget_amount","80","start_date","2026-01-01","end_date","2026-12-31"));assertEquals(false,imports.preview("projects",file("projects",List.of(input))).get("valid"));input.put("manager_username","manager");byte[] valid=file("projects",List.of(input));assertEquals(true,imports.preview("projects",valid).get("valid"));imports.commit("projects",valid,hash("projects",valid));assertTrue(((List<Map<String,Object>>)query.page("projects",Map.of("q",input.get("code")),100).get("items")).stream().anyMatch(r->input.get("code").equals(r.get("code"))));as("warehouse");status(403,()->imports.template("materials"));}
 @Test void excelKeepsDangerousTextAsTextAndDecimalsAsNumbers()throws Exception{String code=code("TEXT");db.insert("suppliers",row("code",code,"name","=1+1","phone","+8613800000000"));byte[] bytes=exports.excel("suppliers",Map.of("q",code));try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){var row=book.getSheetAt(0).getRow(1);assertEquals(CellType.STRING,row.getCell(1).getCellType());assertEquals("=1+1",row.getCell(1).getStringCellValue());assertEquals(CellType.STRING,row.getCell(3).getCellType());}}
 @Test void paginatedQueriesAndPrintsApplyPermissionAndProjectScope()throws Exception{as("warehouse");status(403,()->query.page("supplier-statement",Map.of(),100));assertNotNull(exports.excel("projects",Map.of()));var quantities=query.page("issues",Map.of("size","1"),100);assertEquals(1,((List<?>)quantities.get("items")).size());Map<String,Object> issue=((List<Map<String,Object>>)quantities.get("items")).get(0);assertFalse(issue.containsKey("amount"));String html=exports.print("issues",((Number)issue.get("id")).longValue(),Map.of());assertTrue(html.contains("签字"));assertFalse(html.contains("原始金额合计"));assertTrue(html.contains("size:A4"));as("manager");assertTrue(((List<?>)query.page("projects",Map.of("size","1"),100).get("items")).size()<=1);var others=db.list("SELECT id FROM projects WHERE manager_id<>(SELECT id FROM users WHERE username='manager')");if(!others.isEmpty()){var result=query.page("orders",Map.of("project_id",others.get(0).get("id").toString()),100);assertEquals(0,((Number)result.get("total")).intValue());}as("finance");assertNotNull(query.page("payment-applications",Map.of("q","验收","size","1"),100));}

 @Test void warehouseImportUsesTextCodesAndAtomicValidation() throws Exception {
  String good=code("WHX"),bad=code("WHBAD");
  byte[] invalid=file("warehouses",List.of(Map.of("code",good,"name","Excel仓库","location","工地一号库"),Map.of("code",bad,"location","缺少名称")));
  var preview=imports.preview("warehouses",invalid);
  assertEquals(false,preview.get("valid"));assertEquals(1L,preview.get("valid_count"));assertEquals(1L,preview.get("error_count"));
  assertTrue(((Map<?,?>)preview.get("columns")).containsKey("location"));
  status(400,()->imports.commit("warehouses",invalid,preview.get("hash").toString()));
  assertTrue(db.list("SELECT id FROM warehouses WHERE code IN (?,?)",good,bad).isEmpty());
  byte[] valid=file("warehouses",List.of(Map.of("code",good,"name","Excel仓库","location","工地一号库")));
  assertEquals(1,imports.commit("warehouses",valid,hash("warehouses",valid)).get("created"));
  assertEquals("工地一号库",db.one("SELECT location FROM warehouses WHERE code=?",good).get("location"));
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(imports.template("warehouses")))) {assertEquals("@",book.getSheet("导入数据").getColumnStyle(0).getDataFormatString());}
  as("warehouse");status(403,()->imports.template("warehouses"));assertNotNull(exports.excel("warehouses",Map.of("q",good)));
 }
 @Test void expandedExportsUseQueryableColumnsAndNumericBusinessValues() throws Exception {
  for(String entity:List.of("warehouses","requests","contracts","cost-overview","expenses","inventory","stock-movements","issue-returns","stocktakes","payables","payment-applications","payments","supplier-refunds")) {
   try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel(entity,Map.of())))) {
    assertNotNull(book.getSheet("业务数据"));assertNotNull(book.getSheet("口径说明"));
    var h=book.getSheet("业务数据").getRow(0);assertTrue(h.getLastCellNum()>2,entity);
   }
  }
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel("cost-overview",Map.of())))) {
   var row=book.getSheet("业务数据").getRow(1);assertNotNull(row);
   for(int index:List.of(2,3,4,5,6,7))assertEquals(CellType.NUMERIC,row.getCell(index).getCellType());
  }
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel("expenses",Map.of())))) {
   var row=book.getSheet("业务数据").getRow(1);assertEquals(CellType.NUMERIC,row.getCell(3).getCellType());assertTrue(DateUtil.isCellDateFormatted(row.getCell(4)));
  }
 }
 @Test void requestExportsIncludeLinesAndRespectStatusAndProjectScope() throws Exception {
  var request=db.one("SELECT r.*,p.name project_name FROM purchase_requests r JOIN projects p ON p.id=r.project_id ORDER BY r.id LIMIT 1");
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel("requests",Map.of("id",request.get("id").toString()))))) {
   assertEquals(1,book.getSheet("业务数据").getLastRowNum());
   assertEquals(request.get("code"),book.getSheet("单据明细").getRow(1).getCell(0).getStringCellValue());
   assertEquals(CellType.NUMERIC,book.getSheet("单据明细").getRow(1).getCell(5).getCellType());
  }
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel("payment-applications",Map.of("status","PENDING"))))) {
   var sheet=book.getSheet("业务数据");for(int i=1;i<=sheet.getLastRowNum();i++)assertEquals("PENDING",sheet.getRow(i).getCell(6).getStringCellValue());
  }
  as("manager");var other=db.one("SELECT id FROM projects WHERE manager_id<>(SELECT id FROM users WHERE username='manager') LIMIT 1");
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel("requests",Map.of("project_id",other.get("id").toString()))))) {assertEquals(0,book.getSheet("业务数据").getLastRowNum());}
  status(403,()->exports.excel("stocktakes",Map.of()));
 }
 @Test void warehouseExportsHideValuationsAndStocktakeDetailsRequireCompanyAccess() throws Exception {
  long warehouse=((Number)db.one("SELECT id FROM warehouses ORDER BY id LIMIT 1").get("id")).longValue();
  long user=((Number)db.one("SELECT id FROM users WHERE username='admin'").get("id")).longValue();
  long material=((Number)db.one("SELECT id FROM materials ORDER BY id LIMIT 1").get("id")).longValue();
  String stocktakeCode=code("XCOUNT");long id=db.insert("stocktakes",row("code",stocktakeCode,"warehouse_id",warehouse,"applicant_id",user));
  db.insert("stocktake_items",row("stocktake_id",id,"material_id",material,"snapshot_quantity",dec("5"),"snapshot_stock_value",dec("50"),"snapshot_average_price",dec("10"),"snapshot_revision",0,"counted_quantity",dec("6"),"difference_quantity",dec("1"),"reason","重新清点","adjustment_amount",dec("10")));
  as("warehouse");
  for(String entity:List.of("inventory","stock-movements"))try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel(entity,Map.of())))) {
   String headers=book.getSheet("业务数据").getRow(0).toString();assertFalse(headers.contains("均价"));assertFalse(headers.contains("成本"));assertFalse(headers.contains("金额"));
  }
  try(var book=new XSSFWorkbook(new ByteArrayInputStream(exports.excel("stocktakes",Map.of("q",stocktakeCode,"status","DRAFT"))))) {
   assertEquals(1,book.getSheet("业务数据").getLastRowNum());var sheet=book.getSheet("单据明细");assertEquals(1,sheet.getLastRowNum());assertEquals(8,sheet.getRow(0).getLastCellNum());
   assertEquals(6d,sheet.getRow(1).getCell(5).getNumericCellValue());
  }
  status(403,()->exports.excel("expenses",Map.of()));status(403,()->exports.excel("payments",Map.of()));
 }
}
