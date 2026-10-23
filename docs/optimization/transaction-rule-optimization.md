# v1.1.3 CSV 与规则执行可靠性优化

日期：2026-10-23。范围仅限交易导入、R001–R003 规则执行和相关测试；R004–R008 仍为禁用规划项。

## 修改前问题与方案

平台及 AMLSim CSV 原先逐次 `BufferedReader.readLine()` 后自行解析，引号字段内换行被拆成错误记录。现使用 Apache Commons CSV 1.14.1 流式读取，剥离可选 UTF-8 BOM，支持引号、字段内逗号/换行、双引号转义及空字段；错误按逻辑记录编号定位。版本兼容性参考 [Apache Commons CSV 发布说明](https://commons.apache.org/proper/commons-csv/changes.html)。

原导入方法把整个文件置于一个事务。现在最多积累 100 条有效记录后由 `ImportChunkWriter` 独立事务提交；单个块失败则回滚该块并逐条重试，先前已提交的块不受后续坏行影响。保留批次编号、总数、成功、重复、拒绝和最多 100 条错误；文件级异常标记批次 FAILED。同文件重复编号由集合拦截，数据库 `source + external_id` 唯一约束仍为最终去重边界。

规则执行原先将截至 `asOf` 的全部历史交易读入 JVM。R001 现在按当前版本金额阈值在 SQL 中筛选，但保留全部历史时间范围；R002/R003 分别以配置的分钟/小时窗口为下界，包含下界且排除 `asOf` 之后的交易。DRL、聚合分组和命中键不变。`processed` 现计进入评价的去重交易数，`candidates` 计每条规则构建的事实数，可能少于旧版扫描计数；命中判断保持不变。现有交易时间索引可服务窗口查询，本轮未新增索引。

`RuleExecutionStateService` 使用独立事务创建、完成和标记 FAILED，`scope` 保存实际规则版本；保存开始/结束时间、状态、处理数、命中数和固定错误类别，不保存异常消息或秘密。业务事务失败时命中/预警回滚，FAILED 记录保留。参数预检在运行记录创建前失败时仍无执行记录。

## 修改文件

`backend/pom.xml`；`transaction/CsvInput.java`、`ImportChunkWriter.java`、两个导入服务和批次实体；`rule/RuleExecutionService.java`、`RuleExecutionStateService.java`；对应测试和真实 CSV 样例 `backend/src/test/resources/csv/quoted-multiline.csv`。本次提交不包含上一轮认证修复及 Testcontainers 依赖。

## 测试结果

- JDK 21 下 `mvn -B -DskipTests compile` 成功；定向 CSV/规则/核心闭环测试 9/9 通过，覆盖 BOM、多行字段、转义、重复、1001 条分块、三规则窗口边界、重复执行与 FAILED 记录。
- 初次测试编译被上一轮未提交的认证测试泛型断言阻断；修正该测试源码后重跑。新增大文件测试首轮因 Mockito 记录可变列表而失败，向写入器传递不可变快照后重跑通过。
- 完整 `mvn -B test` 执行 40 个用例，0 断言失败、4 个错误，均为上一轮认证 PostgreSQL 测试的 Testcontainers 无法发现 Docker 环境。排除该类后，`mvn -B -Dtest=*,!AuthPostgresIntegrationTest test` 为 36/36 通过；**全量回归未通过**。

## 性能与未解决问题

未测量吞吐量或数据库耗时，不填写性能提升数值。10 MB 上传上限未变，同文件去重集合随记录数增长；批次提交后若审计写入失败，已提交交易不能回滚。CSV 语法损坏时停止文件并保留已提交的块，尚无断点续传。R001 在阈值很低时仍可能读取大量历史候选；规则预检失败仍不生成执行记录。本轮未完成独立 PostgreSQL 性能基准。
