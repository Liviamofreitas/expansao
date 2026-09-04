-- =============================================================================
-- SGDF — F1-06 · candidatura de triagem (migração V009)
--
-- A lógica das três decisões tem testes em Java, contra o banco. Aqui interessa
-- só a rede que sobrevive a qualquer caminho de código: o que o banco recusa
-- mesmo que a aplicação erre, e o recorte por contrato que a view sustenta.
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
    v_outro    uuid;
    v_ciclo    uuid;
    v_tipo     uuid;
    v_exig     uuid;
    v_doc      uuid;
    v_cand     uuid;
    n          integer;
BEGIN
    -- Fixture -----------------------------------------------------------------
    INSERT INTO empresa (razao_social, cnpj, criado_por)
    VALUES ('Prestador T005 S.A.', '11222333000181', 'fixture') RETURNING id INTO v_empresa;

    INSERT INTO modalidade (codigo, nome, criado_por)
    VALUES ('OUTSOURCING', 'Outsourcing', 'fixture') ON CONFLICT (codigo) DO NOTHING;

    INSERT INTO versao_matriz (numero, publicada_por, motivo)
    VALUES ('t005-1.0', 'fixture', 'massa') RETURNING id INTO v_matriz;

    INSERT INTO cliente (nome, cnpj, esfera, criado_por)
    VALUES ('Cliente T005', '77000000000005', 'PUBLICA', 'fixture') RETURNING id INTO v_cliente;

    INSERT INTO contrato_servico (cliente_id, numero, servico, modalidade_id, vigencia_ini,
                                  pasta_origem, data_contratual_faturamento, calendario_uf,
                                  empresa_id, criado_por)
    VALUES (v_cliente, 'CT-T005-A', 'PRINCIPAL',
            (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING'), '2025-01-01',
            '/fixture/t005a', '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE',
            v_empresa, 'fixture')
    RETURNING id INTO v_contrato;

    INSERT INTO contrato_servico (cliente_id, numero, servico, modalidade_id, vigencia_ini,
                                  pasta_origem, data_contratual_faturamento, calendario_uf,
                                  empresa_id, criado_por)
    VALUES (v_cliente, 'CT-T005-B', 'PRINCIPAL',
            (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING'), '2025-01-01',
            '/fixture/t005b', '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE',
            v_empresa, 'fixture')
    RETURNING id INTO v_outro;

    INSERT INTO ciclo (contrato_servico_id, competencia, status, versao_matriz_id, criado_por)
    VALUES (v_contrato, '2026-06', 'ABERTO', v_matriz, 'fixture') RETURNING id INTO v_ciclo;

    INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento, defasagem,
                                 criticidade, sigilo, criado_por)
    VALUES ('TST.T005', 'Tipo T005', 'Teste', 'CONTRATO', 'MENSAL', 'M',
            'BLOQUEANTE', 'PESSOAL', 'fixture') RETURNING id INTO v_tipo;

    INSERT INTO exigencia (ciclo_id, tipo_id, evento, status, prazo_calculado, criticidade,
                           responsavel, origem, criado_por)
    VALUES (v_ciclo, v_tipo, 'MENSAL', 'EM_TRIAGEM', '2026-07-05', 'BLOQUEANTE',
            'AP', 'MATRIZ', 'fixture') RETURNING id INTO v_exig;

    INSERT INTO documento (origem, caminho, nome_arquivo, hash_sha256, tamanho, mime_real,
                           formato, status_triagem, criado_por)
    VALUES ('OWNCLOUD', '/fixture/t005a/06', '1041601__CONTRACHEQUE.pdf',
            repeat('a', 64), 1024, 'application/pdf', 'pdf', 'PENDENTE', 'fixture')
    RETURNING id INTO v_doc;

    -- 1. Candidatura aberta bem formada ---------------------------------------
    INSERT INTO candidatura (exigencia_id, documento_id, tipo_proposto_id, score, motivo,
                             situacao)
    VALUES (v_exig, v_doc, v_tipo, 0.720, 'margem de 0,04 sobre o segundo colocado', 'ABERTA')
    RETURNING id INTO v_cand;
    PERFORM teste_ok('F1-06 · candidatura na faixa de triagem é aceita');

    -- 2. Decisão sem quem/quando não é decisão --------------------------------
    BEGIN
        UPDATE candidatura SET situacao = 'CONFIRMADA' WHERE id = v_cand;
        PERFORM teste_falhou('F1-06 · decisão sem decidida_em/decidida_por foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 16 · decisão sem ator e sem data é recusada');
    END;

    -- 3. Recusa sem motivo devolveria a exigência sem dizer por quê -----------
    BEGIN
        UPDATE candidatura SET situacao = 'ILEGIVEL', decidida_em = now(),
                               decidida_por = 'fulano'
         WHERE id = v_cand;
        PERFORM teste_falhou('F1-06 · ilegível sem motivo foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 12 · ilegível sem motivo é recusado — quem reentrega '
                         'precisa saber o que corrigir');
    END;

    BEGIN
        UPDATE candidatura SET situacao = 'ILEGIVEL', decidida_em = now(),
                               decidida_por = 'fulano', decisao_motivo = '   '
         WHERE id = v_cand;
        PERFORM teste_falhou('F1-06 · motivo em branco foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 12 · motivo só com espaços não é motivo');
    END;

    -- 4. Confirmar não exige motivo — concordar não precisa de justificativa ---
    UPDATE candidatura SET situacao = 'CONFIRMADA', decidida_em = now(),
                           decidida_por = 'fulano'
     WHERE id = v_cand;
    PERFORM teste_ok('F1-06 · confirmar não exige motivo — concordar não é corrigir');

    -- 5. Confirmada sai da fila (critério de aceite: "tira da fila") ----------
    SELECT count(*) INTO n FROM fila_de_triagem WHERE candidatura_id = v_cand;
    IF n <> 0 THEN
        PERFORM teste_falhou('F1-06 · candidatura decidida continua na fila');
    END IF;
    PERFORM teste_ok('F1-06 · confirmar tira da fila');

    -- 6. Score fora de [0;1] não é score --------------------------------------
    BEGIN
        INSERT INTO candidatura (exigencia_id, documento_id, tipo_proposto_id, score,
                                 motivo, situacao)
        VALUES (v_exig, v_doc, v_tipo, 1.500, 'x', 'ABERTA');
        PERFORM teste_falhou('F1-06 · score acima de 1 foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 8.3 · score fora de [0;1] é recusado');
    END;

    -- 7. A mesma dupla exigência × documento não vira duas candidaturas -------
    BEGIN
        INSERT INTO candidatura (exigencia_id, documento_id, tipo_proposto_id, score,
                                 motivo, situacao)
        VALUES (v_exig, v_doc, v_tipo, 0.800, 'x', 'ABERTA');
        PERFORM teste_falhou('F1-06 · candidatura duplicada foi aceita');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('F1-06 · a mesma dupla exigência × documento candidata uma vez só');
    END;

    -- 8. O RECORTE. A fila tem contrato — é isso que fecha o RA-01. -----------
    UPDATE candidatura SET situacao = 'ABERTA', decidida_em = NULL, decidida_por = NULL,
                           decisao_motivo = NULL
     WHERE id = v_cand;

    SELECT count(*) INTO n FROM fila_de_triagem
     WHERE contrato_servico_id = v_contrato AND candidatura_id = v_cand;
    IF n <> 1 THEN
        PERFORM teste_falhou('RA-01 · a fila não devolve o contrato da candidatura');
    END IF;
    PERFORM teste_ok('RA-01 · a fila de triagem tem contrato, e o recorte do cap. 15.1 '
                     'tem sobre o que incidir');

    SELECT count(*) INTO n FROM fila_de_triagem WHERE contrato_servico_id = v_outro;
    IF n <> 0 THEN
        PERFORM teste_falhou('RA-01 · a fila de um contrato mostra item de outro');
    END IF;
    PERFORM teste_ok('RA-01 · quem só enxerga o outro contrato não vê este item');

    -- 9. Alias de triagem: a unicidade é global (achado E-02) -----------------
    INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado, origem, criado_por)
    VALUES (v_tipo, '1041601__CONTRACHEQUE.pdf', 'contracheque_t005', 'TRIAGEM', 'fulano');
    PERFORM teste_ok('F1-06 · a confirmação grava alias com origem TRIAGEM');

    BEGIN
        INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado, origem, criado_por)
        VALUES ((SELECT id FROM tipo_documental WHERE codigo <> 'TST.T005' LIMIT 1),
                'outro.pdf', 'contracheque_t005', 'TRIAGEM', 'fulano');
        PERFORM teste_falhou('F1-06 · o mesmo alias foi aceito para dois tipos');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('E-02 · um alias não serve a dois tipos — o determinismo da '
                         'classificação depende disso');
    END;
END $$;

ROLLBACK;
