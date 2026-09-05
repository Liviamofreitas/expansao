-- =============================================================================
-- SGDF — V011 · Rascunho da matriz (história F0-05)
--
-- Cap. 12: "alteração cria rascunho → publicação gera nova versão; bloqueante
-- pede aprovação DAF". Critério de aceite: "alterar regra não afeta ciclo
-- aberto; histórico lista versões com autor e motivo".
--
-- POR QUE O RASCUNHO NÃO É UM ESTADO DE `versao_matriz`
--
-- A saída natural seria acrescentar `situacao IN ('RASCUNHO','PUBLICADA')` e
-- virar o valor na publicação. Não dá, e a impossibilidade é informativa:
-- `versao_matriz` é append-only (V002 revoga UPDATE e DELETE) exatamente porque
-- `ciclo.versao_matriz_id` congela a versão da época e `regra_exigibilidade`
-- aponta para ela. Uma linha que muda de estado é uma linha que muda — e a
-- garantia da F0-05 depende de que não mude.
--
-- Então o rascunho é uma área de trabalho SEPARADA e mutável. Publicar não
-- promove o rascunho: publicar CRIA uma versão nova, imutável, aplicando os
-- itens do rascunho sobre a versão de que ele partiu. O rascunho vira histórico
-- de quem propôs o quê.
-- =============================================================================

BEGIN;

CREATE TABLE rascunho_matriz (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    numero_proposto     text        NOT NULL,
    motivo              text        NOT NULL,
    base_versao_id      uuid        REFERENCES versao_matriz (id),
    situacao            text        NOT NULL DEFAULT 'ABERTO' CHECK (situacao IN (
                            'ABERTO', 'PUBLICADO', 'DESCARTADO')),
    criado_em           timestamptz NOT NULL DEFAULT now(),
    criado_por          text        NOT NULL,
    publicado_em        timestamptz,
    publicado_por       text,
    versao_publicada_id uuid        REFERENCES versao_matriz (id),
    descartado_motivo   text,

    -- O motivo vai para `versao_matriz.motivo` e é o que o histórico da F0-05
    -- exibe. "ajuste" não explica nada a quem lê a lista de versões seis meses
    -- depois, que é exatamente quando alguém precisa dela.
    CONSTRAINT rascunho_motivo_substantivo CHECK (length(btrim(motivo)) >= 20),

    CONSTRAINT rascunho_publicacao_completa CHECK (
        (situacao <> 'PUBLICADO' AND publicado_em IS NULL AND publicado_por IS NULL
             AND versao_publicada_id IS NULL)
     OR (situacao =  'PUBLICADO' AND publicado_em IS NOT NULL AND publicado_por IS NOT NULL
             AND versao_publicada_id IS NOT NULL)),

    CONSTRAINT rascunho_descarte_com_motivo CHECK (
        situacao <> 'DESCARTADO'
        OR (descartado_motivo IS NOT NULL AND btrim(descartado_motivo) <> ''))
);

COMMENT ON TABLE rascunho_matriz IS
    'Área de trabalho mutável. Publicar CRIA uma versao_matriz nova aplicando os '
    'itens sobre a base — nunca promove o rascunho a versão.';
COMMENT ON COLUMN rascunho_matriz.base_versao_id IS
    'A versão de que o rascunho partiu. NULL só na primeira matriz, que não tem base.';
COMMENT ON COLUMN rascunho_matriz.versao_publicada_id IS
    'A versão que este rascunho produziu — o elo entre a proposta e o resultado.';

-- Uma versão publicada tem um rascunho de origem, no máximo: dois rascunhos
-- apontando para a mesma versão significaria que ela foi produzida duas vezes.
CREATE UNIQUE INDEX ux_rascunho_versao_publicada
    ON rascunho_matriz (versao_publicada_id) WHERE versao_publicada_id IS NOT NULL;

CREATE INDEX ix_rascunho_aberto ON rascunho_matriz (criado_em)
    WHERE situacao = 'ABERTO';

