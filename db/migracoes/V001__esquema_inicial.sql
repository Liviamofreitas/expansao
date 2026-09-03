-- =============================================================================
-- SGDF — V001 · Esquema inicial
-- Referência: SGDF_Documentacao_Desenvolvimento_V1, cap. 5 (modelo de dados)
-- Alvo: PostgreSQL 16
--
-- Convenções do cap. 5:
--   · chaves primárias UUID;
--   · timestamps UTC com timezone (timestamptz);
--   · soft delete apenas por campo ativo=false — nenhuma exclusão física fora
--     do expurgo de retenção;
--   · toda tabela de negócio tem criado_em, criado_por, atualizado_em,
--     atualizado_por.
--
-- Domínios fechados são CHECK sobre text, não ENUM nativo: o cap. 1 (princípio 2)
-- exige que comportamento variável seja metadado editável, e ALTER TYPE ... ADD
-- VALUE não é reversível dentro de transação — o que quebraria a exigência do
-- cap. 17 de migrations reversíveis.
-- =============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- -----------------------------------------------------------------------------
-- Papéis. O cap. 15.1 define identidades de serviço com escopos disjuntos; aqui
-- separamos apenas o que o banco precisa distinguir: quem migra e quem opera.
-- A revogação de UPDATE/DELETE em log_auditoria (F0-08) vai na V002, depois de
-- as tabelas existirem.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sgdf_app') THEN
        CREATE ROLE sgdf_app NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sgdf_migracao') THEN
        CREATE ROLE sgdf_migracao NOLOGIN;
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- Colunas de auditoria de linha, repetidas em toda tabela de negócio.
-- criado_por/atualizado_por guardam o identificador do ator (usuário do IdP ou
-- conta de serviço), não FK: o ator pode ser externo ao cadastro de pessoas.
-- -----------------------------------------------------------------------------

-- =============================================================================
-- 5.1 CADASTRO E MATRIZ
-- =============================================================================

-- (1) cliente ------------------------------------------------------------------
CREATE TABLE cliente (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            text        NOT NULL,
    cnpj            char(14)    NOT NULL,
    esfera          text        NOT NULL CHECK (esfera IN ('PUBLICA', 'PRIVADA')),
    ativo           boolean     NOT NULL DEFAULT true,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    criado_por      text        NOT NULL,
    atualizado_em   timestamptz,
    atualizado_por  text,
    CONSTRAINT cliente_cnpj_unico  UNIQUE (cnpj),
    CONSTRAINT cliente_cnpj_digitos CHECK (cnpj ~ '^[0-9]{14}$')
);
COMMENT ON TABLE  cliente      IS 'Contratante. Cap. 5.1.';
COMMENT ON COLUMN cliente.cnpj IS 'Somente dígitos, sem máscara. O DV é validado na aplicação.';

-- (2) modalidade ---------------------------------------------------------------
-- blocos: lista de blocos de exigência herdados (D-01 — documentação de
-- profissionais só em contratos outsourcing).
CREATE TABLE modalidade (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo          text        NOT NULL CHECK (codigo IN ('OUTSOURCING', 'SUSTENTACAO', 'ENTREGA', 'MISTO')),
    nome            text        NOT NULL,
    blocos          jsonb       NOT NULL DEFAULT '[]'::jsonb,
    ativo           boolean     NOT NULL DEFAULT true,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    criado_por      text        NOT NULL,
    atualizado_em   timestamptz,
    atualizado_por  text,
    CONSTRAINT modalidade_codigo_unico UNIQUE (codigo),
    CONSTRAINT modalidade_blocos_lista CHECK (jsonb_typeof(blocos) = 'array')
);

-- (3) calendario_feriados ------------------------------------------------------
-- Sustenta o cômputo de dia útil do prazo estruturado (cap. 7.3, história F0-06).
CREATE TABLE calendario_feriados (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    uf              char(2),                       -- NULL = feriado nacional
    municipio       text,                          -- NULL = não municipal
    data            date        NOT NULL,
    descricao       text        NOT NULL,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    criado_por      text        NOT NULL,
    CONSTRAINT feriado_unico UNIQUE NULLS NOT DISTINCT (uf, municipio, data),
    CONSTRAINT feriado_municipio_exige_uf CHECK (municipio IS NULL OR uf IS NOT NULL)
);
COMMENT ON CONSTRAINT feriado_unico ON calendario_feriados IS
    'NULLS NOT DISTINCT: dois feriados nacionais na mesma data são a mesma linha.';

