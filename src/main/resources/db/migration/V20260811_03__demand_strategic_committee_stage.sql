ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS in_strategic_committee BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS strategic_committee_at TIMESTAMP WITH TIME ZONE;

UPDATE demands
SET in_strategic_committee = TRUE,
    strategic_committee_at = COALESCE(strategic_committee_at, updated_at, created_at)
WHERE UPPER(status) = 'IN_STRATEGIC_COMMITTEE';
