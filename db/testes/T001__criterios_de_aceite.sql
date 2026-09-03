-- =============================================================================
-- SGDF — Testes dos critérios de aceite verificáveis no banco
--
-- Cobre: F0-08 (trilha append-only), F0-09 (SoD na exceção), SEC-03, e as
-- invariantes de esquema que o cap. 5 declara.
--
-- Cada teste tem de FALHAR quando o controle é violado. Um teste que passa
-- porque nada aconteceu não prova nada — por isso as tentativas de violação
-- usam o padrão "esperava erro; se chegou aqui, o controle não existe".
--
-- Execução:  psql -v ON_ERROR_STOP=1 -f db/testes/T001__criterios_de_aceite.sql
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE OR REPLACE FUNCTION teste_ok(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE NOTICE 'PASSOU: %', descricao; END $$;

CREATE OR REPLACE FUNCTION teste_falhou(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'FALHOU: %', descricao; END $$;

-- -----------------------------------------------------------------------------
-- Massa mínima
-- -----------------------------------------------------------------------------
INSERT INTO cliente (id, nome, cnpj, esfera, criado_por)
VALUES ('11111111-1111-1111-1111-111111111111', 'Cliente de Teste', '07237373000120', 'PUBLICA', 'teste');

-- A modalidade pode já existir (carga inicial V100). O teste não pode depender
-- de o banco estar vazio nem de o banco estar semeado: resolve os dois casos.
INSERT INTO modalidade (id, codigo, nome, criado_por)
VALUES ('22222222-2222-2222-2222-222222222222', 'OUTSOURCING', 'Outsourcing', 'teste')
ON CONFLICT (codigo) DO NOTHING;

CREATE TEMP TABLE t_fixture AS
SELECT id AS modalidade_id FROM modalidade WHERE codigo = 'OUTSOURCING';

INSERT INTO empresa (id, razao_social, cnpj, criado_por)
VALUES ('99999999-9999-9999-9999-999999999999', 'Empresa de Teste', '99999999000199', 'teste');

INSERT INTO contrato_servico (id, cliente_id, numero, servico, modalidade_id, vigencia_ini,
                              pasta_origem, data_contratual_faturamento, calendario_uf,
                              empresa_id, criado_por)
VALUES ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
        '482023-TESTE', 'OUT', (SELECT modalidade_id FROM t_fixture), '2025-01-01',
        '/CLIENTE - 482023', '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}', 'CE',
        '99999999-9999-9999-9999-999999999999', 'teste');

INSERT INTO tipo_documental (id, codigo, nome, familia, escopo, evento, defasagem,
                             criticidade, sigilo, criado_por)
VALUES ('44444444-4444-4444-4444-444444444444', 'CER.TESTE', 'Certidão de teste',
        'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF',
        'BLOQUEANTE', 'PUBLICO_CLIENTE', 'teste');

INSERT INTO versao_matriz (id, numero, publicada_por, motivo)
VALUES ('55555555-5555-5555-5555-555555555555', '0.0-teste', 'teste', 'massa de teste');

INSERT INTO ciclo (id, contrato_servico_id, competencia, status, versao_matriz_id, criado_por)
VALUES ('66666666-6666-6666-6666-666666666666', '33333333-3333-3333-3333-333333333333',
        '2026-04', 'ABERTO', '55555555-5555-5555-5555-555555555555', 'teste');

INSERT INTO exigencia (id, ciclo_id, tipo_id, evento, status, prazo_calculado,
                       criticidade, responsavel, origem, criado_por)
VALUES ('77777777-7777-7777-7777-777777777777', '66666666-6666-6666-6666-666666666666',
        '44444444-4444-4444-4444-444444444444', 'MENSAL', 'PENDENTE', '2026-05-10',
        'BLOQUEANTE', 'FINANCEIRO', 'MATRIZ', 'teste');

-- =============================================================================
-- F0-08 / SEC-03 — trilha de auditoria append-only
-- Critério: "UPDATE em log_auditoria negado pelo banco".
-- =============================================================================
INSERT INTO log_auditoria (ator, papel, acao, objeto_tipo, objeto_id, resultado)
VALUES ('teste', 'ADMIN_SISTEMA', 'ABERTURA_CICLO', 'ciclo',
        '66666666-6666-6666-6666-666666666666', 'SUCESSO');

DO $$
DECLARE afetadas integer; conteudo text;
BEGIN
    UPDATE log_auditoria SET resultado = 'NEGADO' WHERE ator = 'teste';
    GET DIAGNOSTICS afetadas = ROW_COUNT;
    SELECT resultado INTO conteudo FROM log_auditoria WHERE ator = 'teste';
    IF afetadas > 0 OR conteudo <> 'SUCESSO' THEN
        PERFORM teste_falhou('F0-08: UPDATE alterou a trilha de auditoria');
    END IF;
    PERFORM teste_ok('F0-08 · UPDATE em log_auditoria não altera nada');
END $$;

DO $$
DECLARE restantes integer;
BEGIN
    DELETE FROM log_auditoria WHERE ator = 'teste';
    SELECT count(*) INTO restantes FROM log_auditoria WHERE ator = 'teste';
    IF restantes = 0 THEN
        PERFORM teste_falhou('F0-08: DELETE removeu linha da trilha de auditoria');
    END IF;
    PERFORM teste_ok('F0-08 · DELETE em log_auditoria não remove nada');
END $$;

DO $$
BEGIN
    IF has_table_privilege('sgdf_app', 'log_auditoria', 'UPDATE')
       OR has_table_privilege('sgdf_app', 'log_auditoria', 'DELETE') THEN
        PERFORM teste_falhou('SEC-03: sgdf_app ainda tem UPDATE/DELETE em log_auditoria');
    END IF;
    IF NOT has_table_privilege('sgdf_app', 'log_auditoria', 'INSERT')
       OR NOT has_table_privilege('sgdf_app', 'log_auditoria', 'SELECT') THEN
        PERFORM teste_falhou('SEC-03: sgdf_app precisa poder inserir e ler a trilha');
    END IF;
    PERFORM teste_ok('SEC-03 · sgdf_app tem INSERT/SELECT e não tem UPDATE/DELETE na trilha');
END $$;

DO $$
BEGIN
    IF has_table_privilege('sgdf_app', 'book', 'UPDATE')
       OR has_table_privilege('sgdf_app', 'versao_matriz', 'UPDATE') THEN
        PERFORM teste_falhou('Cap. 10 / 5.1: book e versao_matriz devem ser append-only');
    END IF;
    PERFORM teste_ok('Cap. 10 · book e versao_matriz são append-only para a aplicação');
END $$;

-- =============================================================================
-- F0-09 — segregação de funções na exceção
-- Critério: "Solicitante não consegue aprovar a própria exceção".
-- =============================================================================
DO $$
BEGIN
    INSERT INTO excecao (exigencia_id, motivo, solicitante, aprovador, aprovado_em)
    VALUES ('77777777-7777-7777-7777-777777777777',
            'Documento inexistente por decisao judicial documentada nos autos',
            'joao.silva', 'joao.silva', now());
    PERFORM teste_falhou('F0-09: o banco aceitou aprovador = solicitante');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('F0-09 · aprovador igual ao solicitante é rejeitado (SoD)');
END $$;

DO $$
BEGIN
    INSERT INTO excecao (exigencia_id, motivo, solicitante, aprovador, aprovado_em)
    VALUES ('77777777-7777-7777-7777-777777777777',
            'Documento inexistente por decisao judicial documentada nos autos',
            'joao.silva', 'maria.andrade', now());
    PERFORM teste_ok('F0-09 · aprovador diferente do solicitante é aceito');
END $$;

DO $$
BEGIN
    INSERT INTO excecao (exigencia_id, motivo, solicitante)
    VALUES ('77777777-7777-7777-7777-777777777777', 'motivo curto', 'joao.silva');
    PERFORM teste_falhou('F0-09: motivo de exceção sem substância foi aceito');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('F0-09 · exceção exige motivo substantivo (>= 20 caracteres)');
END $$;

-- =============================================================================
-- E-02 — unicidade global do alias normalizado
-- Impede que o mesmo padrão aponte para dois tipos (determinismo, cap. 1 p.3).
-- =============================================================================
INSERT INTO tipo_documental (id, codigo, nome, familia, escopo, evento, defasagem,
                             criticidade, sigilo, criado_por)
VALUES ('88888888-8888-8888-8888-888888888888', 'CER.TESTE2', 'Outra certidão',
        'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF',
        'BLOQUEANTE', 'PUBLICO_CLIENTE', 'teste');

INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado, origem, criado_por)
VALUES ('44444444-4444-4444-4444-444444444444', 'CND_TESTE', 'cnd_teste', 'LEGADO', 'teste');

