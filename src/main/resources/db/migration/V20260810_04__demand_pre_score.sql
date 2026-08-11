-- Adicionar campos de pré-score à tabela demands

ALTER TABLE demands
    ADD COLUMN IF NOT EXISTS pre_score NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS pre_score_classification VARCHAR(20);

COMMENT ON COLUMN demands.pre_score IS 'Pré-score calculado automaticamente (0-100)';
COMMENT ON COLUMN demands.pre_score_classification IS 'Classificação do pré-score: VERY_HIGH, HIGH, MEDIUM, LOW, VERY_LOW';
