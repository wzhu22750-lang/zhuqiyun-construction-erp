# 关联读取与金额权限契约

后端入口权限之外，每个详情和聚合返回 access 标志。无权限的关联区返回空数组且 access 对应值为 false，敏感金额字段不返回；不使用零值伪装为真实业务汇总。直接接口仍执行对应模块权限，项目经理还受项目负责人范围约束。

| 数据 | 读取权限 |
| --- | --- |
| 项目基本信息和进度 | project:read |
| 项目预算、已登记成本及成本明细 | cost:read |
| 合同额、合同信息和约定付款计划 | contract:read |
| 采购申请及其原预估金额 | request:read |
| 供应商询比价记录 | quote:read（独立于申请读取） |
| 采购订单、订单单价、原入库及退货应付冲减 | order:read |
| 公司共享库存数量、材料、仓库 | inventory:read + inventory:company；经理默认不授予公司库存权限 |
| 项目范围内的库存流向、领料与退料 | inventory:read |
| 盘点详情和数量快照 | stocktake:read + inventory:company |
| 盘点创建、编辑、提交、撤回、刷新 | stocktake:write，另需盘点读取，原申请人维护 |
| 盘点审核与调整计价 | stocktake:approve，另需盘点读取，禁止申请人自审 |
| 付款计划维护 | contract:plan + contract:write + finance:read，另需合同读取 |
| 实际库存计价、领料/退料成本、退货计价差额 | cost:read；独立盘点审核授权可读取其盘点计价 |
| 应付余额、实际付款、付款申请及审批、退款 | finance:read |
| 通用操作审计详情 | system:manage；业务追溯按对应模块权限过滤 |

项目列表、bootstrap、详情、供应商聚合、订单详情和驾驶舱使用同一规则。项目经理关联查询不能通过供应商、订单、统计、后续导出绕过项目范围。供应商和材料是公司共享基础档案，不据此授予项目成本、报价或付款数据。

合同额减已登记成本字段名为 registered_cost_balance，需同时具有 contract:read 和 cost:read；它只表示当前记录之差，不是最终利润、完工成本预测或收入确认。历史 estimated_profit 名称取消，前端同步改名。

V6 为已有报价写权限追加 quote:read，为原负责人采购审批角色追加报价读取；不向所有 request:read 用户授予报价。现有测试中的金额核对改由有授权的账号读取，仓库操作角色保持数量操作与服务端业务校验。

默认 buyer 不获得实际付款读取和计划维护权限。默认 warehouse 无项目成本读取，仅数量操作；boss 独立审核盘点可读取盘点计价。新读、写权限不会只根据前端菜单判定，直接请求仍受约束。
