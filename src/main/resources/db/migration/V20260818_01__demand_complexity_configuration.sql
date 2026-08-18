CREATE TABLE complexity_criterion_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    criterion VARCHAR(40) NOT NULL,
    low_min INTEGER NOT NULL,
    low_max INTEGER NOT NULL,
    medium_min INTEGER NOT NULL,
    medium_max INTEGER NOT NULL,
    high_min INTEGER NOT NULL,
    high_max INTEGER NOT NULL,
    very_high_min INTEGER NOT NULL,
    very_high_max INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_complexity_criterion_configs_criterion UNIQUE (criterion)
);

CREATE TABLE complexity_level_configs (
    level VARCHAR(20) PRIMARY KEY,
    min_score NUMERIC(4, 2) NOT NULL,
    max_score NUMERIC(4, 2) NOT NULL,
    estimated_duration_months INTEGER NOT NULL
);

ALTER TABLE demands
    ADD COLUMN directions_count INTEGER,
    ADD COLUMN profiles_count INTEGER,
    ADD COLUMN total_resources INTEGER,
    ADD COLUMN dependencies_count INTEGER,
    ADD COLUMN complexity_score NUMERIC(4, 2),
    ADD COLUMN complexity VARCHAR(20),
    ADD COLUMN estimated_duration_months INTEGER,
    ADD COLUMN planned_start_date DATE;
