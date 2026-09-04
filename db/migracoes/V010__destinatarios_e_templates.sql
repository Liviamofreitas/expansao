-- =============================================================================
-- SGDF — V010 · Destinatários, templates e a unicidade que não valia
--
-- ORIGEM: a história F1-09. Ao montar a régua do cap. 11.1 apareceu que a
-- restrição `notificacao_diaria_unica`, criada na V001 com o comentário "cap.
-- 11.2: máximo 1 e-mail por área por ciclo por dia", NÃO impunha isso.
--
--   UNIQUE (ciclo_id, tipo, destinatario, data_referencia)
--
-- Com `tipo` na chave, o mesmo destinatário podia receber, no mesmo dia e no
-- mesmo ciclo, uma PREVENTIVA (algo vence em 48 h), uma COBRANCA (algo venceu
-- hoje) e um ESCALONAMENTO (algo venceu há dois dias) — três e-mails, e o banco
-- aceitaria os três. A regra do capítulo é por ÁREA e por DIA, sem qualificar
-- por tipo: "máximo 1 e-mail por área por ciclo por dia (consolidação forçada)".
--
-- O comentário afirmava uma garantia que a restrição não dava, que é a forma
-- mais cara de erro: quem lesse o esquema pararia de procurar.
--
-- Consequência de projeto: a consolidação não é por momento da régua, é por
-- PESSOA. Um aviso diário por destinatário, com o que vence, o que venceu e o
-- que escalou, tudo junto. O `tipo` da linha passa a ser o momento mais grave
-- presente no aviso — informação, não chave.
-- =============================================================================

BEGIN;

ALTER TABLE notificacao
    DROP CONSTRAINT notificacao_diaria_unica;

ALTER TABLE notificacao
    ADD CONSTRAINT notificacao_diaria_unica
    UNIQUE (ciclo_id, destinatario, data_referencia);

COMMENT ON CONSTRAINT notificacao_diaria_unica ON notificacao IS
    'Cap. 11.2, agora de verdade: um e-mail por destinatário por ciclo por dia. '
    'A V001 incluía `tipo` na chave e permitia três — ver docs/ACHADOS-MASSA-REAL.md § 32.';

COMMENT ON COLUMN notificacao.tipo IS
    'O momento MAIS GRAVE presente no aviso consolidado. Não é chave: o aviso '
    'do dia carrega o que vence, o que venceu e o que escalou, junto.';

-- -----------------------------------------------------------------------------
-- Destinatário nominal por contrato × família (cap. 11.2)
--
-- `familia` nula vale para todas as famílias do contrato — é como GESTOR e DAF
-- funcionam de verdade: eles não variam por família documental. A resolução
-- prefere a linha específica e cai na genérica.
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE destinatario (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contrato_servico_id uuid        NOT NULL REFERENCES contrato_servico (id),
    familia             text,
    papel               text        NOT NULL CHECK (papel IN (
                            'TITULAR', 'SUBSTITUTO', 'GESTOR', 'DAF')),
    nome                text        NOT NULL,
    email               text        NOT NULL,
    vigencia_ini        date        NOT NULL,
    vigencia_fim        date,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    criado_por          text        NOT NULL,

    CONSTRAINT destinatario_email_plausivel CHECK (
        email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    CONSTRAINT destinatario_vigencia_coerente CHECK (
        vigencia_fim IS NULL OR vigencia_fim >= vigencia_ini),

    -- DUAS PESSOAS NO MESMO PAPEL, NO MESMO DIA, É UMA PERGUNTA SEM RESPOSTA.
    --
    -- A régua pergunta "quem é o titular da família X do contrato Y no dia D".
    -- Com vigências sobrepostas há duas respostas e a consulta escolheria uma
    -- pela ordem física das linhas — cobrando silenciosamente a pessoa errada,
    -- e escalando para o gestor de quem nunca foi avisado. A exclusão recusa a
    -- sobreposição no cadastro, que é onde o erro é barato de corrigir.
    CONSTRAINT destinatario_sem_sobreposicao EXCLUDE USING gist (
        contrato_servico_id WITH =,
        coalesce(familia, '*') WITH =,
        papel WITH =,
        daterange(vigencia_ini, vigencia_fim, '[]') WITH &&)
);

COMMENT ON TABLE destinatario IS
    'Cap. 11.2: destinatário nominal resolvido por (contrato × família), com substituto.';
COMMENT ON COLUMN destinatario.familia IS
    'NULL = vale para todas as famílias do contrato. GESTOR e DAF não variam por família.';

CREATE INDEX ix_destinatario_resolucao
    ON destinatario (contrato_servico_id, papel, vigencia_ini);

-- -----------------------------------------------------------------------------
-- Templates versionados (cap. 11.2)
--
-- Append-only pela mesma razão de versao_matriz: o hash gravado em
-- `notificacao.conteudo_hash` só prova alguma coisa se o template daquela
-- versão não puder ser reescrito depois. Editar o texto de uma versão já usada
-- invalidaria silenciosamente toda a trilha de notificações que a referencia.
-- -----------------------------------------------------------------------------
CREATE TABLE template_notificacao (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    momento    text        NOT NULL CHECK (momento IN (
                   'PREVENTIVA', 'COBRANCA', 'ESCALONAMENTO', 'FECHAMENTO', 'RESOLVIDA')),
    versao     text        NOT NULL,
    assunto    text        NOT NULL,
    corpo      text        NOT NULL,
    vigente    boolean     NOT NULL DEFAULT true,
    criado_em  timestamptz NOT NULL DEFAULT now(),
    criado_por text        NOT NULL,

    CONSTRAINT template_versao_unica UNIQUE (momento, versao),

    -- Cap. 11.2: "nunca anexar documento; somente link autenticado com
    -- expiração". Um template que fale em anexo produz um e-mail que o
    -- capítulo proíbe, e o lugar de barrar isso é o cadastro — depois de
    -- enviado não há o que fazer.
    CONSTRAINT template_sem_anexo CHECK (
        corpo !~* '(anex[oa]|em anexo|segue o arquivo|attachment)'),

    -- O rodapé de não-substituição do ateste é obrigatório no fechamento
    -- (cap. 11.1). Sem ele o e-mail sugere que o sistema atesta a medição.
    CONSTRAINT template_fechamento_com_rodape CHECK (
        momento <> 'FECHAMENTO' OR corpo ~* 'não substitui o ateste')
);

CREATE UNIQUE INDEX ux_template_vigente
    ON template_notificacao (momento) WHERE vigente;

COMMENT ON INDEX ux_template_vigente IS
    'Um template vigente por momento. Dois seriam duas mensagens possíveis para '
    'o mesmo fato, e o hash gravado deixaria de identificar o que foi dito.';

-- -----------------------------------------------------------------------------
-- Modo sombra: estado da PRD, não ambiente (cap. 17).
--
-- Semeado LIGADO. Um sistema recém-instalado, sem ninguém ter decidido nada,
-- não pode começar cobrando as áreas: o padrão inseguro aqui é enviar.
-- -----------------------------------------------------------------------------
INSERT INTO parametro (chave, escopo, valor, descricao, criado_por)
VALUES ('notificacao.modo_sombra', 'GLOBAL', 'true'::jsonb,
        'Cap. 11.2 / história F1-09: suprime todo envio e grava o que teria sido '
        'enviado, para calibragem. Desligar é decisão de governança, registrada '
        'na trilha — e só depois da F3-01 (divergência <= 2% por dois ciclos).',
        'migracao-V010');

COMMIT;
