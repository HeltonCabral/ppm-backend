ALTER TABLE projects ADD COLUMN IF NOT EXISTS project_manager_id UUID;

CREATE INDEX IF NOT EXISTS idx_projects_project_manager_id ON projects(project_manager_id);
