CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE sys_role (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), code varchar(50) NOT NULL UNIQUE, name varchar(100) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE sys_permission (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), code varchar(100) NOT NULL UNIQUE, name varchar(100) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE sys_user (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), username varchar(80) NOT NULL UNIQUE, password_hash varchar(255) NOT NULL,
  display_name varchar(120) NOT NULL, enabled boolean NOT NULL DEFAULT true, failed_attempts int NOT NULL DEFAULT 0,
  locked_until timestamptz, last_login_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE sys_user_role (user_id uuid NOT NULL REFERENCES sys_user(id), role_id uuid NOT NULL REFERENCES sys_role(id), PRIMARY KEY(user_id, role_id));
CREATE TABLE sys_role_permission (role_id uuid NOT NULL REFERENCES sys_role(id), permission_id uuid NOT NULL REFERENCES sys_permission(id), PRIMARY KEY(role_id, permission_id));

CREATE TABLE aml_customer (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), customer_no varchar(40) NOT NULL UNIQUE, customer_type varchar(20) NOT NULL,
  full_name varchar(200) NOT NULL, id_type varchar(30), id_number_masked varchar(100), country_code char(2),
  risk_level varchar(10) NOT NULL DEFAULT 'LOW', status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_customer_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);
CREATE INDEX idx_customer_name ON aml_customer(full_name);
CREATE INDEX idx_customer_risk ON aml_customer(risk_level);
CREATE TABLE aml_customer_kyc (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), customer_id uuid NOT NULL REFERENCES aml_customer(id), status varchar(20) NOT NULL,
  verified_at timestamptz, expires_at timestamptz, source varchar(80), notes text,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_kyc_customer ON aml_customer_kyc(customer_id);
CREATE TABLE aml_customer_risk (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), customer_id uuid NOT NULL UNIQUE REFERENCES aml_customer(id), score numeric(8,2) NOT NULL,
  risk_level varchar(10) NOT NULL, model_version varchar(40) NOT NULL, explanation text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_customer_risk_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), customer_id uuid NOT NULL REFERENCES aml_customer(id), score numeric(8,2) NOT NULL,
  risk_level varchar(10) NOT NULL, model_version varchar(40) NOT NULL, explanation text NOT NULL, assessed_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);

CREATE TABLE aml_account (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_no varchar(64) NOT NULL UNIQUE, currency char(3) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'ACTIVE', opened_at timestamptz NOT NULL, closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_account_customer (
  account_id uuid NOT NULL REFERENCES aml_account(id), customer_id uuid NOT NULL REFERENCES aml_customer(id), relation_type varchar(20) NOT NULL,
  ownership_percent numeric(5,2), created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(account_id, customer_id)
);
CREATE INDEX idx_account_customer_customer ON aml_account_customer(customer_id);

CREATE TABLE aml_transaction_import_batch (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), batch_no varchar(50) NOT NULL UNIQUE, source varchar(40) NOT NULL,
  file_name varchar(255), total_count int NOT NULL DEFAULT 0, success_count int NOT NULL DEFAULT 0, error_count int NOT NULL DEFAULT 0,
  status varchar(20) NOT NULL, error_details text,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_transaction (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), external_id varchar(100) NOT NULL, account_id uuid NOT NULL REFERENCES aml_account(id),
  counterparty_account varchar(100), direction varchar(10) NOT NULL, amount numeric(19,4) NOT NULL, currency char(3) NOT NULL,
  transaction_time timestamptz NOT NULL, transaction_type varchar(30) NOT NULL, status varchar(20) NOT NULL, source varchar(40) NOT NULL,
  description varchar(500), import_batch_id uuid REFERENCES aml_transaction_import_batch(id),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
  CONSTRAINT uk_transaction_source_external UNIQUE(source, external_id), CONSTRAINT ck_transaction_amount CHECK(amount > 0)
);
CREATE INDEX idx_tx_account_time ON aml_transaction(account_id, transaction_time DESC);
CREATE INDEX idx_tx_time ON aml_transaction(transaction_time DESC);

CREATE TABLE aml_screening_request (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), customer_id uuid REFERENCES aml_customer(id), query_name varchar(200) NOT NULL,
  provider varchar(40) NOT NULL, provider_mode varchar(20) NOT NULL, status varchar(20) NOT NULL, requested_by uuid REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_screening_match (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), request_id uuid NOT NULL REFERENCES aml_screening_request(id), external_entity_id varchar(120),
  matched_name varchar(200) NOT NULL, score numeric(5,4) NOT NULL, dataset varchar(100), payload_json text,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_screening_review (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), match_id uuid NOT NULL REFERENCES aml_screening_match(id), decision varchar(30) NOT NULL,
  comment text NOT NULL, reviewer_id uuid REFERENCES sys_user(id), reviewed_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);

