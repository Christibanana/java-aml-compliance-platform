# 环境检查报告

检查时间：2026-10-14（Asia/Shanghai）
工作目录：`C:\Users\86152\Desktop\Cpp\java-aml-platform`

| 项目 | 检测值 | 结论 |
|---|---|---|
| Git | 2.53.0.windows.1 | 可用 |
| 系统 Java | Oracle JRE 1.8.0_391，无 `javac` | 不满足 Java 21；未改动系统配置 |
| 项目便携 JDK | Microsoft OpenJDK 21.0.12.1 | 可用，实际构建已通过 |
| Maven | 3.9.16 | 可用；构建时显式设置项目 JDK 21 |
| Node.js | v24.15.0 | 可用 |
| npm | 11.12.1 | 可用 |
| Docker CLI / Engine | 29.8.2 / 29.8.2 | 可用 |
| Docker Compose | v5.6.0 | 可用 |
| PostgreSQL | 16.10（Alpine 容器） | healthy，`pg_isready` 接受连接 |
| 宿主 8080 | 被 PID 9836 占用 | 本次用可配置端口 18080 验证，未终止现有进程 |

## 结论

系统 Java 仍为 Java 8，但项目内 `.tools/jdk21/jdk-21.0.12.1+1` 已提供可复现的 Java 21 构建环境。Docker 引擎、镜像拉取和 Compose 均已恢复可用。Maven Central 下载曾出现连接提前中断，利用 BuildKit Maven 缓存重试后镜像构建成功。环境满足 Phase 2 的本机开发和容器化运行要求。
