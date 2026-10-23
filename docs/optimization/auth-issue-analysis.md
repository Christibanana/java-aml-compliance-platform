# 登录失败事务问题分析

## 位置与触发

`AuthController.login` 标注 `@Transactional`。已存在且启用的账号输入错误密码时，调用 `User.loginFailed()` 修改托管实体，调用 `AuditService.record()` 通过 `JdbcTemplate` 写入 `sys_audit_log`，最后抛出 `IllegalArgumentException`。

## 事务传播与回滚

Controller 开启默认 `REQUIRED` 事务；JPA 更新和 `JdbcTemplate` 审计写入都加入同一事务。未检查异常触发 Spring 默认回滚，HTTP 返回 400，数据库中失败次数、锁定时间和失败审计均不保存。不存在的账号直接抛异常，也没有审计。并发请求还缺少锁定同一用户行的机制。

## 受影响数据

`sys_user.failed_attempts`、`sys_user.locked_until`、`sys_audit_log`。既有第 5 次失败锁定 15 分钟的实体策略实际上不能持续生效。

## 最小修复

移除 Controller 事务，把状态变更和审计交给独立 Spring Service 的 `REQUIRES_NEW` 方法。在该事务中以 PostgreSQL 行级锁读取用户、更新失败次数或重置状态并写入审计；事务正常提交后由非事务 Controller 抛统一的 401 认证异常。账号不存在返回同样的 401 文案，并写不含凭据的失败审计。审计写入异常使状态事务回滚并返回服务器错误，不冒充认证审计成功。锁定到期即自动解锁，保留原有 5 次和 15 分钟策略；不新增管理解锁 API。
