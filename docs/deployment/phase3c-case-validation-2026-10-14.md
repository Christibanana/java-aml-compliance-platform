# Phase 3C 案件调查验证记录（2026-10-14）

工作区：`C:\Users\86152\Desktop\Cpp\java-aml-platform`
时区：Asia/Shanghai
范围：案件分派、调查笔记、证据文件、状态机、关闭、审计，以及交易→规则→预警→案件→关闭闭环。

## 1. 自动化测试

### 1.1 定向测试

```powershell
$env:JAVA_HOME=(Resolve-Path '..\.tools\jdk21\jdk-21.0.12.1+1').Path
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B '-Dtest=CaseServiceTest,CaseEvidenceServiceTest,CoreWorkflowIntegrationTest' test
```

结果：`BUILD SUCCESS`；5 tests，0 failures，0 errors，0 skipped。集成测试完成预警升级、分派、笔记、证据上传/下载/移除、复核、关闭和关闭后拒绝写入。

### 1.2 全量测试中的环境重试

首次误用路径：

```powershell
$env:JAVA_HOME=(Resolve-Path '..\.tools\microsoft-jdk-21.0.12.1').Path
mvn -B test
```

第二次误用路径：

```powershell
$env:JAVA_HOME=(Resolve-Path '..\..\.tools\microsoft-jdk-21.0.12.1').Path
mvn -B test
```

结果：两次 `Resolve-Path` 均失败，Maven 回退到系统 Java 8；Surefire 报 class file version 61.0/52.0，0 tests。属于验证命令路径错误，不是代码测试失败。

修正并重跑：

```powershell
$env:JAVA_HOME=(Resolve-Path '..\.tools\jdk21\jdk-21.0.12.1+1').Path
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
javac -version
mvn -B test
```

结果：Microsoft OpenJDK/Javac 21.0.12.1；`BUILD SUCCESS`；21 tests，0 failures，0 errors，0 skipped。

## 2. 前端与 Docker 构建

```powershell
cd frontend
npm run build
```

结果：Vue TypeScript 检查通过，Vite 7.3.7，1709 modules transformed；保留约 1.09 MB 主 chunk 警告，不阻塞功能验收。

```powershell
$env:AML_BACKEND_HOST_PORT='18080'
$env:AML_FRONTEND_HOST_PORT='15173'
docker compose --profile app config --quiet
docker compose --profile app build backend frontend
```

结果：配置解析通过；前后端镜像均构建成功；后端镜像构建内再次执行 21/21 tests。

```powershell
docker compose --profile app up -d --no-build --force-recreate backend frontend
Invoke-RestMethod http://localhost:18080/actuator/health
Invoke-RestMethod http://localhost:15173/actuator/health
Invoke-WebRequest http://localhost:15173/
Invoke-WebRequest http://localhost:15173/cases
docker compose --profile app ps
```

结果：backend 与 Nginx 代理均为 `UP`；首页和 SPA `/cases` 均 HTTP 200；postgres、backend、frontend 三容器运行，端口为 5432/18080/15173。

## 3. Flyway V5 与数据库结构

```powershell
docker compose exec -T postgres psql -U aml -d aml -P pager=off `
  -c 'select installed_rank,version,description,success from flyway_schema_history order by installed_rank;' `
  -c "select column_name,data_type,is_nullable from information_schema.columns where table_name='aml_case_evidence' order by ordinal_position;"
docker compose logs --tail=80 backend | Select-String -Pattern 'Flyway|Migrat|Started Aml|ERROR'
```

结果：V1～V5 全部 `success=true`；日志显示 schema 从 v4 成功迁移到 v5 `case investigation`。`aml_case_evidence` 当前 15 个字段，新增 `active/removed_at/removed_by/removal_reason`；应用在 PostgreSQL 16.10 上启动成功。

## 4. 真实 HTTP 完整链路

使用 `POST /api/auth/login` 获取临时 JWT（令牌未写入文档），选择既有 `NEW` 状态 AML-R001 预警，然后依次执行：

