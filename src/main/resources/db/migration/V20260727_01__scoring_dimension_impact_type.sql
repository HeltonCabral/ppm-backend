ALTER TABLE scoring_dimensions
    ADD COLUMN IF NOT EXISTS impact_type VARCHAR(20);

UPDATE scoring_dimensions
SET impact_type = 'BENEFIT'
WHERE impact_type IS NULL;

UPDATE scoring_dimensions
SET impact_type = 'PENALTY'
WHERE UPPER(code) IN ('RISK', 'RISCO');

ALTER TABLE scoring_dimensions
    ALTER COLUMN impact_type SET NOT NULL;