# THIRD-PARTY NOTICES

本通知按 2026-10-21 的研究与项目文件快照编制，区分实际保留源码与仅作业务参考的项目。当前应用称为 **RuoYi-Vue-Pro 框架派生精简实现**。本文件保留下列三项上游研究与集成归属，并索引实际 Maven 运行依赖、前端 lock 及许可原文清单；清单覆盖边界和分发待办见 [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md)。它不构成产品运行验收或商用合规完成记录。

## 1. 实际集成：RuoYi-Vue-Pro / yudao-common

- 上游：YunaiV/ruoyi-vue-pro，`master-jdk17`。
- 固定提交：`7c397bbfae3e768b26e3ee962c5aa7064e8d7c96`。
- 上游 revision：`2026.09-SNAPSHOT`。
- 许可证：MIT。
- 原始版权声明：**Copyright (c) 2021 ruoyi-vue-pro**。
- 源码与原许可证位置：`vendor/ruoyi/yudao-framework/yudao-common/`、`vendor/ruoyi/yudao-dependencies/pom.xml`、`vendor/ruoyi/LICENSE`。
- 归档许可证全文：[RuoYi-Vue-Pro-MIT-LICENSE.txt](docs/licenses/RuoYi-Vue-Pro-MIT-LICENSE.txt)。
- [固定 SHA 原始 LICENSE](https://raw.githubusercontent.com/YunaiV/ruoyi-vue-pro/7c397bbfae3e768b26e3ee962c5aa7064e8d7c96/LICENSE)。

本项目保留完整 common 主源码和测试源码、common POM、上游 BOM 以及必要父 POM。核查时 common 的 78 个源码文件、其 POM、BOM 和 LICENSE 与该上游逐字节一致。本项目修改两个父 POM 的聚合模块列表：根仅保留 dependencies/framework，framework 仅保留 common；改动归属与来源亦记录在 `vendor/ruoyi/UPSTREAM.md`。本通知显式记录这些派生改动，common 源码未因此裁成三个 DTO。

后端实际 Maven 依赖 `cn.iocoder.boot:yudao-common:2026.09-SNAPSHOT`，控制器和安全配置使用 CommonResult，异常适配引用并处理 ServiceException。ErrorCode 等上游类型随完整模块保留。Spring Security 平台适配与 construction 业务由本项目实现；没有整包移植 upstream system、infra、server、官方 security starter、BPM、ERP 或 UI。保留 common 中的通用 API 接口不代表相应平台服务已集成。

MIT 允许商业使用、修改及分发，但分发软件副本或重要部分时，应保留原版权声明和完整许可声明，包括免责声明。项目品牌和本地新增代码不能取代或删除上述上游版权归属。上游名称在此用于来源说明，不表示上游作者对本产品背书。

## 2. 仅业务参考：ERPNext

- 上游：frappe/erpnext。
- 研究版本：`v16.51.1`，提交 `5af6561f5aa48104559692fb4fd82f2580e8d338`。
- 根应用许可证：GPL v3（上游路径 `license.txt`）。
- 研究存档全文：[ERPNext-GPL-3.0-license.txt](docs/licenses/ERPNext-GPL-3.0-license.txt)。
- [固定 SHA 原始 license.txt](https://raw.githubusercontent.com/frappe/erpnext/5af6561f5aa48104559692fb4fd82f2580e8d338/license.txt)。

本次仅研究项目、采购、收货、库存、收付款、预算的业务流程与边界，没有因本次研究将 ERPNext 应用代码、DocType JSON schema、SQL、UI 或图片纳入产品。归档 GPL 文本不表示项目已集成 ERPNext，也不据此把本项目自有代码声明为 GPL。

GPL 允许商业用途。若后续实际复制或派生并分发受其覆盖的软件，应根据相应许可证和分发情形履行保留声明、许可证及对应源码等义务，届时应更新这里的集成说明。不能以 Frappe 框架的许可替代 ERPNext 应用许可。

## 3. 仅业务参考：jshERP

- 上游：jishenghua/jshERP。
- 研究快照：master 提交 `fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0`。
- 文件版本：后端 `3.6-SNAPSHOT`，前端 `3.5.0`；独立 release `v3.5` 解引用提交为 `7ca0f9d68fef761d98b00854d09e975eba291465`。
- 根许可证：Apache License 2.0。
- 研究存档全文：[jshERP-Apache-2.0-LICENSE.txt](docs/licenses/jshERP-Apache-2.0-LICENSE.txt)。
- [固定 SHA 原始 LICENSE](https://raw.githubusercontent.com/jishenghua/jshERP/fdc10038e83751e9b4fbd6027ee9f47cf6d69ce0/LICENSE)。

本次仅参考单据头/明细、库存、收付款关联和审核/反审核设计，没有因本研究引入 jshERP 代码或 UI。固定完整树核查未发现 NOTICE 路径；不据此判断其他版本或依赖是否含 NOTICE。

若后续实际分发 Apache-2.0 代码，应附带许可证，保留适用的版权、专利、商标及归属声明，对修改过的文件显著标注修改；若所用上游包含 NOTICE，应按许可证保留相关内容。Apache-2.0 含专利授权及专利诉讼终止条款，不授予商标使用权，不当然要求将独立自有代码全部开源。

该快照后端 POM 依赖 `com.itextpdf:itextpdf:5.5.13.1`，其 [Central POM](https://repo.maven.apache.org/maven2/com/itextpdf/itextpdf/5.5.13.1/itextpdf-5.5.13.1.pom) 声明 GNU Affero General Public License v3。因此根 Apache LICENSE 不能代替全部依赖许可核对。本次没有因研究引入 jshERP/iText；将来整包复用时，应另外处理依赖许可和实际分发义务。

## 4. 分发范围与证据边界

项目分发时应随代码或制品保留实际使用的 MIT 许可证与归属声明，并按最终后端、前端和传递依赖树补齐适用的第三方许可。上游 BOM 中出现的版本条目不等于实际进入运行包的组件；本通知也没有将全部依赖宣布为 MIT 或 Apache。

已读取的日志证明完整 upstream server reactor package 与精简 vendor common install 均成功，且两者日志存在 Tests are skipped。构建成功不能替代测试、启动、权限和建筑业务验收；完整 upstream 启动另作验证。具体来源、版本、模块差异和证据见 [docs/research.md](docs/research.md)；许可证哈希见 [docs/licenses/README.md](docs/licenses/README.md)。


## 5. 实际依赖与分发材料

本次实际解析与制品核查清单如下，详细版本及来源以 JSON 为准：

- [后端实际依赖](docs/dependencies-backend.json)：77 项 Maven runtime（含 compile）加 1 项 Boot 打包组件。检查成品中 69 个嵌套 JAR，哈希与记录制品相符。
- [前端全部 lock 条目](docs/dependencies-frontend.json)：162 项，含 dev、optional 与其他平台包，162/162 tarball integrity 已验证；不表示全部进入 dist。
- [原文索引](docs/dependencies-licenses.json)：121 份按内容 SHA256 去重的许可、NOTICE 和版权材料。原文位于 [docs/licenses/dependencies/](docs/licenses/dependencies/)，原始字节和来源保留。
- [采集证据](docs/dependencies-evidence.json)、[镜像声明与元数据](docs/dependencies-images.json) 记录范围、输入哈希及已知缺口；后者不是完整镜像 SBOM。

MIT、BSD、ISC 的原版权、完整许可条件及免责声明应按各条款随副本保留；Apache-2.0 还应处理适用 NOTICE、归属、修改标记及专利条款。Hutool all/core 5.8.47 实际为 MulanPSL-2.0，附完整中英许可，保留版权、专利、商标及免责声明。Logback 1.5.34 是 EPL-2.0 OR LGPL-2.1-only；jakarta.annotation-api 2.1.1 是 EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0。应保留双许可/例外说明，并按实际选择路径履行覆盖组件的源码及通知要求，不能把本项目全部依赖概称为 MIT/Apache。

### 实际 JDBC 驱动：MariaDB 3.5.8

后端已解析并打包 `org.mariadb.jdbc:mariadb-java-client:3.5.8`，发布 POM 及精确版本源码头声明 **LGPL-2.1-or-later**；清单未发现 `mysql-connector-j`。数据库仍使用 MySQL 8，数据库与客户端的许可分别判断。`flyway-mysql` 是 Flyway 的 Apache-2.0 适配模块，不是 MySQL Connector/J。

MariaDB 原版权为 Copyright (c) 2012–2014 Monty Program Ab、Copyright (c) 2015–2025 MariaDB Corporation Ab；精确 [原始版权头](docs/licenses/dependencies/bd1d0e7ddca0d820e273bdfd7fb453e222843ac87b6871d0c2f96b02d41e2eea.txt) 与 [LGPL-2.1 全文](docs/licenses/dependencies/c8cfa082f122323774057c2808eb7e796703bde9459fa04ff6f2c9cc25f74eff.txt) 已随本通知归档。

采用 LGPL 2.1 分发路径时，需根据适用条款保留许可/版权、显著说明使用该库，落实对应库源码（含所作修改和适用构建材料）的提供方式，以及符合第 6 条的替换/重新链接安排；不得禁止为自身使用的修改和调试库修改所需的反向工程。当前 fat JAR 未做库替换或重新链接验收。精确版本 sources JAR 仅下载到临时审计缓存，来源、字节数和哈希见后端 JSON，未作为交付源码包分发。一个公开链接或更换驱动本身不等于闭源分发自动合规。具体要求及未完成项见 DEPENDENCIES.md。

### 两项许可原文缺口

`@napi-rs/lzma-linux-x64-gnu:1.5.1` 和 `lodash-unified:1.0.3` 的精确发布包均声明 MIT，但本次未取得完整上游版权/许可原文。这是明确的分发待办；不能用其他库原文替代、伪造版权，或把“已知 MIT 标签”写成“原文与义务已齐备”。是否进入最终 dist 及原生二进制内部依赖仍须核对。

### 独立 MySQL 镜像与基座

MySQL 8.0.43 作为独立容器经协议使用，不仅据此推定自有应用整体适用 GPL；但向接收者分发 MySQL 镜像/服务器二进制本身，仍需履行其 GPL-2.0 及附带组件的适用通知、许可和对应源码提供义务。已归档该精确版本上游 LICENSE，不能以 Docker 构建脚本许可代替服务器许可。

Maven/Node 构建镜像与 Temurin JRE/nginx 运行基座分别存在工具、OpenJDK、OS 包及附带组件的许可。采集范围未穷尽镜像 OS 包、原生 Rust/二进制内部依赖、全部构建插件或测试依赖；最终 digest/架构的完整镜像清单、对应源码及分发措施仍须按交付件落实。保留基座已有通知，不因添加本通知而删除。

最终镜像建议以原相对结构附带本文件、`docs/licenses/`、`docs/dependencies-*.json` 与 `docs/DEPENDENCIES.md`。这些材料用于通知与溯源，不能代替 LGPL/EPL/GPL 所需的源码提供、替换/重新链接或其他分发措施。研究对象的 LICENSE 留存不表示它们全部进入运行镜像。
