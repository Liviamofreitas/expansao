-- =============================================================================
-- SGDF — F0-05 · rascunho da matriz (migração V011)
--
-- A lógica da publicação tem testes em Java. Aqui, a rede que sobrevive a
-- qualquer caminho de código: o que o banco recusa mesmo que o serviço erre.
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
    v_versao   uuid;
    v_tipo     uuid;
    v_mod      uuid;
    v_regra    uuid;
    v_rascunho uuid;
BEGIN
    INSERT INTO versao_matriz (numero, publicada_por, motivo)
    VALUES ('t007-1.0', 'fixture', 'base') RETURNING id INTO v_versao;

    INSERT INTO modalidade (codigo, nome, criado_por)
    VALUES ('OUTSOURCING', 'Outsourcing', 'fixture') ON CONFLICT (codigo) DO NOTHING;
    SELECT id INTO v_mod FROM modalidade WHERE codigo = 'OUTSOURCING';

    INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento, defasagem,
                                 criticidade, sigilo, criado_por)
    VALUES ('TST.T007', 'Tipo T007', 'Teste', 'CONTRATO', 'MENSAL', 'M',
            'BLOQUEANTE', 'INTERNO', 'fixture') RETURNING id INTO v_tipo;

    INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id, obrigatoriedade,
                                     prazo, responsavel_titular, vigencia_ini,
                                     versao_matriz_id, criado_por)
    VALUES (v_tipo, 'MODALIDADE', v_mod, 'OBRIGATORIO',
            '{"ancora":"INICIO_COMPETENCIA","tipo_dia":"UTIL","offset":5}', 'AP',
            '2025-01-01', v_versao, 'fixture') RETURNING id INTO v_regra;

    -- 1. Motivo substantivo -------------------------------------------------
    BEGIN
        INSERT INTO rascunho_matriz (numero_proposto, motivo, base_versao_id, criado_por)
        VALUES ('t007-2.0', 'ajuste', v_versao, 'fixture');
        PERFORM teste_falhou('F0-05 · rascunho com motivo vago foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-05 · motivo vago é recusado — o histórico da versão vive dele');
    END;

    INSERT INTO rascunho_matriz (numero_proposto, motivo, base_versao_id, criado_por)
    VALUES ('t007-2.0', 'revisão do prazo da CND após mudança no calendário fiscal',
            v_versao, 'fixture')
    RETURNING id INTO v_rascunho;
    PERFORM teste_ok('F0-05 · rascunho com motivo substantivo é aceito');

    -- 2. Publicação incompleta ----------------------------------------------
    BEGIN
        UPDATE rascunho_matriz SET situacao = 'PUBLICADO' WHERE id = v_rascunho;
        PERFORM teste_falhou('F0-05 · publicação sem quem/quando/qual versão foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 16 · publicado exige publicador, data e a versão produzida');
    END;

    BEGIN
        UPDATE rascunho_matriz SET situacao = 'DESCARTADO' WHERE id = v_rascunho;
        PERFORM teste_falhou('F0-05 · descarte sem motivo foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-05 · descartar um rascunho também exige motivo');
    END;

    -- 3. Itens: ALTERAR precisa dizer o quê ----------------------------------
    BEGIN
        INSERT INTO rascunho_regra (rascunho_id, acao, tipo_id, alvo, alvo_modalidade_id,
                                    obrigatoriedade, prazo, responsavel_titular,
                                    vigencia_ini, criado_por)
        VALUES (v_rascunho, 'ALTERAR', v_tipo, 'MODALIDADE', v_mod, 'OBRIGATORIO',
                '{"ancora":"INICIO_COMPETENCIA","tipo_dia":"UTIL","offset":9}', 'AP',
                '2025-01-01', 'fixture');
        PERFORM teste_falhou('F0-05 · ALTERAR sem regra de origem foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-05 · ALTERAR sem dizer QUAL regra é recusado');
    END;

    BEGIN
        INSERT INTO rascunho_regra (rascunho_id, acao, regra_origem_id, tipo_id, alvo,
                                    alvo_modalidade_id, obrigatoriedade, prazo,
                                    responsavel_titular, vigencia_ini, criado_por)
        VALUES (v_rascunho, 'INCLUIR', v_regra, v_tipo, 'MODALIDADE', v_mod, 'OBRIGATORIO',
                '{"ancora":"INICIO_COMPETENCIA","tipo_dia":"UTIL","offset":9}', 'AP',
                '2025-01-01', 'fixture');
        PERFORM teste_falhou('F0-05 · INCLUIR apontando para uma regra existente foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-05 · INCLUIR não aponta para regra — não há o que apontar');
    END;

    -- 4. O item precisa produzir uma regra VÁLIDA ---------------------------
    BEGIN
        INSERT INTO rascunho_regra (rascunho_id, acao, regra_origem_id, tipo_id, alvo,
                                    alvo_modalidade_id, obrigatoriedade, prazo,
                                    responsavel_titular, vigencia_ini, criado_por)
        VALUES (v_rascunho, 'ALTERAR', v_regra, v_tipo, 'MODALIDADE', v_mod, 'OBRIGATORIO',
                '{"ancora":"MEIO_DO_MES","tipo_dia":"UTIL","offset":9}', 'AP',
                '2025-01-01', 'fixture');
        PERFORM teste_falhou('F0-05 · item com âncora inexistente foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-05 · prazo malformado é recusado NO RASCUNHO — descobrir isso '
                         'ao publicar seria descobrir tarde demais');
    END;

    BEGIN
        INSERT INTO rascunho_regra (rascunho_id, acao, regra_origem_id, tipo_id, alvo,
                                    alvo_modalidade_id, alvo_contrato_id, obrigatoriedade,
                                    prazo, responsavel_titular, vigencia_ini, criado_por)
        VALUES (v_rascunho, 'ALTERAR', v_regra, v_tipo, 'MODALIDADE', v_mod,
                NULL, 'OBRIGATORIO',
                '{"ancora":"INICIO_COMPETENCIA","tipo_dia":"UTIL","offset":9}', NULL,
                '2025-01-01', 'fixture');
        PERFORM teste_falhou('F0-05 · item sem responsável foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('F0-05 · item sem responsável é recusado — regra_exigibilidade o exige');
    END;

    -- 5. REMOVER não precisa dos campos: não produz regra --------------------
    INSERT INTO rascunho_regra (rascunho_id, acao, regra_origem_id, criado_por)
    VALUES (v_rascunho, 'REMOVER', v_regra, 'fixture');
    PERFORM teste_ok('F0-05 · REMOVER dispensa os campos da regra — ela não é produzida');

    -- 6. A mesma regra tocada duas vezes é ambiguidade -----------------------
    BEGIN
        INSERT INTO rascunho_regra (rascunho_id, acao, regra_origem_id, tipo_id, alvo,
                                    alvo_modalidade_id, obrigatoriedade, prazo,
                                    responsavel_titular, vigencia_ini, criado_por)
        VALUES (v_rascunho, 'ALTERAR', v_regra, v_tipo, 'MODALIDADE', v_mod, 'OBRIGATORIO',
                '{"ancora":"INICIO_COMPETENCIA","tipo_dia":"UTIL","offset":9}', 'AP',
                '2025-01-01', 'fixture');
        PERFORM teste_falhou('F0-05 · alterar e remover a mesma regra no mesmo rascunho');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('F0-05 · a mesma regra tocada duas vezes no rascunho é recusada — '
                         'alterar e remover a mesma linha não tem resultado definido');
    END;

    -- 7. Uma versão publicada tem um rascunho de origem, no máximo -----------
    UPDATE rascunho_matriz
       SET situacao = 'PUBLICADO', publicado_em = now(), publicado_por = 'fixture',
           versao_publicada_id = v_versao
     WHERE id = v_rascunho;
    PERFORM teste_ok('F0-05 · publicação completa é aceita');

    BEGIN
        INSERT INTO rascunho_matriz (numero_proposto, motivo, base_versao_id, situacao,
                                     publicado_em, publicado_por, versao_publicada_id,
                                     criado_por)
        VALUES ('t007-3.0', 'outra proposta que aponta para a mesma versão publicada',
                v_versao, 'PUBLICADO', now(), 'fixture', v_versao, 'fixture');
        PERFORM teste_falhou('F0-05 · dois rascunhos produziram a mesma versão');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('F0-05 · uma versão tem um rascunho de origem — dois significariam '
                         'que ela foi produzida duas vezes');
    END;
END $$;

ROLLBACK;
