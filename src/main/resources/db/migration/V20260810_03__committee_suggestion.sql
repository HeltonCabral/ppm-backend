ALTER TABLE committees
    ADD COLUMN IF NOT EXISTS minimum_budget NUMERIC(18,2);

CREATE TABLE IF NOT EXISTS committee_domains (
    committee_id UUID NOT NULL REFERENCES committees(id) ON DELETE CASCADE,
    domain_order INTEGER NOT NULL,
    domain_name VARCHAR(200) NOT NULL,
    PRIMARY KEY (committee_id, domain_order)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_committee_domains_ci
    ON committee_domains (committee_id, LOWER(domain_name));

CREATE TABLE IF NOT EXISTS committee_risk_levels (
    committee_id UUID NOT NULL REFERENCES committees(id) ON DELETE CASCADE,
    risk_level_order INTEGER NOT NULL,
    risk_level_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (committee_id, risk_level_order)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_committee_risk_levels_ci
    ON committee_risk_levels (committee_id, LOWER(risk_level_name));

ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS suggested_committee_id UUID REFERENCES committees(id),
    ADD COLUMN IF NOT EXISTS responsible_committee_id UUID REFERENCES committees(id),
    ADD COLUMN IF NOT EXISTS committee_change_justification VARCHAR(4000);

UPDATE demands
SET responsible_committee_id = committee_id
WHERE responsible_committee_id IS NULL
  AND committee_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_demands_suggested_committee_id
    ON demands(suggested_committee_id);

CREATE INDEX IF NOT EXISTS idx_demands_responsible_committee_id
    ON demands(responsible_committee_id);