-- (4) contrato_servico ---------------------------------------------------------
-- D-02: a chave do sistema é o contrato-serviço, não o cliente.
CREATE TABLE contrato_servico (
    id                            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id                    uuid        NOT NULL REFERENCES cliente (id),
    numero                        text        NOT NULL,
    servico                       text        NOT NULL,
    modalidade_id                 uuid        NOT NULL REFERENCES modalidade (id),
    vigencia_ini                  date        NOT NULL,
    vigencia_fim                  date,
    pasta_origem                  text        NOT NULL,
    data_contratual_faturamento   jsonb       NOT NULL,
    calendario_uf                 char(2)     NOT NULL,
    ativo                         boolean     NOT NULL DEFAULT true,
    criado_em                     timestamptz NOT NULL DEFAULT now(),
    criado_por                    text        NOT NULL,
    atualizado_em                 timestamptz,
    atualizado_por                text,
    CONSTRAINT contrato_servico_unico UNIQUE (cliente_id, numero, servico),
    CONSTRAINT contrato_vigencia_coerente CHECK (vigencia_fim IS NULL OR vigencia_fim >= vigencia_ini)
);
COMMENT ON COLUMN contrato_servico.pasta_origem IS
    'Caminho raiz no OwnCloud. Cap. 14.1: fora dela, nada é lido.';
COMMENT ON COLUMN contrato_servico.data_contratual_faturamento IS
    'Prazo estruturado do cap. 7.3: {ancora, tipo_dia, offset}. Nunca texto livre.';

CREATE INDEX ix_contrato_servico_cliente ON contrato_servico (cliente_id) WHERE ativo;

-- (5) tipo_documental ----------------------------------------------------------
CREATE TABLE tipo_documental (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo            text        NOT NULL,
    nome              text        NOT NULL,
    familia           text        NOT NULL,
    escopo            text        NOT NULL CHECK (escopo IN ('CORPORATIVO', 'CONTRATO', 'PROFISSIONAL')),
    evento            text        NOT NULL CHECK (evento IN ('MENSAL', '13O', 'FERIAS', 'RESCISAO', 'ADMISSAO', 'EVENTUAL')),
    defasagem         text        NOT NULL CHECK (defasagem IN ('M', 'M_MENOS_1', 'VIGENCIA_NF', 'EVENTO')),
    formatos          jsonb       NOT NULL DEFAULT '["pdf"]'::jsonb,
    criticidade       text        NOT NULL CHECK (criticidade IN ('BLOQUEANTE', 'NAO_BLOQUEANTE')),
    sigilo            text        NOT NULL CHECK (sigilo IN ('PUBLICO_CLIENTE', 'INTERNO', 'PESSOAL', 'PESSOAL_SENSIVEL')),
    fonte_mestre      text,
    condicional_grupo text,
    fundamento        text,
    ativo             boolean     NOT NULL DEFAULT true,
    criado_em         timestamptz NOT NULL DEFAULT now(),
    criado_por        text        NOT NULL,
    atualizado_em     timestamptz,
    atualizado_por    text,
    CONSTRAINT tipo_documental_codigo_unico UNIQUE (codigo),
    CONSTRAINT tipo_documental_codigo_formato CHECK (codigo ~ '^[A-Z]{3}\.[A-Z0-9_]+$'),
    CONSTRAINT tipo_documental_formatos_lista CHECK (
        jsonb_typeof(formatos) = 'array' AND jsonb_array_length(formatos) > 0
    )
);
COMMENT ON COLUMN tipo_documental.codigo IS 'Formato FAM.NOME. Cap. 5.1.';
COMMENT ON COLUMN tipo_documental.formatos IS
    'D-05: quando há mais de um formato, a satisfação exige TODOS.';
