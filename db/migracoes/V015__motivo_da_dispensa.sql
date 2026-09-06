-- =============================================================================
-- SGDF — V015 · O motivo da dispensa (história F2-05)
--
-- ORIGEM: a F2-05 precisa marcar como satisfeita a exigência que a ALTERNATIVA
-- do grupo condicional satisfez — o termo de não adesão entregue satisfaz o
-- vale-transporte daquele profissional (cap. 7.5). O estado natural é
-- DISPENSADO, e é aí que aparece o problema.
--
-- DISPENSADO JÁ SIGNIFICAVA OUTRA COISA.
--
-- O cap. 6.1 só chega a DISPENSADO por um caminho: "PENDENTE|DIVERGENTE →
-- DISPENSADO · Exceção aprovada · Aprovador DAF". É uma decisão de governança,
-- tomada por uma pessoa, com motivo, evidência e segregação de funções (F0-09).
--
-- A substituição condicional não é nada disso: é rotina, decidida pelo próprio
-- documento que chegou. Pôr as duas no mesmo estado, sem distinção, faria o
-- auditor que lê o book não conseguir dizer se um documento foi formalmente
-- dispensado pela DAF ou apenas substituído pela sua alternativa — e o número de
-- exceções aprovadas, que é indicador de governança (cap. 21), passaria a contar
-- substituições de vale-transporte.
--
-- `dispensa_motivo` separa as duas, e a restrição impede DISPENSADO sem motivo.
-- =============================================================================

BEGIN;

ALTER TABLE exigencia
    ADD COLUMN dispensa_motivo text CHECK (dispensa_motivo IN ('EXCECAO', 'CONDICIONAL'));

COMMENT ON COLUMN exigencia.dispensa_motivo IS
    'EXCECAO = decisão do APROVADOR_DAF (F0-09). CONDICIONAL = a alternativa do '
    'grupo do cap. 7.5 foi entregue. Sem a distinção, o indicador de exceções '
    'aprovadas contaria substituições de rotina.';

-- BACKFILL ANTES DA RESTRIÇÃO — a lição da V012.
--
-- Toda dispensa que existe hoje veio do único caminho que existia: exceção
-- aprovada. Deixar o motivo nulo faria a restrição abaixo recusar as linhas
-- antigas, e pior: faria a leitura futura tratá-las como indefinidas.
UPDATE exigencia SET dispensa_motivo = 'EXCECAO' WHERE status = 'DISPENSADO';

ALTER TABLE exigencia
    ADD CONSTRAINT exigencia_dispensa_com_motivo CHECK (
        (status = 'DISPENSADO' AND dispensa_motivo IS NOT NULL)
     OR (status <> 'DISPENSADO' AND dispensa_motivo IS NULL));

CREATE INDEX ix_exigencia_dispensa ON exigencia (dispensa_motivo)
    WHERE dispensa_motivo IS NOT NULL;

COMMIT;
