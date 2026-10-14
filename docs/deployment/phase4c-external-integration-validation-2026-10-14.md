# Phase 4C 外部项目与适配器核验记录（2026-10-14）

## 官方仓库与许可证

| 项目 | 官方仓库 | 核验分支/提交 | 软件许可证 | 结论 |
|---|---|---|---|---|
| IBM AMLSim | https://github.com/IBM/AMLSim | `master` / `7338a4bcb1af9bcfea2201ad7daccfe2a4d569ca` | Apache-2.0 | 可用于合成交易测试；生成标签不是真实 SAR 结论 |
| Apache Fineract | https://github.com/apache/fineract | `develop` / `d4ef4069547fba99400871877ceefadb62ad585b` | Apache-2.0 | 可通过 REST API 读取核心银行账户与交易；生产部署责任由使用方承担 |
| OpenSanctions/yente | https://github.com/opensanctions/yente | `main` / `9a2bb5e8c4a9cabb06203ae82d4a1776b303fd80` | MIT | 软件可自托管；OpenSanctions 名单数据可能需要另行许可，不能把 MIT 软件许可当作数据许可 |

核验来源包括各官方仓库的 `LICENSE`、README、AMLSim `paramFiles/schema.json`、Fineract OpenAPI/官方 API 文档以及 OpenSanctions 当前 OpenAPI 和认证文档。项目没有复制上游源代码或名单数据，仅实现独立 HTTP/CSV 适配。

## 已实现集成

### AMLSim

- 新增官方默认交易 CSV 适配：`tran_id,tran_timestamp,base_amt,tx_type,orig_acct,bene_acct,is_sar,alert_id`。
- 支持列重排和附加列；按本地账户映射确定 `IN/OUT`。
- AMLSim CSV 没有币种，导入时必须显式指定并校验本地账户币种。
- 来源保存为 `AMLSIM`，审计保存 `dataMode=SIMULATED`。
- `is_sar`/`alert_id` 仅作为合成标签说明，不直接产生预警或案件。

### Apache Fineract

- 新增只读 `FineractApiAdapter` 和 `FineractClient` 接口。
- 使用 Basic Auth、`fineract-platform-tenantid`、超时和 `associations=transactions`。
- 新增储蓄账户流水导入，按本地 `accountNo` 与币种严格映射，冲正和方向不明确流水不导入。
- 默认关闭；失败返回 HTTP 502 类型错误，不伪造空结果或本地成功。

### OpenSanctions/Yente

- 已有 `ScreeningProvider`、Yente `/match/{dataset}` 适配器和 Mock provider。
- 本次重新对照当前官方 OpenAPI 核验请求、响应、threshold 和 `ApiKey` 认证方式。
- Yente 为 `REAL`，Mock 为 `SIMULATED`；真实 provider 失败不会静默切换 Mock。

## 未实现与未验证

- 没有在本机启动 IBM AMLSim 模拟器；只实现并测试其默认输出格式导入。
- 没有在本机启动 Apache Fineract；HTTP 契约由本地测试服务器验证，不声称真实实例集成成功。
- 没有在本机启动 Yente/OpenSearch，也没有托管 API key；不声称真实 OpenSanctions 数据筛查成功。
- 未实现 Fineract 客户/贷款/分页增量同步、AMLSim 账户/图/SAR 文件导入、Yente 实体详情和增量同步。

## 命令与结果

| 命令/检查 | 结果 |
|---|---|
| `git ls-remote https://github.com/IBM/AMLSim.git refs/heads/master` | 成功；提交 `7338a4bc...` |
| `git ls-remote https://github.com/apache/fineract.git refs/heads/develop` | 成功；提交 `d4ef4069...` |
| `git ls-remote https://github.com/opensanctions/yente.git refs/heads/main` | 成功；提交 `9a2bb5e8...` |
| PowerShell `Invoke-WebRequest -SkipCertificateCheck` 检查本地服务 | 失败；当前 PowerShell 不支持该参数，未形成服务可用性结论 |
| `curl.exe ... http://localhost:8000/healthz` | 失败，exit 7 / HTTP 000；本机 Yente 未运行 |
| `curl.exe -k ... https://localhost:8443/fineract-provider/actuator/health` | 失败，exit 7 / HTTP 000；本机 Fineract 未运行 |
| `mvn -B -Dtest=AmlSimImportServiceTest,FineractApiAdapterTest,FineractImportServiceTest,YenteScreeningProviderTest test` | 通过；8 tests，0 failures/errors/skipped |
| `npm run build` | 通过；2307 modules；保留既有大 chunk 警告 |
| `mvn -B test` | 全量通过；30 tests，0 failures/errors/skipped |
| `mvn -B -Dtest=CoreWorkflowIntegrationTest test`（补充 HTTP 集成断言后） | 通过；验证状态接口、Fineract 502、AMLSim 首次导入与重复幂等 |
| `docker compose --profile app build backend frontend` | 通过；镜像内后端 30/30，前端 2307 modules |
| `docker compose --profile app up -d --force-recreate backend frontend` | 通过；backend、frontend、PostgreSQL 正常运行，PostgreSQL healthy |
| `GET /api/transactions/integrations` | LOCAL_CSV/AMLSIM_CSV=`SIMULATED, available=true`；FINERACT_API=`EXTERNAL, available=false` |
| 禁用状态调用 `POST /api/transactions/import/fineract/1` | HTTP 502；未生成替代交易 |
| 首次导入 `official-transactions-sample.csv?currency=CNY` | PostgreSQL 实际写入 1 条；source=`AMLSIM`、direction=`OUT`、amount=`1000.0000` |
| 再次导入同一 AMLSim 文件 | imported=0、duplicates=1；数据库幂等生效 |
| `GET http://localhost:5173/transactions` | HTTP 200 |

AMLSim 的上述一条合成交易保留在开发数据库中，用于演示与回归；它不是生产数据。未执行或缺少真实上游服务的项目均未写为通过。
