ALTER TABLE scoring_dimensions ADD COLUMN IF NOT EXISTS weight NUMERIC(8,4);

UPDATE scoring_dimensions d
SET weight = x.weight_value
FROM (
    SELECT c.dimension_id,
           AVG(c.weight)::NUMERIC(8,4) AS weight_value
    FROM scoring_criteria c
    WHERE c.dimension_id IS NOT NULL
      AND c.weight IS NOT NULL
    GROUP BY c.dimension_id
) x
WHERE d.id = x.dimension_id
  AND d.weight IS NULL;

UPDATE scoring_dimensions
SET weight = 1.0000
WHERE weight IS NULL;

ALTER TABLE scoring_dimensions ALTER COLUMN weight SET NOT NULL;

ALTER TABLE scoring_criteria DROP COLUMN IF EXISTS weight;
