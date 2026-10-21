# 实际依赖、许可证与分发边界

本清单依据 2026-10-21T01:30:25+08:00 的项目输入快照生成。它记录实际 Maven 解析、成品 JAR 检查、前端 lock 全量条目与已取得的许可原文；不构成产品运行、Compose 验收或商用合规完成声明。最终部署及业务测试证据见 [演示与验收记录](DEMO.md)。

## 快照与机器清单

| 文件 | 内容及边界 |
| --- | --- |
| [dependencies-backend.json](dependencies-backend.json) | 77 项 Maven runtime（含 compile）依赖，加 1 项 Boot repackager 组件；各项精确版本、POM 继承链、许可表达式、制品 SHA256、打包状态与原文映射 |
| [dependencies-frontend.json](dependencies-frontend.json) | package-lock v3 全部 162 个非根条目；含 dev、optional、其他平台包，记录真实包名、安装别名、版本、integrity、原文和来源 |
| [dependencies-licenses.json](dependencies-licenses.json) | 121 份按内容 SHA256 去重的 LICENSE、NOTICE、COPYING、版权注释等原始文本及来源；不代表 121 种许可证 |
| [dependencies-images.json](dependencies-images.json) | 构建阶段、运行阶段及独立 MySQL 镜像声明与采集时可得本地元数据；不是镜像 OS 包完整 SBOM |
| [dependencies-evidence.json](dependencies-evidence.json) | 采集命令、输入哈希、计数、已知缺口及校验结果 |
| [licenses/dependencies/](licenses/dependencies/) | 内容寻址的许可原文；原字节保留，文件名即 SHA256 |

快照实际核对 `backend/target/construction-erp-0.1.0.jar` 中 69 个 `BOOT-INF/lib/` JAR，全部与解析制品或单独识别的打包组件哈希匹配。成品 SHA256 为 `0d5c47773b08a422d51dc71783fa7bcd26389c27c3a03674f6363c72e7009747`。Maven 清单中的 starter 等条目未必是嵌套 JAR；`packaged=false` 只说明未以该 JAR 进入这个成品。`spring-boot-jarmode-tools:3.5.15` 是 Boot 打包额外加入的组件，因此后端清单为 77+1 项。

前端 162/162 精确 tarball 的 lock integrity 已验证，并读取发布包 package.json 核对身份。开发依赖和其他平台 optional 包进入清单，不表示它们全部进入浏览器 dist。`node_modules/@popperjs/core` 实际解析为 `@sxzz/popperjs-es:2.11.8`，已分别记录 `component` 与 `installedAs`。本清单没有逐段证明 dist 中的组件归属。

许可表达式由发布 POM（含父级继承）、npm metadata、制品内原文及固定上游文件归一化，属于有来源的分析记录。机器清单保留原声明和来源；不能把统一标签当作对每份源文件、原生二进制内部依赖或所有分发条件的穷尽证明。JSON 中 `licenseTextStatus=archived` 表示取得有关原文，不表示法律义务全部履行。

## MariaDB JDBC：已确认版本与尚须落实的分发措施

实际依赖并打包的是 **`org.mariadb.jdbc:mariadb-java-client:3.5.8`，`LGPL-2.1-or-later`**。依据为该精确版本发布 POM、成品内 JAR 哈希及精确 tag LICENSE。清单未发现 `mysql-connector-j`。`org.flywaydb:flyway-mysql:11.7.2` 是 Apache-2.0 的 Flyway 适配模块，不是 MySQL Connector/J。数据库服务仍可为 MySQL 8；数据库产品和 JDBC 客户端分别适用各自许可。

保留以下已取得材料：

- [MariaDB LGPL-2.1 全文](licenses/dependencies/c8cfa082f122323774057c2808eb7e796703bde9459fa04ff6f2c9cc25f74eff.txt)。
- [精确版本 Driver.java 原始版权头](licenses/dependencies/bd1d0e7ddca0d820e273bdfd7fb453e222843ac87b6871d0c2f96b02d41e2eea.txt)：2012–2014 Monty Program Ab、2015–2025 MariaDB Corporation Ab，附 LGPL-2.1-or-later 声明。只提取注释，不混入实现代码。
- 精确 sources JAR 的公开来源、字节数及 SHA256 记录在后端 JSON 的 `sourceArchive`。本次下载 517720 字节到任务临时审计缓存，SHA256 为 `7cff7b968fbf3ef4a6f0dd6138728bf7daa2716996e7ffbc590dc44560bd4bf6`；**未将该源码包作为交付件分发**。

若采用 LGPL 2.1 路径分发组合程序，应按实际分发方式落实第 4、6 条等适用要求：显著说明使用该 LGPL 库、附完整许可及版权声明，向接收者提供覆盖该库及所作修改的对应源码，并选择并落实第 6 条允许的链接/源码交付方式。相应源码范围可能包括构建所需脚本，不能仅以 sources JAR 名称推定完整。第 6 条的其他合法履约方式需逐项满足其条件，不能把一个下载链接自动当作有效的源码提供或书面要约。

若交付可替换的独立库，应提供可用的替换说明和机制；若走重新链接路径，应提供该路径要求的应用目标代码/源码及必要数据或工具，使接收者能够修改库后重建可运行组合程序。交付条款不得禁止用户为自身使用而修改作品，以及为调试对库的修改而作的反向工程。是否修改过库、最终制品如何装载库、签名/校验是否阻止替换，均应按最终交付件确认。

**当前 fat JAR 打包并未完成独立库替换或重新链接验收；源码交付包及相应交付承诺也未闭合。更换为 MariaDB 驱动不等于闭源分发自动满足 LGPL。** 本次仅确认版本、许可、已取得原文和源码包来源，尚未验收该分发机制。

## 其他实际许可证及保留要求

| 许可/组件 | 分发时应保留或落实的要点 |
| --- | --- |
| MIT（含 RuoYi、Vue 等） | 随软件副本或重要部分保留原版权及完整许可声明、免责声明；自有品牌声明不能取代上游归属 |
| Apache-2.0 | 附完整许可，保留适用版权、专利、商标、归属声明和 NOTICE；修改文件须显著说明修改。专利及终止条款仍适用，不授予商标使用权 |
| BSD-2-Clause / BSD-3-Clause / ISC | 按各自文本保留版权、许可条件及免责声明；BSD-3-Clause 还限制未经许可用权利人或贡献者名称背书 |
| 0BSD | 该文本没有上述保留声明条件，仍保留实际归档以便溯源；不把其他 BSD 条件套用到它 |
| Hutool all/core 5.8.47：MulanPSL-2.0 | 本版本是木兰宽松许可证第 2 版，不能统一写为 Apache。向接收者提供许可副本，并保留版权、专利、商标及免责声明。归档是精确 tag 中英全文，不填造模板权利人 |
| Logback classic/core 1.5.34：EPL-2.0 OR LGPL-2.1-only | 上游允许选择许可路径；保留原始 QOS 版权/双许可说明及所选许可全文。可评估 EPL-2.0 路径，分发目标代码时仍须按 EPL 提供受覆盖程序源码的获取说明及适用源码，修改受覆盖部分时履行相应源码义务；独立新增代码边界按实际关系判断。不能把 OR 误写成必须同时采用两者，也不能说无义务 |
| jakarta.annotation-api 2.1.1：EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 | 保留制品 LICENSE.md、NOTICE.md，包括 Classpath 例外及双许可说明。按所选许可落实受覆盖组件的许可/源码义务；不能删去例外后将它描述成无例外 GPL |

