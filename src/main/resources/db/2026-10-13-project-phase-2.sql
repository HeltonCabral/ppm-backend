-- Projeto, fase 2. Idempotente; executar com a aplicação parada.

ALTER TABLE person_capacity ADD COLUMN IF NOT EXISTS max_concurrent integer;

CREATE TABLE IF NOT EXISTS capacity_settings (
    id uuid PRIMARY KEY,
    max_concurrent_projects integer NOT NULL DEFAULT 3
);

CREATE TABLE IF NOT EXISTS project_cost (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES projects(id),
    budget_line_id uuid NOT NULL REFERENCES lookup_values(id),
    month date NOT NULL,
    type varchar(10) NOT NULL,
    planned numeric(15,2) NOT NULL,
    actual numeric(15,2),
    description text,
    source varchar(10) NOT NULL DEFAULT 'MANUAL',
    created_at timestamptz,
    created_by varchar(150),
    updated_at timestamptz,
    updated_by varchar(150)
);
CREATE INDEX IF NOT EXISTS idx_project_cost_project_month ON project_cost(project_id, month);

CREATE TABLE IF NOT EXISTS project_baseline (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES projects(id),
    version integer NOT NULL,
    captured_at timestamptz NOT NULL,
    captured_by varchar(150) NOT NULL,
    reason varchar(1000) NOT NULL,
    budget numeric(18,2),
    planned_start_date date,
    planned_end_date date,
    CONSTRAINT uk_project_baseline_version UNIQUE(project_id, version)
);

CREATE TABLE IF NOT EXISTS project_baseline_phase (
    id uuid PRIMARY KEY,
    baseline_id uuid NOT NULL REFERENCES project_baseline(id) ON DELETE CASCADE,
    phase_id uuid,
    name varchar(150) NOT NULL,
    sequence integer NOT NULL,
    weight numeric(5,2),
    planned_start_date date,
    planned_end_date date
);

CREATE TABLE IF NOT EXISTS project_baseline_milestone (
    id uuid PRIMARY KEY,
    baseline_id uuid NOT NULL REFERENCES project_baseline(id) ON DELETE CASCADE,
    milestone_id uuid,
    name varchar(180) NOT NULL,
    due_date date
);

CREATE TABLE IF NOT EXISTS project_phase_gate (
    id uuid PRIMARY KEY,
    phase_id uuid NOT NULL UNIQUE REFERENCES project_phase(id),
    status varchar(15) NOT NULL DEFAULT 'OPEN',
    justification text,
    requested_by varchar(150),
    requested_at timestamptz,
    decided_by varchar(150),
    decided_at timestamptz,
    decision_note text
);

CREATE TABLE IF NOT EXISTS project_phase_gate_item (
    id uuid PRIMARY KEY,
    gate_id uuid NOT NULL REFERENCES project_phase_gate(id) ON DELETE CASCADE,
    label varchar(300) NOT NULL,
    met boolean NOT NULL DEFAULT false,
    created_at timestamptz
);

CREATE TABLE IF NOT EXISTS notification_read (
    id uuid PRIMARY KEY,
    user_name varchar(100) NOT NULL,
    alert_id varchar(300) NOT NULL,
    read_at timestamptz NOT NULL,
    CONSTRAINT uk_notification_read_user_alert UNIQUE(user_name, alert_id)
);
CREATE INDEX IF NOT EXISTS idx_notification_read_user ON notification_read(user_name);

CREATE TABLE IF NOT EXISTS project_dependency (
    id uuid PRIMARY KEY,
    predecessor_id uuid NOT NULL REFERENCES projects(id),
    successor_id uuid NOT NULL REFERENCES projects(id),
    type varchar(25) NOT NULL,
    milestone_id uuid REFERENCES project_milestone(id),
    notes text,
    created_at timestamptz,
    created_by varchar(150),
    updated_at timestamptz,
    updated_by varchar(150)
);
CREATE INDEX IF NOT EXISTS idx_project_dependency_predecessor ON project_dependency(predecessor_id);
CREATE INDEX IF NOT EXISTS idx_project_dependency_successor ON project_dependency(successor_id);

-- O check gerado anteriormente pelo Hibernate não conhece os novos tipos de evento.
ALTER TABLE IF EXISTS project_event DROP CONSTRAINT IF EXISTS project_event_type_check;
