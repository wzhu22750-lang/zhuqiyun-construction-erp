# 筑企云 Construction ERP

公开源码：[wzhu22750-lang/zhuqiyun-construction-erp](https://github.com/wzhu22750-lang/zhuqiyun-construction-erp)。

面向小型建筑企业、可在本地 Docker Compose 试用的单公司 ERP：工程项目 → 材料需求 → 审批 → 供应商比价 → 采购订单 → 部分验收入库 → 项目领料 → 成本与应付付款。所有业务数据来自 Spring Boot API 和 MySQL，界面没有静态业务数据替代接口。

这是基于 RuoYi-Vue-Pro 公共框架派生的模块化单体。保留完整 `yudao-common` 和上游 BOM，实际使用 `CommonResult` / `ServiceException`；用户权限、建筑领域、审批和 Vue 界面为本项目实现。**未整包集成上游 system、BPM 或 ERP 模块**。选择理由、固定提交和许可证见 [研究记录](docs/research.md)。

本轮已增加角色工作台、按权限的全链路追溯、可编辑合同付款计划、独立审核库存盘点、基础档案 Excel 导入、授权导出及 A4 打印。Excel 支持四类档案导入与 20 类业务导出，操作入口和填写规则见 [Excel 使用说明](docs/EXCEL.md)。业务口径、权限、V6–V13 迁移及复验方法见 [试用增强交付](docs/TRIAL-UPGRADE.md)，实测结果见 [本轮验收](docs/TRIAL-ACCEPTANCE.md)。

## 快速启动

需要 Docker Engine / Docker Desktop（含 Compose、BuildKit）和 Python 3。首次构建需要联网下载镜像及 Maven/npm 依赖。

```sh
cp .env.example .env
./scripts/start-compose.sh
```

默认访问 `http://127.0.0.1:8080`，后端 `http://127.0.0.1:18080/api`，MySQL 绑定本机 `13306`。端口已占用时在 `.env` 中调整 `UI_PORT`、`API_PORT`、`DB_PORT`，不要停止其他项目的服务。

原验收实例仍保留在 `http://127.0.0.1:18081`。本轮独立试用增强实例为 **http://127.0.0.1:18091**（API 18092 / MySQL 13317，Compose 项目 zqy-trial），不共用原实例数据。原实例命令：

```sh
UI_PORT=18081 API_PORT=18082 DB_PORT=13307 COMPOSE_PROJECT_NAME=zqy-acceptance \
  ./scripts/start-compose.sh up -d --build
```

脚本将固定范围内的源码复制到任务专用 ASCII 临时目录，解决中文源路径下 BuildKit 会话标识兼容问题；原项目 `.env` 显式传给 Compose，不复制进镜像。卷按 `COMPOSE_PROJECT_NAME` 保留。管理同一实例时始终使用相同配置：

```sh
./scripts/start-compose.sh ps
./scripts/start-compose.sh logs --tail=80 backend
./scripts/start-compose.sh stop
./scripts/start-compose.sh up -d
```

容器运行与应用就绪有短暂间隔；后端日志出现 `Started ConstructionApplication` 后登录。初始化由 Flyway V1–V13 和事务性 `DemoSeed` 自动完成，无需手工导入 SQL。公司表非空时跳过种子，重启不会覆盖业务数据。数据库与合同附件分别保存在命名卷中。不要用 `down -v` 作为日常停止方式。

## 手机浏览器

已适配手机导航、卡片列表、采购与仓库表单、详情和付款审批。手机与电脑连接同一 Wi-Fi 后，可按 [手机访问与验收说明](docs/MOBILE.md) 开放网页端并访问；桌面继续使用原有表格工作区。

## 演示账号

首次空库初始化的密码统一为 **`Zhuqi2026!`**。可在首次启动前通过 `DEMO_PASSWORD` 配置；修改环境变量不会重置已有账号密码。

| 账号 | 角色 | 主要权限 |
| --- | --- | --- |
| admin | 系统管理员 | 全部功能；账号、角色菜单、公司、字典与审计 |
| boss | 企业负责人 | 经营、项目、采购及付款审批、合同、成本及应付查询 |
| manager | 项目经理 | 所负责项目及关联单据、材料需求、其他费用 |
| buyer | 采购专员 | 供应商与材料维护、询价比价、下单及采购跟踪 |
| warehouse | 仓库管理员 | 验收入库、库存流水、项目领料与退料 |
| finance | 财务专员 | 合同、费用、应付、付款申请与已批准付款执行 |

公司为华建工程建设有限公司。种子包含 3 个项目、10 家供应商、40 种材料、15 条采购申请、10 张订单、8 份合同及关联报价、收货、领料、审批和付款。项目经理负责滨江商务中心、城南住宅二期；科技产业园项目用于验证范围限制，并有真实费用构成的超预算预警。验收脚本会额外创建带 `QA-` 前缀的测试单据。

完整现场演示步骤与可核对金额见 [DEMO.md](docs/DEMO.md)。本轮付款审批规则、迁移说明和最新验收见 [付款工作流](docs/PAYMENT-WORKFLOW.md)。

## 已实现模块

| 模块 | 可操作能力 |
| --- | --- |
| 经营驾驶舱 | 数据库汇总指标、项目预算对比、采购趋势、成本构成、进度、订单状态、待办 |
| 项目管理 | 新建/编辑/删除未关联项目、详情 Tab、进度、预算、关联采购合同、成本与付款 |
| 采购与审批 | 多材料草稿、提交、驳回修改重提、审批意见与历史、自审拦截 |
| 询价与订单 | 多供应商报价、总价/交期对比、中标原因、整单下单、状态与部分到货 |
| 材料与仓库 | 供应商/材料/仓库维护、安全库存、验收入库、领料、加权计价、项目退料、库存流水 |
| 合同与财务 | 主合同/采购/分包合同、付款计划、认证附件、实际入库应付、部分付款、独立审批、撤回、供应商退款与操作记录 |
| 平台配置 | 登录会话、6 角色、菜单/按钮权限、经理项目范围、公司主题、数据字典、审计 |

新增的订单关闭、补采、原仓退料、采购退货、供应商退款和单据追溯见 [采购与 ERP 升级记录](docs/PROCUREMENT-ERP-UPGRADE.md)。

重要约束在服务端实现：未经审批不可下单、同一申请不可重复授予、不可超收或向已取消订单收货、不可负库存、不可超付或重复登记付款。关键操作使用事务、行锁、唯一约束，并记录操作人。

项目材料成本取领料减退料，采购退货计价差额单独记录；采购申请、订单、入库、付款分别显示，不重复累计成成本。金额使用 BigDecimal / DECIMAL；前端只格式化展示。计价和扩展设计见 [架构说明](docs/ARCHITECTURE.md)。

## 本地二次开发

需要 Java 17+（本次使用 JDK 21）、Node 22+、Python 3、Docker。脚本下载并校验 Maven 3.9.9，安装 vendor 公共模块、构建后端、执行 `npm ci` 并启动 Vite：

```sh
./scripts/start-local.sh
./scripts/stop-local.sh
```

默认本地界面 `http://127.0.0.1:5173`、API `18080`、数据库 `13306`；通过 `.env` 或环境配置更改端口。脚本拒绝占用中的端口，停止时只处理它登记且身份匹配的进程/数据库容器，保留数据。已有数据库可配置 `LOCAL_USE_EXISTING_DB=true` 和 `DB_URL/DB_USER/DB_PASSWORD`。当前完整启动验收以 Compose 为准，本地脚本已通过语法/配置检查，开发服务另以 Java/Vite 实际运行验证。

```sh
mvn -B -f vendor/ruoyi/pom.xml -DskipTests install
mvn -B -f backend/pom.xml test package
npm ci --prefix frontend
npm run build --prefix frontend
API_BASE=http://127.0.0.1:18092/api python3 scripts/test_http.py
API_BASE=http://127.0.0.1:18092/api python3 scripts/test_concurrency.py
API_BASE=http://127.0.0.1:18092/api python3 scripts/test_procurement_adjustments.py
API_BASE=http://127.0.0.1:18092/api python3 scripts/test_trial.py
API_BASE=http://127.0.0.1:18092/api python3 scripts/test_excel.py
```

上述 HTTP/并发命令使用独立试用实例 zqy-trial，会持久化带 QA 标识的测试单据，请在演示或专用测试数据库运行。只读对账脚本是 [check_invariants.sql](scripts/check_invariants.sql)，43 项 `violations` 均应为 0。普通后端构建共 75 项测试，其中 74 项通过、1 项真实 MySQL 测试需显式配置临时测试库，默认跳过；本轮原四组 HTTP 回归 94 项与 Excel 专项 13 项，合计 107 项通过。最新执行结果、关联跳转与重启持久化证据见 [本轮验收](docs/TRIAL-ACCEPTANCE.md)，原始演示步骤见 [DEMO.md](docs/DEMO.md)。

## 源码与交付文档

- `backend/`：平台、project、procurement、inventory、finance 领域，Flyway 迁移与事务性演示种子。
- `frontend/`：Vue 3 / TypeScript / Element Plus / Pinia / Axios / ECharts，14 个导航入口及项目/申请/订单详情。
- `vendor/ruoyi/`：固定上游提交的公共源码、BOM、许可及裁剪说明。
- `compose.yml`、`deploy/`、`scripts/`：镜像、启动、验收及只读一致性检查。
- [接口契约](docs/API-CONTRACT.md) · [模块和架构](docs/ARCHITECTURE.md) · [业务演示和验收](docs/DEMO.md) · [实施记录](docs/PLAN.md)。
- [第三方声明](THIRD_PARTY_NOTICES.md) · [依赖许可证清单](docs/DEPENDENCIES.md) · [原始许可文本](docs/licenses/README.md)。

## 功能边界

当前是面向小型企业本地试用的单公司业务系统，**不是已完成生产上线验收的商用 SaaS**。`tenant_id=1` 为扩展点，未实现跨租户隔离、开户和计费。每个账号一个角色；项目经理受项目负责人范围约束，其他默认角色按公司访问。菜单/权限可配置，尚无复杂组织部门和多级数据范围。

审批为采购专用状态机；询价后整单授予一个供应商，一条申请对应一张订单，取消或关闭后可按未履行数量生成一次补采草稿，重新走审批。验收与入库为一次原子动作，已支持原仓项目退料、采购退货、计价差额和实际退款登记；未实现独立质检、税票红冲、拆单采购及跨仓调拨。付款申请已实现独立单级审批：财务提交、负责人审批、财务执行；申请人不可自审，审批人不可自行执行。待审批和已批准申请占用余额，驳回或撤回后释放。直接记账需要独立的 `finance:record` 权限，默认仅管理员具备。尚无多级审批和银行支付。合同付款统计来自关联采购订单；工程回款和分包付款没有独立核算，付款计划为结构化业务记录，未实现自动分期执行。

未实现总账、税务发票、银行接口、电子签章、企业微信、BIM、原生 App/小程序和高级报表；企业 Logo 上传尚未实现，公司名称、系统名和主题可配置。Redis 当前不需要。TLS、备份恢复演练、完整安全审计、监控告警和灾备需在具体交付项目中完成。前端图表和组件包有构建体积提醒，功能构建通过。

上游完整 server reactor 已构建（跳过测试），没有启动完整上游平台。本项目自己的前后端、数据库与业务链路已单独验收，详细证据见 DEMO 文档。
