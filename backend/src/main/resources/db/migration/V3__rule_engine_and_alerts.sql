ALTER TABLE aml_rule
  ADD COLUMN implementation_status varchar(20) NOT NULL DEFAULT 'PLANNED';

UPDATE aml_rule SET implementation_status = 'IMPLEMENTED'
WHERE code IN ('AML-R001', 'AML-R002', 'AML-R003');

ALTER TABLE aml_rule
  ADD CONSTRAINT ck_rule_implementation_status
  CHECK (implementation_status IN ('IMPLEMENTED', 'PLANNED'));

ALTER TABLE aml_rule_execution
  ADD COLUMN window_start timestamptz,
  ADD COLUMN window_end timestamptz,
  ADD COLUMN error_message text;

CREATE TABLE aml_rule_hit_transaction (
  rule_hit_id uuid NOT NULL REFERENCES aml_rule_hit(id),
  transaction_id uuid NOT NULL REFERENCES aml_transaction(id),
  PRIMARY KEY(rule_hit_id, transaction_id)
);

INSERT INTO aml_rule_hit_transaction(rule_hit_id, transaction_id)
SELECT id, transaction_id FROM aml_rule_hit WHERE transaction_id IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX idx_rule_hit_rule_created ON aml_rule_hit(rule_id, created_at DESC);
CREATE INDEX idx_rule_hit_customer_created ON aml_rule_hit(customer_id, created_at DESC);
CREATE INDEX idx_rule_execution_started ON aml_rule_execution(started_at DESC);
CREATE INDEX idx_alert_customer_status ON aml_alert(customer_id, status, created_at DESC);
