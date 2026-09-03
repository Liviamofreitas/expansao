-- =============================================================================
-- SGDF — Critério de aceite da história F0-07
--   "No 1º dia útil, 15 ciclos abertos; exigência corporativa única compartilhada"
--
-- A RESOLUÇÃO (quais exigências existem, com que prazo) é verificada pela suíte
-- especificacao/materializacao/casos.json. Aqui verifica-se a PERSISTÊNCIA: a
-- contagem de ciclos e, sobretudo, que a exigência corporativa existe UMA vez e
-- é enxergada por todos os ciclos da empresa naquela competência.
--
-- A massa é FIXTURE, não dado de produção. Os 15 contratos-serviço vêm da
-- decisão D-02 (CAIXA em 3, BNB e TJCE em 2 cada) completada até 15 com um
-- desdobramento arbitrário — a composição real depende das pendências A03 e
-- A04. O que se testa é o comportamento com 15 contratos, não quais são.
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE OR REPLACE FUNCTION teste_ok(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE NOTICE 'PASSOU: %', descricao; END $$;

CREATE OR REPLACE FUNCTION teste_falhou(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'FALHOU: %', descricao; END $$;

-- -----------------------------------------------------------------------------
-- Fixture
-- -----------------------------------------------------------------------------
INSERT INTO empresa (id, razao_social, cnpj, criado_por)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', 'Prestador Fixture S.A.', '11222333000181', 'fixture');

INSERT INTO modalidade (codigo, nome, criado_por)
VALUES ('OUTSOURCING', 'Outsourcing', 'fixture')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO versao_matriz (id, numero, publicada_por, motivo)
VALUES ('aaaaaaaa-0000-0000-0000-000000000002', 'fixture-1.0', 'fixture', 'massa de teste');

INSERT INTO tipo_documental (id, codigo, nome, familia, escopo, evento, defasagem,
                             criticidade, sigilo, criado_por)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000010', 'CER.FIXTURE_CND', 'CND fixture',
     'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF',
     'BLOQUEANTE', 'PUBLICO_CLIENTE', 'fixture'),
    ('aaaaaaaa-0000-0000-0000-000000000011', 'OPE.FIXTURE_MEDICAO', 'Medição fixture',
     'Operação e medição', 'CONTRATO', 'MENSAL', 'M',
     'BLOQUEANTE', 'PUBLICO_CLIENTE', 'fixture');

-- 15 clientes/contratos-serviço. Composição arbitrária (A03/A04 abertas).
INSERT INTO cliente (id, nome, cnpj, esfera, criado_por)
SELECT ('bbbbbbbb-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       'Cliente Fixture ' || n, lpad((77000000000000 + n)::text, 14, '0'), 'PUBLICA', 'fixture'
FROM generate_series(1, 15) n;

INSERT INTO contrato_servico (id, cliente_id, numero, servico, modalidade_id, vigencia_ini,
                              pasta_origem, data_contratual_faturamento, calendario_uf,
                              empresa_id, criado_por)
SELECT ('cccccccc-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       ('bbbbbbbb-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       'CT-' || lpad(n::text, 4, '0'), 'PRINCIPAL',
       (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING'),
       '2025-01-01', '/fixture/CT-' || lpad(n::text, 4, '0'),
       '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE',
       'aaaaaaaa-0000-0000-0000-000000000001', 'fixture'
FROM generate_series(1, 15) n;

-- -----------------------------------------------------------------------------
-- Abertura dos ciclos da competência (cap. 7.1 passo 4: congela a versão)
-- -----------------------------------------------------------------------------
INSERT INTO ciclo (contrato_servico_id, competencia, status, versao_matriz_id, criado_por)
SELECT id, '2026-04', 'ABERTO', 'aaaaaaaa-0000-0000-0000-000000000002', 'fixture'
FROM contrato_servico WHERE criado_por = 'fixture';

DO $$
DECLARE n integer;
BEGIN
    SELECT count(*) INTO n FROM ciclo c
      JOIN contrato_servico cs ON cs.id = c.contrato_servico_id
     WHERE cs.criado_por = 'fixture' AND c.competencia = '2026-04';
    IF n <> 15 THEN
        PERFORM teste_falhou(format('F0-07: esperava 15 ciclos abertos, obteve %s', n));
    END IF;
    PERFORM teste_ok('F0-07 · 15 ciclos abertos na competência');
END $$;

-- Exigência de escopo CONTRATO: uma por ciclo.
INSERT INTO exigencia (ciclo_id, tipo_id, evento, status, prazo_calculado,
                       criticidade, responsavel, origem, criado_por)
SELECT c.id, 'aaaaaaaa-0000-0000-0000-000000000011', 'MENSAL', 'PENDENTE', '2026-05-08',
       'BLOQUEANTE', 'OPERACAO', 'MATRIZ', 'fixture'
FROM ciclo c JOIN contrato_servico cs ON cs.id = c.contrato_servico_id
WHERE cs.criado_por = 'fixture';

-- Exigência de escopo CORPORATIVO: UMA por (empresa, competência).
INSERT INTO exigencia (empresa_id, competencia, tipo_id, evento, status, prazo_calculado,
                       criticidade, responsavel, origem, criado_por)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '2026-04',
        'aaaaaaaa-0000-0000-0000-000000000010', 'MENSAL', 'PENDENTE', '2026-05-08',
        'BLOQUEANTE', 'FINANCEIRO', 'MATRIZ', 'fixture');

-- =============================================================================
-- O coração do critério: uma linha, quinze ciclos
-- =============================================================================
DO $$
DECLARE linhas integer; visoes integer; ciclos_cobertos integer;
BEGIN
    SELECT count(*) INTO linhas FROM exigencia
     WHERE empresa_id = 'aaaaaaaa-0000-0000-0000-000000000001' AND competencia = '2026-04';
    IF linhas <> 1 THEN
        PERFORM teste_falhou(format('F0-07: a exigência corporativa existe %s vezes, deveria existir 1', linhas));
    END IF;

    SELECT count(*), count(DISTINCT ciclo_id) INTO visoes, ciclos_cobertos
      FROM exigencia_do_ciclo
     WHERE procedencia = 'CORPORATIVA'
       AND tipo_id = 'aaaaaaaa-0000-0000-0000-000000000010';
    IF ciclos_cobertos <> 15 THEN
        PERFORM teste_falhou(format('F0-07: a exigência corporativa alcança %s ciclos, deveria alcançar 15', ciclos_cobertos));
    END IF;

    PERFORM teste_ok(format('F0-07 · exigência corporativa: 1 linha vista por %s ciclos', ciclos_cobertos));
END $$;

DO $$
DECLARE proprias integer;
BEGIN
    SELECT count(*) INTO proprias FROM exigencia_do_ciclo
     WHERE procedencia = 'PROPRIA' AND tipo_id = 'aaaaaaaa-0000-0000-0000-000000000011';
    IF proprias <> 15 THEN
        PERFORM teste_falhou(format('Cap. 7.1: escopo CONTRATO gerou %s exigências, deveria gerar 15', proprias));
    END IF;
    PERFORM teste_ok('Cap. 7.1 · escopo CONTRATO gera uma exigência por ciclo');
END $$;

DO $$
DECLARE total integer;
BEGIN
    SELECT count(*) INTO total FROM exigencia_do_ciclo
     WHERE ciclo_id = (SELECT c.id FROM ciclo c JOIN contrato_servico cs ON cs.id = c.contrato_servico_id
                        WHERE cs.numero = 'CT-0001' AND c.competencia = '2026-04');
    IF total <> 2 THEN
        PERFORM teste_falhou(format('F0-07: um ciclo deveria enxergar 2 exigências (1 própria + 1 corporativa), enxergou %s', total));
    END IF;
    PERFORM teste_ok('F0-07 · um ciclo enxerga as próprias e as corporativas pela mesma view');
END $$;

-- =============================================================================
-- Unicidade: é ela que faz valer o "satisfeita uma única vez"
-- =============================================================================
DO $$
BEGIN
    INSERT INTO exigencia (empresa_id, competencia, tipo_id, evento, status, prazo_calculado,
                           criticidade, responsavel, origem, criado_por)
    VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '2026-04',
            'aaaaaaaa-0000-0000-0000-000000000010', 'MENSAL', 'PENDENTE', '2026-05-08',
            'BLOQUEANTE', 'FINANCEIRO', 'MATRIZ', 'fixture');
    PERFORM teste_falhou('F0-07: exigência corporativa duplicada na mesma competência foi aceita');
EXCEPTION WHEN unique_violation THEN
    PERFORM teste_ok('F0-07 · exigência corporativa é única por (empresa, competência, tipo, evento)');
END $$;

DO $$
BEGIN
    INSERT INTO exigencia (empresa_id, competencia, tipo_id, evento, status, prazo_calculado,
                           criticidade, responsavel, origem, criado_por)
    VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '2026-05',
            'aaaaaaaa-0000-0000-0000-000000000010', 'MENSAL', 'PENDENTE', '2026-06-08',
            'BLOQUEANTE', 'FINANCEIRO', 'MATRIZ', 'fixture');
    PERFORM teste_ok('F0-07 · a mesma exigência corporativa em outra competência é outra linha');
END $$;

-- =============================================================================
-- Endereçamento exclusivo (V004)
-- =============================================================================
DO $$
BEGIN
    INSERT INTO exigencia (ciclo_id, empresa_id, competencia, tipo_id, evento, status,
                           prazo_calculado, criticidade, responsavel, origem, criado_por)
    VALUES ((SELECT id FROM ciclo LIMIT 1), 'aaaaaaaa-0000-0000-0000-000000000001', '2026-04',
            'aaaaaaaa-0000-0000-0000-000000000010', 'MENSAL', 'PENDENTE', '2026-05-08',
            'BLOQUEANTE', 'FINANCEIRO', 'MATRIZ', 'fixture');
    PERFORM teste_falhou('V004: exigência com os dois modos de endereçamento foi aceita');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('V004 · exigência tem exatamente um modo de endereçamento');
END $$;

DO $$
BEGIN
    INSERT INTO exigencia (tipo_id, evento, status, prazo_calculado, criticidade,
                           responsavel, origem, criado_por)
    VALUES ('aaaaaaaa-0000-0000-0000-000000000010', 'MENSAL', 'PENDENTE', '2026-05-08',
            'BLOQUEANTE', 'FINANCEIRO', 'MATRIZ', 'fixture');
    PERFORM teste_falhou('V004: exigência sem nenhum endereçamento foi aceita');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('V004 · exigência sem endereçamento é rejeitada');
END $$;

DO $$
DECLARE prof_id uuid;
BEGIN
    INSERT INTO profissional (matricula, nome, cpf_cifrado, cpf_hash, admissao, criado_por)
    VALUES ('FIX0001', 'Fulano Fixture', '\x00'::bytea, '\x01'::bytea, '2025-01-01', 'fixture')
    RETURNING id INTO prof_id;

    INSERT INTO exigencia (empresa_id, competencia, tipo_id, evento, profissional_id, status,
                           prazo_calculado, criticidade, responsavel, origem, criado_por)
    VALUES ('aaaaaaaa-0000-0000-0000-000000000001', '2026-07',
            'aaaaaaaa-0000-0000-0000-000000000010', 'MENSAL', prof_id, 'PENDENTE', '2026-08-08',
            'BLOQUEANTE', 'FINANCEIRO', 'MATRIZ', 'fixture');
    PERFORM teste_falhou('V004: exigência corporativa com profissional foi aceita');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('V004 · exigência corporativa não admite profissional (é por CNPJ)');
END $$;

-- =============================================================================
-- Contrato ativo exige empresa emitente (V004)
-- =============================================================================
DO $$
BEGIN
    INSERT INTO contrato_servico (cliente_id, numero, servico, modalidade_id, vigencia_ini,
                                  pasta_origem, data_contratual_faturamento, calendario_uf,
                                  ativo, criado_por)
    VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'CT-SEM-EMPRESA', 'X',
            (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING'), '2025-01-01', '/x',
            '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE', true, 'fixture');
    PERFORM teste_falhou('V004: contrato ativo sem empresa emitente foi aceito');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('V004 · contrato ativo exige empresa emitente');
END $$;

-- =============================================================================
-- Cap. 7.1 passo 4: a versão da matriz fica congelada no ciclo
-- =============================================================================
DO $$
DECLARE versoes integer;
BEGIN
    SELECT count(DISTINCT versao_matriz_id) INTO versoes
      FROM ciclo c JOIN contrato_servico cs ON cs.id = c.contrato_servico_id
     WHERE cs.criado_por = 'fixture';
    IF versoes <> 1 THEN
        PERFORM teste_falhou('Cap. 7.1: os ciclos da competência deveriam compartilhar a versão da matriz');
    END IF;
    PERFORM teste_ok('Cap. 7.1 · a versão da matriz é congelada no ciclo na abertura');
END $$;

ROLLBACK;