全部原文映射见 JSON 的 `legalFiles`，包括各制品携带的 NOTICE；下表仅选一个原文入口。相同正文按字节去重不表示合并权利人或删去各组件独立版权。原始文本内的模板占位保持原样，不据此伪造实际作者。

ERPNext GPL-3.0 和 jshERP Apache-2.0 在本项目中仅作业务参考，未集成其代码、UI 或 schema。相应研究 LICENSE 留存不代表实际运行组件，不据此把自有应用声明为 GPL。RuoYi 则实际保留并依赖完整 yudao-common 与 BOM，属于 MIT 框架派生精简实现；完整上游构建成功与整套平台启动/集成是不同证据。具体归属见根目录 THIRD_PARTY_NOTICES.md。

## 两项尚缺完整原文的 npm 包

| 实际包与版本 | 已有证据 | 尚缺材料与处理边界 |
| --- | --- | --- |
| @napi-rs/lzma-linux-x64-gnu 1.5.1 | 精确 tarball integrity 通过，package.json 声明 MIT；对应上游 gitHead 可追溯 | 已检查发布包及对应源码树，未取得完整版权/许可正文；不能用另一个压缩库的许可证替代。若交付该包或其中二进制，须补齐其权利人原文及原生依赖归属 |
| lodash-unified 1.0.3 | 精确 tarball integrity 通过，package.json 声明 MIT，author 为 Jack Works | 发布包未带独立 LICENSE，也未提供可核验 repository/gitHead；未取得完整权利人许可原文，不能以 lodash 的版权原文冒充本包 |

这两项是**分发待办**。已知 MIT 声明不等于已满足 MIT 原版权/许可保留要求；不能因为没有未知许可标签就声称全部原文齐备。是否随最终 dist 分发仍需制品归属分析。agent-base 6.0.2 与 https-proxy-agent 5.0.1 的完整 MIT 段来自对应精确 npm gitHead 的 README，并验证 Git blob SHA1；Vue devtools-api 原文来自精确源码提交。JSON 明示这些原文来源，不冒称来自 tarball 内文件。

## MySQL 独立容器与镜像基座边界

MySQL 8.0.43 是独立服务器容器，经数据库协议连接；不能仅凭这种协议使用就推定自有应用需整体 GPL 开源。**如果向客户分发 MySQL Docker 镜像或其服务器二进制，仍须履行服务器自身 GPL-2.0 及附带第三方组件的适用分发义务**，包括相关许可证、通知和对应源码或符合适用条款的源码提供方式。Docker 构建脚本的许可不等于服务器及整个镜像的许可。已归档精确 mysql-8.0.43 LICENSE（含第三方条款），来源及哈希见 images JSON；该文件不能代替完整镜像内容清单和源码交付。

| 镜像声明 | 阶段 | 当前许可核查边界 |
| --- | --- | --- |
| maven:3.9.9-eclipse-temurin-21 | backend 构建 | Maven 为 Apache-2.0；JDK 与 OS 另有许可。通常不进入最终多阶段镜像，若另行分发 builder/工具链仍须处理 |
| eclipse-temurin:21-jre-jammy | backend 运行 | OpenJDK 通常为 GPL-2.0 加 Classpath 例外，附带库及 Ubuntu 包各有许可；须保留基座内声明并核查最终 digest 对应内容 |
| node:22-alpine | frontend 构建 | Node 的 MIT 及其附带依赖、多种 Alpine 包许可；不能将整个镜像统一称为 MIT |
| nginx:1.28-alpine | frontend 运行 | nginx 的 BSD 类许可及 Alpine、动态模块等各自条款；须保留基座内已有声明 |
| mysql:8.0.43 | 独立数据库运行 | MySQL 社区服务器 GPL-2.0 及附带第三方许可；本次只读取得本地 linux/arm64 元数据及上游 LICENSE |

镜像 JSON 是采集时点元数据，浮动 tag 可能变化，后续构建成功不自动刷新这份快照。最终镜像 digest/架构、OS 包与本地/原生传递依赖、Node/npm 内置组件和所有构建插件/测试依赖**未做完整清单核查**。未执行镜像 SBOM、LGPL 库替换/relink 或对应源码包完整性验收。前端原生 Rust/二进制内部依赖也未被 npm lock 全量覆盖。

## 镜像随附的最小通知包

当前清单采用完整原文目录配套索引的方式。保留根目录相对结构，建议 backend 与 nginx 最终镜像各随附：`THIRD_PARTY_NOTICES.md`、`docs/licenses/`、`docs/dependencies-*.json`；再附本文件 `docs/DEPENDENCIES.md`，使接收者可读到分发要求与已知缺口。各镜像保留全量通知目录是当前简单可靠的交付方式，不表示每个被列组件都进入该镜像。

```dockerfile
# backend：在已含 THIRD_PARTY_NOTICES.md 的基础上
COPY docs/licenses/ /app/docs/licenses/
COPY docs/dependencies-*.json docs/DEPENDENCIES.md /app/docs/

# nginx：在已含 THIRD_PARTY_NOTICES.md 的基础上
COPY docs/licenses/ /usr/share/nginx/html/docs/licenses/
COPY docs/dependencies-*.json docs/DEPENDENCIES.md /usr/share/nginx/html/docs/
```

若从 staging 目录构建，需同时带上这些源路径。RuoYi MIT 原文已在 docs/licenses 内，原有 `vendor/ruoyi/LICENSE` 副本可继续保留。无需把采集脚本、依赖 tarball、Maven 缓存或临时审计日志加入运行镜像。**通知包本身不能替代 LGPL/EPL/GPL 所需的对应源码、替换/重新链接安排或有效源码提供措施。**

## 采集复现与证据

采集脚本：[scripts/license_inventory.py](../scripts/license_inventory.py)。它使用项目匿名 Maven settings，公开 HTTPS 获取发布包/原文，不执行 npm 包、不读模型或服务凭据、不改全局配置；原文以新增且内容寻址方式归档。复现会刷新上述 JSON，宜在冻结输入后进行。

