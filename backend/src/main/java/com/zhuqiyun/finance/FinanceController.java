package com.zhuqiyun.finance;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.zhuqiyun.platform.Auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.zhuqiyun.domain.Rules.*;
@RestController @RequestMapping("/api")
public class FinanceController {
 private final FinanceService service;private final Auth auth;private final Path attachmentDir;
 public FinanceController(FinanceService service,Auth auth,@Value("${app.attachment-dir}") String directory){this.service=service;this.auth=auth;this.attachmentDir=Path.of(directory).toAbsolutePath().normalize();}
 @GetMapping("/costs") Object costs(){return CommonResult.success(service.costs());}
 @PostMapping("/costs") Object cost(@RequestBody Map<String,Object> b){return CommonResult.success(service.cost(b));}
 @GetMapping("/contracts") Object contracts(){return CommonResult.success(service.contracts());}
 @PostMapping("/contracts") Object contract(@RequestBody Map<String,Object> b){return CommonResult.success(service.saveContract(null,b));}
 @PutMapping("/contracts/{id}") Object edit(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.saveContract(id,b));}
 @DeleteMapping("/contracts/{id}") Object delete(@PathVariable long id){service.deleteContract(id);return CommonResult.success(true);}
 @PostMapping("/contracts/{id}/attachment") Object upload(@PathVariable long id,@RequestParam("file") MultipartFile file) throws java.io.IOException {auth.require("contract:write");service.contract(id);check(!file.isEmpty()&&file.getSize()<=10*1024*1024,"附件必须为1字节至10MB");String name=Objects.toString(file.getOriginalFilename(),"合同附件").replaceAll("[\\\\/\\r\\n]","_");check(name.length()<=200,"附件名称过长");String key=UUID.randomUUID().toString();Files.createDirectories(attachmentDir);Path target=attachmentDir.resolve(key);try{file.transferTo(target);service.attachment(id,name,key);}catch(Exception e){Files.deleteIfExists(target);throw e;}return CommonResult.success(row("attachment_name",name));}
 @GetMapping("/contracts/{id}/attachment") ResponseEntity<Resource> download(@PathVariable long id) throws java.io.IOException {auth.require("contract:read");Map<String,Object> c=service.contract(id);String key=Objects.toString(c.get("attachment_key"),"");check(key.matches("[a-f0-9-]{36}"),"此合同暂无附件");Path file=attachmentDir.resolve(key).normalize();check(file.startsWith(attachmentDir)&&Files.isRegularFile(file),"附件文件不存在");return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(c.get("attachment_name").toString(),StandardCharsets.UTF_8).build().toString()).body(new FileSystemResource(file));}
 @GetMapping("/payables") Object payables(){return CommonResult.success(service.payables());}
 @GetMapping("/payments") Object payments(){return CommonResult.success(service.payments());}
 @PostMapping("/payments") Object payment(@RequestBody Map<String,Object> b){return CommonResult.success(service.payment(b));}
 @GetMapping("/payment-applications") Object applications(){return CommonResult.success(service.applications());}
 @PostMapping("/payment-applications") Object apply(@RequestBody Map<String,Object> b){return CommonResult.success(service.apply(b));}
 @GetMapping("/payment-applications/{id}/records") Object applicationRecords(@PathVariable long id){return CommonResult.success(service.applicationRecords(id));}
 @PostMapping("/payment-applications/{id}/review") Object review(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.review(id,b));}
 @PostMapping("/payment-applications/{id}/cancel") Object cancelApplication(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.cancelApplication(id,b));}
 @PostMapping("/payment-applications/{id}/pay") Object execute(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.execute(id,b));}
}
