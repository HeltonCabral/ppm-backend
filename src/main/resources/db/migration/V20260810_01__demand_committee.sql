ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS committee_id UUID REFERENCES committees(id);

CREATE INDEX IF NOT EXISTS idx_demands_committee_id
    ON demands(committee_id);
