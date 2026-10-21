package com.zhuqiyun.inventory;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class AdjustmentController {
 private final AdjustmentService service;
 public AdjustmentController(AdjustmentService service){this.service=service;}
 @GetMapping("/issues") Object issues(){return CommonResult.success(service.issues());}
 @GetMapping("/issue-returns") Object returns(){return CommonResult.success(service.issueReturns());}
 @PostMapping("/issues/{id}/returns") Object returnIssue(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.returnIssue(id,body));}
 @GetMapping("/purchase-returns") Object purchaseReturns(){return CommonResult.success(service.purchaseReturns());}
 @PostMapping("/receipts/{id}/returns") Object returnPurchase(@PathVariable long id,@RequestBody Map<String,Object> body){return CommonResult.success(service.returnPurchase(id,body));}
 @GetMapping("/supplier-refunds") Object refunds(){return CommonResult.success(service.refunds());}
 @PostMapping("/supplier-refunds") Object refund(@RequestBody Map<String,Object> body){return CommonResult.success(service.refund(body));}
}
