-- Program can reference multiple strategic objectives.
-- This migration creates the join table and backfills data from the legacy programs.strategic_objective_id.

CREATE TABLE IF NOT EXISTS program_strategic_objectives (
    program_id UUID NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    strategic_objective_id UUID NOT NULL REFERENCES strategic_objectives(id),
    PRIMARY KEY (program_id, strategic_objective_id)
);

CREATE INDEX IF NOT EXISTS idx_program_strategic_objectives_objective
    ON program_strategic_objectives(strategic_objective_id);

INSERT INTO program_strategic_objectives (program_id, strategic_objective_id)
SELECT p.id, p.strategic_objective_id
FROM programs p
WHERE p.strategic_objective_id IS NOT NULL
ON CONFLICT DO NOTHING;