COMMENT ON COLUMN tipo_documental.condicional_grupo IS
    'Cap. 7.5: exigências do mesmo grupo no ciclo são satisfeitas por qualquer uma.';
COMMENT ON COLUMN tipo_documental.criticidade IS
    'Cap. 5.1: alteração exige papel APROVADOR_DAF — imposto no serviço, não no banco.';

CREATE INDEX ix_tipo_documental_familia   ON tipo_documental (familia) WHERE ativo;
CREATE INDEX ix_tipo_documental_escopo    ON tipo_documental (escopo)  WHERE ativo;
CREATE INDEX ix_tipo_documental_condgrupo ON tipo_documental (condicional_grupo)
    WHERE condicional_grupo IS NOT NULL;

-- (6) tipo_alias ---------------------------------------------------------------
-- Cap. 8.3: a confirmação em triagem grava um alias novo, e o mesmo padrão não
-- volta à fila.
CREATE TABLE tipo_alias (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_id           uuid        NOT NULL REFERENCES tipo_documental (id) ON DELETE RESTRICT,
    texto_original    text        NOT NULL,
    texto_normalizado text        NOT NULL,
    origem            text        NOT NULL CHECK (origem IN ('LEGADO', 'TRIAGEM')),
    criado_em         timestamptz NOT NULL DEFAULT now(),
    criado_por        text        NOT NULL,
    CONSTRAINT tipo_alias_normalizado_unico UNIQUE (texto_normalizado)
);
-- A unicidade é GLOBAL, não por tipo. Um alias que aponta para dois tipos
-- destrói o determinismo da classificação (cap. 1, princípio 3), porque o bônus
-- de alias do cap. 8.3 seria concedido a dois candidatos ao mesmo tempo.
-- É esta restrição que faz a carga inicial falhar sobre o achado E-02
-- (GFD_GUIA_DO_FGTS em FGT.GUIA e FGT.RELATORIO_DIGITAL) — ver docs/ERRATA-V1.md.
COMMENT ON CONSTRAINT tipo_alias_normalizado_unico ON tipo_alias IS
    'Unicidade global e deliberada. Ver docs/ERRATA-V1.md, achado E-02.';

CREATE INDEX ix_tipo_alias_tipo ON tipo_alias (tipo_id);

-- (7) versao_matriz ------------------------------------------------------------
-- Append-only. D-03/cap. 7.1: reprocessamentos usam a versão da época.
CREATE TABLE versao_matriz (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    numero        text        NOT NULL,
    publicada_em  timestamptz NOT NULL DEFAULT now(),
    publicada_por text        NOT NULL,
    motivo        text        NOT NULL,
    CONSTRAINT versao_matriz_numero_unico UNIQUE (numero)
);

-- (8) regra_exigibilidade ------------------------------------------------------
-- Resolução: contrato sobrepõe modalidade; a mais específica vence (cap. 7.1).
CREATE TABLE regra_exigibilidade (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_id                  uuid        NOT NULL REFERENCES tipo_documental (id),
    alvo                     text        NOT NULL CHECK (alvo IN ('MODALIDADE', 'CONTRATO')),
    alvo_modalidade_id       uuid        REFERENCES modalidade (id),
    alvo_contrato_id         uuid        REFERENCES contrato_servico (id),
    obrigatoriedade          text        NOT NULL CHECK (obrigatoriedade IN ('OBRIGATORIO', 'CONDICIONAL', 'DISPENSADO')),
    criticidade              text        CHECK (criticidade IN ('BLOQUEANTE', 'NAO_BLOQUEANTE')),
    prazo                    jsonb       NOT NULL,
    responsavel_titular      text        NOT NULL,
    responsavel_substituto   text,
    fundamento               text,
    vigencia_ini             date        NOT NULL,
    vigencia_fim             date,
    versao_matriz_id         uuid        NOT NULL REFERENCES versao_matriz (id),
    criado_em                timestamptz NOT NULL DEFAULT now(),
    criado_por               text        NOT NULL,
    CONSTRAINT regra_alvo_coerente CHECK (
        (alvo = 'MODALIDADE' AND alvo_modalidade_id IS NOT NULL AND alvo_contrato_id IS NULL) OR
        (alvo = 'CONTRATO'   AND alvo_contrato_id   IS NOT NULL AND alvo_modalidade_id IS NULL)
    ),
    CONSTRAINT regra_vigencia_coerente CHECK (vigencia_fim IS NULL OR vigencia_fim >= vigencia_ini)
);
COMMENT ON COLUMN regra_exigibilidade.criticidade IS
    'NULL = herda a criticidade do tipo documental. Preenchido só quando a regra sobrepõe.';
