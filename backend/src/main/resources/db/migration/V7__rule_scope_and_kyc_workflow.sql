ALTER TABLE aml_rule_execution ALTER COLUMN scope TYPE varchar(160);

ALTER TABLE aml_customer_kyc
  ADD COLUMN document_summary varchar(500),
  ADD COLUMN submitted_by uuid REFERENCES sys_user(id),
  ADD COLUMN submitted_at timestamptz,
  ADD COLUMN reviewed_by uuid REFERENCES sys_user(id),
  ADD COLUMN reviewed_at timestamptz,
  ADD COLUMN review_reason varchar(500);

ALTER TABLE aml_customer_kyc
  ADD CONSTRAINT ck_kyc_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED')) NOT VALID;

CREATE INDEX idx_kyc_customer_created ON aml_customer_kyc(customer_id, created_at DESC);

CREATE TABLE aml_customer_kyc_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kyc_id uuid NOT NULL REFERENCES aml_customer_kyc(id),
  customer_id uuid NOT NULL REFERENCES aml_customer(id),
  from_status varchar(20),
  to_status varchar(20) NOT NULL,
  operator_id uuid REFERENCES sys_user(id),
  reason varchar(500),
  occurred_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_kyc_history_status CHECK (to_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'))
);
CREATE INDEX idx_kyc_history_customer_time ON aml_customer_kyc_history(customer_id, occurred_at DESC);

INSERT INTO sys_permission(code, name) VALUES ('KYC_REVIEW', '客户尽职调查审核')
ON CONFLICT (code) DO NOTHING;
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code IN ('SYSTEM_ADMIN', 'AML_MANAGER') AND p.code = 'KYC_REVIEW'
ON CONFLICT DO NOTHING;
