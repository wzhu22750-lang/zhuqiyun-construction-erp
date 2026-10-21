# 建筑行业 ERP 开源研究与派生实现决策

核查日期：2026-10-21（Asia/Shanghai）。本报告通过 GitHub 公开 API、固定提交的原始文件、Maven Central POM、本地源码比对及已有构建日志核查。使用公开匿名请求，未使用凭据，未修改用户全局配置。版本与“最新发布”均为该次查询快照，交付应以提交 SHA 固定来源。

## 1. 结论与实际集成边界

本项目采用 **RuoYi-Vue-Pro 框架派生精简实现**：在 `vendor/ruoyi` 保留完整 `yudao-common` 源码、其 POM、上游 `yudao-dependencies` BOM、必要父 POM 和 MIT LICENSE；裁剪父 POM 的模块列表；后端通过 Maven 实际依赖 `cn.iocoder.boot:yudao-common:2026.09-SNAPSHOT`，使用上游 CommonResult 和异常类型。平台适配使用本项目 Spring Security 实现，建筑业务放入独立 construction 业务边界。

本项目没有整包移植上游 `yudao-server`、system、infra、官方 security starter、BPM、ERP 或官方前端。保留 common 中的 system/infra 通用 API 接口不代表集成这些模块的服务实现。ERPNext 与 jshERP 仅作为业务流程、单据关系和边界设计参考；本次研究没有将其应用代码、DocType JSON、SQL、UI 或图片复制进产品。

选择裁剪是为了匹配建筑单体 Demo 的功能范围、部署和维护需求。原平台覆盖广泛，system/infra 与 Redis、租户、权限、任务、消息、存储等组件存在依赖关系；整包移植会增加当前 Demo 不需要的平台功能和配置成本。**这属于技术范围决策，不是上游无法编译或依赖版本不存在。**

## 2. 真实版本与来源快照

| 项目 | 研究基线 | 文件声明/发布信息 | 许可与本次用途 |
| --- | --- | --- | --- |
| RuoYi-Vue-Pro | `master-jdk17`，`7c397bbfae3e768b26e3ee962c5aa7064e8d7c96`，提交时间 2026-10-09 12:33:14 +08:00 | revision `2026.09-SNAPSHOT`；Java 17；Spring Boot `3.5.15`；Lombok `1.18.48`；MapStruct `1.7.0.Beta2` | MIT；common/BOM 实际保留，平台及建筑业务自行适配 |
| ERPNext | release `v16.51.1`，`5af6561f5aa48104559692fb4fd82f2580e8d338` | latest-release API 本次返回该 tag，发布时间 `2026-10-15T17:09:25Z`；`erpnext/__init__.py` 为 `16.51.1` | 根 `license.txt` 为 GPL v3；仅业务参考 |
| jshERP | `master`，`fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0` | 后端 POM `3.6-SNAPSHOT`；前端 package `3.5.0`；二者不能合称一个正式发布版 | 根 LICENSE 为 Apache-2.0；仅业务参考 |

补充版本区分：ERPNext 默认分支是 develop，查询时 head 为 `4c9a193549a09b19a546271cc33d2f34c463321f`，本报告的具体业务文件取稳定 release SHA。jshERP latest-release API 返回 `v3.5`（2024-11-11 发布），其 annotated tag 对象 `bfd14a13d887312f0fc8f8e577c28fe5e269cc80` 解引用后 commit 为 `7ca0f9d68fef761d98b00854d09e975eba291465`；不能把 master 的文件版本归到该 release。

## 3. RuoYi 架构与最小依赖可行性

### 3.1 原默认完整底座

该提交采用 Java 17、Spring Boot 3、多模块 Maven 架构。根 POM 默认启用 `yudao-dependencies`、`yudao-framework`、`yudao-server`、`yudao-module-system`、`yudao-module-infra`。BPM、ERP、CRM、AI 等模块在根 POM 中被注释，是可选业务能力，不能描述为默认全部启用。

