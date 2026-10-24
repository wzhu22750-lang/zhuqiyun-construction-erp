package com.zhuqiyun.inventory;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class InventoryController {
 private final InventoryService service;public InventoryController(InventoryService service){this.service=service;}
 @GetMapping("/inventory") Object inventory(){return CommonResult.success(service.inventory());}
 @GetMapping("/stock-movements") Object movements(){return CommonResult.success(service.movements());}
 @GetMapping("/issue-sources") Object sources(@RequestParam long project_id,@RequestParam long warehouse_id,@RequestParam long material_id){return CommonResult.success(service.sources(project_id,warehouse_id,material_id));}
 @PostMapping("/issues") Object issue(@RequestBody Map<String,Object> body){return CommonResult.success(service.issue(body));}
}
