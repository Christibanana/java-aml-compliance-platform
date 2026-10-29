# 问题与阻塞记录

## 2026-10-30 当前状态

旧版 `scope varchar(30)` 的默认三规则故障已由 Flyway V7 修复并用 PostgreSQL/Edge 验证；认证 Testcontainers 测试在固定 Docker API 1.44 与本机关闭不可下载的 Ryuk 后通过。仍需在正常镜像拉取环境验证 Testcontainers 清理机制；KYC 尚无自动提醒与强制双人复核，系统仍不具备生产监管用途。见 `docs/optimization/kyc-closure-report.md`。

## v1.1.3 全量测试未通过（2026-10-23）

- `mvn -B test` 执行 40 个测试，0 断言失败、4 个错误，全部来自上一轮未提交的 `AuthPostgresIntegrationTest` 静态容器初始化；Testcontainers 报 `Could not find a valid Docker environment`，尽管本机 `docker info` 可读。尚未解决该环境适配问题，不将认证测试或全量回归标为通过。
- 排除该类的 `mvn -B -Dtest=*,!AuthPostgresIntegrationTest test` 为 36/36 通过；本轮 CSV/规则定向 9/9 通过。尚无 PostgreSQL 专项吞吐量测量。

## ARCHIVE-001：GitHub 远程归档等待确认

- 状态：待人工处理
- 当前仓库尚无 remote，宿主未安装 `gh`。按照任务边界，本轮只完成本地审查与提交准备；创建/选择 GitHub 仓库及推送需先确认账号、仓库名和公开性。
- 项目尚无 `LICENSE`/`NOTICE`，发布前需由所有者选择许可，不能代替所有者推断。

## ENV-006：Docker Desktop / WSL 数据盘短暂异常