DO $$
BEGIN
    INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado, origem, criado_por)
    VALUES ('88888888-8888-8888-8888-888888888888', 'CND_TESTE', 'cnd_teste', 'LEGADO', 'teste');
    PERFORM teste_falhou('E-02: o mesmo alias normalizado foi aceito para dois tipos');
EXCEPTION WHEN unique_violation THEN
    PERFORM teste_ok('E-02 · alias normalizado é único globalmente');
END $$;

-- =============================================================================
-- Cap. 9 — regra sem tolerância não pode bloquear
-- =============================================================================
DO $$
BEGIN
    INSERT INTO regra_conciliacao (codigo, nome, logica, modo, fase, criado_por)
    VALUES ('RX01', 'Regra sem tolerância', 'a = b', 'BLOQUEIO', '1A', 'teste');
    PERFORM teste_falhou('Cap. 9: regra sem tolerância foi aceita em modo BLOQUEIO');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('Cap. 9 · regra sem tolerância é forçada a modo ALERTA');
END $$;

-- =============================================================================
-- Cap. 5.2 — unicidade das chaves de negócio
-- =============================================================================
DO $$
BEGIN
    INSERT INTO ciclo (contrato_servico_id, competencia, status, versao_matriz_id, criado_por)
    VALUES ('33333333-3333-3333-3333-333333333333', '2026-04', 'ABERTO',
            '55555555-5555-5555-5555-555555555555', 'teste');
    PERFORM teste_falhou('Cap. 5.2: dois ciclos para o mesmo contrato e competência');
