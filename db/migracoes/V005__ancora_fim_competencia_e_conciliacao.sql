-- =============================================================================
-- SGDF — V005 · Âncora FIM_COMPETENCIA e severidade pretendida da conciliação
-- Referência: decisões sobre os achados E-08 e E-01 (docs/ERRATA-V1.md).
--
-- Duas mudanças independentes, na mesma migration porque ambas destravam a
-- carga da matriz e das regras de conciliação.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. E-08 · âncora FIM_COMPETENCIA
--
-- "ENTRE DIA 30 A 31" quer dizer "até o fim do mês". A âncora expressa isso
-- diretamente, em vez de depender do ajuste com aviso.
--
-- Diferença de domínio em relação a INICIO_COMPETENCIA: aqui offset 0 é válido
-- e significa "o próprio fim do mês"; lá não existe "dia zero do mês".
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION prazo_bem_formado(p jsonb) RETURNS boolean
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT p IS NOT NULL
       AND jsonb_typeof(p) = 'object'
       AND p ? 'ancora' AND p ? 'tipo_dia' AND p ? 'offset'
       AND p->>'ancora'  IN ('INICIO_COMPETENCIA', 'FIM_COMPETENCIA',
                             'ATESTE', 'SOLICITACAO_FATURAMENTO', 'EVENTO')
       AND p->>'tipo_dia' IN ('UTIL', 'CORRIDO')
       AND jsonb_typeof(p->'offset') = 'number'
       AND (p->>'offset') ~ '^-?[0-9]+$'
       AND (p->>'offset')::numeric >= 0
       AND (p->>'ancora' <> 'INICIO_COMPETENCIA' OR (p->>'offset')::numeric >= 1)
$$;

COMMENT ON FUNCTION prazo_bem_formado(jsonb) IS
    'Valida a FORMA do prazo estruturado do cap. 7.3, com a âncora '
    'FIM_COMPETENCIA acrescentada pela decisão sobre o achado E-08. O cálculo '
    'da data é do serviço — ver especificacao/prazo/casos.json.';

-- -----------------------------------------------------------------------------
-- 2. E-01 · severidade pretendida da regra de conciliação
--
-- A numeração canônica passa a ser a do Anexo 1 (aba REGRAS_CONCILIACAO). As 12
-- regras chegam com a coluna "Tolerância (preencher)" vazia, e o cap. 9 manda
-- que regra sem tolerância opere em modo ALERTA — restrição já imposta pela
-- V001 (regra_conc_sem_tolerancia_nao_bloqueia).
--
-- Sem um lugar para guardar a severidade que o Anexo 1 PRETENDE, essa
-- informação se perderia na carga, e ninguém saberia quais regras devem virar
-- BLOQUEIO assim que a tolerância for preenchida. É isso que esta coluna
-- guarda: a intenção declarada, distinta do modo em vigor.
-- -----------------------------------------------------------------------------
ALTER TABLE regra_conciliacao
    ADD COLUMN modo_pretendido text
    CHECK (modo_pretendido IS NULL OR modo_pretendido IN ('BLOQUEIO', 'ALERTA'));

COMMENT ON COLUMN regra_conciliacao.modo_pretendido IS
    'Severidade declarada no Anexo 1. Enquanto tolerancia for NULL, o modo em '
    'vigor é ALERTA por força do cap. 9; preenchida a tolerância, esta coluna '
    'diz para onde o modo deve ir. NULL = sem intenção declarada.';

-- Consulta operacional: o que falta parametrizar para a regra passar a bloquear.
CREATE VIEW regra_conciliacao_a_parametrizar AS
    SELECT codigo, nome, fase, modo, modo_pretendido
      FROM regra_conciliacao
     WHERE ativo
       AND tolerancia IS NULL
       AND modo_pretendido = 'BLOQUEIO'
     ORDER BY fase, codigo;

COMMENT ON VIEW regra_conciliacao_a_parametrizar IS
    'Regras que o Anexo 1 quer bloqueantes e que ainda operam em alerta por '
    'falta de tolerância cadastrada. Lista de trabalho da área demandante.';

COMMIT;
