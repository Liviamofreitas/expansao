-- =============================================================================
-- SGDF — V020 · Tabela de temporalidade e registro de expurgo
-- Pendência A08 · requisito LGPD-02 (art. 15, I; art. 16; art. 18, IV e VI).
--
-- O QUE ESTA MIGRAÇÃO FAZ, E O QUE ELA DELIBERADAMENTE NÃO FAZ.
--
-- Ela cria o mecanismo de expurgo por temporalidade. Ela NÃO decide um único
-- prazo. Prazo de guarda é decisão jurídica, não de engenharia: errar para
-- menos destrói prova de regularidade trabalhista numa reclamatória; errar para
-- mais é retenção indevida de dado pessoal. Nenhum dos dois erros é reversível
-- no primeiro caso, e nenhum dos dois é de quem escreve o código.
--
-- POR ISSO A APROVAÇÃO É UMA TRAVA ESTRUTURAL, NÃO UM `if`.
--
-- `expurgo.autorizado_em` é NOT NULL e é copiada de `temporalidade.aprovado_em`
-- pelo próprio INSERT. Uma classe não aprovada tem `aprovado_em` nula, o INSERT
-- viola o NOT NULL, a transação aborta — e o DELETE que vem depois dela, na
-- mesma transação, nunca acontece. Não há caminho que apague sem registrar, e
-- não há registro possível sem alguém ter aprovado o prazo com nome e data.
--
-- O ESTADO DE HOJE, DITO SEM MAQUIAGEM.
--
-- Nenhuma classe está aprovada. Logo, o expurgo não apaga nada. Isso é o
-- correto, e é também o modo de falha mais perigoso desta base: um job que roda
-- todo mês, apaga zero e deixa o painel verde faria a não conformidade da A08
-- sobreviver anos sem sintoma. É o mesmo padrão da F0-05, da completude na 1ª
-- conferência, do agendador morto e do ciclo vazio — um componente cuja falha
-- se parece com sucesso.
--
-- A defesa é a mesma das outras quatro vezes: a recusa é registrada e contada.
-- "Rodou, estava autorizado e nada tinha vencido" grava linha em `expurgo` com
-- itens = 0. "Rodou e não havia classe aprovada" NÃO grava linha em `expurgo` —
-- não pode, por construção — e sai como não conformidade nomeada.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. A tabela de temporalidade
-- -----------------------------------------------------------------------------
CREATE TABLE temporalidade (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    classe       text        NOT NULL,
    alvo         text        NOT NULL,
    marco        text        NOT NULL,
    prazo_meses  integer     NOT NULL,
    acao         text        NOT NULL,
    fundamento   text        NOT NULL,
    aprovado_em  timestamptz,
    aprovado_por text,
    criado_em    timestamptz NOT NULL DEFAULT now(),
    criado_por   text        NOT NULL,

    CONSTRAINT temporalidade_alvo_unico UNIQUE (alvo),
    CONSTRAINT temporalidade_classe_substantiva CHECK (btrim(classe) <> ''),

    -- O ALVO É LISTA FECHADA, E AS AUSÊNCIAS SÃO O CONTEÚDO.
    --
    -- `log_auditoria` não está aqui e não pode entrar. A trilha guarda o
    -- identificador do ATOR e a ação, não dado do titular (inventário, seção 3):
    -- não é ela que o art. 16 manda eliminar, e é ela que o cap. 12 e o controle
    -- A.8.15 da ISO/IEC 27001 mandam preservar. Ela é append-only por RULE e por
    -- REVOKE desde a V002; declarar temporalidade para ela seria escrever uma
    -- regra que o banco recusa cumprir, e uma regra que não se cumpre é pior que
    -- nenhuma.
    --
    -- `book` também não está, por outro motivo. A retenção do book é do
    -- armazenamento — `book.retencao_modo`, Object Lock —, não de um DELETE de
    -- linha. Destruir um book exige antes levantar o LEGAL_HOLD, que é ato
    -- humano de papel autorizado. Pôr BOOK aqui daria ao agendador o poder de
    -- apagar a evidência selada que o cliente atestou.
    CONSTRAINT temporalidade_alvo_conhecido CHECK (alvo IN (
        'ACESSO_OBSERVADO',   -- SEC-10: quem acessou o quê, por dia
        'NOTIFICACAO',        -- cap. 11.1: a régua enviada
        'DOCUMENTO',          -- o documento coletado e o seu binário
        'CAMPO_EXTRAIDO',     -- os valores lidos do documento
        'PROFISSIONAL'        -- cadastro do titular
    )),

    -- O MARCO IMPORTA MAIS QUE O PRAZO, E É ONDE SE ERRA.
    --
    -- "Cinco anos" não diz nada sem dizer cinco anos a partir de quê. A
    -- prescrição do art. 7º, XXIX da Constituição corre da EXTINÇÃO DO CONTRATO
    -- DE TRABALHO, não da competência. Um documento da competência 2020-01 de
    -- alguém desligado em 2029 ainda é prova em 2033; contado da competência,
    -- ele teria sido apagado em 2025.
    CONSTRAINT temporalidade_marco_conhecido CHECK (marco IN (
        'REGISTRO',                     -- a data em que a própria linha nasceu
        'FIM_DA_COMPETENCIA',           -- o último dia da competência
        'PUBLICACAO_DO_BOOK',           -- a selagem da evidência
        'DESLIGAMENTO_DO_PROFISSIONAL'  -- a extinção do vínculo (art. 7º, XXIX)
    )),

    CONSTRAINT temporalidade_acao_conhecida CHECK (acao IN (
        'EXPURGAR',    -- apaga
        'ANONIMIZAR',  -- art. 16: guarda o fato, perde o titular
        'REVISAR'      -- vence e vira lista para humano, não para o agendador
    )),

    -- Prazo zero seria "apague ao nascer", e prazo negativo não é prazo. Os dois
    -- entrariam como número e sairiam como destruição imediata de prova.
    CONSTRAINT temporalidade_prazo_positivo CHECK (prazo_meses > 0),

    -- Fundamento vazio produz a pergunta que ninguém consegue responder na
    -- auditoria: por que este prazo e não outro?
    CONSTRAINT temporalidade_com_fundamento CHECK (btrim(fundamento) <> ''),

    -- APROVAÇÃO É NOME E DATA, OU NADA.
    -- Data sem nome é prazo que se aprovou sozinho.
    CONSTRAINT temporalidade_aprovacao_completa CHECK (
        (aprovado_em IS NULL     AND aprovado_por IS NULL) OR
        (aprovado_em IS NOT NULL AND aprovado_por IS NOT NULL
                                 AND btrim(aprovado_por) <> ''))
);

