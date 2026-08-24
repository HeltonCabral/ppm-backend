-- Rolling strategic planning model: configuration table + strategic_plans adjustments.
-- Note: this project runs Hibernate with ddl-auto=update and has no Flyway runner wired in,
-- so this script is kept for manual/DBA reference only; schema changes are applied automatically
-- in dev via ddl-auto=update.

CREATE TABLE IF NOT EXISTS strategic_planning_configs (
    id UUID PRIMARY KEY,
    rolling_plan BOOLEAN NOT NULL DEFAULT TRUE,
    horizon_years INTEGER NOT NULL DEFAULT 3,
    review_frequency VARCHAR(20) NOT NULL DEFAULT 'ANNUAL',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE strategic_plans ADD COLUMN IF NOT EXISTS horizon_years INTEGER;
ALTER TABLE strategic_plans ADD COLUMN IF NOT EXISTS revision_number INTEGER NOT NULL DEFAULT 1;
UPDATE strategic_plans SET revision_number = revision WHERE revision IS NOT NULL;

-- Data note: the status enum constant REPLACED was renamed to SUPERSEDED. Existing rows with
-- status = 'REPLACED' must be migrated before the application is deployed, e.g.:
-- UPDATE strategic_plans SET status = 'SUPERSEDED' WHERE status = 'REPLACED';

-- Enforce "no more than one ACTIVE plan" and "no more than one plan in planning" at the DB level.
CREATE UNIQUE INDEX IF NOT EXISTS uk_strategic_plans_single_active
    ON strategic_plans ((1))
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_strategic_plans_single_planning
    ON strategic_plans ((1))
    WHERE status IN ('DRAFT', 'CONDITIONALLY_APPROVED', 'APPROVED') AND deleted_at IS NULL;