COMMENT ON COLUMN regra_exigibilidade.fundamento IS
    'Pendência A06: campo obrigatório na matriz, preenchível em paralelo ao desenvolvimento.';

CREATE INDEX ix_regra_exig_versao     ON regra_exigibilidade (versao_matriz_id);
CREATE INDEX ix_regra_exig_modalidade ON regra_exigibilidade (alvo_modalidade_id) WHERE alvo = 'MODALIDADE';
CREATE INDEX ix_regra_exig_contrato   ON regra_exigibilidade (alvo_contrato_id)   WHERE alvo = 'CONTRATO';

-- (9) regra_reconhecimento -----------------------------------------------------
-- Cap. 8.3: score = Σ peso(âncora) + Σ peso(campo) + bônus(pasta) + bônus(alias).
-- Ausência de regra ⇒ conferência manual (nunca bloqueia — cap. 1, princípio 1).
CREATE TABLE regra_reconhecimento (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_id         uuid          NOT NULL REFERENCES tipo_documental (id),
    ancoras         jsonb         NOT NULL DEFAULT '[]'::jsonb,
    campos          jsonb         NOT NULL DEFAULT '[]'::jsonb,
    limiar_auto     numeric(4, 3) NOT NULL DEFAULT 0.950,
    limiar_triagem  numeric(4, 3) NOT NULL DEFAULT 0.700,
    versao          integer       NOT NULL DEFAULT 1,
    ativo           boolean       NOT NULL DEFAULT true,
    criado_em       timestamptz   NOT NULL DEFAULT now(),
    criado_por      text          NOT NULL,
    CONSTRAINT regra_recon_versao_unica UNIQUE (tipo_id, versao),
    CONSTRAINT regra_recon_limiares CHECK (
        limiar_auto BETWEEN 0 AND 1 AND
        limiar_triagem BETWEEN 0 AND 1 AND
        limiar_triagem < limiar_auto
    )
);

-- (10) regra_conciliacao -------------------------------------------------------
-- Cap. 9 / Anexo 1 aba REGRAS_CONCILIACAO.
-- NÃO SEMEADA NA CARGA INICIAL: os códigos R01..R12 designam conjuntos de regras
-- diferentes nas duas fontes (achado E-01). Semear qualquer uma das duas seria
-- resolver por suposição, o que a regra de leitura do documento proíbe.
CREATE TABLE regra_conciliacao (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo          text        NOT NULL,
    nome            text        NOT NULL,
    tipos_envolvidos jsonb      NOT NULL DEFAULT '[]'::jsonb,
    logica          text        NOT NULL,
    tolerancia      jsonb,
    excecoes        jsonb       NOT NULL DEFAULT '[]'::jsonb,
    modo            text        NOT NULL CHECK (modo IN ('BLOQUEIO', 'ALERTA')),
    fase            text        NOT NULL CHECK (fase IN ('1A', '1B', '2')),
    versao          integer     NOT NULL DEFAULT 1,
    ativo           boolean     NOT NULL DEFAULT true,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    criado_por      text        NOT NULL,
    CONSTRAINT regra_conc_versao_unica UNIQUE (codigo, versao),
    -- Cap. 9: regra sem tolerância cadastrada opera em modo ALERTA (nunca bloqueia).
    CONSTRAINT regra_conc_sem_tolerancia_nao_bloqueia CHECK (
        tolerancia IS NOT NULL OR modo = 'ALERTA'
    )
);
COMMENT ON CONSTRAINT regra_conc_sem_tolerancia_nao_bloqueia ON regra_conciliacao IS
    'Cap. 9: sem tolerância cadastrada, o modo ALERTA é forçado. Imposto no banco '
    'porque é a garantia de que o sistema não trava faturamento por falta de '
    'configuração própria (cap. 1, princípio 1).';

