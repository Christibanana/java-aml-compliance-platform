# 实际技术栈与环境核验

## 实际采用的技术

| 层次 | 实际技术/版本 | 证据 |
|---|---|---|
| 后端 | Java 21、Spring Boot 3.5.7 | `pom.xml`、Dockerfile、运行容器 |
| Web/校验 | Spring MVC、Jakarta Validation | starter 与 Controller DTO |
| 安全 | Spring Security 6.5.6、JWT 0.12.6、BCrypt 12 | `security`、`auth` 包 |
| 数据 | Spring Data JPA、Hibernate 6.6.33、JdbcTemplate | Repository 与查询服务 |
| 数据库 | PostgreSQL 16.10、Flyway 11.7.2 | Compose、POM、V1–V6 |
| 规则 | Drools/KIE 10.1.0 | POM、`DroolsRuleEvaluator` |
| API 文档 | springdoc-openapi 2.8.13 | POM、`/v3/api-docs` |
| 后端测试 | JUnit 5、Mockito、MockMvc、AssertJ、H2 2.3.232 | test scope 与 18 个测试类 |
| 前端 | Vue 3.5、TypeScript 5.9、Vite 7.3 | package-lock 实际构建输出 |
| UI/状态/HTTP | Element Plus 2.11、Pinia 3、Vue Router 4.6、Axios 1.12、ECharts 6 | `package.json` 与源码 |
| 部署 | Docker 29.8.2、Compose 5.6.0、Nginx 1.29 Alpine | 环境与 Dockerfile |

## 宿主环境

- 当前工作区父目录为 `C:\Users\86152\Desktop\Cpp`，项目根为其下 `java-aml-platform`。
- 宿主 `java` 是 Oracle JRE 8u391，且没有 `javac`；Maven 3.9.16 也绑定 Java 8，不能直接构建本项目。
- 容器构建使用 `maven:3.9.11-eclipse-temurin-21`，运行使用 Temurin 21 JRE；本次测试实际使用 Java 21。
- Node.js v24.15.0、npm 11.12.1。
- Docker Client/Server 29.8.2、Docker Compose v5.6.0。
- 已安装 MiKTeX 26.5，XeLaTeX 4.18 可用；三份交付 PDF 已由 XeLaTeX 双遍编译并完成结构和渲染抽检。LaTeX Workshop 10.19.0 已配置；`latexmk` 因缺少 Perl 未作为验证路径。
- 当前会话没有内置浏览器自动化执行接口；最终整理阶段使用本机 Edge 与临时 Puppeteer 控制实际 localhost 页面，取得 11 张截图并人工抽检。仓库仍没有前端自动化测试套件。

## 配置与运行方式

- Hibernate 为 `ddl-auto=validate`，结构只由 Flyway 演进。
- JDBC 时间区设为 UTC；业务报表日期边界按 Asia/Shanghai 计算。
- dev profile 默认启用演示管理员；Yente/Fineract 默认关闭，Mock 筛查默认启用。
- Nginx 提供 SPA fallback，并代理 `/api/` 和 `/actuator/`。
- PostgreSQL 和证据文件分别使用命名卷。

## 没有采用或尚未具备

- 未使用微服务、消息队列、缓存、图数据库或工作流引擎；实际架构是模块化单体。
- 2026-10-15 历史基线没有 Testcontainers、前端单元测试、ESLint、浏览器自动化或 CI 配置。当前工作区已增加 Testcontainers 依赖及认证 PostgreSQL 测试源码，但依赖下载和测试尚未完成；不能将其列为已验证覆盖。
- 审查后新增三套 LaTeX 文档源文件和已编译 PDF；这属于归档文档，不改变业务功能。
- 没有生产密钥管理、TLS、对象存储、病毒扫描、集中可观测性或高可用部署。
