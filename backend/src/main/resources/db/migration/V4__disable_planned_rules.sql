UPDATE aml_rule
SET enabled = false,
    updated_at = now(),
    version = version + 1
WHERE implementation_status = 'PLANNED'
  AND enabled = true;