-- (11) profissional ------------------------------------------------------------
CREATE TABLE profissional (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    matricula       text        NOT NULL,
    nome            text        NOT NULL,
    cpf_cifrado     bytea       NOT NULL,
    cpf_hash        bytea       NOT NULL,
    admissao        date        NOT NULL,
    desligamento    date,
    ativo           boolean     NOT NULL DEFAULT true,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    criado_por      text        NOT NULL,
    atualizado_em   timestamptz,
    atualizado_por  text,
    CONSTRAINT profissional_matricula_unica UNIQUE (matricula),
    CONSTRAINT profissional_cpf_hash_unico  UNIQUE (cpf_hash),
    CONSTRAINT profissional_datas_coerentes CHECK (desligamento IS NULL OR desligamento >= admissao)
);
COMMENT ON COLUMN profissional.cpf_cifrado IS
    'SEC-02: AES-256-GCM com chave no cofre, cifrado na aplicação. Exibição sempre mascarada.';
COMMENT ON COLUMN profissional.cpf_hash IS
    'HMAC-SHA256 com chave no cofre. Permite busca e unicidade sem decifrar — '
    'hash simples permitiria enumerar CPFs por força bruta (espaço de 10^11).';

-- (12) alocacao ----------------------------------------------------------------
CREATE TABLE alocacao (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    profissional_id     uuid        NOT NULL REFERENCES profissional (id),
    contrato_servico_id uuid        NOT NULL REFERENCES contrato_servico (id),
    inicio              date        NOT NULL,
    fim                 date,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    criado_por          text        NOT NULL,
    CONSTRAINT alocacao_periodo_coerente CHECK (fim IS NULL OR fim >= inicio)
);
CREATE INDEX ix_alocacao_contrato     ON alocacao (contrato_servico_id, inicio);
CREATE INDEX ix_alocacao_profissional ON alocacao (profissional_id);

-- =============================================================================
-- 5.2 EXECUÇÃO
-- =============================================================================

