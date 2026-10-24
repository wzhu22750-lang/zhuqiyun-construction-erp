package com.zhuqiyun.exchange;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;import org.springframework.http.*;import java.util.*;import java.io.IOException;
@RestController @RequestMapping("/api/exchange")
public class ExchangeController {
 private final ImportService imports;private final ExportService exports;public ExchangeController(ImportService imports,ExportService exports){this.imports=imports;this.exports=exports;}
 private ResponseEntity<byte[]> workbook(byte[] bytes,String name){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+".xlsx\"").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);}
 @GetMapping("/template/{entity}") Object template(@PathVariable String entity){return workbook(imports.template(entity),entity+"-template");}
 @PostMapping("/preview/{entity}") Object preview(@PathVariable String entity,@RequestParam MultipartFile file)throws IOException{return CommonResult.success(imports.preview(entity,file.getBytes()));}
 @PostMapping("/import/{entity}") Object commit(@PathVariable String entity,@RequestParam MultipartFile file,@RequestParam String expected_hash)throws IOException{return CommonResult.success(imports.commit(entity,file.getBytes(),expected_hash));}
 @GetMapping("/export/{entity}") Object export(@PathVariable String entity,@RequestParam Map<String,String> filters){return workbook(exports.excel(entity,filters),entity+"-export");}
 @GetMapping(value="/print/{entity}/{id}",produces="text/html;charset=UTF-8") Object print(@PathVariable String entity,@PathVariable long id,@RequestParam Map<String,String> filters){return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/html;charset=UTF-8")).header("Cache-Control","no-store").body(exports.print(entity,id,filters));}
}
