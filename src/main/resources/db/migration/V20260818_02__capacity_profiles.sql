CREATE TABLE profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL UNIQUE,
    category VARCHAR(40) NOT NULL,
    description TEXT,
    available_capacity INTEGER NOT NULL CHECK (available_capacity >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE demand_profile_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demand_id UUID NOT NULL REFERENCES demands(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    required_quantity INTEGER NOT NULL CHECK (required_quantity > 0),
    allocation_percentage INTEGER NOT NULL CHECK (allocation_percentage IN (25, 50, 75, 100)),
    CONSTRAINT uk_demand_profile_requirements_demand_profile UNIQUE (demand_id, profile_id)
);

CREATE INDEX idx_demand_profile_requirements_demand_id
    ON demand_profile_requirements(demand_id);

CREATE INDEX idx_demand_profile_requirements_profile_id
    ON demand_profile_requirements(profile_id);
