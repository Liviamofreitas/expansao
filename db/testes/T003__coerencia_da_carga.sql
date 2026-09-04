-- =============================================================================
-- SGDF — Coerência da carga real (A03/A04, E-04, E-01, A09)
--
-- Os seeds são gerados. Este arquivo é a rede que impede que um gerador
-- alterado produza carga incoerente sem ninguém notar: verifica as invariantes
-- que a carga tem de manter, não os números de uma execução específica.
--
-- Num banco sem carga todos os blocos se auto-pulam, porque a suíte tem de
-- passar nos dois cenários.
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE OR REPLACE FUNCTION teste_ok(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE NOTICE 'PASSOU: %', descricao; END $$;

CREATE OR REPLACE FUNCTION teste_falhou(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'FALHOU: %', descricao; END $$;

DO $$
DECLARE n integer;
BEGIN
    SELECT count(*) INTO n FROM regra_exigibilidade;
    IF n = 0 THEN
        RAISE NOTICE 'PASSOU: banco sem carga — verificações de coerência puladas';
        RETURN;
    END IF;

    -- Toda regra aponta para tipo existente e ativo -------------------------
    SELECT count(*) INTO n FROM regra_exigibilidade r
      LEFT JOIN tipo_documental t ON t.id = r.tipo_id
     WHERE t.id IS NULL OR NOT t.ativo;
    IF n > 0 THEN
        PERFORM teste_falhou(format('%s regra(s) apontam para tipo inexistente ou inativo', n));
    END IF;
    PERFORM teste_ok('E-04 · toda regra de exigibilidade aponta para tipo ativo');

    -- Todo contrato-serviço tem regras --------------------------------------
    SELECT count(*) INTO n FROM contrato_servico cs
     WHERE NOT EXISTS (SELECT 1 FROM regra_exigibilidade r WHERE r.alvo_contrato_id = cs.id);
    IF n > 0 THEN
        PERFORM teste_falhou(format('%s contrato(s)-serviço sem nenhuma regra', n));
    END IF;
    PERFORM teste_ok('A03/A04 · todo contrato-serviço carregado tem matriz própria');

    -- Contrato sem empresa real permanece inativo ---------------------------
    SELECT count(*) INTO n FROM contrato_servico WHERE ativo AND empresa_id IS NULL;
    IF n > 0 THEN
        PERFORM teste_falhou(format('%s contrato(s) ativos sem empresa emitente', n));
    END IF;
    PERFORM teste_ok('A07 · contratos da carga entram inativos até o CNPJ real ser cadastrado');

    -- Prazos: a CHECK da V005 já valida a forma; aqui o domínio efetivo -----
    SELECT count(*) INTO n FROM regra_exigibilidade
     WHERE NOT prazo_bem_formado(prazo);
    IF n > 0 THEN
        PERFORM teste_falhou(format('%s prazo(s) malformados na carga', n));
    END IF;
    PERFORM teste_ok('Cap. 7.3 · todos os prazos da carga são bem formados');

    -- E-08: nenhuma regra sobrou com offset ordinal que estoura mês curto ---
    SELECT count(*) INTO n FROM regra_exigibilidade
     WHERE prazo->>'ancora' = 'INICIO_COMPETENCIA'
       AND (prazo->>'offset')::int > 28;
    IF n > 0 THEN
        PERFORM teste_falhou(format(
            '%s regra(s) ainda pedem dia > 28 com âncora de início — deveriam usar FIM_COMPETENCIA (E-08)', n));
    END IF;
    PERFORM teste_ok('E-08 · nenhuma regra pede dia do mês que possa não existir');

    -- A09: repositório-mestre declarado, ou explicitamente ausente ----------
    SELECT count(*) INTO n FROM tipo_documental
     WHERE ativo AND repositorio_mestre IS NULL
       AND jsonb_array_length(repositorios_alternativos) = 0;
    IF n > 0 THEN
        RAISE NOTICE 'PASSOU: A09 · % tipo(s) sem mestre declarado vão a conferência manual', n;
    ELSE
        PERFORM teste_ok('A09 · todo tipo ativo tem repositório-mestre declarado');
    END IF;

    -- A09: quem tem alternativa também tem mestre ---------------------------
    SELECT count(*) INTO n FROM tipo_documental
     WHERE jsonb_array_length(repositorios_alternativos) > 0 AND repositorio_mestre IS NULL;
    IF n > 0 THEN
        PERFORM teste_falhou(format('%s tipo(s) com alternativa e sem mestre — desempate impossível', n));
    END IF;
    PERFORM teste_ok('A09 · tipo com repositório alternativo tem mestre para desempatar');

    -- Nenhuma regra órfã de versão de matriz --------------------------------
    SELECT count(*) INTO n FROM regra_exigibilidade r
      LEFT JOIN versao_matriz v ON v.id = r.versao_matriz_id
     WHERE v.id IS NULL;
    IF n > 0 THEN
        PERFORM teste_falhou(format('%s regra(s) sem versão de matriz', n));
    END IF;
    PERFORM teste_ok('D-03 · toda regra pertence a uma versão de matriz');
END $$;

-- =============================================================================
-- A08 · modo de retenção do book
-- =============================================================================
-- Fixture mínima: sem um ciclo real, o INSERT falharia por NOT NULL em
-- ciclo_id e o teste passaria pelo motivo errado — que é pior que não testar.
INSERT INTO empresa (id, razao_social, cnpj, criado_por)
VALUES ('dddddddd-0000-0000-0000-000000000001', 'Empresa T003', '55666777000188', 'teste');

INSERT INTO cliente (id, nome, cnpj, esfera, criado_por)
VALUES ('dddddddd-0000-0000-0000-000000000002', 'Cliente T003', '55666777000277', 'PUBLICA', 'teste');

INSERT INTO modalidade (codigo, nome, criado_por)
VALUES ('OUTSOURCING', 'Outsourcing', 'teste') ON CONFLICT (codigo) DO NOTHING;

INSERT INTO versao_matriz (id, numero, publicada_por, motivo)
VALUES ('dddddddd-0000-0000-0000-000000000003', 'T003', 'teste', 'massa');

INSERT INTO contrato_servico (id, cliente_id, numero, servico, modalidade_id, vigencia_ini,
                              pasta_origem, data_contratual_faturamento, calendario_uf,
                              empresa_id, criado_por)
VALUES ('dddddddd-0000-0000-0000-000000000004', 'dddddddd-0000-0000-0000-000000000002',
        'CT-T003', 'X', (SELECT id FROM modalidade WHERE codigo = 'OUTSOURCING'), '2025-01-01',
        '/t003', '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE',
        'dddddddd-0000-0000-0000-000000000001', 'teste');

INSERT INTO ciclo (id, contrato_servico_id, competencia, status, versao_matriz_id, criado_por)
VALUES ('dddddddd-0000-0000-0000-000000000005', 'dddddddd-0000-0000-0000-000000000004',
        '2026-04', 'PRONTO', 'dddddddd-0000-0000-0000-000000000003', 'teste');

DO $$
BEGIN
    INSERT INTO book (ciclo_id, versao, publicado_por, hash_conjunto, manifesto,
                      caminho_bucket, pecas, retencao_modo, retencao_ate)
    VALUES ('dddddddd-0000-0000-0000-000000000005', 1, 'teste', repeat('a', 64), '{}',
            '/x', 1, 'LEGAL_HOLD', now());
    PERFORM teste_falhou('A08: LEGAL_HOLD com data de retenção foi aceito');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('A08 · LEGAL_HOLD não admite data (retenção sem prazo determinado)');
END $$;

DO $$
BEGIN
    INSERT INTO book (ciclo_id, versao, publicado_por, hash_conjunto, manifesto,
                      caminho_bucket, pecas, retencao_modo, retencao_ate)
    VALUES ('dddddddd-0000-0000-0000-000000000005', 2, 'teste', repeat('b', 64), '{}',
            '/x', 1, 'COMPLIANCE', NULL);
    PERFORM teste_falhou('A08: COMPLIANCE sem data de retenção foi aceito');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('A08 · COMPLIANCE exige data — é o modo irreversível');
END $$;

DO $$
BEGIN
    INSERT INTO book (ciclo_id, versao, publicado_por, hash_conjunto, manifesto,
                      caminho_bucket, pecas)
    VALUES ('dddddddd-0000-0000-0000-000000000005', 3, 'teste', repeat('c', 64), '{}',
            '/x', 1);
    PERFORM teste_ok('A08 · book selado sem informar retenção fica em LEGAL_HOLD por padrão');
END $$;

DO $$
DECLARE v text;
BEGIN
    SELECT column_default INTO v FROM information_schema.columns
     WHERE table_name = 'book' AND column_name = 'retencao_modo';
    IF v IS NULL OR v NOT LIKE '%LEGAL_HOLD%' THEN
        PERFORM teste_falhou('A08: o padrão de retenção deveria ser LEGAL_HOLD (reversível)');
    END IF;
    PERFORM teste_ok('A08 · o padrão é LEGAL_HOLD — COMPLIANCE é irreversível e exige temporalidade');
END $$;

ROLLBACK;
