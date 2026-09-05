-- =============================================================================
-- SGDF — V012 · A decisão da exceção (história F0-09)
--
-- ORIGEM: `excecao` nasceu na V001 com `aprovador`, `aprovado_em` e a restrição
-- de segregação de funções. O que ela NÃO tem é onde registrar uma NEGATIVA:
-- nulo em `aprovador` significa "ainda não decidida", e depois de o DAF recusar
-- a linha continua parecendo pendente.
--
-- As consequências não são estéticas:
--   * a exigência fica esperando por uma decisão que já foi tomada;
--   * quem solicitou pede de novo, sem saber que já foi negado, e sem saber
--     por quê — a informação que faria a próxima solicitação ser melhor;
--   * a trilha do cap. 16 registra as aprovações e perde as recusas, que são
--     exatamente as decisões que alguém questiona depois.
--
-- Acrescenta `situacao` e `motivo_decisao`. `aprovador` e `aprovado_em` passam a
-- significar "quem decidiu" e "quando" — o nome fica, porque renomear coluna em
-- tabela referenciada é mudança cara para ganho de vocabulário.
-- =============================================================================

BEGIN;

ALTER TABLE excecao
    ADD COLUMN situacao text NOT NULL DEFAULT 'SOLICITADA'
        CHECK (situacao IN ('SOLICITADA', 'APROVADA', 'NEGADA')),
    ADD COLUMN motivo_decisao text;

COMMENT ON COLUMN excecao.situacao IS
    'F0-09. NEGADA é estado de primeira classe: sem ele, a recusa some e a '
    'exigência fica esperando decisão já tomada.';
COMMENT ON COLUMN excecao.aprovador IS
    'Quem DECIDIU — aprovou ou negou. O nome é da V001; a semântica é a da V012.';

-- BACKFILL ANTES DA RESTRIÇÃO.
--
-- O DEFAULT põe 'SOLICITADA' em toda linha existente, inclusive nas que já têm
-- aprovador — e essas passariam a dizer "ainda não decidida" sobre uma decisão
-- tomada. Sem esta linha a própria migração criaria a ambiguidade que veio
-- remover, e a restrição abaixo recusaria as linhas antigas.
--
-- Não há como distinguir aprovação de negativa nas linhas da V001: a coluna que
-- registrava isso é a que está sendo criada. Todas viram APROVADA, que era o
-- único desfecho que a V001 sabia registrar.
UPDATE excecao SET situacao = 'APROVADA' WHERE aprovador IS NOT NULL;

-- A restrição da V001 exigia aprovador+data juntos ou ambos nulos, o que não
-- alcança os três estados de agora.
ALTER TABLE excecao DROP CONSTRAINT excecao_aprovacao_completa;

ALTER TABLE excecao
    ADD CONSTRAINT excecao_decisao_completa CHECK (
        (situacao = 'SOLICITADA' AND aprovador IS NULL AND aprovado_em IS NULL)
     OR (situacao IN ('APROVADA', 'NEGADA')
             AND aprovador IS NOT NULL AND aprovado_em IS NOT NULL));

-- Negar sem dizer por quê devolve o problema a quem solicitou sem nada que o
-- ajude a resolvê-lo: ou ele pede de novo igual, ou desiste de uma exceção que
-- talvez fosse legítima com outra evidência.
ALTER TABLE excecao
    ADD CONSTRAINT excecao_negativa_com_motivo CHECK (
        situacao <> 'NEGADA'
        OR (motivo_decisao IS NOT NULL AND length(btrim(motivo_decisao)) >= 20));

-- Uma exigência tem no máximo uma exceção EM ABERTO. Duas solicitações
-- simultâneas para a mesma exigência produzem duas decisões possíveis para o
-- mesmo fato — e a segunda aprovação encontraria a exigência já DISPENSADA.
CREATE UNIQUE INDEX ux_excecao_aberta
    ON excecao (exigencia_id) WHERE situacao = 'SOLICITADA';

COMMENT ON INDEX ux_excecao_aberta IS
    'Cap. 15.1 / F0-09: uma exceção em aberto por exigência. Reabrir depois de '
    'negada é permitido — é pedir de novo, com o que faltava.';

CREATE INDEX ix_excecao_pendente ON excecao (criado_em)
    WHERE situacao = 'SOLICITADA';

COMMIT;
