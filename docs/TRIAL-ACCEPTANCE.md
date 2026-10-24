# 本轮本地验收记录

日期：2026-10-21 至 2026-10-22（上海时间）。源码基线 db6850a。前阶段证据目录 `/tmp/zqy-trial-work`，2026-10-22 续验目录 `/tmp/zqy-continuation-20261022`；临时截图、PDF、测试文件和日志未放入源码交付目录。此记录区分构建、服务/API、并发、SQL 和浏览器，不把某一层通过替代其他层。

## 环境与数据保护

- 原项目 `zqy-acceptance`：18081 / 18082 / 13307，保持运行，未重建、停机或清库。
- 本轮独立 `zqy-trial`：UI 18091 / API 18092 / DB 13317，数据库 zhuqiyun_trial，独立 MySQL 和附件命名卷。
- 从 V6 升级到 V13，旧订单/入库/付款/项目仍存在；随后验收仅新增带 QA 标识的隔离仓库和单据。V1–V5 无 diff，未重写历史迁移。
- 单独创建 zqy_platform_validation_trial_20261021 空 schema 运行可选 MySQL 测试，结束后只删除该临时 schema，不删除试用实例。

## 构建和自动测试

| 验证 | 实际结果 | 主要证据 |
| --- | --- | --- |
| Maven test + package | 70 项通过、1 项可选 MySQL 跳过（总 71） | final-backend-verified.log |
| 可选真实 MySQL 测试独立运行 | 1 项通过、无跳过 | mysql-test.log |
| Vue TypeScript + Vite build | 通过 | final-frontend-verified.log、Compose 构建日志 |
| 原业务 HTTP 验收 | 40 项通过 | test_http.py.delivery.log |
| 原业务并发 | 6 项通过 | test_concurrency.py.delivery.log |
| 采购退料/退货/关闭/补采/退款回归 | 21 项通过，含 5 组并发对 | test_procurement_adjustments.py.delivery.log |
| 新试用功能 HTTP | 27 项通过，含同单并发盘点审核 | test_trial.py.delivery.log |
| SQL 对账扩展 | 43 项 violations 全为 0 | invariants.tsv |

后端重点覆盖：模块读取组合和直接 MockMvc 越权请求、经理跨项目、库存计价隐藏、利润名称纠正、工作台计数和状态转换、明确来源追溯、旧计划兼容和原文保留、计划精度/日期/额度/版本/实际关联、盘点自审/重复审批/快照 ABA/整批回滚/纠正单、导入逐行校验和原子性、Excel 文本类型及打印范围。

新增 HTTP 完整采购样本 QA-28CE7B1B74：申请32、订单24、合同11、隔离仓15、盘点10。先分两批收6，退2，付款50退款10，应付40净实付40，待履行4。随后在同单继续53条累计收货记录，验证时间线和收货列表不因详情50条样本上限丢失早期历史；原始单、应付和库存继续遵守对账。

盘点验证包括两次同时审核只生效一次、领料后退料使数量恢复但 revision 变化仍拒绝旧快照、明确刷新后按移动加权价盘亏、另建纠正单、经理不能访问公司盘点或库存、已生效不可编辑。非法导入未写任何行，重复导入不覆盖，Excel 字符串 `=SUM(1,2)` 保持文本。

## 真实浏览器操作

采用系统 Chrome 的 Playwright 浏览器，不是前端静态截图：

- 桌面 1440×1000：仓库角色工作台；新建盘点、远程搜索仓库/材料、填写数量/原因、保存草稿、提交；切换企业负责人独立审核并生效。
- 手机 Pixel 7 模拟 412×839：财务工作台；合同计划修改金额、新增阶段、上移排序、保存、删除未关联阶段、再次保存；页面宽度检查为 412，无页面横向溢出。
- 同一手机浏览器：仓库角色选择无账面价格的新材料，输入实盘1.111和明确价格2.3456，保存与提交盘点；无页面横向溢出。
- 桌面：材料导入真实 xlsx，逐行校验显示错误，“正式导入”处于禁用状态。
- 桌面：订单 A4 预览显示公司、单号、项目、材料、金额、制单时间、签字；独立 iframe 中无 nav/aside/button，实际通过 Chrome 打印生成 `/tmp/zqy-trial-work/order-print.pdf`。

截图：desktop-workbench.png、desktop-count-draft.png、desktop-count-approved.png、mobile-finance-workbench.png、mobile-plan-saved.png、mobile-count-draft.png、desktop-import-errors.png、desktop-print-preview.png。

浏览器过程中边修改代码边重建曾使旧页面引用已删除的 hash chunk；当时动态加载报错，刷新后恢复，已加一次自动刷新保护避免旧标签页停在空壳。最终浏览器回归使用最新页面重新加载；桌面追溯翻页和打印、手机盘点详情及订单追溯在 412px、360px 下均无页面溢出，采集到的 pageerror/console error 为空。收货、采购退货、报价和审批链接均附具体单据编号；同页 query 变化同步活动页签，返回入口保留原订单追溯地址。未把该历史错误日志删掉或称为始终零错误。

## 重启与最终复查

上一阶段已完成最新镜像构建和四组 HTTP 复验：40 + 6 + 21 + 27 项全部通过。样本 QA-35BBCABC57 为申请46、订单36、合同14、仓26、盘点14。

2026-10-22 续验完成，结果如下。续验仅修改交付文档，保留上一阶段所有源代码和新增迁移。