COMMENT ON TABLE temporalidade IS
    'A08/LGPD-02: por quanto tempo cada classe de dado se guarda, a partir de '
    'que marco, e com que ação ao vencer. Linha sem aprovado_em existe e não '
    'autoriza nada — é a proposta esperando o jurídico.';
COMMENT ON COLUMN temporalidade.marco IS
    'De onde o prazo conta. DESLIGAMENTO_DO_PROFISSIONAL é o marco do art. 7º, '
    'XXIX da CF; contar da competência apaga prova ainda exigível.';
COMMENT ON COLUMN temporalidade.aprovado_em IS
    'Nula = não aprovada. É a trava: expurgo.autorizado_em copia este valor e é '
    'NOT NULL, então uma classe não aprovada não consegue nem registrar o '
    'expurgo — e o que não se registra não se apaga.';

-- -----------------------------------------------------------------------------
-- 2. O registro do expurgo — um lote por (execução, alvo)
-- -----------------------------------------------------------------------------
CREATE TABLE expurgo (
    id               bigserial PRIMARY KEY,
    temporalidade_id uuid        NOT NULL REFERENCES temporalidade (id),
    autorizado_em    timestamptz NOT NULL,
    alvo             text        NOT NULL,
    acao             text        NOT NULL,
    corte            date        NOT NULL,
    itens            integer     NOT NULL,
    executado_em     timestamptz NOT NULL DEFAULT now(),
    executado_por    text        NOT NULL,
    execucao_job_id  bigint      REFERENCES execucao_de_job (id),

    -- ZERO É RESULTADO, E É DIFERENTE DE LINHA NENHUMA.
    -- "Estava autorizado, o corte foi 2019-12-31, nada tinha vencido" é um fato
    -- que prova que a política está viva. Ausência de linha não prova nada.
    CONSTRAINT expurgo_itens_nao_negativos CHECK (itens >= 0),

    -- REVISAR NÃO ENTRA AQUI, E A RECUSA É O PONTO.
    -- Esta tabela significa "algo deixou de existir". Uma revisão não eliminou
    -- nada: ela produziu lista para humano. Registrá-la aqui faria `itens`
    -- querer dizer duas coisas — apagados numa linha, listados na outra — e uma
    -- contagem ambígua num registro de eliminação é pior que contagem nenhuma.
    CONSTRAINT expurgo_acao_elimina CHECK (acao IN ('EXPURGAR', 'ANONIMIZAR')),
    CONSTRAINT expurgo_executor_nomeado CHECK (btrim(executado_por) <> ''),

    -- A autorização não pode ser posterior ao ato. Uma linha assim seria um
    -- expurgo aprovado depois de executado, que é exatamente o que o registro
    -- existe para tornar impossível.
    CONSTRAINT expurgo_autorizado_antes CHECK (autorizado_em <= executado_em)
);

