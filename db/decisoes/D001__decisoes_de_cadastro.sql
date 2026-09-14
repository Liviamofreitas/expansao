-- =============================================================================
-- SGDF — D001 · As decisões de cadastro que faltam para o primeiro ciclo abrir
--
-- Este arquivo NÃO é migração de esquema. Ele aplica três decisões de negócio
-- que só as áreas podem tomar, e que hoje impedem a abertura dos 12
-- contratos-serviço. Ver docs/GO-LIVE-2026-09-15.md.
--
--   B1 · Qual o prazo do comprovante de pagamento da rescisão: 1º ou 10º dia
--        útil do início da competência? (pendência A13)
--   B2 · Qual empresa do grupo emite para cada contrato-serviço? (pendência A14)
--   B3 · Qual é a empresa real — hoje o cadastro só tem um marcador.
--
-- COMO USAR
--
--   1. Preencha os três \set abaixo.
--   2. psql -v ON_ERROR_STOP=1 -f db/decisoes/D001__decisoes_de_cadastro.sql
--   3. Leia o relatório do fim: ele diz o que FALTA, não o que deu certo.
--   4. Confirme com o ensaio de abertura antes de considerar resolvido.
--
-- ESTE ARQUIVO RECUSA RODAR PELA METADE.
--
-- Um script de cadastro que aplica o que entendeu e ignora o que não foi
-- preenchido é o pior desfecho possível: alguém o roda, vê COMMIT, e acredita
-- que decidiu. Com placeholder em qualquer campo ele aborta ANTES de escrever
-- coisa alguma, e diz qual campo falta.
--
-- É IDEMPOTENTE. Rodar duas vezes não duplica nada e não desfaz nada.
--
-- NOTA TÉCNICA: os valores viajam por set_config e não por :'VAR' dentro dos
-- blocos. O psql NÃO interpola variáveis dentro de $$...$$ — um arquivo escrito
-- assim falha com "syntax error at or near :", e falharia depois de já ter
-- escrito as seções anteriores.
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

-- -----------------------------------------------------------------------------
-- B3 · A EMPRESA EMITENTE REAL
--
-- Razão social e CNPJ (14 dígitos, só números). O CNPJ que aparece nos
-- documentos da massa é 03681946000130 — CONFIRA antes de usar: o documento
-- prova que a empresa existe, não que é ela quem emite para estes contratos.
-- -----------------------------------------------------------------------------
\set EMPRESA_RAZAO_SOCIAL 'PREENCHER'
\set EMPRESA_CNPJ 'PREENCHER'

-- -----------------------------------------------------------------------------
-- B1 · O PRAZO DO COMPROVANTE DE PAGAMENTO DA RESCISÃO
--
-- A matriz tem DUAS regras para RES.COMPROVANTE_PG | RESCISAO em CEF e TJ CE,
-- iguais em tudo menos no offset: uma diz 1º dia útil, a outra 10º, ambas a
-- partir de INICIO_COMPETENCIA, ambas OBRIGATORIO e BLOQUEANTE.
--
-- Preencha com 1 ou 10. A regra NÃO escolhida tem a vigência encerrada — não é
-- apagada: um ciclo já aberto congelou a versão da matriz, e apagar a linha
-- deixaria exigência apontando para regra inexistente.
-- -----------------------------------------------------------------------------
\set PRAZO_RESCISAO_OFFSET 'PREENCHER'

-- Quem está aplicando. Vai para a trilha.
\set ATOR 'decisao-cadastro'

\echo ''
\echo '=== SGDF · D001 — decisões de cadastro ==================================='

BEGIN;

\o /dev/null
SELECT set_config('sgdf.razao_social', :'EMPRESA_RAZAO_SOCIAL', true),
       set_config('sgdf.cnpj',         :'EMPRESA_CNPJ',         true),
       set_config('sgdf.offset',       :'PRAZO_RESCISAO_OFFSET', true),
       set_config('sgdf.ator',         :'ATOR',                 true);
\o

