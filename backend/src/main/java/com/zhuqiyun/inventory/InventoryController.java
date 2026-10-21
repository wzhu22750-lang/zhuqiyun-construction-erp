package com.zhuqiyun.inventory;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class InventoryController {
 private final InventoryService service;public InventoryController(InventoryService service){this.service=service;}
 @GetMapping("/inventory") Object inventory(){return CommonResult.success(service.inventory());}
 @GetMapping("/stock-movements") Object movements(){return CommonResult.success(service.movements());}
 @PostMapping("/issues") Object issue(@RequestBody Map<String,Object> body){return CommonResult.success(service.issue(body));}
}
