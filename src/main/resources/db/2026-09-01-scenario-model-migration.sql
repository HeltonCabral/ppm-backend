-- ---------------------------------------------------------------------------
-- Migração para o modelo de decisão por cenários de portfólio.
--
-- Executar UMA VEZ, com a aplicação parada, antes do primeiro arranque do novo
-- build. O Hibernate (ddl-auto=update) cria as tabelas novas mas não converte
-- os valores antigos da coluna demand_planning_cycles.status, que deixaram de
-- existir na enum DemandPlanningStatus e rebentam em qualquer leitura.
--
--   psql -h localhost -U postgres -d db_ppm \
--        -f src/main/resources/db/2026-09-01-scenario-model-migration.sql
--
-- O script é idempotente: pode ser reexecutado sem efeitos adicionais.
-- ---------------------------------------------------------------------------

BEGIN;

-- 1. Conversão dos estados -----------------------------------------------------
--
--    APPROVED / CONDITIONALLY_APPROVED  -> IN_PLAN     (selecionadas para o plano)
--    CONVERTED_TO_PROJECT / _PROGRAM    -> IN_PLAN     (a conversão passa a ser
--                                                       marcada por converted_project_id
--                                                       / converted_program_id, não pelo status)
--    AWAITING_COMMITTEE                 -> PRIORITIZED (pool de composição de cenários)
--    CLOSED                             -> BACKLOG

UPDATE demand_planning_cycles
   SET status = 'IN_PLAN'
 WHERE status IN ('APPROVED', 'CONDITIONALLY_APPROVED', 'CONVERTED_TO_PROJECT', 'CONVERTED_TO_PROGRAM');

UPDATE demand_planning_cycles
   SET status = 'PRIORITIZED'
 WHERE status = 'AWAITING_COMMITTEE';

UPDATE demand_planning_cycles
   SET status = 'BACKLOG'
 WHERE status = 'CLOSED';

-- Salvaguarda: qualquer valor não previsto fica em BACKLOG em vez de rebentar.
UPDATE demand_planning_cycles
   SET status = 'BACKLOG'
 WHERE status NOT IN ('DRAFT', 'IN_ANALYSIS', 'IN_REVIEW', 'IN_PRIORITIZATION',
                      'PRIORITIZED', 'IN_PLAN', 'BACKLOG');

-- 2. Histórico de pedidos de revisão ------------------------------------------
--
--    Os pedidos de revisão que viviam nas colunas do ciclo passam para
--    demand_committee_actions. São importados como RESOLVIDOS: o ciclo já não
--    está em revisão, e deixá-los abertos bloquearia a composição de cenários.
--    previous_status recebe o status atual do ciclo — o valor original não foi
--    guardado no modelo antigo.

DO $$
BEGIN
    IF to_regclass('public.demand_committee_actions') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'demand_planning_cycles'
                      AND column_name = 'committee_decision') THEN

        INSERT INTO demand_committee_actions (
            id, planning_cycle_id, committee_id, action, justification,
            requested_by, requested_at, resolved_by, resolved_at,
            previous_status, resolution_notes, created_at)
        SELECT gen_random_uuid(),
               c.id,
               c.responsible_committee_id,
               'REQUEST_REVISION',
               COALESCE(NULLIF(TRIM(c.rejection_reason), ''), 'Pedido de revisão migrado do modelo anterior'),
               COALESCE(NULLIF(TRIM(c.decision_by), ''), 'system'),
               COALESCE(c.decision_at, NOW()),
               COALESCE(NULLIF(TRIM(c.decision_by), ''), 'system'),
               COALESCE(c.decision_at, NOW()),
               c.status,
               'Importado da coluna committee_decision do modelo anterior',
               COALESCE(c.decision_at, NOW())
          FROM demand_planning_cycles c
         WHERE c.committee_decision = 'REVISION_REQUESTED'
           AND NOT EXISTS (SELECT 1 FROM demand_committee_actions a
                            WHERE a.planning_cycle_id = c.id);
    END IF;
END $$;

-- 3. Remoção das colunas do modelo anterior -----------------------------------
--
--    O Hibernate nunca remove colunas; ficam órfãs e a duplicar informação que
--    passou a viver em demand_committee_actions e nos cenários.

ALTER TABLE demand_planning_cycles DROP COLUMN IF EXISTS committee_decision;
ALTER TABLE demand_planning_cycles DROP COLUMN IF EXISTS decision_at;
ALTER TABLE demand_planning_cycles DROP COLUMN IF EXISTS decision_by;
ALTER TABLE demand_planning_cycles DROP COLUMN IF EXISTS approval_type;
ALTER TABLE demand_planning_cycles DROP COLUMN IF EXISTS rejection_reason;

-- Tabela criada na iteração anterior (decisões individuais do comité), agora
-- substituída por demand_committee_actions.
DROP TABLE IF EXISTS demand_committee_decisions;

COMMIT;

-- Verificação ------------------------------------------------------------------
-- SELECT status, COUNT(*) FROM demand_planning_cycles GROUP BY status ORDER BY 1;
