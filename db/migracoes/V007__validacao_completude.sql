-- =============================================================================
-- SGDF — V007 · Validação V8 (completude de campos essenciais)
-- Origem: achado A12 de docs/ACHADOS-MASSA-REAL.md.
--
-- Dois comprovantes reais do Itaú (SISPAG SALÁRIOS) trazem SÓ OS RÓTULOS —
-- "Nome da empresa:", "Agência:", "Conta corrente:", "Valor:" — e nenhum valor.
-- São 603 caracteres de texto nativo. Passam em V1 (antivírus e MIME), passam
-- em V2 (legibilidade, que conta caracteres) e seriam anexados a uma exigência
-- como prova de pagamento.
--
-- Nenhuma das validações do cap. 8.4 pergunta se o documento tem CONTEÚDO.
-- V1 pergunta se é seguro, V2 se é legível, V3 de quem é, V4 de quando é,
-- V5 até quando vale, V6 se os formatos estão completos, V7 se é inédito.
-- Um documento vazio responde bem a todas.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Declaração dos campos essenciais por tipo documental
--
-- Campo essencial é aquele cuja AUSÊNCIA torna o documento inútil para a função
-- que ele cumpre no ciclo — não é "todo campo que a regra extrai".
--
-- O critério de derivação está declarado aqui porque a lista não foi inventada:
-- é essencial o campo que alguma OUTRA validação ou regra de conciliação
-- consome. Um documento sem ele não pode ser validado nem conciliado, o que o
-- torna, para efeito do portão documental, equivalente a não ter sido entregue.
--
--   V3 consome cnpj        → essencial em todo tipo de escopo corporativo
--   V4 consome competencia → essencial em todo tipo com defasagem por competência
--   V5 consome validade    → essencial nas certidões
--   R01/R02 consomem valor → essencial nas guias e nos comprovantes
--
-- Fica separado de `campos` de propósito: `campos` é COMO extrair, isto é O QUE
-- não pode faltar. Misturar os dois faria a mudança de um padrão de extração
-- mexer, sem querer, no critério de reprovação.
-- -----------------------------------------------------------------------------
ALTER TABLE regra_reconhecimento
    ADD COLUMN campos_essenciais jsonb NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN regra_reconhecimento.campos_essenciais IS
    'V8 (achado A12): lista de {campo, formato, motivo}. A ausência ou o formato '
    'inválido de qualquer um reprova o documento. Derivada do que V3/V4/V5 e as '
    'regras de conciliação consomem — ver o cabeçalho de V007.';

-- Forma de cada entrada, e a garantia de que todo campo essencial é um campo
-- que a regra sabe extrair — uma regra que exige um campo sem saber onde
-- procurá-lo reprova tudo, sempre.
--
-- Vai numa função porque CHECK não aceita subconsulta, e percorrer um array
-- jsonb exige uma. Uma função SQL pura, IMMUTABLE, é aceita e faz o mesmo.
CREATE FUNCTION regra_recon_essenciais_validos(campos jsonb, essenciais jsonb)
    RETURNS boolean
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
AS $$
    SELECT jsonb_typeof($2) = 'array'
       AND NOT EXISTS (
            SELECT 1
            FROM jsonb_array_elements($2) AS e
            WHERE jsonb_typeof(e) <> 'object'
               OR e->>'campo'   IS NULL OR btrim(e->>'campo')   = ''
               OR e->>'formato' IS NULL OR btrim(e->>'formato') = ''
               -- Reprovar um documento sem dizer ao usuário por que aquele
               -- campo era indispensável é o que o cap. 12 proíbe.
               OR e->>'motivo'  IS NULL OR length(btrim(e->>'motivo')) < 10
               -- O campo essencial precisa estar entre os que a regra extrai.
               OR NOT EXISTS (
                    SELECT 1
                    FROM jsonb_array_elements($1) AS c
                    WHERE c->>'nome' = e->>'campo'
               )
       );
$$;

COMMENT ON FUNCTION regra_recon_essenciais_validos(jsonb, jsonb) IS
    'Usada em CHECK: uma expressão com subconsulta não é aceita diretamente, '
    'mas uma função IMMUTABLE e pura é.';

ALTER TABLE regra_reconhecimento
    ADD CONSTRAINT regra_recon_essenciais_validos CHECK (
        regra_recon_essenciais_validos(campos, campos_essenciais)
    );

COMMENT ON CONSTRAINT regra_recon_essenciais_validos ON regra_reconhecimento IS
    'Forma da lista de campos essenciais e coerência com os campos extraíveis. '
    'Sem isto o jsonb aceita qualquer coisa e V8 passa a depender de o cadastro '
    'estar bem preenchido, que é o que o banco existe para não precisar supor.';

-- -----------------------------------------------------------------------------
-- 2. Registro do veredito das validações unitárias
--
-- LACUNA ENCONTRADA AO IMPLEMENTAR V8: a máquina de estados do cap. 6.1 leva
-- RECEBIDO → REJEITADO "por falha unitária", mas não havia onde gravar QUAL
-- validação falhou e por quê. O motivo existia só na mensagem de notificação,
-- que não é registro auditável.
--
-- O cap. 16 exige que toda decisão automática seja reproduzível a partir de
-- documento (hash) + versão da regra. Sem esta tabela, uma reprovação não é
-- reproduzível: ninguém consegue dizer, seis meses depois, por que aquele
-- documento foi recusado.
-- -----------------------------------------------------------------------------
CREATE TABLE validacao_documento (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_id   uuid        NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    codigo         text        NOT NULL CHECK (codigo IN (
                       'V1', 'V2', 'V3', 'V4', 'V5', 'V6', 'V7', 'V8')),
    resultado      text        NOT NULL CHECK (resultado IN (
                       'APROVADO', 'REPROVADO', 'NAO_APLICAVEL')),
    motivo         text,
    detalhe        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    regra_recon_id uuid        REFERENCES regra_reconhecimento (id),
    executada_em   timestamptz NOT NULL DEFAULT now(),
    -- Reprovar sem dizer por quê é o que a tabela existe para impedir.
    CONSTRAINT validacao_reprovada_tem_motivo CHECK (
        resultado <> 'REPROVADO' OR length(btrim(coalesce(motivo, ''))) >= 10
    ),
    -- NAO_APLICAVEL também precisa de motivo: "não se aplica" é uma decisão.
    CONSTRAINT validacao_dispensada_tem_motivo CHECK (
        resultado <> 'NAO_APLICAVEL' OR length(btrim(coalesce(motivo, ''))) >= 10
    )
);

COMMENT ON TABLE validacao_documento IS
    'Veredito de cada validação unitária do cap. 8.4 sobre um documento. '
    'Histórico: uma nova execução acrescenta linha, nunca substitui — '
    'reprocessar com regra nova não apaga por que a regra antiga recusou.';

CREATE INDEX ix_validacao_documento ON validacao_documento (documento_id, codigo);
CREATE INDEX ix_validacao_reprovada ON validacao_documento (documento_id)
    WHERE resultado = 'REPROVADO';

-- O veredito vigente de cada validação: a execução mais recente.
CREATE VIEW validacao_vigente AS
SELECT DISTINCT ON (documento_id, codigo)
       documento_id, codigo, resultado, motivo, detalhe, regra_recon_id, executada_em
FROM   validacao_documento
ORDER  BY documento_id, codigo, executada_em DESC;

COMMENT ON VIEW validacao_vigente IS
    'Última execução de cada validação por documento. Consultar a tabela direto '
    'devolve o histórico, que quase nunca é o que a tela quer.';

COMMIT;
