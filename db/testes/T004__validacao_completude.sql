-- =============================================================================
-- SGDF — V8 · completude de campos essenciais (achado A12)
--
-- Verifica o que a migração V007 impõe no banco. O validador em Java tem os
-- seus próprios testes; aqui interessa a rede de segurança que sobrevive a
-- qualquer caminho de código: cadastro mal formado não entra, e reprovação sem
-- motivo não é gravada.
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
    v_tipo_id  uuid;
    v_regra_id uuid;
    v_doc_id   uuid;
    n          integer;
BEGIN
    -- Fixture próprio, para não depender da carga nem sujá-la.
    INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento, defasagem,
                                 criticidade, sigilo, repositorio_mestre, criado_por)
    VALUES ('TST.V8', 'Tipo de teste V8', 'Teste', 'CORPORATIVO', 'MENSAL', 'M',
            'BLOQUEANTE', 'INTERNO', 'OWNCLOUD', 'teste')
    RETURNING id INTO v_tipo_id;

    -- 1. Cadastro bem formado é aceito -------------------------------------
    INSERT INTO regra_reconhecimento (tipo_id, ancoras, campos, campos_essenciais, criado_por)
    VALUES (v_tipo_id, '[]'::jsonb,
            '[{"nome": "valor", "padrao": "valor:\\s*([\\d.,]+)", "grupo": 1},
              {"nome": "data",  "padrao": "data:\\s*(\\d{2}/\\d{2}/\\d{4})", "grupo": 1}]'::jsonb,
            '[{"campo": "valor", "formato": "valor",
               "motivo": "sem valor o comprovante não pareia com a guia (R01)"}]'::jsonb,
            'teste')
    RETURNING id INTO v_regra_id;
    PERFORM teste_ok('V8 · regra com campos essenciais bem formados é aceita');

    -- 2. Campo essencial que a regra não sabe extrair é recusado -----------
    BEGIN
        INSERT INTO regra_reconhecimento (tipo_id, ancoras, campos, campos_essenciais,
                                          versao, criado_por)
        VALUES (v_tipo_id, '[]'::jsonb,
                '[{"nome": "valor", "padrao": "x", "grupo": 0}]'::jsonb,
                '[{"campo": "competencia", "formato": "competencia",
                   "motivo": "campo que a regra não sabe extrair"}]'::jsonb,
                2, 'teste');
        PERFORM teste_falhou('V8 · campo essencial não extraível deveria ser recusado');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('V8 · campo essencial que a regra não extrai é recusado no cadastro');
    END;

    -- 3. Campo essencial sem motivo substantivo é recusado -----------------
    BEGIN
        INSERT INTO regra_reconhecimento (tipo_id, ancoras, campos, campos_essenciais,
                                          versao, criado_por)
        VALUES (v_tipo_id, '[]'::jsonb,
                '[{"nome": "valor", "padrao": "x", "grupo": 0}]'::jsonb,
                '[{"campo": "valor", "formato": "valor", "motivo": "sei la"}]'::jsonb,
                3, 'teste');
        PERFORM teste_falhou('V8 · motivo curto deveria ser recusado');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('V8 · campo essencial sem motivo substantivo é recusado (cap. 12)');
    END;

    -- 4. Entrada sem formato é recusada ------------------------------------
    BEGIN
        INSERT INTO regra_reconhecimento (tipo_id, ancoras, campos, campos_essenciais,
                                          versao, criado_por)
        VALUES (v_tipo_id, '[]'::jsonb,
                '[{"nome": "valor", "padrao": "x", "grupo": 0}]'::jsonb,
                '[{"campo": "valor", "motivo": "sem formato declarado nenhum"}]'::jsonb,
                4, 'teste');
        PERFORM teste_falhou('V8 · entrada sem formato deveria ser recusada');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('V8 · campo essencial sem formato declarado é recusado');
    END;

    -- 5. Lista vazia é válida: V8 fica inerte, não trava faturamento -------
    INSERT INTO regra_reconhecimento (tipo_id, ancoras, campos, versao, criado_por)
    VALUES (v_tipo_id, '[]'::jsonb, '[]'::jsonb, 5, 'teste');
    PERFORM teste_ok('Cap. 1 · tipo sem campos essenciais é válido — V8 fica inerte');

    -- 6. Veredito: reprovar sem motivo não é gravável ----------------------
    INSERT INTO documento (origem, caminho, nome_arquivo, hash_sha256, tamanho,
                           mime_real, formato, status_triagem, criado_por)
    VALUES ('OWNCLOUD', '/teste/v8.pdf', 'v8.pdf', repeat('a', 64), 603,
            'application/pdf', 'pdf', 'PENDENTE', 'teste')
    RETURNING id INTO v_doc_id;

    BEGIN
        INSERT INTO validacao_documento (documento_id, codigo, resultado, regra_recon_id)
        VALUES (v_doc_id, 'V8', 'REPROVADO', v_regra_id);
        PERFORM teste_falhou('V8 · reprovação sem motivo deveria ser recusada');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('Cap. 16 · reprovação sem motivo não é gravável');
    END;

    BEGIN
        INSERT INTO validacao_documento (documento_id, codigo, resultado, motivo)
        VALUES (v_doc_id, 'V8', 'NAO_APLICAVEL', NULL);
        PERFORM teste_falhou('V8 · NAO_APLICAVEL sem motivo deveria ser recusado');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('V8 · "não se aplica" também é decisão e exige motivo');
    END;

    -- 7. Histórico: reprocessar não apaga o veredito anterior --------------
    INSERT INTO validacao_documento (documento_id, codigo, resultado, motivo, detalhe,
                                     regra_recon_id, executada_em)
    VALUES (v_doc_id, 'V8', 'REPROVADO',
            'documento incompleto: 1 de 1 campo(s) essencial(is) não pôde ser lido',
            '{"ausentes": ["valor"]}'::jsonb, v_regra_id, now() - interval '1 day');
    INSERT INTO validacao_documento (documento_id, codigo, resultado, motivo,
                                     regra_recon_id, executada_em)
    VALUES (v_doc_id, 'V8', 'APROVADO', NULL, v_regra_id, now());

    SELECT count(*) INTO n FROM validacao_documento
     WHERE documento_id = v_doc_id AND codigo = 'V8';
    IF n <> 2 THEN
        PERFORM teste_falhou(format('esperava 2 execuções no histórico, achei %s', n));
    END IF;
    PERFORM teste_ok('Cap. 16 · reprocessar acrescenta veredito, não substitui');

    SELECT count(*) INTO n FROM validacao_vigente
     WHERE documento_id = v_doc_id AND codigo = 'V8' AND resultado = 'APROVADO';
    IF n <> 1 THEN
        PERFORM teste_falhou('a view vigente deveria devolver só a execução mais recente');
    END IF;
    PERFORM teste_ok('V8 · a view vigente devolve a última execução de cada validação');
