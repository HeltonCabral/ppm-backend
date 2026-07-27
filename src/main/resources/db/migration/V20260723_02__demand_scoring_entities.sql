CREATE TABLE IF NOT EXISTS scoring_criteria (
    id UUID PRIMARY KEY,
    label VARCHAR(150) NOT NULL,
    dimension VARCHAR(20) NOT NULL,
    weight NUMERIC(8,4) NOT NULL,
    min_score NUMERIC(10,2) NOT NULL,
    max_score NUMERIC(10,2) NOT NULL,
    order_index INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_scoring_criteria_min_max CHECK (min_score <= max_score)
);

CREATE INDEX IF NOT EXISTS idx_scoring_criteria_dimension ON scoring_criteria(dimension);
CREATE INDEX IF NOT EXISTS idx_scoring_criteria_active ON scoring_criteria(active);
CREATE INDEX IF NOT EXISTS idx_scoring_criteria_order ON scoring_criteria(order_index);

CREATE TABLE IF NOT EXISTS demand_scoring (
    id UUID PRIMARY KEY,
    demand_id UUID NOT NULL REFERENCES demands(id) ON DELETE CASCADE,
    criterion_id UUID NOT NULL REFERENCES scoring_criteria(id),
    score NUMERIC(10,2) NOT NULL,
    weighted_score NUMERIC(12,4) NOT NULL,
    notes TEXT,
    scored_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    scored_by VARCHAR(150),
    CONSTRAINT uk_demand_scoring_demand_criterion UNIQUE (demand_id, criterion_id)
);

CREATE INDEX IF NOT EXISTS idx_demand_scoring_demand ON demand_scoring(demand_id);
CREATE INDEX IF NOT EXISTS idx_demand_scoring_criterion ON demand_scoring(criterion_id);
CREATE INDEX IF NOT EXISTS idx_demand_scoring_scored_at ON demand_scoring(scored_at);
