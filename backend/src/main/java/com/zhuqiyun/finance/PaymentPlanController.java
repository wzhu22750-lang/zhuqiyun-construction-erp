package com.zhuqiyun.finance;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/contracts/{id}")
public class PaymentPlanController {
 private final PaymentPlanService service;public PaymentPlanController(PaymentPlanService service){this.service=service;}
 @GetMapping("/payment-plan") Object view(@PathVariable long id){return CommonResult.success(service.view(id));}
 @PutMapping("/payment-plan") Object save(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.save(id,body));}
 @PostMapping("/payment-allocations") Object allocate(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.allocate(id,body));}
 @PostMapping("/payment-allocations/{allocation}/void") Object cancel(@PathVariable long id,@PathVariable long allocation,@RequestBody Map<String,Object> body){return CommonResult.success(service.voidAllocation(id,allocation,body));}
}