```sh
python3 scripts/license_inventory.py \
  --maven /tmp/zqy-work/tools/apache-maven-3.9.9/bin/mvn \
  --work-dir /tmp/zqy-work/dependency-inventory-20261021
```

实际 Maven 调用为 `dependency:list -DincludeScope=runtime -DoutputAbsoluteArtifactFilename=true`，含 `-B -ntp -o`，通过 `-s deploy/maven-settings.xml -gs deploy/maven-settings.xml` 指定项目 settings；没有借用旧 MySQL 驱动快照。完整命令存入 evidence JSON。运行日志在任务临时目录 `inventory.log` 与 `maven-list.log`。离线 Maven 解析成功、前端 integrity 162/162、嵌套 JAR 哈希差异 0；两项原文缺口在 evidence JSON 明列。此次校验没有执行产品测试、服务启动、镜像构建或容器更改。

## 后端全部条目

许可计数：EPL-2.0 OR LGPL-2.1-only = 2; MulanPSL-2.0 = 2; MIT = 4; Apache-2.0 = 68; EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 = 1; LGPL-2.1-or-later = 1。

| Maven group:artifact | 精确版本 | 许可表达式 | 当前成品嵌套 JAR | 原文入口 |
| --- | --- | --- | --- | --- |
| `ch.qos.logback:logback-classic` | `1.5.34` | EPL-2.0 OR LGPL-2.1-only | 是 | [原文](licenses/dependencies/f1eedf4f2cb9e901d6ae549a06d0c20dcde0136d37e38b50adaf51d4673f66d2.txt) |
| `ch.qos.logback:logback-core` | `1.5.34` | EPL-2.0 OR LGPL-2.1-only | 是 | [原文](licenses/dependencies/f1eedf4f2cb9e901d6ae549a06d0c20dcde0136d37e38b50adaf51d4673f66d2.txt) |
| `cn.hutool:hutool-all` | `5.8.47` | MulanPSL-2.0 | 是 | [原文](licenses/dependencies/d7136cebc1c0c2a50e02013928fc283b8d69cb805a994e21e6a8cc3983f45131.txt) |
| `cn.hutool:hutool-core` | `5.8.47` | MulanPSL-2.0 | 是 | [原文](licenses/dependencies/d7136cebc1c0c2a50e02013928fc283b8d69cb805a994e21e6a8cc3983f45131.txt) |
| `cn.iocoder.boot:yudao-common` | `2026.09-SNAPSHOT` | MIT | 是 | [原文](licenses/dependencies/772daf1f82a50025a74e4066050490d875feba62a49cf0ecefa1b4cc824e9a07.txt) |
| `com.alibaba.fastjson2:fastjson2` | `2.0.65` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.alibaba.fastjson2:fastjson2-extension` | `2.0.65` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.alibaba:fastjson` | `2.0.65` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.alibaba:transmittable-thread-local` | `2.14.5` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.baomidou:mybatis-plus` | `3.5.17` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.baomidou:mybatis-plus-annotation` | `3.5.17` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.baomidou:mybatis-plus-core` | `3.5.17` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.baomidou:mybatis-plus-extension` | `3.5.17` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.baomidou:mybatis-plus-spring` | `3.5.17` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.baomidou:mybatis-plus-spring-boot-autoconfigure` | `3.5.17` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.baomidou:mybatis-plus-spring-boot-native-image` | `3.5.17` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.baomidou:mybatis-plus-spring-boot3-starter` | `3.5.17` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.fasterxml.jackson.core:jackson-annotations` | `2.21` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.fasterxml.jackson.core:jackson-core` | `2.21.4` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.fasterxml.jackson.core:jackson-databind` | `2.21.4` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-toml` | `2.21.4` | Apache-2.0 | 是 | [原文](licenses/dependencies/eef8e359fd7799a1740765099fa4d114dcd296bb96dcfa7d45d5b81b2504bed2.txt) |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` | `2.21.4` | Apache-2.0 | 是 | [原文](licenses/dependencies/bc3d1a9b9fe02f5b9186447ca2896a63015e5e626eccfdda6a04bd7d71be3ce0.txt) |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | `2.21.4` | Apache-2.0 | 是 | [原文](licenses/dependencies/66ea9c8987b1674aababbcc24f61c83ab4fb7baf3af1d701caf0a3aaea97fa92.txt) |
| `com.fasterxml.jackson.module:jackson-module-parameter-names` | `2.21.4` | Apache-2.0 | 是 | [原文](licenses/dependencies/a09ba3adcfdcac2d7e31b89e8e7799cec96d7303dc82c0bb3d38d8a352d41d19.txt) |
| `com.fasterxml:classmate` | `1.7.3` | Apache-2.0 | 是 | [原文](licenses/dependencies/7d8d549eb31995fe67822c860acc7ac3dd958a03a5a71aef4069033e7df72d94.txt) |
| `com.zaxxer:HikariCP` | `6.3.3` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `io.micrometer:micrometer-commons` | `1.15.12` | Apache-2.0 | 是 | [原文](licenses/dependencies/62fb8a3a9621dc2388174caaabe9c2317b694bb9a1d46c98bcf5655b68f51be3.txt) |
| `io.micrometer:micrometer-observation` | `1.15.12` | Apache-2.0 | 是 | [原文](licenses/dependencies/62fb8a3a9621dc2388174caaabe9c2317b694bb9a1d46c98bcf5655b68f51be3.txt) |
| `io.opentelemetry:opentelemetry-api` | `1.49.0` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `io.opentelemetry:opentelemetry-context` | `1.49.0` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `io.swagger.core.v3:swagger-annotations-jakarta` | `2.2.41` | Apache-2.0 | 是 | [原文](licenses/dependencies/aa4dc81ef2c6b4c2fd8cb7a7a4245c34396acd5a5dfa1ba6c8c0cf9ee88423fb.txt) |
| `jakarta.annotation:jakarta.annotation-api` | `2.1.1` | EPL-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 | 是 | [原文](licenses/dependencies/6e1f002892b81cbe0647019b150c8a056efc1add565671a4f8af629b6cd2cc7b.txt) |
| `jakarta.validation:jakarta.validation-api` | `3.0.2` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.apache.logging.log4j:log4j-api` | `2.24.3` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.apache.logging.log4j:log4j-to-slf4j` | `2.24.3` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.apache.tomcat.embed:tomcat-embed-core` | `10.1.55` | Apache-2.0 | 是 | [原文](licenses/dependencies/b15780b4c93aef25618df419dcde6cc9646f497c29fc615c1b67b6fa3bb26a65.txt) |
| `org.apache.tomcat.embed:tomcat-embed-el` | `10.1.55` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.apache.tomcat.embed:tomcat-embed-websocket` | `10.1.55` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.dromara:easy-trans-anno` | `3.1.8` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.flywaydb:flyway-core` | `11.7.2` | Apache-2.0 | 是 | [原文](licenses/dependencies/73332bc915a16b4ca4bb913d4a55b66e865c6ef0d5feb289063248556fab48b8.txt) |
| `org.flywaydb:flyway-mysql` | `11.7.2` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.hibernate.validator:hibernate-validator` | `8.0.3.Final` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.jboss.logging:jboss-logging` | `3.6.3.Final` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.mapstruct:mapstruct` | `1.7.0.Beta2` | Apache-2.0 | 是 | [原文](licenses/dependencies/29cb77b4d7beecc89e3137293fc39d975b8ef2ae8987f2f03e20bfb47b63f9ad.txt) |
| `org.mapstruct:mapstruct-jdk8` | `1.7.0.Beta2` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |
| `org.mapstruct:mapstruct-processor` | `1.7.0.Beta2` | Apache-2.0 | 否 | [原文](licenses/dependencies/3ddf9be5c28fe27dad143a5dc76eea25222ad1dd68934a047064e56ed2fa40c5.txt) |
| `org.mariadb.jdbc:mariadb-java-client` | `3.5.8` | LGPL-2.1-or-later | 是 | [原文](licenses/dependencies/bd1d0e7ddca0d820e273bdfd7fb453e222843ac87b6871d0c2f96b02d41e2eea.txt) |
| `org.mybatis:mybatis` | `3.5.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/91b00d08eb9c5cb1bc6370d42b63c4874032caf26b9641bfab636827cff9c5d3.txt) |
| `org.mybatis:mybatis-spring` | `3.0.5` | Apache-2.0 | 是 | [原文](licenses/dependencies/9818ec66553209eebf329d4bf4bc6c992e1ac340bd3314c25ee478b8ce2a7bac.txt) |
| `org.projectlombok:lombok` | `1.18.46` | MIT | 是 | [原文](licenses/dependencies/76479448741d7a7a3a97b6afd9ab9699d95621faafc65a1b8e9149342ea00feb.txt) |
| `org.slf4j:jul-to-slf4j` | `2.0.18` | MIT | 是 | [原文](licenses/dependencies/4e7f90c86ab51278228bce153122f1d8df30149d13ce9ef524c8444a84c32dcc.txt) |
| `org.slf4j:slf4j-api` | `2.0.18` | MIT | 是 | [原文](licenses/dependencies/4e7f90c86ab51278228bce153122f1d8df30149d13ce9ef524c8444a84c32dcc.txt) |
| `org.springframework.boot:spring-boot` | `3.5.15` | Apache-2.0 | 是 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-autoconfigure` | `3.5.15` | Apache-2.0 | 是 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-jarmode-tools` | `3.5.15` | Apache-2.0 | 是 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-starter` | `3.5.15` | Apache-2.0 | 否 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-starter-jdbc` | `3.5.15` | Apache-2.0 | 否 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-starter-json` | `3.5.15` | Apache-2.0 | 否 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-starter-logging` | `3.5.15` | Apache-2.0 | 否 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-starter-security` | `3.5.15` | Apache-2.0 | 否 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-starter-tomcat` | `3.5.15` | Apache-2.0 | 否 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-starter-validation` | `3.5.15` | Apache-2.0 | 否 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.boot:spring-boot-starter-web` | `3.5.15` | Apache-2.0 | 否 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.security:spring-security-config` | `6.5.11` | Apache-2.0 | 是 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.security:spring-security-core` | `6.5.11` | Apache-2.0 | 是 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.security:spring-security-crypto` | `6.5.11` | Apache-2.0 | 是 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework.security:spring-security-web` | `6.5.11` | Apache-2.0 | 是 | [原文](licenses/dependencies/7200000367e0e8b47822d563a5ddd2dd3a97cc05b32bd7dbcc7b4eeb92b76628.txt) |
| `org.springframework:spring-aop` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-beans` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-context` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-core` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-expression` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-jcl` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-jdbc` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-tx` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-web` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.springframework:spring-webmvc` | `6.2.19` | Apache-2.0 | 是 | [原文](licenses/dependencies/9b57549a55aa67c9e0cd3f5dc96cd5177c5f9b2036aabdfbbd2aee18e0586fea.txt) |
| `org.yaml:snakeyaml` | `2.4` | Apache-2.0 | 是 | [原文](licenses/dependencies/cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30.txt) |

## 前端 lock 全部条目

下表“dev/optional”来自 lock 标记，均为经过下载完整性及包身份核对的条目；它不是 dist 包含证明。许可计数：MIT = 153; Apache-2.0 = 2; BSD-2-Clause = 1; BSD-3-Clause = 4; ISC = 1; 0BSD = 1。

| 实际包名 | 精确版本 | 许可声明 | dev / optional | lock 路径 | 原文入口 |
| --- | --- | --- | --- | --- | --- |
| `@babel/helper-string-parser` | `7.29.7` | MIT | 否 / 否 | `node_modules/@babel/helper-string-parser` | [原文](licenses/dependencies/117da2af0d4ce0fe1c8e19b5cff9dcd806adf973d328d27b11d4448c4ff24f76.txt) |
| `@babel/helper-validator-identifier` | `7.29.7` | MIT | 否 / 否 | `node_modules/@babel/helper-validator-identifier` | [原文](licenses/dependencies/117da2af0d4ce0fe1c8e19b5cff9dcd806adf973d328d27b11d4448c4ff24f76.txt) |
| `@babel/parser` | `7.29.9` | MIT | 否 / 否 | `node_modules/@babel/parser` | [原文](licenses/dependencies/2e97627cb278aa7556fb9e8817368302301a595b6c7582512b8d74c57b773652.txt) |
| `@babel/types` | `7.29.8` | MIT | 否 / 否 | `node_modules/@babel/types` | [原文](licenses/dependencies/117da2af0d4ce0fe1c8e19b5cff9dcd806adf973d328d27b11d4448c4ff24f76.txt) |
| `@ctrl/tinycolor` | `4.2.1` | MIT | 否 / 否 | `node_modules/@ctrl/tinycolor` | [原文](licenses/dependencies/40623170cffa92890b9a2decc5456de6bba6d4f75a7a7b7abc60cc9fdaf78b65.txt) |
| `@element-plus/icons-vue` | `2.3.2` | MIT | 否 / 否 | `node_modules/@element-plus/icons-vue` | [原文](licenses/dependencies/5db4ebb3abd8353774ae148723ccc14087d6dab99473f77a7b799b55defd8dfe.txt) |
| `@esbuild/aix-ppc64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/aix-ppc64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/android-arm` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/android-arm` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/android-arm64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/android-arm64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/android-x64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/android-x64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/darwin-arm64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/darwin-arm64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/darwin-x64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/darwin-x64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/freebsd-arm64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/freebsd-arm64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/freebsd-x64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/freebsd-x64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-arm` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-arm` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-arm64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-arm64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-ia32` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-ia32` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-loong64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-loong64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-mips64el` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-mips64el` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-ppc64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-ppc64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-riscv64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-riscv64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-s390x` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-s390x` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/linux-x64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/linux-x64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/netbsd-arm64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/netbsd-arm64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/netbsd-x64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/netbsd-x64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/openbsd-arm64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/openbsd-arm64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/openbsd-x64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/openbsd-x64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/openharmony-arm64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/openharmony-arm64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/sunos-x64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/sunos-x64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/win32-arm64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/win32-arm64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/win32-ia32` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/win32-ia32` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@esbuild/win32-x64` | `0.28.2` | MIT | 是 / 是 | `node_modules/@esbuild/win32-x64` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `@floating-ui/core` | `1.8.0` | MIT | 否 / 否 | `node_modules/@floating-ui/core` | [原文](licenses/dependencies/0e4c9a9b6c71019cbbea3bdc20b01223110a9035700f9c960c8fcbf78c2325ce.txt) |
| `@floating-ui/dom` | `1.8.0` | MIT | 否 / 否 | `node_modules/@floating-ui/dom` | [原文](licenses/dependencies/0e4c9a9b6c71019cbbea3bdc20b01223110a9035700f9c960c8fcbf78c2325ce.txt) |
| `@floating-ui/utils` | `0.2.12` | MIT | 否 / 否 | `node_modules/@floating-ui/utils` | [原文](licenses/dependencies/0e4c9a9b6c71019cbbea3bdc20b01223110a9035700f9c960c8fcbf78c2325ce.txt) |
| `@jridgewell/sourcemap-codec` | `1.6.0` | MIT | 否 / 否 | `node_modules/@jridgewell/sourcemap-codec` | [原文](licenses/dependencies/769d154fbde32a915af110b1123650bc79f4cbe675acc66e005265bf069c6c6c.txt) |
| `@napi-rs/lzma-linux-x64-gnu` | `1.5.1` | MIT | 是 / 是 | `node_modules/@napi-rs/lzma-linux-x64-gnu` | **待补原文** |
| `@rolldown/pluginutils` | `1.0.1` | MIT | 是 / 否 | `node_modules/@rolldown/pluginutils` | [原文](licenses/dependencies/e1919b3b98b8bf6c1b99fcc60a0ed46027c97ac947432e1278446c9606f50161.txt) |
| `@rollup/rollup-android-arm-eabi` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-android-arm-eabi` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-android-arm64` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-android-arm64` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-darwin-arm64` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-darwin-arm64` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-darwin-x64` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-darwin-x64` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-freebsd-arm64` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-freebsd-arm64` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-freebsd-x64` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-freebsd-x64` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-arm-gnueabihf` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-arm-gnueabihf` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-arm-musleabihf` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-arm-musleabihf` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-arm64-gnu` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-arm64-gnu` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-arm64-musl` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-arm64-musl` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-loong64-gnu` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-loong64-gnu` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-loong64-musl` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-loong64-musl` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-ppc64-gnu` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-ppc64-gnu` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-ppc64-musl` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-ppc64-musl` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-riscv64-gnu` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-riscv64-gnu` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-riscv64-musl` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-riscv64-musl` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-s390x-gnu` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-s390x-gnu` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-x64-gnu` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-x64-gnu` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-linux-x64-musl` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-linux-x64-musl` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-openbsd-x64` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-openbsd-x64` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-openharmony-arm64` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-openharmony-arm64` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-win32-arm64-msvc` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-win32-arm64-msvc` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-win32-ia32-msvc` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-win32-ia32-msvc` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-win32-x64-gnu` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-win32-x64-gnu` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@rollup/rollup-win32-x64-msvc` | `4.64.12` | MIT | 是 / 是 | `node_modules/@rollup/rollup-win32-x64-msvc` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `@sxzz/popperjs-es` | `2.11.8` | MIT | 否 / 否 | `node_modules/@popperjs/core` | [原文](licenses/dependencies/bf67e2c9b7974543bb18ced3a0b37f53729aaff953b442dd89a3adaf90fd93e8.txt) |
| `@types/estree` | `1.0.9` | MIT | 是 / 否 | `node_modules/@types/estree` | [原文](licenses/dependencies/c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383.txt) |
| `@types/lodash` | `4.17.26` | MIT | 否 / 否 | `node_modules/@types/lodash` | [原文](licenses/dependencies/c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383.txt) |
| `@types/lodash-es` | `4.17.12` | MIT | 否 / 否 | `node_modules/@types/lodash-es` | [原文](licenses/dependencies/c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383.txt) |
| `@types/node` | `24.19.2` | MIT | 是 / 否 | `node_modules/@types/node` | [原文](licenses/dependencies/c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383.txt) |
| `@types/web-bluetooth` | `0.0.21` | MIT | 否 / 否 | `node_modules/@types/web-bluetooth` | [原文](licenses/dependencies/c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383.txt) |
| `@vitejs/plugin-vue` | `6.0.10` | MIT | 是 / 否 | `node_modules/@vitejs/plugin-vue` | [原文](licenses/dependencies/29b68325fe026047d13e187b44c33b2acacf7dc647dec4583702e59f235e13b5.txt) |
| `@volar/language-core` | `2.4.28` | MIT | 是 / 否 | `node_modules/@volar/language-core` | [原文](licenses/dependencies/a56351045b45475385a9479d7310208ff0be9489babdb4e635fcea5d7dc1fb02.txt) |
| `@volar/source-map` | `2.4.28` | MIT | 是 / 否 | `node_modules/@volar/source-map` | [原文](licenses/dependencies/a56351045b45475385a9479d7310208ff0be9489babdb4e635fcea5d7dc1fb02.txt) |
| `@volar/typescript` | `2.4.28` | MIT | 是 / 否 | `node_modules/@volar/typescript` | [原文](licenses/dependencies/a56351045b45475385a9479d7310208ff0be9489babdb4e635fcea5d7dc1fb02.txt) |
| `@vue/compiler-core` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/compiler-core` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vue/compiler-dom` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/compiler-dom` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vue/compiler-sfc` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/compiler-sfc` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vue/compiler-ssr` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/compiler-ssr` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vue/devtools-api` | `6.6.4` | MIT | 否 / 否 | `node_modules/vue-router/node_modules/@vue/devtools-api` | [原文](licenses/dependencies/050bbca6960784db52ff387271bf2ecc5cbed7cf8581b415d528a6ecb6585015.txt) |
| `@vue/devtools-api` | `7.7.10` | MIT | 否 / 否 | `node_modules/@vue/devtools-api` | [原文](licenses/dependencies/d0ac09e8df2b54625ea1606dfae75d5b6af1cbc5d28a17eca1350be9c367175c.txt) |
| `@vue/devtools-kit` | `7.7.10` | MIT | 否 / 否 | `node_modules/@vue/devtools-kit` | [原文](licenses/dependencies/d0ac09e8df2b54625ea1606dfae75d5b6af1cbc5d28a17eca1350be9c367175c.txt) |
| `@vue/devtools-shared` | `7.7.10` | MIT | 否 / 否 | `node_modules/@vue/devtools-shared` | [原文](licenses/dependencies/d0ac09e8df2b54625ea1606dfae75d5b6af1cbc5d28a17eca1350be9c367175c.txt) |
| `@vue/language-core` | `3.3.13` | MIT | 是 / 否 | `node_modules/@vue/language-core` | [原文](licenses/dependencies/a56351045b45475385a9479d7310208ff0be9489babdb4e635fcea5d7dc1fb02.txt) |
| `@vue/reactivity` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/reactivity` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vue/runtime-core` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/runtime-core` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vue/runtime-dom` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/runtime-dom` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vue/server-renderer` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/server-renderer` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vue/shared` | `3.5.44` | MIT | 否 / 否 | `node_modules/@vue/shared` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `@vueuse/core` | `15.0.0` | MIT | 否 / 否 | `node_modules/@vueuse/core` | [原文](licenses/dependencies/0f844b1160603552a01662ac2999c14e5fb4df487e65619b9ed4406e43eb3c0b.txt) |
| `@vueuse/metadata` | `15.0.0` | MIT | 否 / 否 | `node_modules/@vueuse/metadata` | [原文](licenses/dependencies/0f844b1160603552a01662ac2999c14e5fb4df487e65619b9ed4406e43eb3c0b.txt) |
| `@vueuse/shared` | `15.0.0` | MIT | 否 / 否 | `node_modules/@vueuse/shared` | [原文](licenses/dependencies/0f844b1160603552a01662ac2999c14e5fb4df487e65619b9ed4406e43eb3c0b.txt) |
| `agent-base` | `6.0.2` | MIT | 否 / 否 | `node_modules/agent-base` | [原文](licenses/dependencies/7bb86273b08315255c72546e7f8080e1c7604a597d318fe60b84908fdad3e923.txt) |
| `alien-signals` | `3.2.1` | MIT | 是 / 否 | `node_modules/alien-signals` | [原文](licenses/dependencies/75863f712f8f0603b0dba33edf4b725bab80fc858a51756e5905a3c4c8087169.txt) |
| `async-validator` | `4.2.5` | MIT | 否 / 否 | `node_modules/async-validator` | [原文](licenses/dependencies/5d0427088e37071c4b942de990f6365d5c2d195f3e957ce925322d184784e2eb.txt) |
| `asynckit` | `0.4.0` | MIT | 否 / 否 | `node_modules/asynckit` | [原文](licenses/dependencies/1953150d5d4b10c7542cee6f6e0c613b2682545233f069d75cfff1936386ce10.txt) |
| `axios` | `1.20.0` | MIT | 否 / 否 | `node_modules/axios` | [原文](licenses/dependencies/82761059eaedacb3356803aea8a170d8298609f91b14fc32ee1bfb40d690183c.txt) |
| `birpc` | `2.9.0` | MIT | 否 / 否 | `node_modules/birpc` | [原文](licenses/dependencies/67cd5e903c1f908edfa27c25606fa806ce629d9a8ddb7d83019d9fd1f83f7fde.txt) |
| `call-bind-apply-helpers` | `1.0.2` | MIT | 否 / 否 | `node_modules/call-bind-apply-helpers` | [原文](licenses/dependencies/5e325595b4ea8cfec3802f545b1def5d7b73e4a5b8e9ba63e32a320f67732292.txt) |
| `combined-stream` | `1.0.8` | MIT | 否 / 否 | `node_modules/combined-stream` | [原文](licenses/dependencies/47eb8ca82c798246774946d1be0f9aa08f025fa8325ced0947aeeb4c05fe5547.txt) |
| `copy-anything` | `4.1.5` | MIT | 否 / 否 | `node_modules/copy-anything` | [原文](licenses/dependencies/91d090a8e06fd0f8b599f7bd5bc9e3d97896548037ef43ee1926c6109b7cd342.txt) |
| `csstype` | `3.2.3` | MIT | 否 / 否 | `node_modules/csstype` | [原文](licenses/dependencies/11d55bd4541c75ee7879547ac49089c489163dae49551389713c3d026cab383e.txt) |
| `dayjs` | `1.11.23` | MIT | 否 / 否 | `node_modules/dayjs` | [原文](licenses/dependencies/5faab7526d055651be3aab769d58897be6bd91f3d39d137f25f12dba1b31d5dc.txt) |
| `debug` | `4.4.3` | MIT | 否 / 否 | `node_modules/debug` | [原文](licenses/dependencies/3a61c6c96caf5c1d9b623fb9b04c822b783dfcb78aa7e49c76a3f643e6ed7f95.txt) |
| `delayed-stream` | `1.0.0` | MIT | 否 / 否 | `node_modules/delayed-stream` | [原文](licenses/dependencies/47eb8ca82c798246774946d1be0f9aa08f025fa8325ced0947aeeb4c05fe5547.txt) |
| `dunder-proto` | `1.0.1` | MIT | 否 / 否 | `node_modules/dunder-proto` | [原文](licenses/dependencies/2b770a704c15de238c3f622b01b0044ddd60b49ee30608ea6991ebf19db7a7a1.txt) |
| `echarts` | `6.1.0` | Apache-2.0 | 否 / 否 | `node_modules/echarts` | [原文](licenses/dependencies/634293835b43a6dd2094fa39182a3d9a6b9ca43b7fdb9ac354e8037af2a3093a.txt) |
| `element-plus` | `2.14.7` | MIT | 否 / 否 | `node_modules/element-plus` | [原文](licenses/dependencies/0790118bb4d66681db1d63181f72ef68e632d632f6db0373ef87cf328561af27.txt) |
| `entities` | `7.0.1` | BSD-2-Clause | 否 / 否 | `node_modules/entities` | [原文](licenses/dependencies/cb992345949ccd6e8394b2cd6c465f7b897c864f845937dbf64e8997f389e164.txt) |
| `es-define-property` | `1.0.1` | MIT | 否 / 否 | `node_modules/es-define-property` | [原文](licenses/dependencies/5e325595b4ea8cfec3802f545b1def5d7b73e4a5b8e9ba63e32a320f67732292.txt) |
| `es-errors` | `1.3.0` | MIT | 否 / 否 | `node_modules/es-errors` | [原文](licenses/dependencies/5e325595b4ea8cfec3802f545b1def5d7b73e4a5b8e9ba63e32a320f67732292.txt) |
| `es-object-atoms` | `1.1.2` | MIT | 否 / 否 | `node_modules/es-object-atoms` | [原文](licenses/dependencies/5e325595b4ea8cfec3802f545b1def5d7b73e4a5b8e9ba63e32a320f67732292.txt) |
| `es-set-tostringtag` | `2.1.0` | MIT | 否 / 否 | `node_modules/es-set-tostringtag` | [原文](licenses/dependencies/1a3aeb1f1398bd697d57c3c585faadf59d825aca6e3162cd7eeb72ff76eb2466.txt) |
| `esbuild` | `0.28.2` | MIT | 是 / 否 | `node_modules/esbuild` | [原文](licenses/dependencies/b40ec5baec7bb34fa5b1c09521fa3cd52d5fad7adafed74932a2010d3612a681.txt) |
| `estree-walker` | `2.0.2` | MIT | 否 / 否 | `node_modules/estree-walker` | [原文](licenses/dependencies/8a6dcbabe7179f9c8489c08a7d874d0f1e093ae7449e03756ccf87cb9d0e296e.txt) |
| `fdir` | `6.5.0` | MIT | 是 / 否 | `node_modules/fdir` | [原文](licenses/dependencies/9a39f2aadab11a3697edd668ff2d8ad885b649737b7ab4d3bf12b34e5ada0c86.txt) |
| `follow-redirects` | `1.16.1` | MIT | 否 / 否 | `node_modules/follow-redirects` | [原文](licenses/dependencies/bfa8a54bb952ccda79f0f1889721d108f5b605babbb2b8a3705ffb52f4132eb7.txt) |
| `form-data` | `4.0.6` | MIT | 否 / 否 | `node_modules/form-data` | [原文](licenses/dependencies/e5b780d4f38d1d3328e3e53186c4e62d3fa149ea6f2bacd5de5ad0c30ac85343.txt) |
| `fsevents` | `2.3.3` | MIT | 是 / 是 | `node_modules/fsevents` | [原文](licenses/dependencies/2a8a5d6197bb97a018b8a190dd3ed853d750ca87c8e80c17aad41d6c6ff448e1.txt) |
| `function-bind` | `1.1.2` | MIT | 否 / 否 | `node_modules/function-bind` | [原文](licenses/dependencies/773e131a7684726005a7e4688a80b4620033bc08499bc1404dd1a1eb3bca725e.txt) |
| `get-intrinsic` | `1.3.0` | MIT | 否 / 否 | `node_modules/get-intrinsic` | [原文](licenses/dependencies/39c5ec504cf6bd5cd782a7c695828e09189df79f5d94840e4f08feb97b9fd416.txt) |
| `get-proto` | `1.0.1` | MIT | 否 / 否 | `node_modules/get-proto` | [原文](licenses/dependencies/be46ce1e3b0479af9ce82d22b465a6d7d2ff084fca0aaf3d54172da2b5eb5781.txt) |
| `gopd` | `1.2.0` | MIT | 否 / 否 | `node_modules/gopd` | [原文](licenses/dependencies/d90bf0a089da4cf43d644ed240a0b3825dcdb705e64e38371d56995a4cc9e4c5.txt) |
| `has-symbols` | `1.1.0` | MIT | 否 / 否 | `node_modules/has-symbols` | [原文](licenses/dependencies/206c1adcf206dc0031b11232f5b054ec5f1662407ab1ca415247921cab2068ab.txt) |
| `has-tostringtag` | `1.0.2` | MIT | 否 / 否 | `node_modules/has-tostringtag` | [原文](licenses/dependencies/e2560e002e13281578c75c850061d9255c33d16d732939e8c2db64c2506642fa.txt) |
| `hasown` | `2.0.4` | MIT | 否 / 否 | `node_modules/hasown` | [原文](licenses/dependencies/bf9b0d665be2a689851eea667ca9f42066ea1d903b38349c51e6a44b2577680a.txt) |
| `hookable` | `5.5.3` | MIT | 否 / 否 | `node_modules/hookable` | [原文](licenses/dependencies/3555f8c47cbff86d199d5a5033afd5ef9560e0095927296af670a886b8b1988d.txt) |
| `https-proxy-agent` | `5.0.1` | MIT | 否 / 否 | `node_modules/https-proxy-agent` | [原文](licenses/dependencies/7bb86273b08315255c72546e7f8080e1c7604a597d318fe60b84908fdad3e923.txt) |
| `lodash` | `4.18.1` | MIT | 否 / 否 | `node_modules/lodash` | [原文](licenses/dependencies/f71e8ed126b46346494aad5486874cd8f0aafe95092ed67d2e3cb6110f939abc.txt) |
| `lodash-es` | `4.18.1` | MIT | 否 / 否 | `node_modules/lodash-es` | [原文](licenses/dependencies/f71e8ed126b46346494aad5486874cd8f0aafe95092ed67d2e3cb6110f939abc.txt) |
| `lodash-unified` | `1.0.3` | MIT | 否 / 否 | `node_modules/lodash-unified` | **待补原文** |
| `magic-string` | `0.30.21` | MIT | 否 / 否 | `node_modules/magic-string` | [原文](licenses/dependencies/1cbe51b907662f6cb1492b16c359384a595180bf0e4d101603ed525e75c4e484.txt) |
| `math-intrinsics` | `1.1.0` | MIT | 否 / 否 | `node_modules/math-intrinsics` | [原文](licenses/dependencies/2b770a704c15de238c3f622b01b0044ddd60b49ee30608ea6991ebf19db7a7a1.txt) |
| `memoize-one` | `6.0.0` | MIT | 否 / 否 | `node_modules/memoize-one` | [原文](licenses/dependencies/b1c501095de70182748cb8e8d97ac5577701a4ef3b755ba30d59fd87b24bdb0f.txt) |
| `mime-db` | `1.52.0` | MIT | 否 / 否 | `node_modules/mime-db` | [原文](licenses/dependencies/cc1dfd4dafa27271e8212cd3b274eeb3f262e40a6fdab36ddc3f9696f706f58b.txt) |
| `mime-types` | `2.1.35` | MIT | 否 / 否 | `node_modules/mime-types` | [原文](licenses/dependencies/71f83c4c0621102a56d9853812777b85751bce7e9726f686f5b056c1f8a4b0e6.txt) |
| `mitt` | `3.0.1` | MIT | 否 / 否 | `node_modules/mitt` | [原文](licenses/dependencies/1cab22f196264195a4caec8ca5630170fdde76ee8f43346e47021d087332d3b0.txt) |
| `ms` | `2.1.3` | MIT | 否 / 否 | `node_modules/ms` | [原文](licenses/dependencies/1662fae9b5314d11cf51284e2dcd1f006a354f7343f08712a730fcff9a359801.txt) |
| `muggle-string` | `0.4.1` | MIT | 是 / 否 | `node_modules/muggle-string` | [原文](licenses/dependencies/df52411dcd3c8f5897bbbec8c94e971519e13e75818c263e9a99a86255dd301b.txt) |
| `nanoid` | `3.3.20` | MIT | 否 / 否 | `node_modules/nanoid` | [原文](licenses/dependencies/da4db1480d9beea3483a2eda5c53b22238d0827d57da162b48f122e04d2d9987.txt) |
| `normalize-wheel-es` | `1.2.0` | BSD-3-Clause | 否 / 否 | `node_modules/normalize-wheel-es` | [原文](licenses/dependencies/658d037c5c078a24c27ab1e0f37050d407ad6628be94886a4e48f3e761f47f7d.txt) |
| `path-browserify` | `1.0.1` | MIT | 是 / 否 | `node_modules/path-browserify` | [原文](licenses/dependencies/a22b9d5763f574e5db347c30acc0b33eaf4846767c03d2e27d012e864e79a824.txt) |
| `perfect-debounce` | `1.0.0` | MIT | 否 / 否 | `node_modules/perfect-debounce` | [原文](licenses/dependencies/46231df5a7733c3f52f11b71f3df61813007745b62b09031acfb45fb42d75082.txt) |
| `picocolors` | `1.1.1` | ISC | 否 / 否 | `node_modules/picocolors` | [原文](licenses/dependencies/6582629e2979466878f6014313dcc2f3756c9616148682227ce3063dde310750.txt) |
| `picomatch` | `4.0.7` | MIT | 是 / 否 | `node_modules/picomatch` | [原文](licenses/dependencies/d0cd141b0c322fded5dfad1d4645bb2fedfc05b7321fe1009469638190d59ef9.txt) |
| `pinia` | `3.0.4` | MIT | 否 / 否 | `node_modules/pinia` | [原文](licenses/dependencies/91a2845c4db44e7497b514b98634a674c737986ad0db81599307cf733bf850b2.txt) |
| `postcss` | `8.5.31` | MIT | 否 / 否 | `node_modules/postcss` | [原文](licenses/dependencies/5be1f3465bba68a626777f984878814aaf35e7ef8e9fd314d469bcf887050fb8.txt) |
| `proxy-from-env` | `2.1.0` | MIT | 否 / 否 | `node_modules/proxy-from-env` | [原文](licenses/dependencies/f55828df4b8752c48e765a806465b76a103e3cb363379c569b15a1df2ba2d79e.txt) |
| `rfdc` | `1.4.1` | MIT | 否 / 否 | `node_modules/rfdc` | [原文](licenses/dependencies/1ce941682a96c7b898ecd125d0f2e22319758ea6f3adbf498a65d649b0f3f7dd.txt) |
| `rollup` | `4.64.12` | MIT | 是 / 否 | `node_modules/rollup` | [原文](licenses/dependencies/fa1bd040c5bdeefe65b3821cebf474f2733ce65df13089bd151dda1778e62fe8.txt) |
| `source-map-js` | `1.2.2` | BSD-3-Clause | 否 / 否 | `node_modules/source-map-js` | [原文](licenses/dependencies/6cb0631f71c7749763fd3dd1d5bee52dd1070ec17f2edc1710079ad070bd2fbd.txt) |
| `speakingurl` | `14.0.1` | BSD-3-Clause | 否 / 否 | `node_modules/speakingurl` | [原文](licenses/dependencies/4cf85d9fa395446ce467f60711fe8154a374bd6c86e3fd44b847bcadec86fe33.txt) |
| `superjson` | `2.2.6` | MIT | 否 / 否 | `node_modules/superjson` | [原文](licenses/dependencies/d6b3a220bde9bf72fd0f9aecf71cd48c43ddc652abea727fc98a9b21a9534bb9.txt) |
| `tinyglobby` | `0.2.17` | MIT | 是 / 否 | `node_modules/tinyglobby` | [原文](licenses/dependencies/22c68811e174cbbfb3813d4135918df4f540959c14d872e601d9abe83d3cde8f.txt) |
| `tslib` | `2.3.0` | 0BSD | 否 / 否 | `node_modules/tslib` | [原文](licenses/dependencies/210b19e543130388c68654b7497e967119ce17145f66ab7d85688fbd70f08751.txt) |
| `typescript` | `5.9.3` | Apache-2.0 | 否 / 否 | `node_modules/typescript` | [原文](licenses/dependencies/a7d00bfd54525bc694b6e32f64c7ebcf5e6b7ae3657be5cc12767bce74654a47.txt) |
| `undici-types` | `7.24.6` | MIT | 是 / 否 | `node_modules/undici-types` | [原文](licenses/dependencies/a6db8096b2707bc0102d256917d4d33f298ba36d8c3f25de067a2b5bb379db27.txt) |
| `vite` | `7.3.7` | MIT | 是 / 否 | `node_modules/vite` | [原文](licenses/dependencies/a77a1c089806b39ad339535bdf3677f636c91d96693e8ad7b11fe733f650ea64.txt) |
| `vscode-uri` | `3.2.0` | MIT | 是 / 否 | `node_modules/vscode-uri` | [原文](licenses/dependencies/c669138cb1eb01fbb745e8e460b291e4f2869c14a40061390b6095d845ed4c82.txt) |
| `vue` | `3.5.44` | MIT | 否 / 否 | `node_modules/vue` | [原文](licenses/dependencies/1bb85cc9b13b81ef41c81c51866172fc345e0503c86726a6755b796590b70175.txt) |
| `vue-component-type-helpers` | `3.3.13` | MIT | 否 / 否 | `node_modules/vue-component-type-helpers` | [原文](licenses/dependencies/ac07310d94980d9819cf2c28b3a2f63d11db283f48f02f67208b549f44860ac0.txt) |
| `vue-router` | `4.6.4` | MIT | 否 / 否 | `node_modules/vue-router` | [原文](licenses/dependencies/91a2845c4db44e7497b514b98634a674c737986ad0db81599307cf733bf850b2.txt) |
| `vue-tsc` | `3.3.13` | MIT | 是 / 否 | `node_modules/vue-tsc` | [原文](licenses/dependencies/a56351045b45475385a9479d7310208ff0be9489babdb4e635fcea5d7dc1fb02.txt) |
| `zrender` | `6.1.0` | BSD-3-Clause | 否 / 否 | `node_modules/zrender` | [原文](licenses/dependencies/a681e9d183c72b4af4856935893805eb48c08f0f5a3f260203db7444c20fb98a.txt) |
