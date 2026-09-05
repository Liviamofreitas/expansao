-- =============================================================================
-- SGDF — V014 · De-para de rubricas (histórias F2-01 e F2-02)
--
-- Cap. 7.2, última linha: "os códigos de rubrica são CADASTRO (tabela de-para
-- por sistema de folha), NÃO código-fonte".
--
-- A razão é operacional, não estética: a Engesoftware pode trocar de sistema de
-- folha, e o cliente pode ter mais de um. Um código de rubrica em constante Java
-- transforma "o RH mudou o plano de contas" em release de software.
--
-- DUAS FORMAS DE MAPEAR, E A SEGUNDA EXISTE POR CAUSA DO A05
--
-- A FOPAG imprime o código ("08305 VALE ALIMENTACAO"); o CONTRACHEQUE não. Como
-- a folha da fase 1b é derivada dos contracheques enquanto o export do cap. 14.3
-- não existe, um de-para só por código não reconheceria nada na fonte que temos.
-- Por isso a linha aceita mapear por código OU por descrição normalizada — e o
-- código, quando existe, vence: a descrição varia de grafia entre competências.
-- =============================================================================

BEGIN;

CREATE TABLE rubrica_de_para (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sistema               text        NOT NULL,
    codigo                text,
    descricao_normalizada text,
    descricao_original    text        NOT NULL,
    papel                 text        NOT NULL CHECK (papel IN (
                              -- estruturais: totais e bases da folha
                              'TOTAL_PROVENTOS', 'TOTAL_DESCONTOS', 'LIQUIDO',
                              'BASE_INSS', 'BASE_IRRF', 'BASE_FGTS', 'FGTS_MES',
                              -- encargos e descontos
                              'INSS', 'IRRF',
                              -- benefícios (cap. 7.2, condicional A-ou-B do VT)
                              'VALE_TRANSPORTE', 'VALE_ALIMENTACAO', 'PLANO_DE_SAUDE',
                              -- gatilhos de evento do cap. 7.2
                              'ADIANTAMENTO_13', 'FERIAS', 'RESCISAO',
                              -- o resto: mapeada, mas sem papel semântico
                              'OUTRA')),
    vigencia_ini          date        NOT NULL,
    vigencia_fim          date,
    criado_em             timestamptz NOT NULL DEFAULT now(),
    criado_por            text        NOT NULL,

    CONSTRAINT rubrica_tem_como_casar CHECK (
        codigo IS NOT NULL OR descricao_normalizada IS NOT NULL),
    CONSTRAINT rubrica_vigencia_coerente CHECK (
        vigencia_fim IS NULL OR vigencia_fim >= vigencia_ini),

    -- A descrição normalizada é o que a junção compara: sem acento, sem caixa,
    -- separadores colapsados. Guardá-la já normalizada evita que a comparação
    -- dependa de a aplicação normalizar do mesmo jeito toda vez.
    CONSTRAINT rubrica_descricao_normalizada CHECK (
        descricao_normalizada IS NULL
        OR descricao_normalizada = upper(btrim(regexp_replace(
               descricao_normalizada, '\s+', ' ', 'g'))))
);

COMMENT ON TABLE rubrica_de_para IS
    'Cap. 7.2: código de rubrica é cadastro, não código-fonte. Mapeia por código '
    '(FOPAG) ou por descrição normalizada (contracheque, que não imprime código).';
COMMENT ON COLUMN rubrica_de_para.papel IS
    'O que a rubrica SIGNIFICA. É o que a derivação de eventos lê — nunca o código.';

-- Um código por sistema numa data. Dois papéis para o mesmo código seriam duas
-- respostas para "o que esta linha significa", e a derivação escolheria uma.
CREATE UNIQUE INDEX ux_rubrica_codigo
    ON rubrica_de_para (sistema, codigo, vigencia_ini) WHERE codigo IS NOT NULL;

CREATE UNIQUE INDEX ux_rubrica_descricao
    ON rubrica_de_para (sistema, descricao_normalizada, vigencia_ini)
    WHERE descricao_normalizada IS NOT NULL;

CREATE INDEX ix_rubrica_papel ON rubrica_de_para (sistema, papel);

COMMIT;