- 构建身份核对：运行中后端 JAR 的 64 个应用类路径文件（含迁移和资源）与最后一次测试打包的 JAR 全部一致；前端入口与本地最终构建一致。证据为 `build-identity.json`。
- 浏览器在重启前、后均实际点击订单24的追溯链接：收货41、采购退货11、报价46、采购审批53、付款申请26。五个目标均自动选中正确页签、关联列表仅显示对应的一条单据；点击“← 返回关联单据”均回到 `/orders/24?tab=trace`。重启后结果见 `navigation-after-restart.log` 和 `navigation-after-restart-summary.json`，截图在续验目录 `output/playwright/`。
- 412px、360px 下等待响应式布局稳定后，页面 scrollWidth 分别为 412、360；两个浏览器复验均无 pageerror / console error。测试脚本曾在调整视口后立即测量，产生一次布局尚未切换完的误报；确认稳定布局正常后，改为等待布局条件再复验，未据此更改业务页面。
- 仅依次重启 zqy-trial 的 MySQL、后端、前端；MySQL 恢复 healthy，UI HTTP 200，未登录 API 正常返回 401。原 zqy-acceptance 三个容器保持原启动时间，没有停止、重建或清理其卷。服务状态与启动时间见 `restart.json`。
- 重启前、后 39 张表按主键顺序计算内容 SHA-256，共 1772 行，逐表行数及摘要全部相同；其中 flyway_schema_history 的 13 条迁移记录同样一致。3 个附件的路径和 SHA-256 全部相同。此比对排除 users、sessions、audit_logs 三张账号/会话/审计表，因此不对它们声称逐行一致。证据为 `before-restart.json`、`after-restart.json`、`persistence-comparison.json`。比对在下述新增 QA 数据之前完成。
- 重启后重新执行四组 HTTP：test_http.py 40 项、test_concurrency.py 6 项、test_procurement_adjustments.py 21 项、test_trial.py 27 项，共 94 项全部通过。摘要为 `http-summary.json`，完整日志为各脚本同名 `.log`。最新试用样本 QA-46803D49EA：申请60、订单48、合同17、仓37、盘点20；其新增数据不属于重启前后持久化快照。
- 全部 HTTP 脚本完成后再次执行只读 SQL 对账，43 项 violations 全为 0，见续验目录 `invariants.tsv` / `invariants-summary.json`。README 的端口、脚本和测试数量已同步为本轮试用实例与实际结果。

本轮关联跳转复验与重启验收已完成；临时浏览器、日志、截图和验收脚本均留在工作目录，最终交付文档保存在源码目录。

## Excel 导入导出增强验收（2026-10-22）

本次在上一阶段基础上补全四类档案导入、20 类业务导出，统一入口、中文文件名、逐行预览及当前页签筛选。操作步骤见 [Excel 使用说明](EXCEL.md)。证据目录为 `/tmp/zqy-excel-20261022`，未引入新依赖或数据库迁移。

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| Maven test + package | 74 项通过、1 项可选 MySQL 跳过（共 75），ExchangeTest 的 10 项全部通过 | backend-build.log、exchange-tests.log |
| Vue TypeScript + Vite build | 通过 | frontend-build.log |
| 独立试用实例更新 | zqy-trial 构建和启动成功，保留 MySQL 及附件卷 | compose-build.log |
| Excel 专项真实 HTTP | 13 项通过；实际验证 4 类模板 / 导入和 20 类导出 | excel-http.log |
| 原有四组 HTTP 回归 | 40 + 6 + 21 + 27 项全部通过，与 Excel 专项合计 107 项 | http-summary.json、各脚本同名 .log |
| SQL 对账 | 43 项 violations 全为 0 | invariants.tsv |

Excel HTTP 样本 QA-32F409B709：仓库38、供应商15、材料50、项目13。非法仓库批次中合法行与错误行均未写入；合法档案只新增一次，重复导入拒绝。导入编号前导零、公式外观的供应商名称和电话均保持文本；仓库 / 材料导入没有生成期初库存。验证了账号读取 / 写入权限、经理项目范围及仓库角色隐藏计价列。新增导出均成功解析为 xlsx，未发现公式单元格。

浏览器实际操作：下载中文名仓库模板，上传非法文件并确认按钮禁用，上传合法文件、查看中文逐行预览、确认导入，并看到列表自动刷新。再按“浏览器Excel仓库”搜索后导出，解析下载文件确认只有对应的一条新记录。随后实际下载采购申请、合同、成本总览、费用明细、库存、流水、入库、领料、退料、盘点、付款申请、付款记录、供应商退款和应付共 14 类工作簿。已解析下载文件，确认采购申请 APPROVED、付款申请 PENDING 的筛选保留正确。

手机模拟在 412px、360px 上选择新仓库文件并通过预览，页面 scrollWidth 分别为 412、360，弹窗右边界分别为 404、352，未横向溢出。最终两个浏览器流程采集的 pageerror / console error 均为空。证据为 browser-excel-final.log、browser-exports-mobile.log，截图在 output/playwright/，浏览器实际下载的文件以 ui- 开头。首轮自动化脚本把确认按钮定位为“确认操作”，实际按钮是“确认”，导致脚本等待超时；修正选择器后完整复跑通过，保留初始 browser-excel.log。

续验未操作原 zqy-acceptance 容器；本次临时浏览器在检查结束后关闭。所有原源代码和业务数据保留，新增测试单据带 QA 标识。

## 未执行或不宣称的验证

没有 iPhone/Android 实机、真实软键盘、物理打印机、外部企业生产数据、大数据量压力、断电恢复或灾备演练。A4 PDF 为浏览器打印输出，不代表物理打印已测试。没有公网部署、银行调用或自动实际付款。Excel 全量 npm 许可重新下载曾遇到平台包网络超时，前端依赖未变化，保留并校验既有 lock 哈希与已归档许可；后端新增 POI 及传递依赖重新解析和成品 JAR 哈希核对。既有分发许可待办仍见 DEPENDENCIES.md，不将功能验收等同于商业分发合规结论。