-- -----------------------------------------------------------------------------
-- 1. A RECUSA, ANTES DE QUALQUER ESCRITA
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_faltando text[] := ARRAY[]::text[];
BEGIN
    IF current_setting('sgdf.razao_social') = 'PREENCHER'
       OR btrim(current_setting('sgdf.razao_social')) = '' THEN
        v_faltando := array_append(v_faltando, 'B3 · EMPRESA_RAZAO_SOCIAL');
    END IF;
    IF current_setting('sgdf.cnpj') !~ '^[0-9]{14}$' THEN
        v_faltando := array_append(v_faltando,
            'B3 · EMPRESA_CNPJ (14 dígitos, só números)');
    END IF;
    IF current_setting('sgdf.offset') NOT IN ('1', '10') THEN
        v_faltando := array_append(v_faltando,
            'B1 · PRAZO_RESCISAO_OFFSET (1 ou 10)');
    END IF;

    IF cardinality(v_faltando) > 0 THEN
        RAISE EXCEPTION E'\n\n  Faltam decisões, e NADA foi escrito:\n    - %\n\n'
            '  Preencha os \\set no topo deste arquivo e rode de novo.\n'
            '  Ver docs/GO-LIVE-2026-09-15.md, seção 3.1.\n',
            array_to_string(v_faltando, E'\n    - ');
    END IF;
    RAISE NOTICE 'As três decisões estão preenchidas. Aplicando.';
END $$;

-- -----------------------------------------------------------------------------
-- 2. B3 · A EMPRESA
-- -----------------------------------------------------------------------------
INSERT INTO empresa (razao_social, cnpj, matriz, ativo, criado_por)
SELECT current_setting('sgdf.razao_social'), current_setting('sgdf.cnpj'),
       true, true, current_setting('sgdf.ator')
ON CONFLICT (cnpj) DO UPDATE
    SET razao_social   = EXCLUDED.razao_social,
        ativo          = true,
        atualizado_em  = now(),
        atualizado_por = EXCLUDED.criado_por;

