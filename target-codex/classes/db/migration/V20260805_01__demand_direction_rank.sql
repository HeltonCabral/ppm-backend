ALTER TABLE demands ADD COLUMN IF NOT EXISTS direction_rank INTEGER;

WITH ranked_by_direction AS (
    SELECT id,
           CAST(ROW_NUMBER() OVER (
               PARTITION BY LOWER(BTRIM(direction))
               ORDER BY score_total DESC, created_at ASC, id ASC
           ) AS INTEGER) AS direction_rank
    FROM demands
    WHERE deleted_at IS NULL
      AND score_total IS NOT NULL
      AND NULLIF(BTRIM(direction), '') IS NOT NULL
)
UPDATE demands AS demand
SET direction_rank = ranked.direction_rank
FROM ranked_by_direction AS ranked
WHERE demand.id = ranked.id;

UPDATE demands
SET direction_rank = NULL
WHERE deleted_at IS NOT NULL
   OR score_total IS NULL
   OR NULLIF(BTRIM(direction), '') IS NULL;

CREATE INDEX IF NOT EXISTS idx_demands_direction_rank ON demands(direction, direction_rank);