CREATE TABLE aml_rule (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), code varchar(30) NOT NULL UNIQUE, name varchar(150) NOT NULL, rule_type varchar(40) NOT NULL,
  enabled boolean NOT NULL DEFAULT true, severity varchar(10) NOT NULL, active_version int NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_rule_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), rule_id uuid NOT NULL REFERENCES aml_rule(id), version_no int NOT NULL,
  parameters_json text NOT NULL, description text, created_by uuid REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
  UNIQUE(rule_id, version_no)
);
CREATE TABLE aml_rule_execution (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), execution_key varchar(160) NOT NULL UNIQUE, scope varchar(30) NOT NULL,
  started_at timestamptz NOT NULL, finished_at timestamptz, status varchar(20) NOT NULL, processed_count int NOT NULL DEFAULT 0, hit_count int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_rule_hit (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), execution_id uuid NOT NULL REFERENCES aml_rule_execution(id), rule_id uuid NOT NULL REFERENCES aml_rule(id),
  rule_version int NOT NULL, transaction_id uuid REFERENCES aml_transaction(id), customer_id uuid REFERENCES aml_customer(id),
  hit_key varchar(200) NOT NULL UNIQUE, score numeric(8,2), explanation text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);

CREATE TABLE aml_alert (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), alert_no varchar(50) NOT NULL UNIQUE, rule_hit_id uuid NOT NULL UNIQUE REFERENCES aml_rule_hit(id),
  customer_id uuid REFERENCES aml_customer(id), risk_level varchar(10) NOT NULL, status varchar(20) NOT NULL, assigned_to uuid REFERENCES sys_user(id),
  summary varchar(500) NOT NULL, closed_reason text,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_alert_status CHECK(status IN ('NEW','ASSIGNED','IN_REVIEW','CLOSED','ESCALATED'))
);
CREATE INDEX idx_alert_status ON aml_alert(status, created_at DESC);
CREATE TABLE aml_alert_transaction (alert_id uuid NOT NULL REFERENCES aml_alert(id), transaction_id uuid NOT NULL REFERENCES aml_transaction(id), PRIMARY KEY(alert_id, transaction_id));
CREATE TABLE aml_alert_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), alert_id uuid NOT NULL REFERENCES aml_alert(id), from_status varchar(20), to_status varchar(20) NOT NULL,
  operator_id uuid REFERENCES sys_user(id), comment text, occurred_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);

CREATE TABLE aml_case (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), case_no varchar(50) NOT NULL UNIQUE, title varchar(200) NOT NULL, status varchar(30) NOT NULL,
  priority varchar(10) NOT NULL, investigator_id uuid REFERENCES sys_user(id), conclusion text, submitted_at timestamptz, closed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_case_status CHECK(status IN ('OPEN','INVESTIGATING','PENDING_REVIEW','CLOSED'))
);
CREATE INDEX idx_case_status ON aml_case(status, created_at DESC);
CREATE TABLE aml_case_alert (case_id uuid NOT NULL REFERENCES aml_case(id), alert_id uuid NOT NULL UNIQUE REFERENCES aml_alert(id), PRIMARY KEY(case_id, alert_id));
CREATE TABLE aml_case_note (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), case_id uuid NOT NULL REFERENCES aml_case(id), author_id uuid REFERENCES sys_user(id), content text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_case_evidence (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), case_id uuid NOT NULL REFERENCES aml_case(id), original_name varchar(255) NOT NULL,
  storage_key varchar(255) NOT NULL, content_type varchar(100) NOT NULL, size_bytes bigint NOT NULL, sha256 char(64) NOT NULL,
  uploaded_by uuid REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_case_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), case_id uuid NOT NULL REFERENCES aml_case(id), from_status varchar(30), to_status varchar(30) NOT NULL,
  operator_id uuid REFERENCES sys_user(id), comment text, occurred_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);

CREATE TABLE sys_audit_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), actor varchar(80) NOT NULL, action varchar(80) NOT NULL, resource_type varchar(80) NOT NULL,
  resource_id varchar(100), result varchar(20) NOT NULL, client_ip varchar(64), details_json text, occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_time ON sys_audit_log(occurred_at DESC);