framework 默认聚合 common 及 MyBatis、Redis、Web、Security、WebSocket、monitor、protection、job、mq、excel、test、tenant、data-permission、IP 等 starter。server 依赖 system、infra 与相关基础组件；system/infra 又组合租户、权限、Redis、任务、消息、文件等能力。`yudao-module-erp` 也依赖 system 和若干框架 starter，因此不能简单把其业务目录复制出来就视为独立系统。

这些依赖说明完整平台的运行配置和集成面较大，不表示每一种消息队列、云服务都必须启动。具体服务要求由启用模块、配置与运行验证确定。

### 3.2 本项目保留内容与源码核对

2026-10-21 对本地只读上游与项目 vendor 逐文件 SHA-256 比对：`yudao-common/src` 的 78 个文件（73 个主源码 Java 文件、5 个测试 Java 文件）全部一致；common POM、BOM POM 和 LICENSE 也均字节一致。两个聚合父 POM 的 module 列表已裁剪。源码保留范围可查 `vendor/ruoyi/UPSTREAM.md`。

- `vendor/ruoyi/pom.xml` 仅聚合 `yudao-dependencies`、`yudao-framework`。
- `vendor/ruoyi/yudao-framework/pom.xml` 仅聚合 `yudao-common`。
- 后端 POM 使用 Boot `3.5.15`、Java 17、MyBatis Plus Boot 3 starter `3.5.17`，实际依赖上述 common artifact。
- `platform/SecurityConfig.java`、`platform/AuthController.java`、`procurement/ProcurementController.java` 已引用 CommonResult；`platform/ApiErrors.java` 已引用并处理 ServiceException。
- ErrorCode 随完整上游 common 保留，也是其返回与异常契约的一部分；本次核查不把“保留该类”扩大成所有建筑业务都已经直接使用 ErrorCode。

完整 common 不只是三个无依赖 DTO。其 POM 包含 Spring context/expression、Jackson、validation、Hutool、Guava、OpenTelemetry、Lombok、MapStruct 等依赖。保留完整源码可维护来源一致性，但仍需按实际依赖树核查交付包；BOM 中列出一个 artifact 不表示该 artifact 已进入后端运行包。

### 3.3 与上游默认底座的差异

| 方面 | 上游默认完整底座 | 本项目派生精简实现 |
| --- | --- | --- |
| 启动入口 | `yudao-server` | 本项目 `backend` 的 ConstructionApplication |
| 公共契约与依赖管理 | common、上游 BOM 与完整 starter 体系 | 完整 common 和 BOM 原样保留；父 reactor 缩小；后端另用 Boot parent 管理应用依赖 |
| 用户、角色、菜单、安全 | system 与官方 security/tenant/data-permission 等组件 | 本项目 Spring Security 平台适配；权限、菜单、项目范围按建筑业务实现，行为需独立验证 |
| 平台运维功能 | infra 及其文件、任务、监控等配置能力 | 按 Demo 必需功能实现；未整体迁入 infra |
| Redis/中间件 | 原 system/infra 的配置与 starter 体系涉及 Redis 等服务 | 不整包继承该部署拓扑；本地应用配置及实测决定实际运行要求 |
| 建筑业务 | 可选 ERP/BPM 等通用业务模块 | 独立 construction 业务边界；不是上游 ERP/BPM 的整包移植 |
| 审批流程 | 可选 BPM 模块及工作流引擎集成 | 不能声称已集成 BPM；本地单据审批需按本项目实现验收 |
| 前端 | 上游独立维护的管理端生态 | 本项目前端；没有因保留 common 自动获得官方 UI |
| 验证结论 | 上游 reactor package 成功 | vendor common install 成功；产品启动与业务验收由后续实际证据确定 |

最小依赖结论：保留 common＋BOM、裁剪聚合 POM 的方案已有源码一致性与 install 成功证据，具备本任务所需的构建可行性。它不是完整 system/infra 的“无损开关式裁剪”；移除的平台能力由本项目适配实现，特别是认证、授权、项目数据范围和审计，不能因共用 CommonResult 就宣称继承了上游全部安全能力。

