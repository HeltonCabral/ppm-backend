-- ---------------------------------------------------------------------------
-- Alocação dos membros da equipa (project_team_members).
--
-- allocation_percentage: 1–100, parte de uma pessoa a tempo inteiro no projeto;
-- start_date / end_date: período da alocação (nulos = período efetivo).
--
-- O ddl-auto=update já cria as colunas, com allocation_percentage DEFAULT 100, o
-- que no PostgreSQL preenche as linhas existentes. Este script garante o mesmo
-- resultado numa base onde a coluna tenha sido criada sem valor por defeito:
-- os membros existentes ficam com 100% e datas nulas.
--
-- Executar UMA VEZ, com a aplicação parada:
--   psql -h localhost -U postgres -d db_ppm \
--        -f src/main/resources/db/2026-10-12-project-team-allocation.sql
--
-- O script é idempotente.
-- ---------------------------------------------------------------------------
ALTER TABLE project_team_members ADD COLUMN IF NOT EXISTS allocation_percentage integer;
ALTER TABLE project_team_members ADD COLUMN IF NOT EXISTS start_date date;
ALTER TABLE project_team_members ADD COLUMN IF NOT EXISTS end_date date;
ALTER TABLE project_team_members ALTER COLUMN allocation_percentage SET DEFAULT 100;
UPDATE project_team_members SET allocation_percentage = 100 WHERE allocation_percentage IS NULL;