EXCEPTION WHEN unique_violation THEN
    PERFORM teste_ok('Cap. 5.2 · ciclo é único por (contrato-serviço, competência)');
END $$;

DO $$
BEGIN
    INSERT INTO exigencia (ciclo_id, tipo_id, evento, status, prazo_calculado,
                           criticidade, responsavel, origem, criado_por)
    VALUES ('66666666-6666-6666-6666-666666666666', '44444444-4444-4444-4444-444444444444',
            'MENSAL', 'PENDENTE', '2026-05-10', 'BLOQUEANTE', 'FINANCEIRO', 'MATRIZ', 'teste');
    PERFORM teste_falhou('Cap. 5.2: exigência de escopo de contrato duplicada (NULLS NOT DISTINCT)');
EXCEPTION WHEN unique_violation THEN
    PERFORM teste_ok('Cap. 5.2 · exigência é única por (ciclo, tipo, evento, profissional)');
END $$;

-- =============================================================================
-- Cap. 11.2 — no máximo 1 notificação por área/ciclo/dia
-- =============================================================================
INSERT INTO notificacao (ciclo_id, tipo, destinatario, conteudo_hash, template_versao, data_referencia)
VALUES ('66666666-6666-6666-6666-666666666666', 'COBRANCA', 'financeiro@exemplo',
        repeat('a', 64), 'v1', '2026-05-10');

DO $$
BEGIN
    INSERT INTO notificacao (ciclo_id, tipo, destinatario, conteudo_hash, template_versao, data_referencia)
    VALUES ('66666666-6666-6666-6666-666666666666', 'COBRANCA', 'financeiro@exemplo',
            repeat('b', 64), 'v1', '2026-05-10');
    PERFORM teste_falhou('Cap. 11.2: segunda cobrança no mesmo dia foi aceita');
EXCEPTION WHEN unique_violation THEN
    PERFORM teste_ok('Cap. 11.2 · máximo 1 notificação por ciclo/tipo/destinatário/dia');
END $$;

-- =============================================================================
-- Cap. 5.2 — no máximo uma pendência aberta por exigência
-- =============================================================================
INSERT INTO pendencia (exigencia_id, prazo)
VALUES ('77777777-7777-7777-7777-777777777777', '2026-05-10');

DO $$
BEGIN
    INSERT INTO pendencia (exigencia_id, prazo)
    VALUES ('77777777-7777-7777-7777-777777777777', '2026-05-11');
    PERFORM teste_falhou('Cap. 5.2: duas pendências abertas para a mesma exigência');
EXCEPTION WHEN unique_violation THEN
    PERFORM teste_ok('Cap. 5.2 · no máximo uma pendência aberta por exigência');
END $$;

-- =============================================================================
-- Cap. 5.1 — formato do código canônico e do CNPJ
-- =============================================================================
DO $$
BEGIN
    INSERT INTO tipo_documental (codigo, nome, familia, escopo, evento, defasagem,
                                 criticidade, sigilo, criado_por)
    VALUES ('codigo_invalido', 'x', 'y', 'CONTRATO', 'MENSAL', 'M', 'BLOQUEANTE', 'INTERNO', 'teste');
    PERFORM teste_falhou('Cap. 5.1: código fora do formato FAM.NOME foi aceito');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('Cap. 5.1 · código canônico exige o formato FAM.NOME');
END $$;

DO $$
BEGIN
    INSERT INTO cliente (nome, cnpj, esfera, criado_por)
    VALUES ('x', '07.237.373/000', 'PRIVADA', 'teste');
    PERFORM teste_falhou('Cap. 5.1: CNPJ com máscara foi aceito');
