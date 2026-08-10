CREATE TABLE IF NOT EXISTS committees (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    name_key VARCHAR(300) NOT NULL UNIQUE,
    description VARCHAR(4000) NOT NULL DEFAULT '',
    status VARCHAR(8) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_committees_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE IF NOT EXISTS committee_members (
    committee_id UUID NOT NULL REFERENCES committees(id) ON DELETE CASCADE,
    member_order INTEGER NOT NULL,
    member_name VARCHAR(200) NOT NULL,
    PRIMARY KEY (committee_id, member_order)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_committee_members_ci
    ON committee_members (committee_id, LOWER(member_name));

CREATE TABLE IF NOT EXISTS committee_directions (
    committee_id UUID NOT NULL REFERENCES committees(id) ON DELETE CASCADE,
    direction_order INTEGER NOT NULL,
    direction_name VARCHAR(200) NOT NULL,
    PRIMARY KEY (committee_id, direction_order)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_committee_directions_ci
    ON committee_directions (committee_id, LOWER(direction_name));

CREATE TABLE IF NOT EXISTS committee_demand_types (
    committee_id UUID NOT NULL REFERENCES committees(id) ON DELETE CASCADE,
    demand_type_order INTEGER NOT NULL,
    demand_type_name VARCHAR(200) NOT NULL,
    PRIMARY KEY (committee_id, demand_type_order)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_committee_demand_types_ci
    ON committee_demand_types (committee_id, LOWER(demand_type_name));
