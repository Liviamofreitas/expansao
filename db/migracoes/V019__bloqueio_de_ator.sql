-- =============================================================================
-- SGDF — V019 · Bloqueio de ator por negativas repetidas (SEC-06)
--
-- ORIGEM: o cap. 15.2 exige "rate limiting e lockout progressivo na API". O SGDF
-- é resource server — quem autentica é o IdP (cap. 14.4) —, então lockout aqui
-- não é sobre senha: é sobre um ator JÁ AUTENTICADO que insiste em pedir o que
-- não pode.
--
-- A trilha registra NEGADO desde a F1-06, com o comentário "é o que permite ver
-- tentativa repetida de acesso". Ver não é reagir. Isto é a reação.
--
-- POR QUE NO BANCO, E NÃO EM MEMÓRIA.
--
-- Um bloqueio em memória some no reinício da réplica e não existe na outra. As
-- duas propriedades são exploráveis sem nenhuma sofisticação: quem sonda espera
-- o deploy, ou simplesmente tenta de novo até cair na réplica que não o conhece.
-- Um controle que o adversário desliga esperando não é controle.
--
-- O limite de VOLUME fica em memória de propósito (ver PoliticaDeUso): ele
-- protege de laço automatizado, o custo de compartilhá-lo seria uma escrita por
-- requisição, e o pior caso — N × o limite, com N réplicas — está declarado.
-- =============================================================================

BEGIN;

CREATE TABLE bloqueio_de_ator (
    id           bigserial PRIMARY KEY,
    ator         text        NOT NULL,
    bloqueado_em timestamptz NOT NULL DEFAULT now(),
    expira_em    timestamptz NOT NULL,
    negativas    integer     NOT NULL,
    motivo       text        NOT NULL,
    liberado_em  timestamptz,
    liberado_por text,
    CONSTRAINT bloqueio_ator_substantivo CHECK (btrim(ator) <> ''),
    -- TEM FIM, E A RESTRIÇÃO GARANTE. Um bloqueio permanente vira chamado de
    -- suporte, e chamado repetido vira um bypass que alguém cria e ninguém
    -- remove. O banco recusa gravar um que não expire.
    CONSTRAINT bloqueio_expira_depois CHECK (expira_em > bloqueado_em),
    CONSTRAINT bloqueio_com_negativas CHECK (negativas > 0),
    CONSTRAINT bloqueio_liberacao_completa CHECK (
        (liberado_em IS NULL AND liberado_por IS NULL)
     OR (liberado_em IS NOT NULL AND liberado_por IS NOT NULL))
);

-- Um bloqueio ativo por ator. Dois seriam duas expirações para o mesmo fato, e
-- a pergunta "ele está bloqueado até quando?" deixaria de ter resposta única.
CREATE UNIQUE INDEX ux_bloqueio_ativo ON bloqueio_de_ator (ator)
    WHERE liberado_em IS NULL;
CREATE INDEX ix_bloqueio_expira ON bloqueio_de_ator (expira_em)
    WHERE liberado_em IS NULL;

COMMENT ON TABLE bloqueio_de_ator IS
    'SEC-06: reação à tentativa repetida de acesso que a trilha já registrava e '
    'ninguém lia. No banco porque em memória o bloqueio some no reinício e não '
    'existe na outra réplica — duas coisas que o adversário explora sem esforço.';
COMMENT ON COLUMN bloqueio_de_ator.liberado_por IS
    'Liberação manual é ato auditável: alguém decidiu que o bloqueio era engano.';

COMMIT;
