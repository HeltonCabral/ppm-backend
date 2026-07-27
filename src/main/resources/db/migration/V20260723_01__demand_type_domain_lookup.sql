ALTER TABLE demands ADD COLUMN IF NOT EXISTS type_id UUID;
ALTER TABLE demands ADD COLUMN IF NOT EXISTS domain_id UUID;

ALTER TABLE demands
    ADD CONSTRAINT IF NOT EXISTS fk_demands_type
    FOREIGN KEY (type_id) REFERENCES lookup_values(id);

ALTER TABLE demands
    ADD CONSTRAINT IF NOT EXISTS fk_demands_domain
    FOREIGN KEY (domain_id) REFERENCES lookup_values(id);

CREATE INDEX IF NOT EXISTS idx_demands_type_id ON demands(type_id);
CREATE INDEX IF NOT EXISTS idx_demands_domain_id ON demands(domain_id);

UPDATE demands d
SET type_id = lv.id
FROM lookup_values lv
WHERE d.type_id IS NULL
  AND UPPER(lv.category) = 'DEMAND_TYPE'
  AND UPPER(lv.code) = UPPER(d.type)
  AND COALESCE(lv.active, TRUE) = TRUE;

UPDATE demands d
SET domain_id = candidate.id
FROM (
    SELECT d2.id AS demand_id,
           (
               SELECT lv.id
               FROM lookup_values lv
               WHERE UPPER(lv.code) = UPPER(d2.domain)
                 AND COALESCE(lv.active, TRUE) = TRUE
                 AND UPPER(lv.category) IN ('DEMAND_DOMAIN', 'PROGRAM_DOMAIN', 'DOMAIN')
               ORDER BY CASE UPPER(lv.category)
                            WHEN 'DEMAND_DOMAIN' THEN 1
                            WHEN 'PROGRAM_DOMAIN' THEN 2
                            WHEN 'DOMAIN' THEN 3
                            ELSE 99
                        END,
                        lv.sort_order NULLS LAST,
                        lv.id
               LIMIT 1
           ) AS id
    FROM demands d2
    WHERE d2.domain_id IS NULL
      AND d2.domain IS NOT NULL
) candidate
WHERE d.id = candidate.demand_id
  AND candidate.id IS NOT NULL;

ALTER TABLE demands ALTER COLUMN type DROP NOT NULL;
