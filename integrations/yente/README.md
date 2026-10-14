# OpenSanctions / Yente 适配状态

- 官方仓库：https://github.com/opensanctions/yente
- 核验分支/提交：`main` / `9a2bb5e8c4a9cabb06203ae82d4a1776b303fd80`
- 软件许可证：MIT。
- 重要边界：Yente 软件许可证不等于 OpenSanctions 数据许可；官方说明商业使用 OpenSanctions 数据可能需要单独的数据许可。

## 已实现

`YenteScreeningProvider` 实现当前官方 `POST /match/{dataset}?threshold=...` 契约：

- `queries -> screening -> schema/properties` 请求结构
- `responses -> screening -> results` 响应结构
- `Authorization: ApiKey ...`
- 连接和请求超时、非 2xx 与坏响应显式失败
- 候选 ID、caption、score、datasets 和原始 payload 保存
- 真实 Yente 候选标记为 `REAL`，本地 Mock 候选标记为 `SIMULATED`

默认 `AML_YENTE_ENABLED=false` 且默认 provider 为 `MOCK`。Yente 失败不会静默返回空匹配，也不会自动伪装成 Mock 成功；本地 Mock 筛查可独立运行。

## 未实现

- 当前环境没有运行 Yente/OpenSearch，也没有配置托管 OpenSanctions API key，未完成真实名单数据端到端验证。
- 未自动下载或分发 OpenSanctions 数据集。
- 未实现实体详情、增量变更、批量异步筛查和 false-positive 排除列表同步。
