package com.zhuqiyun.procurement;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.zhuqiyun.inventory.InventoryService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class ProcurementController {
 private final ProcurementService service;private final InventoryService inventory;
 public ProcurementController(ProcurementService service,InventoryService inventory){this.service=service;this.inventory=inventory;}
 @GetMapping("/requests") Object requests(){return CommonResult.success(service.requests());}
 @GetMapping("/requests/{id}") Object detail(@PathVariable long id){return CommonResult.success(service.detail(id));}
 @PostMapping("/requests") Object create(@RequestBody Map<String,Object> b){return CommonResult.success(service.save(null,b));}
 @PutMapping("/requests/{id}") Object edit(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.save(id,b));}
 @PostMapping("/requests/{id}/submit") Object submit(@PathVariable long id){return CommonResult.success(service.submit(id));}
 @PostMapping("/requests/{id}/approve") Object approve(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.approve(id,b));}
 @GetMapping("/requests/{id}/quotes") Object quotes(@PathVariable long id){return CommonResult.success(service.quotes(id));}
 @PostMapping("/requests/{id}/quotes") Object quote(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.quote(id,b));}
 @PostMapping("/requests/{id}/orders") Object award(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.award(id,b));}
 @GetMapping("/orders") Object orders(){return CommonResult.success(service.orders());}
 @GetMapping("/orders/{id}") Object order(@PathVariable long id){return CommonResult.success(service.orderDetail(id));}
 @PostMapping("/orders/{id}/receive") Object receive(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(inventory.receive(id,b));}
 @PostMapping("/orders/{id}/cancel") Object cancel(@PathVariable long id,@RequestBody Map<String,Object> b){return CommonResult.success(service.cancel(id,b));}
 @GetMapping("/approvals") Object pending(){return CommonResult.success(service.pending());}
 @GetMapping("/approval-records") Object records(){return CommonResult.success(service.approvals());}
}
