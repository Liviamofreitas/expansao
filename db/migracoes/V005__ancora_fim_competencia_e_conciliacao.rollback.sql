-- Reversão da V005. Reverter a âncora invalida prazos já cadastrados com
-- FIM_COMPETENCIA: a CHECK passa a rejeitá-los na próxima escrita.
BEGIN;
DROP VIEW IF EXISTS regra_conciliacao_a_parametrizar;
ALTER TABLE regra_conciliacao DROP COLUMN IF EXISTS modo_pretendido;
CREATE OR REPLACE FUNCTION prazo_bem_formado(p jsonb) RETURNS boolean
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT p IS NOT NULL
       AND jsonb_typeof(p) = 'object'
       AND p ? 'ancora' AND p ? 'tipo_dia' AND p ? 'offset'
       AND p->>'ancora'  IN ('INICIO_COMPETENCIA', 'ATESTE', 'SOLICITACAO_FATURAMENTO', 'EVENTO')
       AND p->>'tipo_dia' IN ('UTIL', 'CORRIDO')
       AND jsonb_typeof(p->'offset') = 'number'
       AND (p->>'offset') ~ '^-?[0-9]+$'
       AND (p->>'offset')::numeric >= 0
       AND (p->>'ancora' <> 'INICIO_COMPETENCIA' OR (p->>'offset')::numeric >= 1)
$$;
COMMIT;
