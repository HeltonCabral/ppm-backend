-- Remover campo suggested_committee_id e índice relacionado da tabela demands

DROP INDEX IF EXISTS idx_demands_suggested_committee_id;

ALTER TABLE demands
    DROP COLUMN IF EXISTS suggested_committee_id;