```text
POST /api/alerts/{alertId}/transition       IN_REVIEW
POST /api/alerts/{alertId}/escalate         创建案件
GET  /api/cases/investigators               查询合格调查员
POST /api/cases/{caseId}/assign             分派 admin
POST /api/cases/{caseId}/transition         INVESTIGATING
POST /api/cases/{caseId}/notes              添加调查笔记
POST /api/cases/{caseId}/evidence           上传 TXT 证据
GET  /api/cases/{caseId}/evidence/{id}/download
DELETE /api/cases/{caseId}/evidence/{id}    带原因逻辑移除
POST /api/cases/{caseId}/evidence           再上传一份活动证据
POST /api/cases/{caseId}/transition         PENDING_REVIEW
POST /api/cases/{caseId}/transition         CLOSED
GET  /api/cases/{caseId}
GET  /api/audit-logs
```

证据文件为 `integrations/evidence/case-evidence-demo.txt`，上传 MIME 为 `text/plain`。首次下载校验脚本在旧版 PowerShell 因未加载 `System.Net.Http` 中断；服务端已成功完成到首次上传。执行下列命令后从同一案件续跑，没有重复创建案件：

```powershell
Add-Type -AssemblyName System.Net.Http
```

结果：

- 交易：`TX-RULE-LARGE-001`，150000.0000 CNY。
- 规则：`AML-R001` v2。
- 预警：`ALT-6559532D-04F6`，最终 `ESCALATED`。
- 案件：`CASE-1791956341822-9FB95427`，调查员 `admin`，最终 `CLOSED`。
- 状态历史：创建→OPEN、OPEN→INVESTIGATING、INVESTIGATING→PENDING_REVIEW、PENDING_REVIEW→CLOSED。
- 证据 SHA-256：`62d86b98b980243dcea5d6ee58c336da181bd18072c60b41fd1c4c9b72ba34bb`；下载内容哈希完全一致。
- 第一份证据逻辑移除后不再出现在活动清单，数据库保留 `active=false` 和完整移除元数据；第二份相同证据保持活动状态。

关闭后写入验证：

```powershell
Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:18080/api/cases/9fb95427-398c-4379-87e8-ed2ee2dcf64a/notes' `
  -Headers @{Authorization='Bearer <redacted>'} `
  -ContentType 'application/json' `
  -Body '{"content":"must be rejected after closure"}'
```

结果：HTTP 400。此前一次 `curl.exe` 版本的相同检查因 PowerShell JSON/参数引用方式错误返回 401；改用与其他验证一致的 `Invoke-RestMethod` 后确认业务结果为 400。

## 5. SQL 关联与审计核验

```sql
select a.alert_no,r.code,t.external_id,t.amount,t.currency,c.case_no,c.status
from aml_case c
join aml_case_alert ca on ca.case_id=c.id
join aml_alert a on a.id=ca.alert_id
join aml_rule_hit h on h.id=a.rule_hit_id
join aml_rule r on r.id=h.rule_id
join aml_rule_hit_transaction ht on ht.rule_hit_id=h.id
join aml_transaction t on t.id=ht.transaction_id
where c.id='9fb95427-398c-4379-87e8-ed2ee2dcf64a';

select action,actor,result,occurred_at
from sys_audit_log
where resource_id='9fb95427-398c-4379-87e8-ed2ee2dcf64a'
order by occurred_at;

select original_name,active,sha256,removed_at is not null as removal_recorded
from aml_case_evidence
where case_id='9fb95427-398c-4379-87e8-ed2ee2dcf64a'
order by created_at;
```

结果：关联查询返回完整交易→规则→预警→案件链；案件资源共有 10 条成功审计记录：CREATE、ASSIGN、3 次 TRANSITION、ADD_NOTE、2 次 UPLOAD_EVIDENCE、DOWNLOAD_EVIDENCE、REMOVE_EVIDENCE。

## 6. 验收结论

Phase 3C 案件调查模块已通过单元/集成测试、前端生产构建、Docker 镜像构建、真实 PostgreSQL/Flyway、HTTP 状态机、证据完整性和审计追溯验证。多预警手工并案、病毒扫描和外部对象存储明确留作后续扩展，不影响本阶段验收。