### 3.4 依赖版本实查与旧 tag 备选

Maven Central 对以下精确坐标的 POM 均返回 HTTP 200，并读取确认 artifact/version：

| 坐标 | 核实版本 |
| --- | --- |
| `org.springframework.boot:spring-boot-dependencies` | `3.5.15` |
| `org.projectlombok:lombok` | `1.18.48` |
| `org.mapstruct:mapstruct`、`mapstruct-processor` | `1.7.0.Beta2` |
| `com.baomidou:mybatis-plus-spring-boot3-starter` | `3.5.17` |

Boot `3.5.15` 和 MyBatis Plus `3.5.17` 是不同 artifact 的版本。MapStruct 名称含 Beta，不妨碍已发布 artifact 的存在；本报告也不据 HTTP 200 声称整个组合已经完成安全或兼容性验收。

曾核查稳定发布 tag 作为备选：准确名称为 **`v2.4.0(jdk17/21)`**，提交 `46c825cb6f1efe399d339144749ed5f0c95b0314`；裸 `v2.4.0` ref 查询返回 404。该树为 Java 17、Boot `3.4.1`、Lombok `1.18.36`、MapStruct `1.6.3`，对应依赖 POM 可取得，但标签内 revision 仍为 `2.4.0-SNAPSHOT`。当时 system/infra 采用 api/biz 拆分，不能套用到当前树。最终基线是当前 master-jdk17 的固定 SHA，不因不存在的“依赖未发布障碍”回退旧 tag。

### 3.5 已有构建证据与未覆盖事项

| 证据 | 观察结果 | 能证明与不能证明 |
| --- | --- | --- |
| `/tmp/zqy-work/research/ruoyi-build.log` | 上游 `mvn package -pl yudao-server -am` reactor；3677 行生成 server jar，3679 行 Boot 3.5.15 repackage，3838 行 BUILD SUCCESS；完成于 `2026-10-21T00:18:56+08:00` | 证明该次上游编译打包成功；日志多处明确 Tests are skipped，不能称测试通过或启动成功 |
| `/tmp/zqy-work/vendor-build.log` | common、BOM 与父 POM install；330–331 行安装 common POM/JAR，340 行 BUILD SUCCESS；完成于 `2026-10-21T00:21:56+08:00` | 证明该次精简 vendor artifact 本地安装成功；324 行 Tests are skipped，不能称测试通过 |
| 项目源码与 POM 静态核查 | 实际依赖 common、实际 import、Spring Security 适配类存在 | 证明接入事实；不能证明产品服务已启动或业务闭环已验收 |

本报告不宣称产品已通过运行。完整上游启动、Redis/数据库初始化、登录权限、项目隔离、审批与库存资金业务验收另作验证。上述日志是本次环境的临时证据路径，正式交付验收应另留存对应构建/启动记录，不能用临时路径代替客户验收结果。

## 4. ERPNext：业务流程参考

ERPNext 基于 Frappe，采用 Python/JavaScript、DocType 元数据与数据库/权限/REST 框架构建企业应用。该固定版本 `pyproject.toml` 要求 Python `>=3.14`、Frappe `>=16.50.0,<17.0.0`，不可按旧教程假设其环境版本。

本次实际读取以下固定 SHA 的 DocType JSON，仅用于理解业务边界：

| 模块/源文件 | 可参考内容 | 建筑 Demo 的独立实现方向 |
| --- | --- | --- |
| Projects / Project | 项目状态、成本、销售/开票汇总、成本中心 | 项目台账与项目维度成本汇总 |
| Buying / Purchase Order | 供应商、项目、仓库、订单状态 | 项目采购订单与明细 |
| Stock / Purchase Receipt | 收货仓、拒收仓、供应商、项目、状态 | 采购收货、验收和退货边界 |
| Stock / Stock Entry | 调拨/领料用途、出入仓、项目、成本中心 | 项目材料领用、退库和库存流水 |
| Accounts / Payment Entry | 付款账户、金额、核销引用、未分配金额 | 收付款单与原单关联、余额计算 |
| Accounts / Budget | 项目/成本中心预算、超预算处理策略 | 预算预警与审批约束 |

