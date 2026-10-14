ALTER TABLE aml_case_evidence
  ADD COLUMN active boolean NOT NULL DEFAULT true,
  ADD COLUMN removed_at timestamptz,
  ADD COLUMN removed_by uuid REFERENCES sys_user(id),
  ADD COLUMN removal_reason text;

ALTER TABLE aml_case_evidence
  ADD CONSTRAINT ck_case_evidence_size CHECK (size_bytes > 0),
  ADD CONSTRAINT ck_case_evidence_removal CHECK (
    (active = true AND removed_at IS NULL AND removed_by IS NULL)
    OR (active = false AND removed_at IS NOT NULL AND removed_by IS NOT NULL)
  ),
  ADD CONSTRAINT uk_case_evidence_storage_key UNIQUE (storage_key);

CREATE INDEX idx_case_investigator_status ON aml_case(investigator_id, status, created_at DESC);
CREATE INDEX idx_case_evidence_case_active ON aml_case_evidence(case_id, active, created_at DESC);
CREATE INDEX idx_case_note_case_created ON aml_case_note(case_id, created_at DESC);
CREATE INDEX idx_case_history_case_created ON aml_case_history(case_id, created_at DESC);
