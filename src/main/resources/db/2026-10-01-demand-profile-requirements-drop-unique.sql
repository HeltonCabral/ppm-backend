-- ---------------------------------------------------------------------------
-- Remove a restrição única (demand_id, profile_id) de demand_profile_requirements.
--
-- Contexto: uma Demanda pode ter o mesmo perfil em várias entradas, cada uma
-- com a sua quantidade e as suas fases (ex.: Analista ×1 em Desenvolvimento e
-- Analista ×1 em Testes). A unicidade por (demand_id, profile_id) deixou de ser
-- válida. Como ddl-auto=update nunca remove constraints, é preciso dropá-la
-- manualmente em bases já existentes.
--
-- Executar UMA VEZ, com a aplicação parada:
--   psql -h localhost -U postgres -d db_ppm \
--        -f src/main/resources/db/2026-10-01-demand-profile-requirements-drop-unique.sql
--
-- O script é idempotente.
-- ---------------------------------------------------------------------------
ALTER TABLE demand_profile_requirements
    DROP CONSTRAINT IF EXISTS uk_demand_profile_requirements_demand_profile;