还可研究 Assets、Subcontracting 等领域，但不据模块名称宣称具备建筑专用分包结算或中国会计合规。本次未运行 ERPNext，未集成其会计、项目或库存引擎。

ERPNext 根许可证为 GPL v3。GPL 允许商业使用，并非“禁止商业 Demo”；若复制并分发受其覆盖的应用代码或派生作品，应按具体分发方式履行许可证及对应源码等要求。本项目选择仅业务参考、独立实现，不把 ERPNext 应用源码或 DocType JSON 纳入交付。Frappe 框架与 ERPNext 应用是不同项目，不能拿框架许可替代应用许可。

## 5. jshERP：单据与库存参考

核查 master POM/前端 package：Java 8、Spring Boot `2.0.0.RELEASE`，MyBatis、Redis，Vue `^2.7.16`、Ant Design Vue `1.5.2`，前后端分离。该技术栈与当前 Java 17/Boot 3 项目有明显版本差异，不作为本项目直接替换的底座。

实际读取 `DepotHeadService.java`、`AccountHeadService.java`，并核对 DepotItem、Material、UserBusiness 服务路径。可参考采购/入出库的单据头与明细、收付款关联、审核与反审核、库存和资金事务边界；本项目需独立实现并验证自己的状态机、并发约束和权限，不能把阅读源码写成集成或运行通过。

根 LICENSE 为 Apache-2.0。但该固定 POM 明确依赖 `com.itextpdf:itextpdf:5.5.13.1`，其 Maven Central POM 的 licenses 声明为 GNU Affero General Public License v3。**不能仅凭 jshERP 根 Apache LICENSE 推断全部传递依赖都允许同样的闭源交付方式。** 本次没有因研究引入 jshERP/iText；将来若整包复用，应独立处理该依赖许可或另选合适实现/授权。

对该固定提交的 recursive tree 查询 `truncated=false`，未发现 NOTICE 路径；此结论只覆盖该快照，不替代将来版本和依赖复核。

## 6. 交付许可处理

- **实际 RuoYi 源码：MIT。** 原文版权为 `Copyright (c) 2021 ruoyi-vue-pro`。保留上游 `vendor/ruoyi/LICENSE`，本次另原样归档到 `docs/licenses/RuoYi-Vue-Pro-MIT-LICENSE.txt`，并由根 THIRD_PARTY_NOTICES 指向。分发副本或软件的重要部分时附带版权与许可声明，不能以改品牌、精简模块为由删除。
- **ERPNext：GPL 业务参考。** 许可证副本仅为研究存档，不表示产品含 GPL 应用代码。若后来复制代码、schema 或 UI，应重新评估范围及分发义务，不能继续沿用“仅参考”的表述。
- **jshERP：Apache 业务参考。** 将来若分发相关代码，须附 Apache-2.0、保留适用版权/专利/商标/归属声明、显著说明修改；上游含 NOTICE 时按要求保留相关 NOTICE 内容。许可含明确的专利授权及专利诉讼终止规则，不授予项目商标使用权，也不当然要求将独立自有代码全部开源。其依赖另按各自许可证核对。
- 上述三项目通知不等于完整 SBOM 或全部依赖许可审核。真正进入应用包的 Spring、数据库驱动、common 的传递依赖、前端库等，仍需按最终依赖树和分发包生成相应清单及声明。BOM 的版本目录与实际分发组件要区分。

## 7. 可复核的一手来源

以下原始文件 URL 固定至本文研究 SHA；API 的 latest/branch 是查询入口，不作为将来交付的可变版本锁。

### RuoYi

