-- Remover campo committee_change_justification da tabela demands

ALTER TABLE demands
    DROP COLUMN IF EXISTS committee_change_justification;
