-- Migração Demand -> DemandPlanningCycle (planeamento rolling multi-ciclo).
-- Note: este projeto usa Hibernate com ddl-auto=update e não tem Flyway configurado,
-- por isso este script é apenas referência manual (o schema novo é criado automaticamente
-- em dev pelo ddl-auto=update); serve de guia para quem for aplicar isto manualmente em
-- produção, incluindo o backfill de dados que o Hibernate nunca faz sozinho.

CREATE TABLE IF NOT EXISTS demand_planning_cycles (
    id UUID PRIMARY KEY,
    demand_id UUID NOT NULL REFERENCES demands(id),
    strategic_plan_id UUID NOT NULL REFERENCES strategic_plans(id),
    operational_plan_id UUID NOT NULL REFERENCES operational_plans(id),
    strategic_pillar_id UUID REFERENCES strategic_pillars(id),
    strategic_objective_id UUID REFERENCES strategic_objectives(id),
    program_id UUID REFERENCES programs(id),
    status VARCHAR(40) NOT NULL,
    responsible_committee_id UUID REFERENCES committees(id),
    in_strategic_committee BOOLEAN NOT NULL DEFAULT FALSE,
    strategic_committee_at TIMESTAMPTZ,
    pre_score NUMERIC(5,2),
    pre_score_classification VARCHAR(20),
    score_total NUMERIC(19,2),
    score_status VARCHAR(30) NOT NULL DEFAULT 'Não Calculado',
    score_calculated_at TIMESTAMPTZ,
    score_invalidated_at TIMESTAMPTZ,
    score_invalidation_reason TEXT,
    previous_score_snapshot TEXT,
    portfolio_rank INTEGER,
    direction_rank INTEGER,
    committee_rank INTEGER,
    capacity_status VARCHAR(40),
    risk_status VARCHAR(40),
    approval_type VARCHAR(40),
    committee_decision VARCHAR(60),
    rejection_reason TEXT,
    decision_at TIMESTAMPTZ,
    decision_by VARCHAR(150),
    reprioritization_reason VARCHAR(80),
    reprioritization_justification TEXT,
    reprioritized_at TIMESTAMPTZ,
    reprioritized_by VARCHAR(150),
    carried_over BOOLEAN NOT NULL DEFAULT FALSE,
    previous_cycle_id UUID REFERENCES demand_planning_cycles(id),
    cycle_entry_at TIMESTAMPTZ NOT NULL,
    converted_project_id UUID REFERENCES projects(id),
    converted_program_id UUID REFERENCES programs(id),
    converted_at TIMESTAMPTZ,
    converted_by VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT,
    CONSTRAINT uk_demand_strategic_plan UNIQUE (demand_id, strategic_plan_id)
);

-- Backfill: 1 ciclo inicial por Demand que já tinha strategic_plan_id preenchido.
-- Mapeamento de status antigo (Demand.status) -> DemandPlanningStatus:
--   DRAFT -> DRAFT | IN_ANALYSIS -> UNDER_ANALYSIS | IN_PRIORITIZATION/PRIORITIZED/UNDER_PRIORITIZATION -> PRIORITIZATION
--   IN_STRATEGIC_COMMITTEE -> READY_FOR_COMMITTEE (+ in_strategic_committee=true) | READY_FOR_COMMITTEE -> READY_FOR_COMMITTEE
--   APPROVED (committee_decision=CONDITIONALLY_APPROVED) -> CONDITIONALLY_APPROVED | APPROVED (caso contrário) -> APPROVED
--   BACKLOG -> BACKLOG | CONVERTED_TO_PROJECT -> CONVERTED_TO_PROJECT | ARCHIVED -> CLOSED
INSERT INTO demand_planning_cycles (
    id, demand_id, strategic_plan_id, operational_plan_id, strategic_pillar_id, strategic_objective_id,
    program_id, status, responsible_committee_id, in_strategic_committee, strategic_committee_at,
    pre_score, pre_score_classification, score_total, score_status, score_calculated_at, score_invalidated_at,
    score_invalidation_reason, previous_score_snapshot, portfolio_rank, direction_rank, committee_rank,
    capacity_status, risk_status, approval_type, committee_decision, rejection_reason,
    reprioritization_reason, reprioritization_justification, reprioritized_at, reprioritized_by,
    carried_over, cycle_entry_at, converted_project_id, converted_program_id, converted_at, converted_by,
    created_at, updated_at, version
)
SELECT
    gen_random_uuid(), d.id, d.strategic_plan_id, d.operational_plan_id, d.strategic_pillar_id, d.strategic_objective_id,
    d.program_id,
    CASE
        WHEN d.status = 'DRAFT' THEN 'DRAFT'
        WHEN d.status = 'IN_ANALYSIS' THEN 'UNDER_ANALYSIS'
        WHEN d.status IN ('IN_PRIORITIZATION', 'PRIORITIZED', 'UNDER_PRIORITIZATION') THEN 'PRIORITIZATION'
        WHEN d.status IN ('IN_STRATEGIC_COMMITTEE', 'READY_FOR_COMMITTEE') THEN 'READY_FOR_COMMITTEE'
        WHEN d.status = 'APPROVED' AND d.committee_decision = 'CONDITIONALLY_APPROVED' THEN 'CONDITIONALLY_APPROVED'
        WHEN d.status = 'APPROVED' THEN 'APPROVED'
        WHEN d.status = 'BACKLOG' THEN 'BACKLOG'
        WHEN d.status = 'CONVERTED_TO_PROJECT' THEN 'CONVERTED_TO_PROJECT'
        WHEN d.status = 'ARCHIVED' THEN 'CLOSED'
        ELSE 'DRAFT'
    END,
    d.responsible_committee_id, (d.status = 'IN_STRATEGIC_COMMITTEE'), d.strategic_committee_at,
    d.pre_score, d.pre_score_classification, d.score_total, d.score_status, d.score_calculated_at, d.score_invalidated_at,
    d.score_invalidation_reason, d.previous_score_snapshot, d.portfolio_rank, d.direction_rank, d.committee_rank,
    d.capacity_status, d.risk_status, d.approval_type, d.committee_decision, d.rejection_reason,
    d.reprioritization_reason, d.reprioritization_justification, d.reprioritized_at, d.reprioritized_by,
    FALSE, d.created_at, d.converted_project_id, d.converted_program_id, d.converted_at, d.converted_by,
    d.created_at, d.updated_at, 0
