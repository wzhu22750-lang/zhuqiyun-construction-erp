package com.zhuqiyun.platform;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;import org.springframework.web.bind.annotation.*;import java.util.Map;
@RestController @RequestMapping("/api/records")
public class RecordQueryController {private final RecordQueryService service;public RecordQueryController(RecordQueryService service){this.service=service;}@GetMapping("/{entity}") Object page(@PathVariable String entity,@RequestParam Map<String,String> query){return CommonResult.success(service.page(entity,query,100));}}
