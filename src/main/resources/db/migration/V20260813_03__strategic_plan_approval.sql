-- Strategic Plan Approval: Add fields for conditional and final approval process
-- Adds fields for Program, Project and Demand entities to support demand conversion

-- Update strategic_plans status constraint to allow CONDITIONALLY_APPROVED
ALTER TABLE strategic_plans DROP CONSTRAINT IF EXISTS strategic_plans_status_check;
ALTER TABLE strategic_plans ADD CONSTRAINT strategic_plans_status_check 
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'CONDITIONALLY_APPROVED', 'APPROVED', 'ACTIVE', 'REPLACED', 'CLOSED'));

-- Add fields to programs table
ALTER TABLE programs ADD COLUMN IF NOT EXISTS status VARCHAR(30);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS strategic_plan_id UUID REFERENCES strategic_plans(id);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS operational_plan_id UUID REFERENCES operational_plans(id);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS strategic_pillar_id UUID REFERENCES strategic_pillars(id);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS strategic_objective_id UUID REFERENCES strategic_objectives(id);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS domain_id UUID REFERENCES lookup_values(id);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS direction_name VARCHAR(120);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS direction_code VARCHAR(60);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS area_name VARCHAR(120);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS area_code VARCHAR(60);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS estimated_budget DECIMAL(19,2);
ALTER TABLE programs ADD COLUMN IF NOT EXISTS source_demand_id UUID;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS source_demand_portfolio_rank INTEGER;
ALTER TABLE programs ADD COLUMN IF NOT EXISTS created_from_conditional_plan_approval BOOLEAN;

-- Make program_manager nullable in programs
ALTER TABLE programs ALTER COLUMN program_manager DROP NOT NULL;

-- Add fields to projects table
ALTER TABLE projects ADD COLUMN IF NOT EXISTS strategic_plan_id UUID REFERENCES strategic_plans(id);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS strategic_pillar_id UUID REFERENCES strategic_pillars(id);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS strategic_objective_id UUID REFERENCES strategic_objectives(id);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS direction_name VARCHAR(120);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS direction_code VARCHAR(60);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS area_name VARCHAR(120);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS area_code VARCHAR(60);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS estimated_budget DECIMAL(19,2);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS desired_date DATE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS expected_impact TEXT;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS expected_benefit TEXT;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS portfolio_rank INTEGER;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS source_demand_portfolio_rank INTEGER;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS created_from_conditional_plan_approval BOOLEAN;

-- Make several columns nullable in projects (for conversion from demand)
ALTER TABLE projects ALTER COLUMN program_id DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN business_area DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN project_manager DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN schedule_status DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN cost_status DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN risk_status DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN value_status DROP NOT NULL;
ALTER TABLE projects ALTER COLUMN priority DROP NOT NULL;

-- Add fields to demands table
ALTER TABLE demands ADD COLUMN IF NOT EXISTS converted_program_id UUID REFERENCES programs(id);
ALTER TABLE demands ADD COLUMN IF NOT EXISTS converted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE demands ADD COLUMN IF NOT EXISTS converted_by VARCHAR(150);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_programs_strategic_plan_id ON programs(strategic_plan_id);
CREATE INDEX IF NOT EXISTS idx_programs_source_demand_id ON programs(source_demand_id);
CREATE INDEX IF NOT EXISTS idx_projects_strategic_plan_id ON projects(strategic_plan_id);
CREATE INDEX IF NOT EXISTS idx_projects_source_demand_id ON projects(source_demand_id);
CREATE INDEX IF NOT EXISTS idx_demands_converted_program_id ON demands(converted_program_id);
CREATE INDEX IF NOT EXISTS idx_demands_strategic_plan_decision ON demands(strategic_plan_id, committee_decision) WHERE deleted_at IS NULL;
