-- =============================================================================
-- SGDF — V006 · Repositório-mestre, parâmetros e modo de retenção
-- Decisões sobre as pendências A08 e A09.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. A09 · repositório-mestre por tipo documental
--
-- Cap. 14.2: "quando o mesmo tipo existir nas duas origens, o repositório-mestre
-- do tipo (cadastro) decide qual é a evidência". Decisão: o desempate é dado,
-- não código.
--
-- NULL é deliberadamente permitido e significa "sem mestre declarado": o
-- documento vai para conferência manual em vez de ser descartado. É o
-- princípio 1 do cap. 1 — o sistema não trava por falta de configuração própria.
-- -----------------------------------------------------------------------------
ALTER TABLE tipo_documental
    ADD COLUMN repositorio_mestre text
        CHECK (repositorio_mestre IS NULL OR repositorio_mestre IN ('OWNCLOUD', 'JIRA')),
    ADD COLUMN repositorios_alternativos jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT tipo_repos_alternativos_lista
        CHECK (jsonb_typeof(repositorios_alternativos) = 'array');

COMMENT ON COLUMN tipo_documental.repositorio_mestre IS
    'A09: origem que decide qual arquivo é a evidência quando o tipo aparece em '
    'mais de um repositório. NULL = sem mestre declarado, vai a conferência '
    'manual (cap. 1, princípio 1).';
COMMENT ON COLUMN tipo_documental.repositorios_alternativos IS
    'Origens onde o tipo também pode aparecer. Documento encontrado aqui é '
    'candidato, mas perde do mestre no desempate.';

-- -----------------------------------------------------------------------------
-- 2. Tabela de parâmetros (25ª)
--
-- O documento fala em "padrão" e "parâmetro" ao longo de todos os capítulos sem
-- lhes dar um lugar. Sem esta tabela, cada valor ajustável vira constante em
-- código — o oposto do princípio 2 do cap. 1.
--
-- Escopo hierárquico: o valor mais específico vence (contrato > cliente > global).
-- -----------------------------------------------------------------------------
CREATE TABLE parametro (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    chave          text        NOT NULL,
    escopo         text        NOT NULL CHECK (escopo IN ('GLOBAL', 'CLIENTE', 'CONTRATO')),
    cliente_id     uuid        REFERENCES cliente (id),
    contrato_id    uuid        REFERENCES contrato_servico (id),
    valor          jsonb       NOT NULL,
    descricao      text        NOT NULL,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    criado_por     text        NOT NULL,
    atualizado_em  timestamptz,
    atualizado_por text,
    CONSTRAINT parametro_escopo_coerente CHECK (
        (escopo = 'GLOBAL'   AND cliente_id IS NULL     AND contrato_id IS NULL) OR
        (escopo = 'CLIENTE'  AND cliente_id IS NOT NULL AND contrato_id IS NULL) OR
        (escopo = 'CONTRATO' AND cliente_id IS NULL     AND contrato_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_parametro_global   ON parametro (chave) WHERE escopo = 'GLOBAL';
CREATE UNIQUE INDEX ux_parametro_cliente  ON parametro (chave, cliente_id)  WHERE escopo = 'CLIENTE';
CREATE UNIQUE INDEX ux_parametro_contrato ON parametro (chave, contrato_id) WHERE escopo = 'CONTRATO';

COMMENT ON TABLE parametro IS
    'Valores ajustáveis por cadastro. Resolução: contrato > cliente > global.';

-- -----------------------------------------------------------------------------
-- 3. A08 · modo de retenção do book
--
-- DECISÃO REGISTRADA: retenção "sem tempo determinado".
--
-- Object Lock não tem um modo "sem prazo" com data: o que existe é LEGAL_HOLD,
-- que protege o objeto indefinidamente e é REMOVÍVEL por quem tenha a permissão
-- específica. É essa a primitiva correta para uma retenção sem prazo definido, e
-- é a que fica como padrão.
--
-- COMPLIANCE com data continua disponível e é IRREVERSÍVEL — nem a conta raiz
-- reduz o prazo. Só deve ser usado quando a tabela de temporalidade existir.
--
-- RESSALVA REGISTRADA (LGPD-02, art. 6º III e art. 16): guardar dado pessoal por
-- prazo indeterminado é tratamento sem termo final definido. Os books contêm
-- CPF, salário e, nos tipos PESSOAL_SENSIVEL, dado de saúde. Enquanto a
-- temporalidade não existir, LEGAL_HOLD mantém a decisão reversível — o que
-- COMPLIANCE não faria. Ver docs/PENDENCIAS.md, A08.
-- -----------------------------------------------------------------------------
ALTER TABLE book
    ADD COLUMN retencao_modo text NOT NULL DEFAULT 'LEGAL_HOLD'
        CHECK (retencao_modo IN ('LEGAL_HOLD', 'GOVERNANCE', 'COMPLIANCE')),
    ADD COLUMN retencao_ate  timestamptz,
    ADD CONSTRAINT book_retencao_coerente CHECK (
        (retencao_modo = 'LEGAL_HOLD' AND retencao_ate IS NULL) OR
        (retencao_modo IN ('GOVERNANCE', 'COMPLIANCE') AND retencao_ate IS NOT NULL)
    );

COMMENT ON COLUMN book.retencao_modo IS
    'A08: LEGAL_HOLD = sem prazo, protegido e reversível por papel autorizado '
    '(padrão, enquanto não houver tabela de temporalidade). COMPLIANCE = prazo '
    'irreversível, só com temporalidade definida.';

COMMIT;