-- -----------------------------------------------------------------------------
-- Os itens: o que muda em relação à base.
--
-- O rascunho guarda o DELTA, não a matriz inteira. Guardar a matriz inteira
-- faria cada rascunho copiar 176 regras para alterar uma, e a revisão humana
-- teria de encontrar a diferença no meio da cópia — que é o oposto do que uma
-- revisão de mudança precisa mostrar.
-- -----------------------------------------------------------------------------
CREATE TABLE rascunho_regra (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rascunho_id            uuid        NOT NULL REFERENCES rascunho_matriz (id)
                                       ON DELETE CASCADE,
    acao                   text        NOT NULL CHECK (acao IN (
                               'INCLUIR', 'ALTERAR', 'REMOVER')),
    regra_origem_id        uuid        REFERENCES regra_exigibilidade (id),

    tipo_id                uuid        REFERENCES tipo_documental (id),
    alvo                   text        CHECK (alvo IS NULL OR alvo IN ('MODALIDADE', 'CONTRATO')),
    alvo_modalidade_id     uuid        REFERENCES modalidade (id),
    alvo_contrato_id       uuid        REFERENCES contrato_servico (id),
    obrigatoriedade        text        CHECK (obrigatoriedade IS NULL OR obrigatoriedade IN (
                               'OBRIGATORIO', 'CONDICIONAL', 'DISPENSADO')),
    criticidade            text        CHECK (criticidade IS NULL OR criticidade IN (
                               'BLOQUEANTE', 'NAO_BLOQUEANTE')),
    prazo                  jsonb,
    responsavel_titular    text,
    responsavel_substituto text,
    fundamento             text,
    vigencia_ini           date,
    vigencia_fim           date,
    criado_em              timestamptz NOT NULL DEFAULT now(),
    criado_por             text        NOT NULL,

    -- REMOVER e ALTERAR precisam dizer O QUÊ; INCLUIR não tem o que apontar.
    CONSTRAINT rascunho_regra_origem_coerente CHECK (
        (acao = 'INCLUIR' AND regra_origem_id IS NULL)
     OR (acao <> 'INCLUIR' AND regra_origem_id IS NOT NULL)),

    -- INCLUIR e ALTERAR produzem uma regra, e regra_exigibilidade tem esses
    -- campos NOT NULL. Deixar a validação para o INSERT da publicação faria o
    -- rascunho parecer válido até o momento em que já não dá para voltar atrás.
    CONSTRAINT rascunho_regra_completa CHECK (
        acao = 'REMOVER'
     OR (tipo_id IS NOT NULL AND alvo IS NOT NULL AND obrigatoriedade IS NOT NULL
         AND prazo IS NOT NULL AND responsavel_titular IS NOT NULL
         AND vigencia_ini IS NOT NULL
         AND prazo_bem_formado(prazo)
         AND ((alvo = 'MODALIDADE' AND alvo_modalidade_id IS NOT NULL
                   AND alvo_contrato_id IS NULL)
           OR (alvo = 'CONTRATO' AND alvo_contrato_id IS NOT NULL
                   AND alvo_modalidade_id IS NULL)))),

    CONSTRAINT rascunho_regra_vigencia CHECK (
        vigencia_fim IS NULL OR vigencia_ini IS NULL OR vigencia_fim >= vigencia_ini),

    -- A mesma regra da base tocada duas vezes no mesmo rascunho é ambiguidade:
    -- alterar e remover a mesma linha não tem resultado definido.
    CONSTRAINT rascunho_regra_origem_unica UNIQUE (rascunho_id, regra_origem_id)
);

CREATE INDEX ix_rascunho_regra ON rascunho_regra (rascunho_id);

COMMENT ON CONSTRAINT rascunho_regra_completa ON rascunho_regra IS
    'Valida no rascunho o que regra_exigibilidade exigirá na publicação. Descobrir '
    'a falta no momento de publicar seria descobrir tarde demais.';

COMMIT;
