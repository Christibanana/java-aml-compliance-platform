# IBM AMLSim 适配状态

- 官方仓库：https://github.com/IBM/AMLSim
- 核验分支/提交：`master` / `7338a4bcb1af9bcfea2201ad7daccfe2a4d569ca`
- 许可证：Apache License 2.0。
- 数据属性：AMLSim 生成的是合成银行交易与已知洗钱模式标签，不是真实客户或监管结论。

## 已实现

`POST /api/transactions/import/amlsim?currency=USD` 接受官方默认 `transactions.csv` 的必要列：

`tran_id,tran_timestamp,base_amt,tx_type,orig_acct,bene_acct,is_sar,alert_id`

列可调整顺序并可包含额外字段。平台优先把 `orig_acct` 映射为本地账户并记为 `OUT`；若发起账户不存在但 `bene_acct` 存在，则映射为 `IN`。两个账户都不存在时拒绝该行。AMLSim 默认交易 CSV 没有币种，因此币种必须由导入参数明确给出并与本地账户一致。幂等键为 `source=AMLSIM + externalId=AMLSIM-{tran_id}`。

`is_sar` 和 `alert_id` 只保存在交易说明中，明确作为合成标签；不会直接生成平台预警、案件或监管报告。示例见 `official-transactions-sample.csv`。

## 未实现

- 未在本仓库内构建或运行 AMLSim 模拟器；仅核验官方仓库和输出 schema。
- 未导入 AMLSim 的账户、参与方、`alert_transactions.csv` 或图拓扑文件。
- 未把 AMLSim `is_sar` 当成真实可疑交易报告。