FROM demands d
WHERE d.strategic_plan_id IS NOT NULL
  AND d.operational_plan_id IS NOT NULL;

-- demand_scoring passa a ser por ciclo em vez de por demanda (uma demanda pode ter várias
-- avaliações, uma por ciclo). Assume-se aqui o backfill de 1 ciclo por demanda feito acima.
ALTER TABLE demand_scoring ADD COLUMN IF NOT EXISTS planning_cycle_id UUID REFERENCES demand_planning_cycles(id);
UPDATE demand_scoring ds
SET planning_cycle_id = dpc.id
FROM demand_planning_cycles dpc
WHERE dpc.demand_id = ds.demand_id;
-- Após validar que todas as linhas de demand_scoring têm planning_cycle_id preenchido:
-- ALTER TABLE demand_scoring ALTER COLUMN planning_cycle_id SET NOT NULL;
-- ALTER TABLE demand_scoring DROP CONSTRAINT IF EXISTS demand_scoring_demand_id_fkey;
-- ALTER TABLE demand_scoring DROP COLUMN demand_id;

-- Colunas antigas em `demands` que passam a ficar órfãs (o Hibernate com ddl-auto=update
-- nunca as remove sozinho). Só aplicar depois de confirmar que a aplicação nova está de pé
-- e o backfill acima foi validado:
-- ALTER TABLE demands
--   DROP COLUMN IF EXISTS strategic_plan_id,
--   DROP COLUMN IF EXISTS operational_plan_id,
--   DROP COLUMN IF EXISTS strategic_pillar_id,
--   DROP COLUMN IF EXISTS strategic_objective_id,
--   DROP COLUMN IF EXISTS program_id,
--   DROP COLUMN IF EXISTS responsible_committee_id,
--   DROP COLUMN IF EXISTS status,
--   DROP COLUMN IF EXISTS in_strategic_committee,
--   DROP COLUMN IF EXISTS strategic_committee_at,
--   DROP COLUMN IF EXISTS pre_score,
--   DROP COLUMN IF EXISTS pre_score_classification,
--   DROP COLUMN IF EXISTS capacity_status,
--   DROP COLUMN IF EXISTS risk_status,
--   DROP COLUMN IF EXISTS score_total,
--   DROP COLUMN IF EXISTS score_status,
--   DROP COLUMN IF EXISTS score_calculated_at,
--   DROP COLUMN IF EXISTS score_invalidated_at,
--   DROP COLUMN IF EXISTS score_invalidation_reason,
--   DROP COLUMN IF EXISTS previous_score_snapshot,
--   DROP COLUMN IF EXISTS portfolio_rank,
--   DROP COLUMN IF EXISTS direction_rank,
--   DROP COLUMN IF EXISTS committee_rank,
--   DROP COLUMN IF EXISTS approval_type,
--   DROP COLUMN IF EXISTS committee_decision,
--   DROP COLUMN IF EXISTS rejection_reason,
--   DROP COLUMN IF EXISTS converted_project_id,
--   DROP COLUMN IF EXISTS converted_program_id,
--   DROP COLUMN IF EXISTS converted_at,
--   DROP COLUMN IF EXISTS converted_by,
--   DROP COLUMN IF EXISTS is_converted_to_project,
--   DROP COLUMN IF EXISTS is_converted_to_program,
--   DROP COLUMN IF EXISTS reprioritization_reason,
--   DROP COLUMN IF EXISTS reprioritization_justification,
--   DROP COLUMN IF EXISTS reprioritized_at,
--   DROP COLUMN IF EXISTS reprioritized_by;