-- -----------------------------------------------------------------------------
-- 3. B1 · O PRAZO DA RESCISÃO
--
-- Encerra a vigência da regra NÃO escolhida — e só onde há de fato duas regras
-- vigentes. Contrato com uma regra só não é tocado.
--
-- A DATA É O ÚLTIMO DIA DO MÊS ANTERIOR, E NÃO ONTEM.
--
-- `Regra.vigenteEm` compara a vigência contra o MÊS INTEIRO da competência, não
-- contra o dia 1: uma regra que termina no dia 13 ainda vale para aquela
-- competência — é deliberado, para que uma regra que passa a valer no meio do
-- mês valha no mês em que alguém decidiu que passaria a valer.
--
-- A consequência aqui: encerrar em CURRENT_DATE - 1 NÃO desempata a competência
-- corrente. A regra perdedora continua vigente, o motor continua recusando, e o
-- relatório desta seção diria "0 pares divergentes" — porque ele olha
-- `vigencia_fim IS NULL` e a linha passou a ter data. Custou um ensaio para
-- aparecer: 7 de 12 ciclos abriram e 5 continuaram recusando.
--
-- O último dia do mês anterior encerra a regra a partir da competência corrente
-- e preserva as competências já fechadas, que continuam lendo a matriz como ela
-- era.
-- -----------------------------------------------------------------------------
WITH perdedora AS (
    SELECT r.id
    FROM   regra_exigibilidade r
    JOIN   tipo_documental t ON t.id = r.tipo_id
    WHERE  r.alvo = 'CONTRATO'
      AND  t.codigo = 'RES.COMPROVANTE_PG'
      AND  r.vigencia_fim IS NULL
      AND  (r.prazo #>> '{ancora}') = 'INICIO_COMPETENCIA'
      AND  (r.prazo #>> '{offset}')::int
           <> current_setting('sgdf.offset')::int
      AND  EXISTS (
            SELECT 1 FROM regra_exigibilidade o
            WHERE  o.alvo = 'CONTRATO' AND o.tipo_id = r.tipo_id
              AND  o.alvo_contrato_id = r.alvo_contrato_id
              AND  o.id <> r.id AND o.vigencia_fim IS NULL
              AND  (o.prazo #>> '{offset}')::int
                   = current_setting('sgdf.offset')::int)
)
UPDATE regra_exigibilidade r
SET    vigencia_fim = (date_trunc('month', CURRENT_DATE) - INTERVAL '1 day')::date
FROM   perdedora p
WHERE  r.id = p.id;

-- -----------------------------------------------------------------------------
-- 4. B2 · QUEM EMITE PARA CADA CONTRATO
--
-- Por padrão, a empresa da seção 2 emite para TODOS os contratos.
--
-- EXCEÇÕES: se algum contrato-serviço for emitido por OUTRA empresa do grupo,
-- cadastre-a antes e descomente uma linha por contrato. A chave é
-- (numero, servico) porque o mesmo NÚMERO tem mais de um contrato-serviço — o
-- desdobramento D-02. Usar só o número atribuiria a empresa errada aos irmãos.
-- -----------------------------------------------------------------------------
CREATE TEMP TABLE excecao_de_emissor (numero text, servico text, cnpj char(14))
    ON COMMIT DROP;

-- INSERT INTO excecao_de_emissor VALUES ('TJCE-01', 'SUS', '00000000000000');
-- INSERT INTO excecao_de_emissor VALUES ('482023',  'OUT', '00000000000000');

DO $$
DECLARE
    v_ruins text[];
BEGIN
    SELECT array_agg(x.numero || '/' || x.servico || ' → CNPJ ' || x.cnpj)
      INTO v_ruins
      FROM excecao_de_emissor x
     WHERE NOT EXISTS (SELECT 1 FROM empresa e WHERE e.cnpj = x.cnpj);
    IF v_ruins IS NOT NULL THEN
        RAISE EXCEPTION E'\n\n  Exceção de emissor aponta para empresa não cadastrada:\n'
            '    - %\n\n  Cadastre a empresa antes, ou corrija o CNPJ.\n',
            array_to_string(v_ruins, E'\n    - ');
    END IF;

    SELECT array_agg(x.numero || '/' || x.servico)
      INTO v_ruins
      FROM excecao_de_emissor x
     WHERE NOT EXISTS (SELECT 1 FROM contrato_servico cs
                        WHERE cs.numero = x.numero AND cs.servico = x.servico);
    IF v_ruins IS NOT NULL THEN
        -- Exceção que não casa com contrato nenhum seria silenciosamente
        -- ignorada, e quem a escreveu acreditaria tê-la aplicado.
        RAISE EXCEPTION E'\n\n  Exceção de emissor não corresponde a contrato-serviço '
            'nenhum:\n    - %\n\n  Confira número e serviço.\n',
            array_to_string(v_ruins, E'\n    - ');
    END IF;
END $$;

UPDATE contrato_servico cs
SET    empresa_id = coalesce(
           (SELECT e.id FROM excecao_de_emissor x JOIN empresa e ON e.cnpj = x.cnpj
             WHERE x.numero = cs.numero AND x.servico = cs.servico),
           (SELECT e.id FROM empresa e WHERE e.cnpj = current_setting('sgdf.cnpj'))),
       atualizado_em  = now(),
       atualizado_por = current_setting('sgdf.ator')
WHERE  cs.empresa_id IS NULL
   OR  cs.empresa_id IN (SELECT id FROM empresa WHERE cnpj = '00000000000000')
   OR  EXISTS (SELECT 1 FROM excecao_de_emissor x
                WHERE x.numero = cs.numero AND x.servico = cs.servico);

-- -----------------------------------------------------------------------------
-- 5. HIGIENE · as regras duplicadas
--
-- O motor já as colapsa (caso normativo MAT-16) — isto não muda comportamento,
-- só tira lixo do cadastro. A guarda é o ponto: apaga apenas o que for IDÊNTICO
-- em tudo que a resolução lê, e nunca o que já sustenta exigência gravada.
-- Regra divergente não é tocada aqui: divergência é decisão, e a decisão é a
-- seção 3.
-- -----------------------------------------------------------------------------
WITH numerada AS (
    SELECT r.id,
           row_number() OVER (
               PARTITION BY r.alvo, r.alvo_contrato_id, r.alvo_modalidade_id, r.tipo_id,
                            r.obrigatoriedade, r.criticidade, r.prazo,
                            r.responsavel_titular, r.responsavel_substituto,
                            r.vigencia_ini, r.vigencia_fim, r.fundamento
               ORDER BY r.criado_em, r.id) AS n
    FROM   regra_exigibilidade r
    WHERE  r.vigencia_fim IS NULL
)
DELETE FROM regra_exigibilidade r
USING  numerada x
WHERE  r.id = x.id AND x.n > 1
  AND  NOT EXISTS (SELECT 1 FROM exigencia e WHERE e.regra_id = r.id);

-- -----------------------------------------------------------------------------
-- 6. O RELATÓRIO — que diz o que FALTA, não o que deu certo
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_sem_empresa int;
    v_marcador    int;
    v_divergentes int;
    v_total       int;
    v_pendencias  text[] := ARRAY[]::text[];
BEGIN
    SELECT count(*) INTO v_total FROM contrato_servico;
    SELECT count(*) INTO v_sem_empresa FROM contrato_servico WHERE empresa_id IS NULL;
    SELECT count(*) INTO v_marcador
      FROM contrato_servico cs JOIN empresa e ON e.id = cs.empresa_id
     WHERE e.cnpj = '00000000000000';
    -- VIGENTE NA COMPETÊNCIA CORRENTE, e não `vigencia_fim IS NULL`.
    --
    -- A primeira versão olhava só o nulo, e por isso dizia "0 divergentes"
    -- enquanto o motor recusava 5 contratos: a regra encerrada no meio do mês
    -- tem data e continua vigente. Um relatório que usa um critério diferente do
    -- motor reporta saúde que o motor não reconhece — o defeito que esta base
    -- passou a sessão inteira combatendo.
    SELECT count(*) INTO v_divergentes FROM (
        SELECT r.alvo_contrato_id, r.tipo_id
          FROM regra_exigibilidade r
         WHERE r.alvo = 'CONTRATO'
           AND r.vigencia_ini <= (date_trunc('month', CURRENT_DATE)
                                  + INTERVAL '1 month - 1 day')::date
           AND (r.vigencia_fim IS NULL
                OR r.vigencia_fim >= date_trunc('month', CURRENT_DATE)::date)
         GROUP BY 1, 2
        HAVING count(DISTINCT (r.obrigatoriedade, r.criticidade, r.prazo,
                               r.responsavel_titular, r.vigencia_ini)) > 1) x;

    IF v_sem_empresa > 0 THEN
        v_pendencias := array_append(v_pendencias,
            v_sem_empresa || ' contrato(s) ainda sem empresa emitente (A14)');
    END IF;
    IF v_marcador > 0 THEN
        v_pendencias := array_append(v_pendencias,
            v_marcador || ' contrato(s) ainda apontando para o CNPJ marcador');
    END IF;
    IF v_divergentes > 0 THEN
        v_pendencias := array_append(v_pendencias,
            v_divergentes || ' par(es) de regras ainda DIVERGENTES (A13)');
    END IF;

    RAISE NOTICE '';
    RAISE NOTICE 'Empresa emitente .............. % (CNPJ %)',
        (SELECT razao_social FROM empresa WHERE cnpj = current_setting('sgdf.cnpj')),
        current_setting('sgdf.cnpj');
    RAISE NOTICE 'Contratos com emissor ......... % de %', v_total - v_sem_empresa, v_total;
    RAISE NOTICE 'Prazo da rescisão ............. dia útil %', current_setting('sgdf.offset');
    RAISE NOTICE 'Pares divergentes restantes ... %', v_divergentes;
    RAISE NOTICE '';

    IF cardinality(v_pendencias) = 0 THEN
        RAISE NOTICE 'PRONTO pelo lado do cadastro. Confirme com o ensaio de abertura —';
        RAISE NOTICE 'este relatório diz que o cadastro está coerente, NÃO que o ciclo abre.';
    ELSE
        RAISE WARNING E'AINDA FALTA:\n    - %',
            array_to_string(v_pendencias, E'\n    - ');
    END IF;
END $$;

COMMIT;

\echo '=== fim. Confirme com o ensaio antes de considerar resolvido. ============='
\echo ''
