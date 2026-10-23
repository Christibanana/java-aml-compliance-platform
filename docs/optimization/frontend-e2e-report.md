# Vue 前端端到端测试报告

日期：2026-10-23。仅新增 3 条 Playwright 测试与隔离运行配置；未修改 Java 业务逻辑或前端业务流程。

## 现状与隔离方式

原前端只有 Vite/`vue-tsc` 构建脚本，没有 Vitest、Cypress 或 Playwright 用例。按最小引入原则增加 `@playwright/test` 1.64.1，仅运行本机已有的 Microsoft Edge（Chromium 通道），不部署额外测试平台。测试入口为 `frontend/e2e/aml-workflows.spec.ts`，测试运行器固定单工作进程、无重试。

运行脚本 `scripts/run-frontend-e2e.ps1` 在启动前拒绝复用已有端口，以 Compose 项目 `aml-frontend-e2e` 启动专属 PostgreSQL 16 容器，数据库固定为 `aml_frontend_e2e`，数据目录使用容器 `tmpfs`，结束后停用并移除该容器。后端使用本地端口 18080，前端使用 15173；原有 8080/5173 服务均未接触。演示登录和数据库密码在每次运行时随机生成，只传入子进程环境变量。客户、账户、流水号均为带随机后缀的虚构数据；测试可重复运行，不依赖上次测试的记录。第三条用例通过隔离后端 API 建立虚构客户及账户前置数据，再通过 Vue 页面完成导入、规则执行和预警查看。

## 命令与结果

在项目根目录、具备 Java 21、Maven、Node、Docker Desktop 和 Edge 的 Windows PowerShell 中运行：

```powershell
& .\scripts\run-frontend-e2e.ps1 -JavaHome 'C:\path\to\jdk-21'
```

脚本实际执行 `docker compose --progress quiet -p aml-frontend-e2e -f compose.e2e.yaml up -d --wait`、`mvn.cmd -B -DskipTests package`、`npm.cmd run build`、`npm.cmd run test:e2e`，最后执行 `docker compose ... down`。本轮指定 Java 21.0.12.1、Node 24.15.0、Docker 29.8.2、Vite 7.3.7；后端打包和前端构建成功。Vite 提示部分打包块超过 500 kB，此为非阻断警告。

最终连续三次完整运行均为 **3 passed、0 failed**（分别约 28.0、27.4 和 35.9 秒）：

| 用例 | 核验内容 | 结果 | 截图 |
| --- | --- | --- | --- |
| 登录、仪表盘、退出 | 真实登录、数据库指标页面、退出后令牌清除 | 通过 | [仪表盘](screenshots/frontend-e2e-login-dashboard.png) |
| 客户与账户关联 | 页面创建客户和账户、按客户筛选并显示 OWNER 关联 | 通过 | [客户账户](screenshots/frontend-e2e-customer-account.png) |
| 模拟交易与 R001 预警 | 页面导入 CSV、执行 R001、查询预警并打开关联交易详情 | 通过 | [R001 预警](screenshots/frontend-e2e-r001-alert.png) |

截图均取自实际 Edge 浏览器运行，内容仅含合成测试数据。失败时的 Playwright 截图与 trace 存于忽略提交的 `frontend/test-results/`；HTML 报告在 `frontend/playwright-report/`。构建和后端日志存于忽略提交的 `backend/target/`。

## 初次失败与已知问题

初次运行 3/3 未通过：隔离前端的 Origin `http://127.0.0.1:15173` 被后端既有 CORS 白名单拒绝。现仅在隔离运行模式下由 Vite 代理转发白名单 Origin，不改后端 CORS 配置。随后运行 2/3 通过，第三条暴露两个问题：

1. 规则页面默认同时执行 R001–R003 时，`RuleExecutionStateService` 将三个版本编码写入 `aml_rule_execution.scope`，长度超过 `V1__initial_schema.sql` 定义的 `varchar(30)`；PostgreSQL 报 `value too long for type character varying(30)`，预警不会生成。这是**当前默认全规则执行路径的真实缺陷，未在本次测试任务中修复**。第三条测试只针对指定的 R001，在独立测试库中先通过页面停用 R002/R003，再点击原有执行按钮。不能据此声称默认全规则执行已通过。
2. 测试最初断言预警列表显示客户编号，实际列表展示客户名称；修正为以编号筛选并核对名称。曾在预警抽屉动画尚未结束时截图，现等待动画后截图。这两项仅是测试代码调整。

第一次脚本重定向输出时，PowerShell 曾把 Docker Compose 正常进度写到 stderr 误判为终止错误；清理该测试容器后，脚本改以退出码判断原生命令，并使用 Compose quiet 模式。所有失败运行均未标记为通过。

## 边界

没有使用真实客户、真实身份资料或生产数据库。没有运行 Firefox/WebKit，也没有跨浏览器兼容性结论。当前截图与结果仅证明 R001 的隔离流程；默认 R001–R003 联合执行需要另开业务修复任务调整字段容量并补 PostgreSQL 回归测试。