-- (13) ciclo -------------------------------------------------------------------
-- Máquina de estados do cap. 6.2.
CREATE TABLE ciclo (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_servico_id   uuid        NOT NULL REFERENCES contrato_servico (id),
    competencia           char(7)     NOT NULL,
    status                text        NOT NULL CHECK (status IN (
                              'ABERTO', 'EM_COLETA', 'PRONTO', 'ATESTADO',
                              'FATURADO', 'FECHADO', 'BLOQUEADO', 'REABERTO')),
    versao_matriz_id      uuid        NOT NULL REFERENCES versao_matriz (id),
    aberto_em             timestamptz NOT NULL DEFAULT now(),
    prazo_interno         date,
    ateste_em             timestamptz,
    ateste_forma          text,
    ateste_evidencia_doc_id uuid,
    nf_emitida_em         timestamptz,
    fechado_em            timestamptz,
    criado_em             timestamptz NOT NULL DEFAULT now(),
    criado_por            text        NOT NULL,
    atualizado_em         timestamptz,
    atualizado_por        text,
    CONSTRAINT ciclo_unico UNIQUE (contrato_servico_id, competencia),
    CONSTRAINT ciclo_competencia_formato CHECK (competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ciclo_ateste_completo CHECK (
        (ateste_em IS NULL AND ateste_forma IS NULL) OR
        (ateste_em IS NOT NULL AND ateste_forma IS NOT NULL)
    )
);
COMMENT ON COLUMN ciclo.versao_matriz_id IS
    'Congelado na abertura (cap. 7.1, passo 4): reprocessamentos usam a versão da '
    'época, nunca a atual.';
COMMENT ON COLUMN ciclo.ateste_em IS
    'Marco do indicador D+3 (cap. 21). Registrado por GESTOR_CONTRATO.';

CREATE INDEX ix_ciclo_competencia ON ciclo (competencia, status);
CREATE INDEX ix_ciclo_contrato    ON ciclo (contrato_servico_id, competencia DESC);

-- (14) exigencia ---------------------------------------------------------------
-- Máquina de estados do cap. 6.1.
CREATE TABLE exigencia (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ciclo_id            uuid        NOT NULL REFERENCES ciclo (id),
    tipo_id             uuid        NOT NULL REFERENCES tipo_documental (id),
    evento              text        NOT NULL,
    profissional_id     uuid        REFERENCES profissional (id),
    status              text        NOT NULL CHECK (status IN (
                            'PENDENTE', 'EM_TRIAGEM', 'RECEBIDO', 'VALIDADO', 'REJEITADO',
                            'CONCILIADO', 'DIVERGENTE', 'DISPENSADO', 'PUBLICADO')),
    formatos_pendentes  jsonb       NOT NULL DEFAULT '[]'::jsonb,
    prazo_calculado     date        NOT NULL,
    criticidade         text        NOT NULL CHECK (criticidade IN ('BLOQUEANTE', 'NAO_BLOQUEANTE')),
    condicional_grupo   text,
    responsavel         text        NOT NULL,
    origem              text        NOT NULL CHECK (origem IN ('MATRIZ', 'DERIVADA')),
    regra_id            uuid        REFERENCES regra_exigibilidade (id),
    criado_em           timestamptz NOT NULL DEFAULT now(),
    criado_por          text        NOT NULL,
    atualizado_em       timestamptz,
    atualizado_por      text,
    CONSTRAINT exigencia_unica UNIQUE NULLS NOT DISTINCT (ciclo_id, tipo_id, evento, profissional_id)
);
COMMENT ON COLUMN exigencia.origem IS
    'DERIVADA = criada pela leitura da folha (cap. 7.2), nunca por calendário (D-04).';
COMMENT ON CONSTRAINT exigencia_unica ON exigencia IS
    'A chave da exigência é tipo × evento × (profissional, quando individual). '
    'NULLS NOT DISTINCT impede duplicar a exigência de escopo de contrato.';

CREATE INDEX ix_exigencia_ciclo        ON exigencia (ciclo_id, status);
CREATE INDEX ix_exigencia_pendente     ON exigencia (prazo_calculado)
    WHERE status IN ('PENDENTE', 'EM_TRIAGEM', 'REJEITADO', 'DIVERGENTE');
CREATE INDEX ix_exigencia_condgrupo    ON exigencia (ciclo_id, condicional_grupo)
    WHERE condicional_grupo IS NOT NULL;
CREATE INDEX ix_exigencia_profissional ON exigencia (profissional_id) WHERE profissional_id IS NOT NULL;

-- (15) documento ---------------------------------------------------------------
CREATE TABLE documento (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    origem              text        NOT NULL CHECK (origem IN ('OWNCLOUD', 'JIRA', 'UPLOAD')),
    caminho             text        NOT NULL,
    nome_arquivo        text        NOT NULL,
    hash_sha256         char(64)    NOT NULL,
    tamanho             bigint      NOT NULL CHECK (tamanho > 0),
    mime_real           text        NOT NULL,
    tipo_id             uuid        REFERENCES tipo_documental (id),
    confianca           numeric(4, 3) CHECK (confianca IS NULL OR confianca BETWEEN 0 AND 1),
    regra_recon_id      uuid        REFERENCES regra_reconhecimento (id),
    competencia_extraida char(7),
    validade_extraida   date,
    cnpj_extraido       char(14),
    natureza            text,
    formato             text        NOT NULL,
    ocr                 boolean     NOT NULL DEFAULT false,
    status_triagem      text        NOT NULL CHECK (status_triagem IN (
                            'NAO_APLICAVEL', 'PENDENTE', 'CONFIRMADO', 'RECLASSIFICADO', 'ILEGIVEL')),
    versao_origem       text,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    criado_por          text        NOT NULL,
    CONSTRAINT documento_unico UNIQUE (origem, caminho, hash_sha256),
    CONSTRAINT documento_hash_hex CHECK (hash_sha256 ~ '^[0-9a-f]{64}$')
);
COMMENT ON COLUMN documento.mime_real IS
    'Cap. 8.2: obtido por assinatura binária. Divergência com a extensão gera rejeição.';
COMMENT ON COLUMN documento.versao_origem IS 'ETag do WebDAV — base do delta da varredura (cap. 8.1).';
COMMENT ON COLUMN documento.regra_recon_id IS
    'Versão da regra usada na decisão. Cap. 16: toda decisão automática é reproduzível '
    'a partir de documento (hash) + versão da regra + versão da matriz.';

CREATE INDEX ix_documento_hash        ON documento (hash_sha256);
CREATE INDEX ix_documento_tipo        ON documento (tipo_id) WHERE tipo_id IS NOT NULL;
CREATE INDEX ix_documento_triagem     ON documento (status_triagem) WHERE status_triagem = 'PENDENTE';

-- (16) campo_extraido ----------------------------------------------------------
CREATE TABLE campo_extraido (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_id  uuid          NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    campo         text          NOT NULL,
    valor         text          NOT NULL,
    confianca     numeric(4, 3) NOT NULL CHECK (confianca BETWEEN 0 AND 1),
    posicao       jsonb,
    criado_em     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT campo_extraido_unico UNIQUE (documento_id, campo)
);
COMMENT ON COLUMN campo_extraido.posicao IS
    'Cap. 5.2: permite auditar de onde o valor foi lido — requisito da história F1-02.';

-- (17) vinculo_exigencia_documento ---------------------------------------------
CREATE TABLE vinculo_exigencia_documento (
    exigencia_id  uuid        NOT NULL REFERENCES exigencia (id) ON DELETE CASCADE,
    documento_id  uuid        NOT NULL REFERENCES documento (id),
    formato       text        NOT NULL,
    decidido_por  text        NOT NULL CHECK (decidido_por IN ('SISTEMA', 'USUARIO')),
    decidido_ator text        NOT NULL,
    decidido_em   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (exigencia_id, documento_id, formato)
);
CREATE INDEX ix_vinculo_documento ON vinculo_exigencia_documento (documento_id);

-- (18) conciliacao -------------------------------------------------------------
CREATE TABLE conciliacao (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    regra_id    uuid        NOT NULL REFERENCES regra_conciliacao (id),
    ciclo_id    uuid        NOT NULL REFERENCES ciclo (id),
    itens       jsonb       NOT NULL DEFAULT '[]'::jsonb,
    resultado   text        NOT NULL CHECK (resultado IN ('CONFORME', 'DIVERGENTE', 'NAO_APLICAVEL')),
    delta       numeric(15, 2),
    detalhe     jsonb,
    executada_em timestamptz NOT NULL DEFAULT now()
);
COMMENT ON COLUMN conciliacao.itens IS 'Documentos e valores comparados — base da mensagem acionável.';
CREATE INDEX ix_conciliacao_ciclo ON conciliacao (ciclo_id, resultado);

-- (19) excecao -----------------------------------------------------------------
-- SoD do cap. 15.1: o aprovador nunca é o solicitante. Imposto também no banco.
CREATE TABLE excecao (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    exigencia_id  uuid        NOT NULL REFERENCES exigencia (id),
    motivo        text        NOT NULL,
    solicitante   text        NOT NULL,
    aprovador     text,
    aprovado_em   timestamptz,
    evidencia     text,
    criado_em     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT excecao_sod CHECK (aprovador IS NULL OR aprovador <> solicitante),
    CONSTRAINT excecao_aprovacao_completa CHECK (
        (aprovador IS NULL AND aprovado_em IS NULL) OR
        (aprovador IS NOT NULL AND aprovado_em IS NOT NULL)
    ),
    CONSTRAINT excecao_motivo_substantivo CHECK (length(btrim(motivo)) >= 20)
);
COMMENT ON CONSTRAINT excecao_sod ON excecao IS
    'Segregação de funções (história F0-09). Duplicada no serviço; aqui é a rede de '
    'segurança para o caso de um caminho de código esquecer a checagem.';

CREATE INDEX ix_excecao_exigencia ON excecao (exigencia_id);

-- (20) book --------------------------------------------------------------------
-- Append-only: nova publicação ⇒ versao+1 (cap. 10).
CREATE TABLE book (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ciclo_id       uuid        NOT NULL REFERENCES ciclo (id),
    versao         integer     NOT NULL CHECK (versao >= 1),
    publicado_em   timestamptz NOT NULL DEFAULT now(),
    publicado_por  text        NOT NULL,
    hash_conjunto  char(64)    NOT NULL,
    manifesto      jsonb       NOT NULL,
    caminho_bucket text        NOT NULL,
    pecas          integer     NOT NULL CHECK (pecas > 0),
    CONSTRAINT book_versao_unica UNIQUE (ciclo_id, versao),
    CONSTRAINT book_hash_hex CHECK (hash_conjunto ~ '^[0-9a-f]{64}$')
);

-- (21) notificacao -------------------------------------------------------------
CREATE TABLE notificacao (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ciclo_id        uuid        NOT NULL REFERENCES ciclo (id),
    tipo            text        NOT NULL CHECK (tipo IN (
                        'PREVENTIVA', 'COBRANCA', 'ESCALONAMENTO', 'FECHAMENTO', 'RESOLVIDA')),
    destinatario    text        NOT NULL,
    conteudo_hash   char(64)    NOT NULL,
    template_versao text        NOT NULL,
    modo_sombra     boolean     NOT NULL DEFAULT false,
    enviada_em      timestamptz,
    data_referencia date        NOT NULL,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT notificacao_diaria_unica UNIQUE (ciclo_id, tipo, destinatario, data_referencia)
);
COMMENT ON CONSTRAINT notificacao_diaria_unica ON notificacao IS
    'Cap. 11.2: máximo 1 e-mail por área por ciclo por dia. A consolidação é '
    'forçada pelo banco, não confiada ao agendador.';
COMMENT ON COLUMN notificacao.modo_sombra IS
    'true = registrada mas não enviada (cap. 11.2 / história F1-09).';

-- (22) pendencia ---------------------------------------------------------------
CREATE TABLE pendencia (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    exigencia_id  uuid        NOT NULL REFERENCES exigencia (id),
    aberta_em     timestamptz NOT NULL DEFAULT now(),
    prazo         date        NOT NULL,
    escalonada_em timestamptz,
    resolvida_em  timestamptz,
    resolucao     text        CHECK (resolucao IN ('ENTREGA', 'EXCECAO', 'CANCELAMENTO')),
    CONSTRAINT pendencia_resolucao_completa CHECK (
        (resolvida_em IS NULL AND resolucao IS NULL) OR
        (resolvida_em IS NOT NULL AND resolucao IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_pendencia_aberta ON pendencia (exigencia_id) WHERE resolvida_em IS NULL;
COMMENT ON INDEX ux_pendencia_aberta IS 'No máximo uma pendência aberta por exigência.';

-- (23) log_auditoria -----------------------------------------------------------
-- Append-only. UPDATE/DELETE revogados na V002 (história F0-08).
CREATE TABLE log_auditoria (
    id          bigserial PRIMARY KEY,
    ator        text        NOT NULL,
    papel       text        NOT NULL,
    acao        text        NOT NULL,
    objeto_tipo text        NOT NULL,
    objeto_id   text,
    ip          inet,
    ocorrido_em timestamptz NOT NULL DEFAULT now(),
    resultado   text        NOT NULL CHECK (resultado IN ('SUCESSO', 'NEGADO', 'ERRO')),
    detalhe     jsonb
);
CREATE INDEX ix_auditoria_objeto   ON log_auditoria (objeto_tipo, objeto_id);
CREATE INDEX ix_auditoria_ator     ON log_auditoria (ator, ocorrido_em DESC);
CREATE INDEX ix_auditoria_ocorrido ON log_auditoria (ocorrido_em DESC);

COMMIT;