CREATE INDEX idx_audit_resource ON sys_audit_log(resource_type, resource_id);
CREATE TABLE aml_report (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), report_no varchar(50) NOT NULL UNIQUE, report_type varchar(50) NOT NULL, title varchar(200) NOT NULL,
  status varchar(20) NOT NULL, period_start timestamptz, period_end timestamptz, created_by uuid REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
CREATE TABLE aml_report_generation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), report_id uuid NOT NULL REFERENCES aml_report(id), format varchar(10) NOT NULL,
  status varchar(20) NOT NULL, storage_key varchar(255), error_message text, generated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);

INSERT INTO sys_role(code,name) VALUES
 ('SYSTEM_ADMIN','系统管理员'),('AML_ANALYST','反洗钱分析员'),('AML_INVESTIGATOR','调查员'),('AML_MANAGER','反洗钱经理'),('AML_AUDITOR','审计员');
INSERT INTO sys_permission(code,name) VALUES
 ('USER_MANAGE','用户管理'),('CUSTOMER_READ','客户查看'),('CUSTOMER_WRITE','客户维护'),('ACCOUNT_WRITE','账户维护'),
 ('TRANSACTION_IMPORT','交易导入'),('RULE_EXECUTE','规则执行'),('ALERT_REVIEW','预警审核'),('CASE_INVESTIGATE','案件调查'),
 ('SCREENING_EXECUTE','名单筛查'),('REPORT_READ','报表查看'),('AUDIT_READ','审计查看');
INSERT INTO sys_role_permission(role_id, permission_id) SELECT r.id,p.id FROM sys_role r CROSS JOIN sys_permission p WHERE r.code='SYSTEM_ADMIN';
INSERT INTO sys_role_permission(role_id, permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code IN ('CUSTOMER_READ','CUSTOMER_WRITE','ACCOUNT_WRITE','TRANSACTION_IMPORT','RULE_EXECUTE','ALERT_REVIEW','SCREENING_EXECUTE','REPORT_READ') WHERE r.code='AML_ANALYST';
INSERT INTO sys_role_permission(role_id, permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code IN ('CUSTOMER_READ','ALERT_REVIEW','CASE_INVESTIGATE','SCREENING_EXECUTE') WHERE r.code='AML_INVESTIGATOR';
INSERT INTO sys_role_permission(role_id, permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code IN ('CUSTOMER_READ','RULE_EXECUTE','ALERT_REVIEW','CASE_INVESTIGATE','REPORT_READ','AUDIT_READ') WHERE r.code='AML_MANAGER';
INSERT INTO sys_role_permission(role_id, permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code IN ('CUSTOMER_READ','REPORT_READ','AUDIT_READ') WHERE r.code='AML_AUDITOR';

INSERT INTO aml_rule(code,name,rule_type,severity) VALUES
 ('AML-R001','单笔大额交易','LARGE_SINGLE_TRANSACTION','HIGH'),('AML-R002','短时间高频交易','HIGH_FREQUENCY','MEDIUM'),
 ('AML-R003','多笔交易累计异常','WINDOW_AGGREGATE','HIGH'),('AML-R004','高风险客户异常交易','HIGH_RISK_CUSTOMER','HIGH'),
 ('AML-R005','新账户异常活跃','NEW_ACCOUNT_ACTIVITY','MEDIUM'),('AML-R006','历史交易行为突变','BEHAVIOR_DEVIATION','HIGH'),
 ('AML-R007','多账户循环转账','CIRCULAR_TRANSFER','HIGH'),('AML-R008','资金集中或分散','FUNDS_CONCENTRATION','HIGH');
INSERT INTO aml_rule_version(rule_id,version_no,parameters_json,description)
SELECT id,1,
 CASE code WHEN 'AML-R001' THEN '{"amountThreshold":100000}'
 WHEN 'AML-R002' THEN '{"windowMinutes":10,"countThreshold":10}'
 WHEN 'AML-R003' THEN '{"windowHours":24,"amountThreshold":300000}'
 WHEN 'AML-R004' THEN '{"amountThreshold":50000}'
 WHEN 'AML-R005' THEN '{"accountAgeDays":30,"countThreshold":20}'
 WHEN 'AML-R006' THEN '{"deviationMultiplier":5}'
 WHEN 'AML-R007' THEN '{"maxPathLength":4,"windowHours":24}'
 ELSE '{"windowHours":24,"counterpartyThreshold":5,"amountThreshold":200000}' END,
 '教学演示阈值，不代表法律或监管要求' FROM aml_rule;
