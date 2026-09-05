-- =============================================================================
-- SGDF — F0-09 · decisão da exceção (migração V012)
--
-- A rede que sobrevive a qualquer caminho de código. Medida: com esta rede e
-- sem a checagem em Java, o critério de aceite da F0-09 continua valendo —
-- ver docs/ACHADOS-MASSA-REAL.md § 35.
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE OR REPLACE FUNCTION teste_ok(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE NOTICE 'PASSOU: %', descricao; END $$;

CREATE OR REPLACE FUNCTION teste_falhou(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'FALHOU: %', descricao; END $$;

DO $$
DECLARE
    v_empresa uuid; v_matriz uuid; v_cliente uuid; v_contrato uuid; v_ciclo uuid;
    v_tipo uuid; v_exig uuid; v_exc uuid;
    MOTIVO constant text := 'documento inexistente por decisao judicial nos autos 123/26';
BEGIN
    INSERT INTO empresa (razao_social, cnpj, criado_por)
    VALUES ('Prestador T008', '11222333000181', 'fixture') RETURNING id INTO v_empresa;
    INSERT INTO modalidade (codigo, nome, criado_por)
    VALUES ('OUTSOURCING', 'Outsourcing', 'fixture') ON CONFLICT (codigo) DO NOTHING;
    INSERT INTO versao_matriz (numero, publicada_por, motivo)
    VALUES ('t008-1.0', 'fixture', 'base') RETURNING id INTO v_matriz;
    INSERT INTO cliente (nome, cnpj, esfera, criado_por)
    VALUES ('Cliente T008', '77000000000008', 'PUBLICA', 'fixture') RETURNING id INTO v_cliente;
    INSERT INTO contrato_servico (cliente_id, numero, servico, modalidade_id, vigencia_ini,
                                  pasta_origem, data_contratual_faturamento, calendario_uf,
                                  empresa_id, criado_por)
    VALUES (v_cliente, 'CT-T008', 'PRINCIPAL',
            (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING'), '2025-01-01', '/t008',
            '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE', v_empresa, 'fixture')
    RETURNING id INTO v_contrato;
    INSERT INTO ciclo (contrato_servico_id, competencia, status, versao_matriz_id, criado_por)
    VALUES (v_contrato, '2026-06', 'ABERTO', v_matriz, 'fixture') RETURNING id INTO v_ciclo;
    INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento, defasagem,
                                 criticidade, sigilo, criado_por)
    VALUES ('TST.T008', 'Tipo T008', 'Teste', 'CONTRATO', 'MENSAL', 'M', 'BLOQUEANTE',
            'INTERNO', 'fixture') RETURNING id INTO v_tipo;
    INSERT INTO exigencia (ciclo_id, tipo_id, evento, status, prazo_calculado, criticidade,
                           responsavel, origem, criado_por)
    VALUES (v_ciclo, v_tipo, 'MENSAL', 'PENDENTE', '2026-07-05', 'BLOQUEANTE', 'AP', 'MATRIZ',
            'fixture') RETURNING id INTO v_exig;

    -- 1. SoD, a rede do banco --------------------------------------------------
    INSERT INTO excecao (exigencia_id, motivo, solicitante)
    VALUES (v_exig, MOTIVO, 'joao.silva') RETURNING id INTO v_exc;
    PERFORM teste_ok('F0-09 · a solicitação é aceita e nasce SOLICITADA');

    BEGIN
        UPDATE excecao SET situacao = 'APROVADA', aprovador = 'joao.silva',
                           aprovado_em = now() WHERE id = v_exc;
        PERFORM teste_falhou('F0-09 · o solicitante aprovou a própria exceção');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-09 · aprovador igual ao solicitante é recusado (SoD) — '
                         'a rede que vale mesmo se o serviço esquecer');
    END;

    -- 2. Decisão sem quem/quando ----------------------------------------------
    BEGIN
        UPDATE excecao SET situacao = 'APROVADA' WHERE id = v_exc;
        PERFORM teste_falhou('F0-09 · decisão sem decisor foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 16 · decidida exige quem decidiu e quando');
    END;

    -- 3. Negativa sem motivo ---------------------------------------------------
    BEGIN
        UPDATE excecao SET situacao = 'NEGADA', aprovador = 'maria.andrade',
                           aprovado_em = now() WHERE id = v_exc;
        PERFORM teste_falhou('F0-09 · negativa sem motivo foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-09 · negar sem motivo é recusado — quem pedir de novo '
                         'precisa saber o que faltava');
    END;

    UPDATE excecao SET situacao = 'NEGADA', aprovador = 'maria.andrade', aprovado_em = now(),
                       motivo_decisao = 'a decisao judicial citada nao alcanca esta competencia'
     WHERE id = v_exc;
    PERFORM teste_ok('F0-09 · negativa com motivo é aceita');

    -- 4. Uma exceção em aberto por exigência -----------------------------------
    -- Depois de negada, pedir de novo é legítimo: a anterior não está mais aberta.
    INSERT INTO excecao (exigencia_id, motivo, solicitante)
    VALUES (v_exig, 'nova solicitacao agora com a certidao de objeto e pe anexada',
            'joao.silva');
    PERFORM teste_ok('F0-09 · depois de negada, cabe pedir de novo');

    BEGIN
        INSERT INTO excecao (exigencia_id, motivo, solicitante)
        VALUES (v_exig, 'terceira solicitacao com a primeira ainda em aberto', 'outro.pedinte');
        PERFORM teste_falhou('F0-09 · duas exceções em aberto para a mesma exigência');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('F0-09 · uma exceção em aberto por exigência — duas produzem duas '
                         'decisões possíveis para o mesmo fato');
    END;

    -- 5. Motivo da solicitação substantivo (V001, ainda vale) ------------------
    BEGIN
        INSERT INTO excecao (exigencia_id, motivo, solicitante)
        VALUES (v_exig, 'nao tem', 'joao.silva');
        PERFORM teste_falhou('F0-09 · motivo curto foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-09 · o motivo da solicitação segue exigindo substância');
    END;
END $$;

ROLLBACK;
