-- "EXT – Externo" deixa de ser uma direção participante: passa a ser o campo
-- external_participation da demanda (fornecedor por definir).
--
-- As colunas são criadas pelo ddl-auto=update no arranque; este script move os dados
-- das demandas já importadas com EXT como direção participante. Idempotente.
-- Aplicar à mão, depois do arranque da versão nova do backend.

BEGIN;

-- 1. Marca as demandas que tinham EXT e guarda as observações dessas linhas (se não tiverem já notas).
UPDATE demands d
SET external_participation = TRUE,
    external_participation_notes = COALESCE(NULLIF(TRIM(d.external_participation_notes), ''), (
        SELECT NULLIF(STRING_AGG(DISTINCT TRIM(p.observations), E'\n'), '')
        FROM demand_participating_directions p
        WHERE p.demand_id = d.id
          AND UPPER(TRIM(p.direction_code)) = 'EXT'
          AND p.observations IS NOT NULL
          AND TRIM(p.observations) <> ''
    ))
WHERE EXISTS (
    SELECT 1 FROM demand_participating_directions p
    WHERE p.demand_id = d.id AND UPPER(TRIM(p.direction_code)) = 'EXT'
);

-- 2. Remove as áreas dessas linhas ("N/A – Entidade externa") e as próprias linhas.
DELETE FROM demand_participating_direction_areas a
USING demand_participating_directions p
WHERE a.participating_direction_id = p.id
  AND UPPER(TRIM(p.direction_code)) = 'EXT';

DELETE FROM demand_participating_directions
WHERE UPPER(TRIM(direction_code)) = 'EXT';

-- 3. A contagem de direções guardada pela complexidade incluía o EXT: recalcular a complexidade
--    dessas demandas (Guardar no formulário) atualiza-a.

COMMIT;
