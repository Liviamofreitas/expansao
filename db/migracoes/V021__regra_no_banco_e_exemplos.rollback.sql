-- Rollback da V021.
--
-- A ordem importa: a tabela de exemplos referencia a regra, e as constraints
-- saem antes das colunas que elas restringem.
DROP TABLE IF EXISTS regra_exemplo;

ALTER TABLE regra_reconhecimento DROP CONSTRAINT IF EXISTS regra_recon_peso_campo;
ALTER TABLE regra_reconhecimento DROP COLUMN IF EXISTS peso_por_campo;

ALTER TABLE regra_reconhecimento DROP CONSTRAINT IF EXISTS regra_recon_ancora_obrigatoria;
ALTER TABLE regra_reconhecimento DROP CONSTRAINT IF EXISTS regra_recon_identificacao;
ALTER TABLE regra_reconhecimento DROP COLUMN IF EXISTS identificacao;
