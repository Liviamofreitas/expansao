-- =============================================================================
-- SGDF — V017 · Acesso observado (história F3-06, requisito SEC-10)
--
-- ORIGEM: a recertificação trimestral seria montada sobre `log_auditoria`, que
-- já registra ator e papel. Só que a trilha registra QUEM ESCREVE — e o papel
-- AUDITORIA, por definição do cap. 15.1, não escreve nada ("qualquer escrita" na
-- coluna "não pode"). O auditor nunca apareceria na própria recertificação.
--
-- A INVERSÃO QUE ISSO PRODUZ. Uma revisão de acessos existe para achar a conta
-- que ninguém usa: é ela que sobrevive a um desligamento, e é ela que um
-- atacante quer. Um relatório montado só sobre escrita lista exatamente os
-- ativos e omite exatamente os dormentes — entrega ao aprovador a metade que
-- não precisava ser revista.
--
-- E SEC-10 PEDE "POR CONTRATO". O recorte de contrato do ator vem do token
-- (claim do provedor de identidade) e não estava sendo gravado em lugar nenhum:
-- o sistema sabia decidir com ele e não sabia relatá-lo.
--
-- UMA LINHA POR ATOR, POR DIA, POR CONJUNTO DE CONCESSÕES.
--
-- Não uma por requisição: seria uma escrita a cada leitura, e o custo não
-- compraria informação — o que a recertificação precisa saber é o que foi
-- concedido, não quantas vezes foi exercido (isso a trilha já responde).
-- Incluir papéis e contratos na chave faz a MUDANÇA aparecer: um ator que ganha
-- um papel no meio do dia produz a segunda linha, e a deriva de concessão fica
-- visível na série em vez de ser sobrescrita.
-- =============================================================================

BEGIN;

CREATE TABLE acesso_observado (
    id         bigserial PRIMARY KEY,
    ator       text        NOT NULL,
    dia        date        NOT NULL,
    papeis     text[]      NOT NULL,
    contratos  uuid[]      NOT NULL,
    visto_em   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT acesso_ator_substantivo CHECK (btrim(ator) <> ''),
    -- Papéis vazios não é observação: é ator sem acesso nenhum, que o
    -- Autorizador barra na fronteira e não deveria chegar aqui.
    CONSTRAINT acesso_com_papel CHECK (cardinality(papeis) > 0)
);

-- Os arrays entram no índice ORDENADOS pelo chamador — sem isso, {A,B} e {B,A}
-- seriam duas concessões diferentes e a deriva apareceria onde não houve.
CREATE UNIQUE INDEX ux_acesso_observado
    ON acesso_observado (ator, dia, papeis, contratos);
CREATE INDEX ix_acesso_por_dia ON acesso_observado (dia DESC, ator);

COMMENT ON TABLE acesso_observado IS
    'SEC-10: o que o provedor de identidade concedeu, como o sistema viu. A '
    'trilha registra quem ESCREVE; isto registra quem ENTRA — inclusive o papel '
    'AUDITORIA, que por definição não escreve.';
COMMENT ON COLUMN acesso_observado.contratos IS
    'Recorte do token. Vazio = visão global (APROVADOR_DAF, AUDITORIA).';

COMMIT;
