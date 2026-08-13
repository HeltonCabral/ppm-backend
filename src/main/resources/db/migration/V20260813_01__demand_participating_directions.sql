CREATE TABLE IF NOT EXISTS demand_participating_directions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demand_id UUID NOT NULL REFERENCES demands(id) ON DELETE CASCADE,
    direction_code VARCHAR(60) NOT NULL,
    area_code VARCHAR(60),
    participation_type VARCHAR(50) NOT NULL,
    observations TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(150) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(150) NOT NULL DEFAULT 'system',
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_demand_participating_directions_demand_id
    ON demand_participating_directions(demand_id);

COMMENT ON TABLE demand_participating_directions IS 'Direções participantes de uma demanda';
COMMENT ON COLUMN demand_participating_directions.demand_id IS 'Referência à demanda';
COMMENT ON COLUMN demand_participating_directions.direction_code IS 'Código da direção participante';
COMMENT ON COLUMN demand_participating_directions.area_code IS 'Código da área participante da direção';
COMMENT ON COLUMN demand_participating_directions.participation_type IS 'Tipo de participação (IMPLEMANTATION, INTERVENIENT)';
COMMENT ON COLUMN demand_participating_directions.observations IS 'Observações sobre a participação';