EXCEPTION WHEN check_violation OR string_data_right_truncation THEN
    PERFORM teste_ok('Cap. 5.1 · CNPJ aceita somente 14 dígitos, sem máscara');
END $$;

-- =============================================================================
-- Cap. 7.1 — a regra de exigibilidade aponta para exatamente um alvo
-- =============================================================================
DO $$
BEGIN
    INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id, alvo_contrato_id,
                                     obrigatoriedade, prazo, responsavel_titular,
                                     vigencia_ini, versao_matriz_id, criado_por)
    VALUES ('44444444-4444-4444-4444-444444444444', 'MODALIDADE',
            (SELECT modalidade_id FROM t_fixture), '33333333-3333-3333-3333-333333333333',
            'OBRIGATORIO', '{"ancora":"INICIO_COMPETENCIA","tipo_dia":"UTIL","offset":5}',
            'FINANCEIRO', '2025-01-01',
            '55555555-5555-5555-5555-555555555555', 'teste');
    PERFORM teste_falhou('Cap. 7.1: regra com dois alvos simultâneos foi aceita');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('Cap. 7.1 · regra de exigibilidade tem exatamente um alvo');
END $$;

-- =============================================================================
-- F0-06 / cap. 7.3 — forma do prazo estruturado (V003)
-- =============================================================================
DO $$
BEGIN
    INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id, obrigatoriedade,
                                     prazo, responsavel_titular, vigencia_ini,
                                     versao_matriz_id, criado_por)
    VALUES ('44444444-4444-4444-4444-444444444444', 'MODALIDADE',
            (SELECT modalidade_id FROM t_fixture), 'OBRIGATORIO',
            '{"ancora":"FIM_COMPETENCIA","tipo_dia":"UTIL","offset":5}',
            'FINANCEIRO', '2025-01-01', '55555555-5555-5555-5555-555555555555', 'teste');
    PERFORM teste_falhou('Cap. 7.3: âncora fora do domínio foi aceita no cadastro');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('Cap. 7.3 · âncora fora do domínio é rejeitada no cadastro');
END $$;

DO $$
BEGIN
    INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id, obrigatoriedade,
                                     prazo, responsavel_titular, vigencia_ini,
                                     versao_matriz_id, criado_por)
    VALUES ('44444444-4444-4444-4444-444444444444', 'MODALIDADE',
            (SELECT modalidade_id FROM t_fixture), 'OBRIGATORIO',
            '{"ancora":"INICIO_COMPETENCIA","tipo_dia":"UTIL","offset":0}',
            'FINANCEIRO', '2025-01-01', '55555555-5555-5555-5555-555555555555', 'teste');
    PERFORM teste_falhou('Cap. 7.3: offset ordinal 0 foi aceito no cadastro');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('Cap. 7.3 · offset ordinal 0 é rejeitado (não existe dia zero do mês)');
END $$;

DO $$
BEGIN
    INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id, obrigatoriedade,
                                     prazo, responsavel_titular, vigencia_ini,
                                     versao_matriz_id, criado_por)
    VALUES ('44444444-4444-4444-4444-444444444444', 'MODALIDADE',
            (SELECT modalidade_id FROM t_fixture), 'OBRIGATORIO',
            '{"ancora":"ATESTE","tipo_dia":"CORRIDO"}',
            'FINANCEIRO', '2025-01-01', '55555555-5555-5555-5555-555555555555', 'teste');
    PERFORM teste_falhou('Cap. 7.3: prazo sem offset foi aceito no cadastro');
EXCEPTION WHEN check_violation THEN
    PERFORM teste_ok('Cap. 7.3 · prazo sem campo obrigatório é rejeitado');
END $$;

DO $$
BEGIN
    INSERT INTO regra_exigibilidade (tipo_id, alvo, alvo_modalidade_id, obrigatoriedade,
                                     prazo, responsavel_titular, vigencia_ini,
                                     versao_matriz_id, criado_por)
    VALUES ('44444444-4444-4444-4444-444444444444', 'MODALIDADE',
            (SELECT modalidade_id FROM t_fixture), 'OBRIGATORIO',
            '{"ancora":"ATESTE","tipo_dia":"CORRIDO","offset":3}',
            'FINANCEIRO', '2025-01-01', '55555555-5555-5555-5555-555555555555', 'teste');
    PERFORM teste_ok('Cap. 7.3 · prazo bem formado (D+3 do ateste) é aceito');
END $$;

ROLLBACK;   -- os testes não deixam massa no banco
