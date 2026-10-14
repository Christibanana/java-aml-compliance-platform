ALTER TABLE aml_transaction_import_batch ALTER COLUMN status TYPE varchar(30);

ALTER TABLE aml_customer
  ADD CONSTRAINT ck_customer_type CHECK (customer_type IN ('INDIVIDUAL', 'ORGANIZATION')),
  ADD CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  ADD CONSTRAINT ck_customer_country CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$');

ALTER TABLE aml_account
  ADD CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
  ADD CONSTRAINT ck_account_currency CHECK (currency ~ '^[A-Z]{3}$'),
  ADD CONSTRAINT ck_account_closed_at CHECK (
    (status = 'CLOSED' AND closed_at IS NOT NULL) OR (status <> 'CLOSED' AND closed_at IS NULL)
  );

ALTER TABLE aml_account_customer
  ADD CONSTRAINT ck_account_customer_relation CHECK (
    relation_type IN ('OWNER', 'JOINT_OWNER', 'AUTHORIZED_SIGNER')
  ),
  ADD CONSTRAINT ck_account_customer_ownership CHECK (
    ownership_percent IS NULL OR (ownership_percent > 0 AND ownership_percent <= 100)
  );

ALTER TABLE aml_transaction_import_batch
  ADD CONSTRAINT ck_transaction_batch_counts CHECK (
    total_count >= 0 AND success_count >= 0 AND error_count >= 0
    AND success_count + error_count <= total_count
  ),
  ADD CONSTRAINT ck_transaction_batch_status CHECK (
    status IN ('PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')
  );

ALTER TABLE aml_transaction
  ADD CONSTRAINT ck_transaction_direction CHECK (direction IN ('IN', 'OUT')),
  ADD CONSTRAINT ck_transaction_currency CHECK (currency ~ '^[A-Z]{3}$');

CREATE INDEX idx_account_status ON aml_account(status);
CREATE INDEX idx_transaction_batch ON aml_transaction(import_batch_id);
CREATE INDEX idx_transaction_source_time ON aml_transaction(source, transaction_time DESC);
