DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'demands' AND column_name = 'area'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'demands' AND column_name = 'area_name'
    ) THEN
        ALTER TABLE demands RENAME COLUMN area TO area_name;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'demands' AND column_name = 'area'
    ) THEN
        EXECUTE 'UPDATE demands SET area_name = COALESCE(area_name, area)';
        ALTER TABLE demands DROP COLUMN area;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'demands' AND column_name = 'direction'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'demands' AND column_name = 'direction_name'
    ) THEN
        ALTER TABLE demands RENAME COLUMN direction TO direction_name;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'demands' AND column_name = 'direction'
    ) THEN
        EXECUTE 'UPDATE demands SET direction_name = COALESCE(direction_name, direction)';
        ALTER TABLE demands DROP COLUMN direction;
    END IF;
END $$;

ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS area_code VARCHAR(60),
    ADD COLUMN IF NOT EXISTS direction_code VARCHAR(60),
    ADD COLUMN IF NOT EXISTS direction_participation_type VARCHAR(50);

DROP INDEX IF EXISTS idx_demands_direction_rank;
CREATE INDEX IF NOT EXISTS idx_demands_direction_rank
    ON demands(direction_code, direction_rank);
CREATE INDEX IF NOT EXISTS idx_demands_area_code
    ON demands(area_code);

UPDATE demands
SET direction_rank = NULL
WHERE NULLIF(BTRIM(direction_code), '') IS NULL;