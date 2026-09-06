-- =============================================================================
-- SGDF — F3-03 e F3-04 · marcos do ciclo (migração V016)
--
-- A rede que um UPDATE manual em produção não contorna. O `EstadoDoCiclo` do
-- cap. 6.2 impõe a ORDEM das transições e não é visto por quem escreve SQL à
-- mão; estas restrições são o que resta nesse caminho.
--
-- Medida: com estas restrições e sem a guarda em Java, um ciclo não chega a
-- FATURADO sem os seus marcos — ver docs/ACHADOS-MASSA-REAL.md § 41.
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
BEGIN
    INSERT INTO empresa (razao_social, cnpj, criado_por)
    VALUES ('Prestador T009', '11222333000190', 'fixture') RETURNING id INTO v_empresa;
    INSERT INTO modalidade (codigo, nome, criado_por)
    VALUES ('OUTSOURCING', 'Outsourcing', 'fixture') ON CONFLICT (codigo) DO NOTHING;
    INSERT INTO versao_matriz (numero, publicada_por, motivo)
    VALUES ('t009-1.0', 'fixture', 'base') RETURNING id INTO v_matriz;
    INSERT INTO cliente (nome, cnpj, esfera, criado_por)
    VALUES ('Cliente T009', '77000000000009', 'PUBLICA', 'fixture') RETURNING id INTO v_cliente;
    INSERT INTO contrato_servico (cliente_id, numero, servico, modalidade_id, vigencia_ini,
                                  pasta_origem, data_contratual_faturamento, calendario_uf,
                                  empresa_id, criado_por)
    VALUES (v_cliente, 'CT-T009', 'PRINCIPAL',
            (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING'), '2025-01-01', '/t009',
            '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE', v_empresa, 'fixture')
    RETURNING id INTO v_contrato;
    INSERT INTO ciclo (contrato_servico_id, competencia, status, versao_matriz_id, criado_por)
    VALUES (v_contrato, '2026-06', 'ABERTO', v_matriz, 'fixture') RETURNING id INTO v_ciclo;
    PERFORM teste_ok('Cap. 6.2 · o ciclo nasce ABERTO, sem marco nenhum');

    -- 1. ATESTADO sem ateste ---------------------------------------------------
    BEGIN
        UPDATE ciclo SET status = 'ATESTADO' WHERE id = v_ciclo;
        PERFORM teste_falhou('Cap. 6.2 · ATESTADO foi aceito sem ateste_em');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 6.2 · ATESTADO sem ateste_em é recusado pelo banco — '
                         'é de ateste_em que o D+3 conta');
    END;

    -- 2. FATURADO sem ateste — o caso que some do indicador --------------------
    BEGIN
        UPDATE ciclo SET status = 'FATURADO', nf_emitida_em = now() WHERE id = v_ciclo;
        PERFORM teste_falhou('Cap. 21 · FATURADO sem ateste_em foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 21 · FATURADO sem ateste_em é recusado — um ciclo assim sai '
                         'do numerador E do denominador, e o percentual melhora sozinho');
    END;

    -- 3. FATURADO sem NF -------------------------------------------------------
    UPDATE ciclo SET ateste_em = now() - INTERVAL '5 days', ateste_forma = 'e-mail'
    WHERE id = v_ciclo;
    BEGIN
        UPDATE ciclo SET status = 'FATURADO' WHERE id = v_ciclo;
        PERFORM teste_falhou('Cap. 6.2 · FATURADO foi aceito sem nf_emitida_em');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 6.2 · FATURADO sem nf_emitida_em é recusado — "NF emitida" '
                         'é o que o estado afirma');
    END;

    -- 4. NF anterior ao ateste -------------------------------------------------
    BEGIN
        UPDATE ciclo SET nf_emitida_em = now() - INTERVAL '10 days' WHERE id = v_ciclo;
        PERFORM teste_falhou('Cap. 21 · NF anterior ao ateste foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 21 · NF anterior ao ateste é recusada — o intervalo negativo '
                         'satisfaz "dentro de D+3" com folga que não existe');
    END;

    -- 5. O caminho legítimo continua aberto ------------------------------------
    UPDATE ciclo SET nf_emitida_em = now() - INTERVAL '3 days', status = 'FATURADO'
    WHERE id = v_ciclo;
    PERFORM teste_ok('Cap. 6.2 · com ateste e NF na ordem certa, FATURADO é aceito');

    UPDATE ciclo SET status = 'FECHADO', fechado_em = now() WHERE id = v_ciclo;
    PERFORM teste_ok('Cap. 6.2 · e FECHADO herda a exigência dos dois marcos');

    -- 6. O que as restrições NÃO garantem, dito explicitamente ------------------
    --
    -- Um CHECK não enxerga o valor anterior da linha: ABERTO → FECHADO passa
    -- desde que os marcos estejam lá. A ORDEM é do EstadoDoCiclo, e afirmar isto
    -- aqui evita que alguém leia a V016 como se fosse a máquina de estados.
    UPDATE ciclo SET status = 'ABERTO', ateste_em = NULL, ateste_forma = NULL,
                     nf_emitida_em = NULL, fechado_em = NULL WHERE id = v_ciclo;
    UPDATE ciclo SET status = 'FECHADO', ateste_em = now() - INTERVAL '2 days',
                     ateste_forma = 'e-mail', nf_emitida_em = now() WHERE id = v_ciclo;
    PERFORM teste_ok('Cap. 6.2 · o banco NÃO impede ABERTO → FECHADO num salto: a ordem '
                     'das transições vive no EstadoDoCiclo, não num CHECK');
END $$;

ROLLBACK;
