package com.zhuqiyun.project;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService service;
    public ProjectController(ProjectService service) { this.service=service; }
    @GetMapping public CommonResult<?> list() { return CommonResult.success(service.list()); }
    @GetMapping("/{id}") public CommonResult<?> detail(@PathVariable long id) { return CommonResult.success(service.detail(id)); }
    @PostMapping public CommonResult<?> create(@RequestBody Map<String,Object> body) { return CommonResult.success(service.create(body)); }
    @PutMapping("/{id}") public CommonResult<?> update(@PathVariable long id,@RequestBody Map<String,Object> body) { return CommonResult.success(service.update(id,body)); }
    @PostMapping("/{id}/progress") public CommonResult<?> progress(@PathVariable long id,@RequestBody Map<String,Object> body) { return CommonResult.success(service.progress(id,body)); }
    @DeleteMapping("/{id}") public CommonResult<?> delete(@PathVariable long id) { service.delete(id); return CommonResult.success(true); }
}
