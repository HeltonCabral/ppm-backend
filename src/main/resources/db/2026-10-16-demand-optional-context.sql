-- Problema / oportunidade e resultado pretendido deixam de ser obrigatórios numa demanda
-- (o import do Excel não os traz). Idempotente. A aplicação já o faz no arranque
-- (DemandOptionalFieldsMigration); este script fica para aplicar à mão, se preciso.

ALTER TABLE demands ALTER COLUMN problem_statement DROP NOT NULL;
ALTER TABLE demands ALTER COLUMN desired_outcome DROP NOT NULL;
