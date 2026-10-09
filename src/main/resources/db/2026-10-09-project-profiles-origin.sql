-- ---------------------------------------------------------------------------
-- Origem dos perfis previstos do projeto (project_profiles.origin).
--
-- DEMAND  = copiado da demanda na aprovação do plano;
-- PROJECT = acrescentado no projeto.
--
-- O ddl-auto=update já cria a coluna com DEFAULT 'DEMAND', o que no PostgreSQL
-- preenche as linhas existentes. Este script garante o mesmo resultado numa base
-- onde a coluna tenha sido criada sem valor por defeito.
--
-- Executar UMA VEZ, com a aplicação parada:
--   psql -h localhost -U postgres -d db_ppm \
--        -f src/main/resources/db/2026-10-09-project-profiles-origin.sql
--
-- O script é idempotente.
-- ---------------------------------------------------------------------------
ALTER TABLE project_profiles ADD COLUMN IF NOT EXISTS origin varchar(10);
ALTER TABLE project_profiles ALTER COLUMN origin SET DEFAULT 'DEMAND';
UPDATE project_profiles SET origin = 'DEMAND' WHERE origin IS NULL;
