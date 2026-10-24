package com.zhuqiyun.inventory;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/stocktakes")
public class StocktakeController {
 private final StocktakeService service;public StocktakeController(StocktakeService service){this.service=service;}
 @GetMapping Object page(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String q,@RequestParam(required=false) String status,@RequestParam(required=false) Long warehouse_id){return CommonResult.success(service.page(page,size,q,status,warehouse_id));}
 @GetMapping("/snapshot") Object snapshot(@RequestParam long warehouse_id,@RequestParam long material_id){return CommonResult.success(service.snapshot(warehouse_id,material_id));}
 @GetMapping("/{id}") Object detail(@PathVariable long id){return CommonResult.success(service.detail(id));}
 @PostMapping Object create(@RequestBody Map<String,Object> body){return CommonResult.success(service.save(null,body));}
 @PutMapping("/{id}") Object save(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.save(id,body));}
 @PostMapping("/{id}/submit") Object submit(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.submit(id,body));}
 @PostMapping("/{id}/withdraw") Object withdraw(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.withdraw(id,body));}
 @PostMapping("/{id}/refresh") Object refresh(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.refresh(id,body));}
 @PostMapping("/{id}/review") Object review(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.review(id,body));}
}
