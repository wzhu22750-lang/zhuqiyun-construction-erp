package com.zhuqiyun.project;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("projects")
public class Project {
    @TableId private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String owner;
    private String contractor;
    private Long managerId;
    private String location;
    private String type;
    private BigDecimal contractAmount;
    private BigDecimal budgetAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal progress;
    private String status;
    private LocalDateTime createdAt;
}
