ALTER TABLE aml_screening_request
  ADD COLUMN error_message text,
  ADD COLUMN completed_at timestamptz;

UPDATE aml_screening_request SET provider_mode='REAL' WHERE provider_mode='LIVE';
UPDATE aml_screening_request SET completed_at=updated_at WHERE status='COMPLETED' AND completed_at IS NULL;

ALTER TABLE aml_screening_request
  ADD CONSTRAINT ck_screening_request_mode CHECK (provider_mode IN ('REAL','SIMULATED')),
  ADD CONSTRAINT ck_screening_request_status CHECK (status IN ('PENDING','COMPLETED','FAILED'));

ALTER TABLE aml_screening_match
  ADD COLUMN data_mode varchar(20),
  ADD COLUMN review_status varchar(30) NOT NULL DEFAULT 'PENDING';

UPDATE aml_screening_match m
SET data_mode=r.provider_mode
FROM aml_screening_request r
WHERE r.id=m.request_id;
UPDATE aml_screening_match SET data_mode='SIMULATED' WHERE data_mode IS NULL;

ALTER TABLE aml_screening_match
  ALTER COLUMN data_mode SET NOT NULL,
  ADD CONSTRAINT ck_screening_match_mode CHECK (data_mode IN ('REAL','SIMULATED')),
  ADD CONSTRAINT ck_screening_match_review CHECK (
    review_status IN ('PENDING','NEEDS_MORE_INFO','CONFIRMED_MATCH','FALSE_POSITIVE')
  );

ALTER TABLE aml_screening_review
  ADD CONSTRAINT ck_screening_review_decision CHECK (
    decision IN ('NEEDS_MORE_INFO','CONFIRMED_MATCH','FALSE_POSITIVE')
  );

CREATE INDEX idx_screening_request_status ON aml_screening_request(status, created_at DESC);
CREATE INDEX idx_screening_match_request_review ON aml_screening_match(request_id, review_status, score DESC);
CREATE INDEX idx_screening_review_match_time ON aml_screening_review(match_id, reviewed_at DESC);

ALTER TABLE aml_customer_risk
  ADD COLUMN source_data_mode varchar(20) NOT NULL DEFAULT 'NONE',
  ADD COLUMN calculated_by uuid REFERENCES sys_user(id),
  ADD CONSTRAINT ck_customer_risk_source_mode CHECK (source_data_mode IN ('NONE','REAL','SIMULATED','MIXED'));

ALTER TABLE aml_customer_risk_history
  ADD COLUMN source_data_mode varchar(20) NOT NULL DEFAULT 'NONE',
  ADD COLUMN calculated_by uuid REFERENCES sys_user(id),
  ADD CONSTRAINT ck_customer_risk_history_source_mode CHECK (source_data_mode IN ('NONE','REAL','SIMULATED','MIXED'));

CREATE TABLE aml_transaction_risk (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_id uuid NOT NULL UNIQUE REFERENCES aml_transaction(id),
  score numeric(8,2) NOT NULL,
  risk_level varchar(10) NOT NULL,
  model_version varchar(40) NOT NULL,
  explanation text NOT NULL,
  source_data_mode varchar(20) NOT NULL DEFAULT 'NONE',
  calculated_by uuid REFERENCES sys_user(id),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_transaction_risk_score CHECK (score BETWEEN 0 AND 100),
  CONSTRAINT ck_transaction_risk_level CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_transaction_risk_source_mode CHECK (source_data_mode IN ('NONE','REAL','SIMULATED','MIXED'))
);

CREATE TABLE aml_transaction_risk_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_id uuid NOT NULL REFERENCES aml_transaction(id),
  score numeric(8,2) NOT NULL,
  risk_level varchar(10) NOT NULL,
  model_version varchar(40) NOT NULL,
  explanation text NOT NULL,
  source_data_mode varchar(20) NOT NULL DEFAULT 'NONE',
  calculated_by uuid REFERENCES sys_user(id),
  assessed_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_transaction_risk_history_score CHECK (score BETWEEN 0 AND 100),
  CONSTRAINT ck_transaction_risk_history_level CHECK (risk_level IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_transaction_risk_history_source_mode CHECK (source_data_mode IN ('NONE','REAL','SIMULATED','MIXED'))
);

CREATE INDEX idx_customer_risk_history_customer_time
  ON aml_customer_risk_history(customer_id, assessed_at DESC);
CREATE INDEX idx_transaction_risk_history_transaction_time
  ON aml_transaction_risk_history(transaction_id, assessed_at DESC);
