package com.zhuqiyun.procurement;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/trace")
public class TraceController {
 private final TraceService service;public TraceController(TraceService service){this.service=service;}
 @GetMapping("/orders/{id}") Object order(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return CommonResult.success(service.order(id,page,size));}
 @GetMapping("/requests/{id}") Object request(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return CommonResult.success(service.request(id,page,size));}
}
