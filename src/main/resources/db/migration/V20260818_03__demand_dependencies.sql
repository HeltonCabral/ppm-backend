CREATE TABLE demand_dependencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demand_id UUID NOT NULL REFERENCES demands(id) ON DELETE CASCADE,
    depends_on_demand_id UUID NOT NULL REFERENCES demands(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(150) NOT NULL DEFAULT 'system',
    CONSTRAINT ck_demand_dependencies_not_self CHECK (demand_id <> depends_on_demand_id),
    CONSTRAINT uk_demand_dependencies_demand_target UNIQUE (demand_id, depends_on_demand_id)
);

CREATE INDEX idx_demand_dependencies_demand_id
    ON demand_dependencies(demand_id);

CREATE INDEX idx_demand_dependencies_target_id
    ON demand_dependencies(depends_on_demand_id);
