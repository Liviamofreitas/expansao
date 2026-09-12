-- =============================================================================
-- SGDF — RA-07 · execução de job (migração V018)
--
-- A rede que impede a linha meio escrita. Uma execução que terminou sem dizer
-- como é indistinguível de uma em andamento — e o alerta de job travado passaria
-- a apontar para sempre algo que já acabou.
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE OR REPLACE FUNCTION teste_ok(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE NOTICE 'PASSOU: %', descricao; END $$;

CREATE OR REPLACE FUNCTION teste_falhou(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'FALHOU: %', descricao; END $$;

DO $$
DECLARE v_id bigint;
BEGIN
    INSERT INTO execucao_de_job (job, instancia) VALUES ('VARREDURA_COMPLETA', 'i-1')
    RETURNING id INTO v_id;
    PERFORM teste_ok('RA-07 · a execução abre sem resultado — ela está em andamento');

    -- 1. Terminar sem dizer como -------------------------------------------------
    BEGIN
        UPDATE execucao_de_job SET terminada_em = now() WHERE id = v_id;
        PERFORM teste_falhou('RA-07 · execução terminada sem resultado foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('RA-07 · terminar sem resultado é recusado — a linha ficaria '
                         'indistinguível de uma execução em andamento');
    END;

    -- 2. Resultado sem terminar --------------------------------------------------
    BEGIN
        UPDATE execucao_de_job SET resultado = 'SUCESSO', itens = 0 WHERE id = v_id;
        PERFORM teste_falhou('RA-07 · resultado sem término foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('RA-07 · resultado sem término também é recusado');
    END;

    -- 3. SUCESSO exige a contagem ------------------------------------------------
    BEGIN
        UPDATE execucao_de_job SET terminada_em = now(), resultado = 'SUCESSO'
        WHERE id = v_id;
        PERFORM teste_falhou('RA-07 · SUCESSO sem contagem foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('RA-07 · SUCESSO exige a contagem — é ela que distingue '
                         '"rodou e achou zero" de "rodou", e zero é resposta legítima');
    END;

    -- 4. FALHA exige o motivo ----------------------------------------------------
    BEGIN
        UPDATE execucao_de_job SET terminada_em = now(), resultado = 'FALHA' WHERE id = v_id;
        PERFORM teste_falhou('RA-07 · FALHA sem detalhe foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('RA-07 · FALHA exige detalhe — sem ele, alguém procura sem nada');
    END;

    -- 5. O caminho legítimo ------------------------------------------------------
    UPDATE execucao_de_job SET terminada_em = now(), resultado = 'SUCESSO', itens = 0
    WHERE id = v_id;
    PERFORM teste_ok('RA-07 · SUCESSO com zero item é aceito — rodar e não achar nada '
                     'é um fato, e é diferente de linha nenhuma');

    INSERT INTO execucao_de_job (job, terminada_em, resultado, detalhe, instancia)
    VALUES ('CONCILIACAO', now(), 'FALHA', 'o WebDAV não respondeu', 'i-1');
    PERFORM teste_ok('RA-07 · FALHA com detalhe é aceita');

    INSERT INTO execucao_de_job (job, terminada_em, resultado, instancia)
    VALUES ('CONCILIACAO', now(), 'CONCORRENTE', 'i-2');
    PERFORM teste_ok('RA-07 · CONCORRENTE não exige contagem nem detalhe — ela não '
                     'executou nada, e registrar a disputa já é a informação');

    -- 6. Resultado fora do conjunto ----------------------------------------------
    BEGIN
        INSERT INTO execucao_de_job (job, terminada_em, resultado, itens, instancia)
        VALUES ('CONCILIACAO', now(), 'QUASE', 0, 'i-1');
        PERFORM teste_falhou('RA-07 · resultado desconhecido foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('RA-07 · resultado fora de SUCESSO/FALHA/CONCORRENTE é recusado');
    END;
END $$;

ROLLBACK;