- 状态：已恢复，建议关注宿主磁盘空间
- 2026-10-15 最终回归初次启动失败，日志包含 `WSL_E_USER_VHD_ALREADY_ATTACHED`，此前还出现 containerd blob `input/output error`。
- 仅停止 Docker 进程、执行 `wsl --shutdown` 并重新启动 Docker Desktop；未格式化、重置或删除任何卷。随后 Server 29.8.2、三容器、健康接口和完整 E2E 均恢复通过。
- C 盘空间曾非常紧张；已将未被 Git 跟踪的 JDK 安装压缩包移动到 `D:\CodexTemp\java-aml-latex-20261014\preserved\`。归档后仍应释放磁盘空间并观察 Docker 数据盘。

## DOC-001：LaTeX 编译与 PDF 显示环境

- 状态：已解决
- 已安装并更新至 MiKTeX 26.5，XeLaTeX 双遍编译三套源文件成功；LaTeX Workshop 10.19.0 已配置为使用该编译器，MiKTeX 目录已加入用户 PATH。
- 三份 PDF 均可被 PyMuPDF 解析和渲染，中文字体已嵌入且无替换字符。Edge 无头模式不加载内置 PDF 查看器，因此其空白截图不代表 PDF 损坏；VS Code 标签页查看器作为当前环境的主要显示方式。
- `latexmk` 可执行文件存在，但当前环境没有 Perl，故文档构建使用两次 `xelatex`，不把 `latexmk` 作为已验证路径。

## INTEGRATION-003：外部服务未在本机运行

- 状态：开放（不阻塞本地运行）
- 现象：Yente `localhost:8000` 与 Fineract `localhost:8443` 均连接失败，HTTP 000 / curl exit 7。
- 处理：两个真实适配器默认关闭；Yente 保留 Mock provider，交易保留本地 CSV 与 AMLSim CSV。契约由本地 HTTP 测试服务器验证，但不声称真实服务端到端成功。

## INTEGRATION-004：OpenSanctions 软件与数据许可不同

- 状态：已记录
- 结论：yente 源码为 MIT；OpenSanctions 官方明确说明商业使用名单数据可能需要单独的数据许可。项目不下载、不再分发名单数据，部署者启用托管或自托管数据前必须单独确认授权。

## TEST-008：PowerShell 参数不兼容

- 状态：已绕过
- 现象：环境中的 PowerShell 不支持 `Invoke-WebRequest -SkipCertificateCheck`，因此该命令没有完成连通性检查。
- 处理：改用 `curl.exe`（Fineract 使用 `-k` 仅作本地探测），获得真实连接失败结果并记录。

## REPORT-001：JdbcTemplate 回调 lambda 重载歧义

- 状态：已解决
- 现象：首次后端编译时，预警趋势查询的单表达式 lambda 返回 `Map.put` 结果，导致 `ResultSetExtractor` 与 `RowCallbackHandler` 重载无法判定。
- 修复：改为无返回值代码块回调；后端编译及全量测试通过。

## REPORT-002：集成测试风险等级预期与最新评分不一致

- 状态：已解决
- 现象：首轮新增集成断言预期客户资料等级 HIGH，但该客户已经生成 59 分 MEDIUM 的当前评分。
- 处理：确认仪表盘“当前评分优先、无评分回退客户资料等级”的查询正确，将断言改为 MEDIUM；重跑通过。

## TEST-007：Windows JDK 21 下 Mockito 动态自附加偶发失败

- 状态：已解决
- 现象：首次全量测试中 Byte Buddy 无法通过外部进程自附加，多个既有 Mockito 测试初始化失败；新增核心集成测试此前已独立通过。
- 修复：在 Surefire 显式加载测试范围 `byte-buddy-agent`，不再依赖 JDK 将禁用的动态自附加；本机和 Docker 镜像内全量 25/25 通过。

## SCREEN-001：Yente 适配器双构造器导致 Spring 无法注入

- 状态：已解决
- 现象：首次定向集成测试加载上下文时，`YenteScreeningProvider` 有配置构造器和测试构造器，Spring 无法自动选择。
- 修复：为公开配置构造器增加 `@Autowired`，保留包级测试构造器；定向测试及全量 25/25 测试通过。

## UI-001：风险评分页回调参数触发 TypeScript 隐式 any

- 状态：已解决
- 现象：首次 `npm run build` 在 `RiskScoresView.vue` 的表格回调参数处失败。
- 修复：补充显式类型后重新构建，1713 modules transformed，生产构建通过。

## EXT-001：本机未配置真实 Yente 服务

- 状态：环境边界，不阻塞本阶段代码验收
- 说明：没有可用的真实 Yente base URL/API key，因此未执行或宣称真实名单实连。官方 `/match/{dataset}` 契约、鉴权、响应解析、503 和禁用路径已由本地 HTTP stub 验证；部署到目标环境后仍需受控连通性测试。

## BUILD-002：Phase 3C 全量测试误用系统 Java 8

- 状态：已解决
- 现象：前两次命令使用了错误的便携 JDK 路径，`Resolve-Path` 失败后 Maven 回退到系统 Java 8，Surefire 报 class file version 61/52 不兼容，0 个测试执行。
- 处理：按既有环境记录修正为 `backend/../.tools/jdk21/jdk-21.0.12.1+1`，确认 `java/javac 21.0.12.1` 后重跑，21/21 测试通过。未修改系统环境。

## TEST-006：PowerShell 证据下载校验未加载 HttpClient 程序集

- 状态：已解决
- 现象：真实 HTTP 闭环已执行至调查笔记和首次证据上传，旧版 PowerShell 创建 `System.Net.Http.HttpClient` 时提示类型不存在，脚本中断。
- 处理：执行 `Add-Type -AssemblyName System.Net.Http`，按同一案件从 `INVESTIGATING` 状态继续，下载哈希、逻辑移除、复核与关闭全部通过，未重复创建案件。

## RULE-001：Drools 10 KieHelper 默认构建路径不兼容

- 状态：已解决
- 现象：`drools-engine:10.1.0` 下直接执行默认 `KieHelper` 构建进入经典约束路径，抛出 `DummyConstraintBuilder` 的 `UnsupportedOperationException`。
- 修复：显式使用 `ExecutableModelProject` 构建 KieBase；三条 DRL 编译及边界测试通过。

## RULE-002：PostgreSQL JDBC 无法推断 Instant 参数类型

- 状态：已解决
- 现象：规则执行记录写入 `timestamptz` 时，PostgreSQL 无法推断 `java.time.Instant` SQL 类型。
- 修复：JDBC 边界统一使用 `Timestamp.from()` 写入、`toInstant()` 读取；H2 测试与真实 PostgreSQL 均通过。

## TEST-005：浏览器自动化接口未提供

- 状态：环境限制，不阻塞功能验收
- 现象：browser 技能所需的 JavaScript 浏览器执行工具在当前会话不可用。
- 替代验证：Vue 类型检查/生产构建、Nginx 首页 HTTP 200、真实后端 API 查询与审核闭环均通过；未产出浏览器截图。

## ENV-001：系统缺少 Java 21

- 状态：已绕过（系统环境未修改）
- 现象：系统 `java` 为 Oracle JRE 1.8.0_391，且没有 `javac`。
- 处理：使用项目忽略目录 `.tools/jdk21/jdk-21.0.12.1+1` 内的 Microsoft OpenJDK 21.0.12.1；后端本地与容器构建均通过。

## ENV-002：Docker 引擎曾未启动

- 状态：已解决
- 处理：Docker Desktop Linux 引擎现为 29.8.2，Compose v5.6.0；三服务已完成整栈验证。

## ENV-003：外部网络不稳定

- 状态：未完全消除，但不再阻塞
- 现象：GitHub 曾超时；Maven Central 下载偶发提前中断。
- 处理：保留真实失败记录，使用 BuildKit Maven 缓存重试；所有必需镜像和依赖最终获取成功。

## ENV-004：Docker Registry 代理失效

- 状态：已解决/不可复现
- 处理：2026-10-14 已成功拉取 PostgreSQL、Maven、Temurin、Node 和 Nginx 镜像，并构建项目镜像。未修改用户全局代理设置。

## ENV-005：宿主端口 8080 被占用

- 状态：已规避
- 现象：PID 9836 占用 8080；Spring Boot 默认端口启动因此失败。
- 处理：未终止用户进程；Compose 端口改为环境变量可覆盖，本次使用 18080/15173 完成验证。

## TEST-001：前端第三方类型声明冲突

- 状态：已解决
- 修复：启用 `skipLibCheck`，项目源码仍保持 `strict` 与 `noEmit`；生产构建通过。

## TEST-002：首次 Java 21 编译失败

- 状态：已解决
- 修复：补全 JWT 过滤器异常导入并消除领域 `User` 类型歧义；测试与打包通过。

## TEST-003：PostgreSQL CHAR 字段校验失败

- 状态：已解决
- 现象：Flyway 的 `CHAR(2/3)` 被 PostgreSQL 报告为 `bpchar`，Hibernate 实体最初按 `VARCHAR` 校验，应用启动失败。
- 修复：为 `Account.currency`、`Customer.countryCode`、`AmlTransaction.currency` 增加 `@JdbcTypeCode(SqlTypes.CHAR)` 并保留相应 `char(n)` 定义；真实 PostgreSQL 启动和整栈验证通过。

## BUILD-001：后端容器 Maven 依赖下载中断

- 状态：已解决
- 现象：首次 `go-offline` 依赖图过大；后续 `zstd-jni` 下载仅收到 2,289,664/6,776,400 bytes。
- 修复：Dockerfile 改为直接执行 `mvn -B -U package` 并挂载 BuildKit `/root/.m2` 缓存；重试后构建成功，容器内 9/9 测试通过。

## TEST-004：PowerShell 5 不支持 SkipHttpErrorCheck

- 状态：已绕过
- 现象：验证非法请求时，`Invoke-WebRequest -SkipHttpErrorCheck` 返回参数不存在。
- 处理：改用 `Invoke-RestMethod` 的异常响应流读取 HTTP 400 与 JSON 错误体；不影响应用功能。
