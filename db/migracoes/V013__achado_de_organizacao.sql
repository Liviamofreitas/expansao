-- =============================================================================
-- SGDF — V013 · Achados de organização (história F1-10)
--
-- Cap. 8.1: cópias de conflito de sincronização são "sinalizadas como alerta de
-- organização, nunca publicadas". Até aqui só a metade "nunca publicadas"
-- existia — e por omissão: a varredura as descartava pelo nome e nada chegava
-- ao banco. "Sinalizada" exigia um lugar onde a sinalização vivesse.
--
-- POR QUE NÃO É UMA LINHA EM `documento`
--
-- Seria mais barato marcar `documento.status_triagem = 'CONFLITO'`. Seria também
-- a maneira de, um dia, uma cópia em conflito acabar vinculada: bastaria uma
-- consulta esquecer o filtro. A garantia do critério de aceite — "nunca
-- vinculada" — é ESTRUTURAL aqui: `vinculo_exigencia_documento` referencia
-- `documento`, e o achado não é um documento. Não há filtro para esquecer.
--
-- O `documento_original_id` aponta para o documento cujo hash coincide — o
-- ORIGINAL, não a cópia. É o que transforma o alerta em algo acionável: "esta
-- cópia é redundante, o conteúdo já está no sistema".
-- =============================================================================

BEGIN;

CREATE TABLE achado_de_organizacao (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_servico_id   uuid        REFERENCES contrato_servico (id),
    competencia           char(7),
    origem                text        NOT NULL CHECK (origem IN ('OWNCLOUD', 'JIRA', 'UPLOAD')),
    caminho               text        NOT NULL,
    nome_arquivo          text        NOT NULL,
    motivo                text        NOT NULL CHECK (motivo IN (
                              'COPIA_DE_CONFLITO', 'ARQUIVO_TEMPORARIO', 'ARQUIVO_OCULTO',
                              'EXTENSAO_NAO_ACEITA', 'TAMANHO_EXCEDIDO',
                              'CHECKLIST_EM_PLANILHA')),
    severidade            text        NOT NULL CHECK (severidade IN ('INFORMATIVO', 'ATENCAO')),
    hash_sha256           char(64),
    documento_original_id uuid        REFERENCES documento (id),
    tamanho               bigint,
    versao_origem         text,
    detalhe               text,
    detectado_em          timestamptz NOT NULL DEFAULT now(),
    resolvido_em          timestamptz,
    resolvido_por         text,

    CONSTRAINT achado_hash_hex CHECK (
        hash_sha256 IS NULL OR hash_sha256 ~ '^[0-9a-f]{64}$'),

    -- Não se afirma "é cópia daquele" sem ter comparado o conteúdo. Sem o hash,
    -- a única coisa que se sabe é que o NOME parece de conflito — e o cap. 8.1
    -- pede as duas metades do critério.
    CONSTRAINT achado_duplicata_exige_hash CHECK (
        documento_original_id IS NULL OR hash_sha256 IS NOT NULL),

    -- ATENÇÃO é o caso em que alguém precisa olhar: dizer só "atenção", sem
    -- dizer a quê, transfere o trabalho de volta para quem lê.
    CONSTRAINT achado_atencao_com_detalhe CHECK (
        severidade <> 'ATENCAO' OR (detalhe IS NOT NULL AND btrim(detalhe) <> '')),

    CONSTRAINT achado_resolucao_completa CHECK (
        (resolvido_em IS NULL AND resolvido_por IS NULL)
     OR (resolvido_em IS NOT NULL AND resolvido_por IS NOT NULL)),

    -- A varredura passa de 30 em 30 minutos. Sem isto, o mesmo arquivo viraria
    -- um achado novo a cada passagem e o painel de organização — que existe para
    -- ser resolvido — encheria de repetições do mesmo problema.
    CONSTRAINT achado_unico UNIQUE NULLS NOT DISTINCT (origem, caminho, hash_sha256)
);

COMMENT ON TABLE achado_de_organizacao IS
    'Cap. 8.1 / F1-10. NÃO é documento: é o que garante "nunca vinculada" sem '
    'depender de nenhum filtro de consulta.';
COMMENT ON COLUMN achado_de_organizacao.documento_original_id IS
    'O documento cujo hash coincide — o ORIGINAL. Nulo quando o conteúdo é inédito.';
COMMENT ON COLUMN achado_de_organizacao.severidade IS
    'ATENCAO = cópia em conflito cujo conteúdo não confere com nada conhecido: '
    'pode ser a única versão que sobrou. INFORMATIVO = redundância confirmada.';

CREATE INDEX ix_achado_aberto ON achado_de_organizacao (detectado_em)
    WHERE resolvido_em IS NULL;
CREATE INDEX ix_achado_contrato ON achado_de_organizacao (contrato_servico_id, competencia);
CREATE INDEX ix_achado_hash ON achado_de_organizacao (hash_sha256)
    WHERE hash_sha256 IS NOT NULL;

COMMIT;