- [固定提交 API](https://api.github.com/repos/YunaiV/ruoyi-vue-pro/commits/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96)、[branches](https://api.github.com/repos/YunaiV/ruoyi-vue-pro/branches?per_page=100)、[tags](https://api.github.com/repos/YunaiV/ruoyi-vue-pro/tags?per_page=100)。
- [根 POM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/pom.xml)、[BOM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/yudao-dependencies/pom.xml)、[framework POM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/yudao-framework/pom.xml)、[common POM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/yudao-framework/yudao-common/pom.xml)。
- [server POM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/yudao-server/pom.xml)、[system POM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/yudao-module-system/pom.xml)、[infra POM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/yudao-module-infra/pom.xml)、[ERP POM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/yudao-module-erp/pom.xml)。
- [CommonResult 源码](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/pojo/CommonResult.java)、[MIT LICENSE](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/LICENSE)。
- [v2.4.0 JDK17/21 树 POM](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/46c825cb6f1efe399d339144749ed5f0c95b0314/pom.xml)。

### ERPNext

- [release 查询 API](https://api.github.com/repos/frappe/erpnext/releases/latest)、[固定 release tag ref](https://api.github.com/repos/frappe/erpnext/git/ref/tags/v16.51.1)。
- [README](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/README.md)、[pyproject](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/pyproject.toml)、[版本文件](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/erpnext/__init__.py)、[GPL license.txt](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/license.txt)。
- [Project](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/erpnext/projects/doctype/project/project.json)、[Purchase Order](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/erpnext/buying/doctype/purchase_order/purchase_order.json)、[Purchase Receipt](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/erpnext/stock/doctype/purchase_receipt/purchase_receipt.json)。
- [Stock Entry](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/erpnext/stock/doctype/stock_entry/stock_entry.json)、[Payment Entry](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/erpnext/accounts/doctype/payment_entry/payment_entry.json)、[Budget](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/erpnext/accounts/doctype/budget/budget.json)。

### jshERP 与 Maven Central

- [jshERP release API](https://api.github.com/repos/jishenghua/jshERP/releases/latest)、[master ref](https://api.github.com/repos/jishenghua/jshERP/git/ref/heads/master)、[v3.5 tag 解引用](https://api.github.com/repos/jishenghua/jshERP/git/tags/bfd14a13d887312f0fc8f8e577c28fe5e269cc80)、[固定完整树](https://api.github.com/repos/jishenghua/jshERP/git/trees/fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0?recursive=1)。
- [后端 POM](https://raw.githubusercontent.com/jishenghua/jshERP/fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0/jshERP-boot/pom.xml)、[前端 package](https://raw.githubusercontent.com/jishenghua/jshERP/fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0/jshERP-web/package.json)、[Apache LICENSE](https://raw.githubusercontent.com/jishenghua/jshERP/fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0/LICENSE)。
- [DepotHeadService](https://raw.githubusercontent.com/jishenghua/jshERP/fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0/jshERP-boot/src/main/java/com/jsh/erp/service/DepotHeadService.java)、[AccountHeadService](https://raw.githubusercontent.com/jishenghua/jshERP/fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0/jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java)。
- Central：[Boot 3.5.15](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.15/spring-boot-dependencies-3.5.15.pom)、[Lombok 1.18.48](https://repo.maven.apache.org/maven2/org/projectlombok/lombok/1.18.48/lombok-1.18.48.pom)、[MapStruct 1.7.0.Beta2](https://repo.maven.apache.org/maven2/org/mapstruct/mapstruct/1.7.0.Beta2/mapstruct-1.7.0.Beta2.pom)、[MapStruct processor](https://repo.maven.apache.org/maven2/org/mapstruct/mapstruct-processor/1.7.0.Beta2/mapstruct-processor-1.7.0.Beta2.pom)、[MyBatis Plus 3.5.17](https://repo.maven.apache.org/maven2/com/baomidou/mybatis-plus-spring-boot3-starter/3.5.17/mybatis-plus-spring-boot3-starter-3.5.17.pom)、[iText 5.5.13.1](https://repo.maven.apache.org/maven2/com/itextpdf/itextpdf/5.5.13.1/itextpdf-5.5.13.1.pom)。

许可证副本及 SHA-256 见 [licenses/README.md](licenses/README.md)，实际集成通知见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