END $$;

-- ---------------------------------------------------------------------------
-- Coerência da carga F1-03/V8. Num banco sem carga, o bloco se auto-pula.
--
-- O banco não conhece a lista de formatos — ela vive no validador. Sem esta
-- rede, um erro de digitação no cadastro ("valorr") faria V8 explodir em
-- produção, ou pior, aprovar em silêncio se o validador fosse permissivo.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    n          integer;
    -- Só a carga: as regras de fixture dos testes acima nascem incompletas de
    -- propósito, para exercitar as restrições.
    CARGA      constant text := 'carga-inicial-f1-03';
    conhecidos text[] := ARRAY['cpf', 'cnpj', 'data', 'data_por_extenso', 'competencia',
                               'valor', 'nome', 'natureza', 'inteiro', 'texto'];
BEGIN
    SELECT count(*) INTO n FROM regra_reconhecimento WHERE criado_por = CARGA;
    IF n = 0 THEN
        RAISE NOTICE 'PASSOU: banco sem carga — coerência de F1-03 pulada';
        RETURN;
    END IF;

    SELECT count(*) INTO n
      FROM regra_reconhecimento r, jsonb_array_elements(r.campos_essenciais) e
     WHERE r.criado_por = CARGA AND NOT (e->>'formato' = ANY (conhecidos));
    IF n > 0 THEN
        PERFORM teste_falhou(format(
            '%s campo(s) essencial(is) com formato que o validador não conhece', n));
    END IF;
    PERFORM teste_ok('V8 · todo formato do cadastro é um formato que o validador conhece');

    -- Regra sem âncora classificaria pelo nome do arquivo.
    SELECT count(*) INTO n FROM regra_reconhecimento
     WHERE criado_por = CARGA AND jsonb_array_length(ancoras) = 0;
    IF n > 0 THEN
        PERFORM teste_falhou(format('%s regra(s) de reconhecimento sem âncora', n));
    END IF;
    PERFORM teste_ok('F1-03 · nenhuma regra de reconhecimento fica sem âncora');

    -- Toda regra precisa de ao menos um discriminante, ou empata com a vizinha.
    SELECT count(*) INTO n FROM regra_reconhecimento r
     WHERE r.criado_por = CARGA AND NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(r.ancoras) a
         WHERE (a->>'discriminante')::boolean IS TRUE);
    IF n > 0 THEN
        PERFORM teste_falhou(format(
            '%s regra(s) sem âncora discriminante — empatariam com tipos parecidos', n));
    END IF;
    PERFORM teste_ok('F1-03 · toda regra tem a âncora que a separa das parecidas');

    -- Duas regras com o MESMO discriminante se confundiriam sempre.
    SELECT count(*) INTO n FROM (
        SELECT a->>'expressao' AS exp
          FROM regra_reconhecimento r, jsonb_array_elements(r.ancoras) a
         WHERE r.criado_por = CARGA AND (a->>'discriminante')::boolean IS TRUE
         GROUP BY 1 HAVING count(*) > 1) d;
    IF n > 0 THEN
        PERFORM teste_falhou(format('%s discriminante(s) repetido(s) entre tipos', n));
    END IF;
    PERFORM teste_ok('F1-03 · nenhum discriminante serve a dois tipos ao mesmo tempo');
END $$;

ROLLBACK;
