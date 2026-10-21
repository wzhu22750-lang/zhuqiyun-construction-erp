# 付款申请审批与本轮验收

执行日期：2026-10-21（Asia/Shanghai）。当前本地演示：http://127.0.0.1:18081，API 为 http://127.0.0.1:18082/api。

## 可操作流程

1. finance 登录“应付与付款”，查看验收应付、实付、未付、申请占用、可申请余额，提交付款申请。
2. boss 在同页“付款申请”中筛选待审批，查看供应商、项目、金额、申请说明，批准或填写原因驳回。
3. finance 执行已批准申请，输入唯一付款凭证；服务端按已批准金额登记付款，申请变为已付款。
4. 付款前申请人可撤回本人申请并填写原因；管理员可代撤。驳回/撤回释放占用，原单据和记录保留，重报时新建申请。
5. 每条申请的“记录”显示提交、审批、撤回或付款人员、说明和时间。

默认 boss 具有 `finance:approve`；finance 具有 `finance:write`，负责申请及执行。申请人不可自审，审批人不可自行执行，即使自定义角色同时具有这两个权限也受服务端约束。直接登记付款属于 `finance:record` 单独授权，默认仅 admin 的通配权限可用；这是线下付款的记账入口，不发生银行转账。

待审批和已批准未付款均占用可申请余额。申请与执行同时受实际验收应付余额和关联合同金额约束；付款不计入项目材料成本。金额全部由服务端 BigDecimal 计算。付款申请锁顺序统一为应付 → 申请，事务内按当前记录读取预留，重复或并发执行不重复记账。

合同变更后，原批准金额可能不再可付；执行会拒绝超限，申请人可撤回重报。当前只有单级负责人审批，不包含多级会签、自动支付、银行对账或总账。

## 既有数据库迁移

新增 [V4 迁移](../backend/src/main/resources/db/migration/V4__payment_application_approval.sql)，未修改 V1–V3 校验和。增加审批字段、操作记录表、预留查询索引；已有 boss 角色保留财务读取权限时，追加财务审批权限，其他自定义权限保持原样。角色的菜单仍由管理员配置。

既有 PAID 保持已付款，PENDING 保持待审批并需批准后才能执行；不伪造历史审批及操作人。旧申请操作记录可能为空。迁移前后实际核对：申请 15 条（已付 8、待审批 7），验收应付合计 505,111.99 元、实付 340,011.05 元均未改变。之后验收新增 QA/UI 单据，因此最终总额不再等于迁移时快照。

## 本轮实际验证

| 证据层 | 本轮结果 |
| --- | --- |
| 后端 H2 服务/规则回归 | 21 通过、0 失败；默认跳过 1 个可选 MySQL 测试。BusinessFlowTest 17，PlatformTimeTest 2，DemoFixtureTest 2 |
| 独立 MySQL 测试 | 在任务独立空库 `zqy_platform_validation_payment_20261021` 执行 PlatformMysqlTest，1 通过、0 跳过，Flyway V1–V4 和新种子可运行；测试后删除此临时库 |
| 前端 | TypeScript 检查与 Vite 生产构建通过；保留 Element Plus/ECharts 成品体积提示 |
| Compose | 前后端镜像构建并更新原 `zqy-acceptance` 实例，MySQL 8.0.43 保留原卷；V4 实际增量迁移成功 |
| HTTP | 40 项通过，包括角色权限、未批不可付、独立批准、执行一次、操作记录、驳回/撤回释放，以及原采购库存合同链路 |
| MySQL HTTP 并发 | 6 项通过：收货数量、领料库存、实际付款余额、申请金额预留、重复审批、重复执行。每组两请求仅一项成功，另一项 409 |
| SQL 对账 | 20 项 violations 均为 0，新增预留总额、批准人、自行执行及付款关联约束检查 |
| 浏览器真实操作 | finance 提交 1,000 元 → boss 填写意见并批准 → finance 登记凭证；申请 20 的操作记录正确显示三步人员与时间；普通财务页面没有直接登记付款或审批按钮 |
| 页面与控制台 | 实际 CSS 视口宽 1518px 时 document scrollWidth=clientWidth，无页面横向溢出；本轮控制台 0 错误、0 警告；操作记录截图完成视觉检查 |
| 应用重启 | backend 单独重启前后申请数量/总额、实付/应付、操作记录数、申请 20 的状态/关联付款/审批人完全相同；重启后再次 SQL 对账 20 项全零 |

HTTP 运行标识 `QA-095CA0171A`：申请 18、订单 13，36,000 申请 / 35,000 订单 / 21,000 入库 / 7,000 领料 / 10,000 付款 / 11,000 待付。浏览器随后对该订单另登记 1,000 元，最终该单已付 11,000、未付 10,000；UI 凭证 `UI-PAYMENT-20261021-PAID`。

并发标识 `QA-68B2AAB0FB`：订单 14，入库 60、领料 40、库存值 20。原并发直接记账成功一笔 40；并发申请/审批/执行成功一笔 15，最终已付 55、未付 5。

本轮临时工作目录 `/tmp/zqy-payment-work/` 保存 `backend-test.log`、`mysql-test.log`、`frontend-build.log`、`http.log`、`concurrency.log`、`invariants-final.tsv`、迁移/重启前后快照及页面截图。临时证据不随源码发布，可按以下命令复验；HTTP/并发脚本会新增独立测试单据。

```sh
mvn -B -f backend/pom.xml test package
npm run build --prefix frontend
API_BASE=http://127.0.0.1:18082/api python3 scripts/test_http.py
API_BASE=http://127.0.0.1:18082/api python3 scripts/test_concurrency.py
docker exec -i zqy-acceptance-mysql-1 sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" --batch' \
  < scripts/check_invariants.sql
```

当前仍是单公司 MVP；本轮验证覆盖付款业务增强，不代表生产 TLS、备份灾备、商业许可证和完整安全评估已经完成。其他功能与边界见 [README](../README.md) 和 [架构说明](ARCHITECTURE.md)。