CREATE INDEX ix_expurgo_alvo ON expurgo (alvo, executado_em DESC);

-- -----------------------------------------------------------------------------
-- 3. O item expurgado
--
-- O LOG DO EXPURGO NÃO PODE RECRIAR O QUE O EXPURGO DESTRUIU.
--
-- Registrar "apaguei o contracheque de FULANO, CPF 123..." para provar que o
-- CPF foi eliminado deixa o CPF vivo na tabela de prova. O identificador aqui é
-- a CHAVE da linha apagada — uuid ou inteiro —, nunca nome, CPF, caminho ou
-- nome de arquivo. O CHECK abaixo torna o erro impossível em vez de proibido:
-- só passa o que tem forma de chave.
-- -----------------------------------------------------------------------------
CREATE TABLE expurgo_item (
    id            bigserial PRIMARY KEY,
    expurgo_id    bigint NOT NULL REFERENCES expurgo (id),
    identificador text   NOT NULL,
    marco_em      date   NOT NULL,

    CONSTRAINT expurgo_item_so_chave CHECK (
        identificador ~ '^[0-9]+$'
     OR identificador ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$')
);

CREATE INDEX ix_expurgo_item_lote ON expurgo_item (expurgo_id);

COMMENT ON TABLE expurgo_item IS
    'LGPD-02: o que foi apagado, pela chave. Nunca pelo conteúdo — o log da '
    'eliminação que guarda o dado eliminado não elimina nada.';

-- -----------------------------------------------------------------------------
-- 4. Append-only, pelas duas primitivas
--
-- Mesmo tratamento da trilha (V002). A razão é a mesma e é mais forte aqui: o
-- registro do expurgo é a ÚNICA prova que sobra depois que o dado sumiu. Se ele
-- puder ser editado, a eliminação deixa de ser demonstrável — e uma eliminação
-- que não se demonstra, perante a ANPD, não aconteceu.
--
-- REVOKE cobre a role da aplicação; a RULE cobre também quem entrar como dono
-- da tabela, para quem o REVOKE não vale.
-- -----------------------------------------------------------------------------
REVOKE UPDATE, DELETE, TRUNCATE ON expurgo      FROM sgdf_app;
REVOKE UPDATE, DELETE, TRUNCATE ON expurgo_item FROM sgdf_app;
REVOKE UPDATE, DELETE, TRUNCATE ON expurgo      FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON expurgo_item FROM PUBLIC;

CREATE RULE expurgo_sem_update AS ON UPDATE TO expurgo DO INSTEAD NOTHING;
CREATE RULE expurgo_sem_delete AS ON DELETE TO expurgo DO INSTEAD NOTHING;
CREATE RULE expurgo_item_sem_update AS ON UPDATE TO expurgo_item DO INSTEAD NOTHING;
CREATE RULE expurgo_item_sem_delete AS ON DELETE TO expurgo_item DO INSTEAD NOTHING;

COMMIT;
