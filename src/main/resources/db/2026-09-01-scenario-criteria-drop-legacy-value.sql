-- ---------------------------------------------------------------------------
-- Remove a coluna legada `value` da tabela planning_scenario_criteria.
--
-- Contexto: a entidade PlanningScenarioCriterion mapeia o campo Java `value`
-- para a coluna `criterion_value`. Em bases criadas antes desse rename, o
-- Hibernate deixou a coluna original `value NOT NULL`. Como ddl-auto=update
-- nunca dropa colunas, qualquer INSERT novo passa a violar essa restrição.
--
-- Executar UMA VEZ, com a aplicação parada:
--   psql -h localhost -U postgres -d db_ppm \
--        -f src/main/resources/db/2026-09-01-scenario-criteria-drop-legacy-value.sql
--
-- O script é idempotente.
-- ---------------------------------------------------------------------------

BEGIN;

-- Salvaguarda: se por algum motivo `criterion_value` estiver vazio mas `value`
-- ainda tiver o dado antigo, migrar antes de dropar.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'planning_scenario_criteria'
                  AND column_name = 'value') THEN
        UPDATE planning_scenario_criteria
           SET criterion_value = value
         WHERE criterion_value IS NULL
           AND value IS NOT NULL;
    END IF;
END $$;

ALTER TABLE planning_scenario_criteria DROP COLUMN IF EXISTS value;

COMMIT;
