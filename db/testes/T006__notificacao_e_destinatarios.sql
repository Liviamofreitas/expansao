-- =============================================================================
-- SGDF — F1-09 · notificação, destinatários e templates (migração V010)
--
-- O que importa aqui é a rede que sobrevive a qualquer caminho de código. Em
-- particular a unicidade diária, que a V001 declarava impor e não impunha.
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
    v_empresa  uuid;
    v_matriz   uuid;
    v_cliente  uuid;
    v_contrato uuid;
    v_ciclo    uuid;
    n          integer;
BEGIN
    INSERT INTO empresa (razao_social, cnpj, criado_por)
    VALUES ('Prestador T006 S.A.', '11222333000181', 'fixture') RETURNING id INTO v_empresa;

    INSERT INTO modalidade (codigo, nome, criado_por)
    VALUES ('OUTSOURCING', 'Outsourcing', 'fixture') ON CONFLICT (codigo) DO NOTHING;

    INSERT INTO versao_matriz (numero, publicada_por, motivo)
    VALUES ('t006-1.0', 'fixture', 'massa') RETURNING id INTO v_matriz;

    INSERT INTO cliente (nome, cnpj, esfera, criado_por)
    VALUES ('Cliente T006', '77000000000006', 'PUBLICA', 'fixture') RETURNING id INTO v_cliente;

    INSERT INTO contrato_servico (cliente_id, numero, servico, modalidade_id, vigencia_ini,
                                  pasta_origem, data_contratual_faturamento, calendario_uf,
                                  empresa_id, criado_por)
    VALUES (v_cliente, 'CT-T006', 'PRINCIPAL',
            (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING'), '2025-01-01',
            '/fixture/t006', '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE',
            v_empresa, 'fixture')
    RETURNING id INTO v_contrato;

    INSERT INTO ciclo (contrato_servico_id, competencia, status, versao_matriz_id, criado_por)
    VALUES (v_contrato, '2026-06', 'ABERTO', v_matriz, 'fixture') RETURNING id INTO v_ciclo;

    -- 1. A UNICIDADE QUE NÃO VALIA -------------------------------------------
    -- Cap. 11.2: "máximo 1 e-mail por área por ciclo por dia". Com `tipo` na
    -- chave, PREVENTIVA + COBRANCA + ESCALONAMENTO no mesmo dia passavam.
    INSERT INTO notificacao (ciclo_id, tipo, destinatario, conteudo_hash, template_versao,
                             modo_sombra, data_referencia)
    VALUES (v_ciclo, 'PREVENTIVA', 'ana@x.com', repeat('a', 64), '1.0', true, '2026-07-06');
    PERFORM teste_ok('F1-09 · o primeiro aviso do dia é aceito');

    BEGIN
        INSERT INTO notificacao (ciclo_id, tipo, destinatario, conteudo_hash, template_versao,
                                 modo_sombra, data_referencia)
        VALUES (v_ciclo, 'COBRANCA', 'ana@x.com', repeat('b', 64), '1.0', true, '2026-07-06');
        PERFORM teste_falhou('Cap. 11.2 · a mesma pessoa recebeu DOIS e-mails no mesmo dia');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('Cap. 11.2 · um e-mail por destinatário por ciclo por dia — '
                         'a V001 permitia três, ver achados § 32');
    END;

    INSERT INTO notificacao (ciclo_id, tipo, destinatario, conteudo_hash, template_versao,
                             modo_sombra, data_referencia)
    VALUES (v_ciclo, 'COBRANCA', 'ana@x.com', repeat('c', 64), '1.0', true, '2026-07-07');
    PERFORM teste_ok('Cap. 11.2 · mas no dia seguinte, sim');

    INSERT INTO notificacao (ciclo_id, tipo, destinatario, conteudo_hash, template_versao,
                             modo_sombra, data_referencia)
    VALUES (v_ciclo, 'COBRANCA', 'bruno@x.com', repeat('d', 64), '1.0', true, '2026-07-06');
    PERFORM teste_ok('Cap. 11.2 · e outra pessoa no mesmo dia também');

    -- 2. Destinatário: duas pessoas no mesmo papel é pergunta sem resposta ----
    INSERT INTO destinatario (contrato_servico_id, familia, papel, nome, email,
                              vigencia_ini, vigencia_fim, criado_por)
    VALUES (v_contrato, 'Folha', 'TITULAR', 'Ana', 'ana@x.com',
            '2025-01-01', '2026-06-30', 'fixture');
    PERFORM teste_ok('Cap. 11.2 · titular por contrato × família é aceito');

    INSERT INTO destinatario (contrato_servico_id, familia, papel, nome, email,
                              vigencia_ini, criado_por)
    VALUES (v_contrato, 'Folha', 'TITULAR', 'Bruno', 'bruno@x.com', '2026-07-01', 'fixture');
    PERFORM teste_ok('Cap. 11.2 · a sucessão sem sobreposição é aceita');

    BEGIN
        INSERT INTO destinatario (contrato_servico_id, familia, papel, nome, email,
                                  vigencia_ini, criado_por)
        VALUES (v_contrato, 'Folha', 'TITULAR', 'Carla', 'carla@x.com', '2026-06-01', 'fixture');
        PERFORM teste_falhou('Cap. 11.2 · duas titulares vigentes no mesmo dia foram aceitas');
    EXCEPTION WHEN exclusion_violation THEN
        PERFORM teste_ok('Cap. 11.2 · vigências sobrepostas no mesmo papel são recusadas — '
                         'a régua teria duas respostas e escolheria uma pela ordem física');
    END;

    -- O genérico (família nula) convive com o específico: são papéis diferentes
    -- da mesma pessoa em famílias diferentes.
    INSERT INTO destinatario (contrato_servico_id, familia, papel, nome, email,
                              vigencia_ini, criado_por)
    VALUES (v_contrato, NULL, 'GESTOR', 'Daniel', 'daniel@x.com', '2025-01-01', 'fixture');
    PERFORM teste_ok('Cap. 11.2 · GESTOR genérico (família nula) convive com o específico');

    BEGIN
        INSERT INTO destinatario (contrato_servico_id, familia, papel, nome, email,
                                  vigencia_ini, criado_por)
        VALUES (v_contrato, 'Fiscal', 'TITULAR', 'Erro', 'sem-arroba', '2025-01-01', 'fixture');
        PERFORM teste_falhou('Cap. 11.2 · e-mail sem @ foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 11.2 · endereço implausível é recusado no cadastro');
    END;

    -- 3. Templates: o que o cap. 11.2 proíbe, o cadastro recusa ---------------
    BEGIN
        INSERT INTO template_notificacao (momento, versao, assunto, corpo, vigente, criado_por)
        VALUES ('COBRANCA', '9.9', 'x', 'Segue em anexo o documento pendente.', false,
                'fixture');
        PERFORM teste_falhou('Cap. 11.2 · template que fala em anexo foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 11.2 · template que anuncia anexo é recusado — '
                         'só link autenticado com expiração');
    END;

    BEGIN
        INSERT INTO template_notificacao (momento, versao, assunto, corpo, vigente, criado_por)
        VALUES ('FECHAMENTO', '9.9', 'x', 'O ciclo está pronto.', false, 'fixture');
        PERFORM teste_falhou('Cap. 11.1 · fechamento sem o rodapé do ateste foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 11.1 · fechamento sem "não substitui o ateste" é recusado');
    END;

    BEGIN
        INSERT INTO template_notificacao (momento, versao, assunto, corpo, vigente, criado_por)
        VALUES ('COBRANCA', '9.9', 'x', 'Documentos vencidos: {itens}', true, 'fixture');
        PERFORM teste_falhou('Cap. 11.2 · dois templates vigentes para o mesmo momento');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('Cap. 11.2 · um template vigente por momento — dois seriam duas '
                         'mensagens possíveis e o hash deixaria de identificar o que foi dito');
    END;

    -- 4. A carga traz os cinco momentos --------------------------------------
    SELECT count(*) INTO n FROM template_notificacao WHERE vigente;
    IF n <> 5 THEN
        PERFORM teste_falhou(format('esperava 5 templates vigentes, achei %s', n));
    END IF;
    PERFORM teste_ok('Cap. 11.1 · a carga traz um template vigente para cada momento da régua');

    -- 5. O modo sombra nasce LIGADO ------------------------------------------
    SELECT count(*) INTO n FROM parametro
     WHERE chave = 'notificacao.modo_sombra' AND escopo = 'GLOBAL' AND valor = 'true'::jsonb;
    IF n <> 1 THEN
        PERFORM teste_falhou('F1-09 · o modo sombra não está ligado por padrão');
    END IF;
    PERFORM teste_ok('F1-09 · o modo sombra nasce ligado — sistema recém-instalado não cobra '
                     'ninguém sem alguém ter decidido');
END $$;

ROLLBACK;
