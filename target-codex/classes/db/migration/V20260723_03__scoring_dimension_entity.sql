CREATE TABLE IF NOT EXISTS scoring_dimensions (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    label VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_scoring_dimension_code UNIQUE (code)
);

CREATE INDEX IF NOT EXISTS idx_scoring_dimension_active ON scoring_dimensions(active);

INSERT INTO scoring_dimensions (id, code, label, active)
SELECT gen_random_uuid(), 'VALUE', 'Value', TRUE
WHERE NOT EXISTS (SELECT 1 FROM scoring_dimensions WHERE UPPER(code) = 'VALUE');

INSERT INTO scoring_dimensions (id, code, label, active)
SELECT gen_random_uuid(), 'EFFORT', 'Effort', TRUE
WHERE NOT EXISTS (SELECT 1 FROM scoring_dimensions WHERE UPPER(code) = 'EFFORT');

INSERT INTO scoring_dimensions (id, code, label, active)
SELECT gen_random_uuid(), 'RISK', 'Risk', TRUE
WHERE NOT EXISTS (SELECT 1 FROM scoring_dimensions WHERE UPPER(code) = 'RISK');

ALTER TABLE scoring_criteria ADD COLUMN IF NOT EXISTS dimension_id UUID;

UPDATE scoring_criteria c
SET dimension_id = d.id
FROM scoring_dimensions d
WHERE c.dimension_id IS NULL
  AND UPPER(d.code) = UPPER(c.dimension);

ALTER TABLE scoring_criteria
    ADD CONSTRAINT IF NOT EXISTS fk_scoring_criteria_dimension
    FOREIGN KEY (dimension_id) REFERENCES scoring_dimensions(id);

CREATE INDEX IF NOT EXISTS idx_scoring_criteria_dimension_id ON scoring_criteria(dimension_id);

ALTER TABLE scoring_criteria ALTER COLUMN dimension_id SET NOT NULL;
