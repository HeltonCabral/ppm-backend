ALTER TABLE demands ADD COLUMN IF NOT EXISTS score_status VARCHAR(30);
ALTER TABLE demands ADD COLUMN IF NOT EXISTS score_calculated_at TIMESTAMPTZ;
ALTER TABLE demands ADD COLUMN IF NOT EXISTS score_invalidated_at TIMESTAMPTZ;
ALTER TABLE demands ADD COLUMN IF NOT EXISTS score_invalidation_reason TEXT;
ALTER TABLE demands ADD COLUMN IF NOT EXISTS previous_score_snapshot TEXT;

UPDATE demands
SET score_status = CASE
        WHEN score_total IS NOT NULL THEN 'Válido'
        ELSE 'Não Calculado'
    END,
    score_calculated_at = CASE
        WHEN score_total IS NOT NULL THEN COALESCE(updated_at, created_at, now())
        ELSE NULL
    END
WHERE score_status IS NULL;

ALTER TABLE demands ALTER COLUMN score_status SET DEFAULT 'Não Calculado';
ALTER TABLE demands ALTER COLUMN score_status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_demands_score_status ON demands(score_status);
