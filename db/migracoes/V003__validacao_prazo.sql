-- =============================================================================
-- SGDF — V003 · Validação de forma do prazo estruturado
-- Referência: cap. 7.3, história F0-06.
--
-- O prazo é jsonb em contrato_servico e regra_exigibilidade. Sem validação, um
-- cadastro malformado só apareceria na abertura do ciclo, meses depois e longe
-- de quem o digitou.
--
-- Isto valida FORMA, não semântica: o cálculo da data vive no serviço, com a
-- suíte de conformidade em especificacao/prazo/casos.json. O banco só garante
-- que o objeto tem os campos certos, com valores dentro do domínio.
-- =============================================================================

BEGIN;

CREATE OR REPLACE FUNCTION prazo_bem_formado(p jsonb) RETURNS boolean
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT p IS NOT NULL
       AND jsonb_typeof(p) = 'object'
       AND p ? 'ancora' AND p ? 'tipo_dia' AND p ? 'offset'
       AND p->>'ancora'  IN ('INICIO_COMPETENCIA', 'ATESTE', 'SOLICITACAO_FATURAMENTO', 'EVENTO')
       AND p->>'tipo_dia' IN ('UTIL', 'CORRIDO')
       AND jsonb_typeof(p->'offset') = 'number'
       AND (p->>'offset') ~ '^-?[0-9]+$'                       -- inteiro, não fracionário
       AND (p->>'offset')::numeric >= 0                        -- cap. 7.3: offset >= 0
       -- Âncora ordinal não admite offset 0: não existe "dia zero do mês".
       AND (p->>'ancora' <> 'INICIO_COMPETENCIA' OR (p->>'offset')::numeric >= 1)
$$;

COMMENT ON FUNCTION prazo_bem_formado(jsonb) IS
    'Valida a FORMA do prazo estruturado do cap. 7.3. O cálculo da data é do '
    'serviço — ver especificacao/prazo/casos.json.';

ALTER TABLE contrato_servico
    ADD CONSTRAINT contrato_prazo_bem_formado
    CHECK (prazo_bem_formado(data_contratual_faturamento));

ALTER TABLE regra_exigibilidade
    ADD CONSTRAINT regra_prazo_bem_formado
    CHECK (prazo_bem_formado(prazo));

COMMIT;
