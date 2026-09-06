-- =============================================================================
-- SGDF — V016 · Os marcos do ciclo no banco (histórias F3-03 e F3-04)
--
-- ORIGEM: uma verificação deliberada. Removendo a guarda em Java que impede
-- ATESTADO sem ateste e FATURADO sem NF, o banco aceitava as duas — o CHECK do
-- V001 valida o CONJUNTO de valores de `status`, não a ORDEM em que se chega a
-- eles. São coisas diferentes, e é a segunda que o cap. 6.2 define.
--
-- POR QUE ISSO IMPORTA MAIS QUE UMA TRANSIÇÃO ERRADA.
--
-- `ateste_em` é o marco de onde o indicador D+3 conta (cap. 21). Um ciclo em
-- FATURADO sem `ateste_em` não é apenas um estado inconsistente: ele SAI da
-- conta do indicador — o denominador filtra por `ateste_em IS NOT NULL` —, e
-- some do numerador junto. O ciclo que mais demorou é exatamente o que tem mais
-- chance de ter sido remendado à mão, e o efeito de removê-lo dos dois lados da
-- fração é um percentual mais bonito. O indicador melhora porque o dado piorou.
--
-- Por isso a restrição vive aqui e não só no serviço: um UPDATE manual em
-- produção — o caminho por onde esse remendo passaria — não vê o Java.
--
-- NÃO SUBSTITUI A TABELA DE TRANSIÇÕES. O banco passa a garantir que ATESTADO,
-- FATURADO e FECHADO tenham os seus marcos; a ORDEM entre eles continua no
-- `EstadoDoCiclo` do cap. 6.2, porque um CHECK não enxerga o valor anterior da
-- linha. Duas garantias diferentes, nenhuma redundante.
-- =============================================================================

BEGIN;

-- SEM BACKFILL, E É DELIBERADO — a lição da V012 lida ao contrário.
--
-- A V015 preencheu antes de restringir porque a linha antiga TINHA um valor
-- correto e conhecido: toda dispensa vinha de exceção. Aqui não há valor
-- correto a inventar: um ciclo em FATURADO sem `ateste_em` não tem data de
-- ateste "provável", e escrever `now()` ou `aberto_em` no lugar produziria um
-- D+3 calculado sobre uma data fabricada. Se houver linha assim, a migração
-- FALHA — e falhar é a resposta certa: alguém precisa olhar o ciclo e dizer
-- quando o cliente atestou.
ALTER TABLE ciclo
    ADD CONSTRAINT ciclo_ateste_antes_de_atestado CHECK (
        status NOT IN ('ATESTADO', 'FATURADO', 'FECHADO') OR ateste_em IS NOT NULL);

ALTER TABLE ciclo
    ADD CONSTRAINT ciclo_nf_antes_de_faturado CHECK (
        status NOT IN ('FATURADO', 'FECHADO') OR nf_emitida_em IS NOT NULL);

-- Um intervalo ateste→NF negativo entraria no D+3 como cumprimento: a condição
-- do indicador é `nf_emitida_em <= ateste_em + 3 dias`, e qualquer data
-- anterior ao ateste a satisfaz com folga.
ALTER TABLE ciclo
    ADD CONSTRAINT ciclo_nf_nao_antecede_ateste CHECK (
        nf_emitida_em IS NULL OR ateste_em IS NULL OR nf_emitida_em >= ateste_em);

COMMENT ON CONSTRAINT ciclo_ateste_antes_de_atestado ON ciclo IS
    'Cap. 6.2 e 21: sem ateste_em o ciclo sai do numerador E do denominador do '
    'indicador D+3 — o ciclo mais atrasado é o que mais tende a ser remendado, '
    'e removê-lo dos dois lados melhora o percentual sem melhorar nada.';
COMMENT ON CONSTRAINT ciclo_nf_nao_antecede_ateste ON ciclo IS
    'Cap. 21: nf_emitida_em < ateste_em satisfaz "dentro de D+3" com folga '
    'negativa. Um intervalo impossível não é um cumprimento.';

CREATE INDEX ix_ciclo_d3 ON ciclo (competencia, ateste_em, nf_emitida_em)
    WHERE ateste_em IS NOT NULL;

COMMIT;
