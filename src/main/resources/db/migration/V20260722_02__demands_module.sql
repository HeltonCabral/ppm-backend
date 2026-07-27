CREATE TABLE IF NOT EXISTS demands (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(250) NOT NULL,
    description TEXT,
    requester VARCHAR(150),
    area VARCHAR(120),
    direction VARCHAR(120),
    sponsor VARCHAR(150),
    type VARCHAR(80) NOT NULL,
    origin VARCHAR(80) NOT NULL DEFAULT 'MANUAL',
    easy_vista_ref VARCHAR(120),
    strategic_plan_id UUID REFERENCES strategic_plans(id),
    operational_plan_id UUID REFERENCES operational_plans(id),
    strategic_pillar_id UUID REFERENCES strategic_pillars(id),
    strategic_objective_id UUID REFERENCES strategic_objectives(id),
    program_id UUID REFERENCES programs(id),
    domain VARCHAR(120),
    impacted_system VARCHAR(150),
    initial_priority VARCHAR(40),
    estimated_effort VARCHAR(40),
    expected_impact TEXT,
    expected_benefit TEXT,
    urgency VARCHAR(40),
    estimated_budget NUMERIC(19,2),
    desired_date DATE,
    notes TEXT,
    status VARCHAR(50) NOT NULL,
    capacity_status VARCHAR(40),
    risk_status VARCHAR(40),
    risks_identified TEXT,
    dependencies_identified TEXT,
    score_value NUMERIC(19,2),
    score_effort NUMERIC(19,2),
    score_risk NUMERIC(19,2),
    score_total NUMERIC(19,2),
    portfolio_rank INTEGER,
    approval_type VARCHAR(40),
    committee_decision VARCHAR(60),
    rejection_reason TEXT,
    converted_project_id UUID REFERENCES projects(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(150) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(150) NOT NULL DEFAULT 'system',
    deleted_at TIMESTAMPTZ,
    version BIGINT
);

CREATE INDEX IF NOT EXISTS idx_demands_code ON demands(code);
CREATE INDEX IF NOT EXISTS idx_demands_status ON demands(status);
CREATE INDEX IF NOT EXISTS idx_demands_origin ON demands(origin);
CREATE INDEX IF NOT EXISTS idx_demands_created_at ON demands(created_at);

CREATE TABLE IF NOT EXISTS demand_attachments (
    id UUID PRIMARY KEY,
    demand_id UUID NOT NULL REFERENCES demands(id) ON DELETE CASCADE,
    name VARCHAR(250) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    content_type VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(150) NOT NULL DEFAULT 'system'
);

CREATE INDEX IF NOT EXISTS idx_demand_attachments_demand ON demand_attachments(demand_id);

CREATE TABLE IF NOT EXISTS demand_history (
    id UUID PRIMARY KEY,
    demand_id UUID NOT NULL REFERENCES demands(id) ON DELETE CASCADE,
    event_type VARCHAR(60) NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50),
    description TEXT,
    actor_id VARCHAR(150),
    actor_name VARCHAR(150),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_demand_history_demand ON demand_history(demand_id);
CREATE INDEX IF NOT EXISTS idx_demand_history_occurred ON demand_history(occurred_at);

CREATE TABLE IF NOT EXISTS demand_counters (
    counter_key VARCHAR(80) PRIMARY KEY,
    counter_value BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS demand_projects (
    demand_id UUID NOT NULL REFERENCES demands(id),
    project_id UUID NOT NULL REFERENCES projects(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (demand_id, project_id)
);

ALTER TABLE projects ADD COLUMN IF NOT EXISTS source_demand_id UUID;
