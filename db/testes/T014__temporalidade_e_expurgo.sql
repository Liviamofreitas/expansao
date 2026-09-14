-- =============================================================================
-- SGDF — A08 / LGPD-02 · temporalidade e expurgo (migração V020)
--
-- O que se verifica aqui é uma coisa só, dita de nove maneiras: NÃO HÁ CAMINHO
-- QUE ELIMINE DADO SEM QUE ALGUÉM COM NOME TENHA APROVADO O PRAZO — e o
-- registro dessa eliminação não pode ser editado nem recriar o que ela apagou.
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
    id_proposta uuid;
    id_aprovada uuid;
    id_lote     bigint;
    afetadas    integer;
BEGIN
    -- 0. A CARGA NASCE SEM APROVAÇÃO NENHUMA -----------------------------------
    -- Antes de qualquer outra coisa, porque o resto deste teste APROVA uma delas
    -- para exercitar o caminho autorizado. Verificar depois leria o estado que o
    -- próprio teste criou.
    IF (SELECT count(*) FROM temporalidade WHERE criado_por = 'proposta-a08') <> 4 THEN
        PERFORM teste_falhou('A08 · a carga de temporalidade proposta não está aplicada');
    END IF;
    IF EXISTS (SELECT 1 FROM temporalidade
               WHERE criado_por = 'proposta-a08' AND aprovado_em IS NOT NULL) THEN
        PERFORM teste_falhou('A08 · a carga semeou uma classe já aprovada — engenharia '
                             'teria decidido prazo de guarda de dado pessoal');
    END IF;
    PERFORM teste_ok('A08 · as 4 classes propostas nascem SEM aprovação — o prazo é '
                     'decisão do jurídico e do DPO, e o expurgo recusa cada uma pelo nome');

    -- Uma classe proposta e uma aprovada, para comparar os dois caminhos.
    INSERT INTO temporalidade (classe, alvo, marco, prazo_meses, acao, fundamento, criado_por)
    VALUES ('Proposta em avaliação', 'CAMPO_EXTRAIDO', 'REGISTRO', 60, 'EXPURGAR',
            'proposta sem aprovação', 'teste')
    RETURNING id INTO id_proposta;

    UPDATE temporalidade
    SET    aprovado_em = now() - INTERVAL '1 day', aprovado_por = 'juridico.responsavel'
    WHERE  alvo = 'PROFISSIONAL'
    RETURNING id INTO id_aprovada;
    PERFORM teste_ok('A08 · aprovar é preencher nome e data numa classe que já existia — '
                     'não é deploy, e é por isso que o jurídico não depende de release');

    -- 1. A TRAVA ---------------------------------------------------------------
    -- É o teste central. O INSERT copia aprovado_em, que é nulo na proposta, para
    -- uma coluna NOT NULL. Em produção este INSERT vive no MESMO comando que o
    -- DELETE — logo, a classe não aprovada não consegue nem registrar o expurgo,
    -- e o que não se registra não se apaga.
    BEGIN
        INSERT INTO expurgo (temporalidade_id, autorizado_em, alvo, acao, corte,
                             itens, executado_por)
        SELECT t.id, t.aprovado_em, t.alvo, t.acao, CURRENT_DATE, 3, 'agendador'
        FROM   temporalidade t WHERE t.id = id_proposta;
        PERFORM teste_falhou('A08 · uma classe NÃO aprovada conseguiu registrar expurgo');
    EXCEPTION WHEN not_null_violation THEN
        PERFORM teste_ok('A08 · classe não aprovada não registra expurgo — a trava é o '
                         'NOT NULL de autorizado_em, não um "if" da aplicação');
    END;

    INSERT INTO expurgo (temporalidade_id, autorizado_em, alvo, acao, corte,
                         itens, executado_por)
    SELECT t.id, t.aprovado_em, t.alvo, t.acao, CURRENT_DATE, 0, 'agendador'
    FROM   temporalidade t WHERE t.id = id_aprovada
    RETURNING id INTO id_lote;
    PERFORM teste_ok('A08 · e a classe aprovada registra — inclusive com itens = 0, que '
                     'é "rodou, estava autorizado e nada tinha vencido"');

    -- 2. Aprovação pela metade --------------------------------------------------
    BEGIN
        UPDATE temporalidade SET aprovado_em = now() WHERE alvo = 'DOCUMENTO';
        PERFORM teste_falhou('A08 · aprovação sem nome de quem aprovou foi aceita');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('A08 · data sem nome é recusada — seria um prazo de guarda que '
                         'se aprovou sozinho, e alguém vai perguntar quem decidiu');
    END;

    -- 3. A TRILHA NÃO TEM TEMPORALIDADE -----------------------------------------
    -- A ausência de log_auditoria da lista fechada é conteúdo, não esquecimento:
    -- a trilha guarda o identificador do ATOR, não dado do titular, e é
    -- append-only por RULE desde a V002. Uma regra que o banco recusa cumprir é
    -- pior que regra nenhuma.
    BEGIN
        INSERT INTO temporalidade (classe, alvo, marco, prazo_meses, acao, fundamento,
                                   criado_por)
        VALUES ('Trilha de auditoria', 'LOG_AUDITORIA', 'REGISTRO', 12, 'EXPURGAR',
                'tentativa de dar prazo à trilha', 'teste');
        PERFORM teste_falhou('A08 · aceitou temporalidade para a trilha de auditoria');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('A08 · a trilha não admite temporalidade — nem o book, e por '
                         'motivos diferentes: um é prova de quem agiu, o outro está '
                         'selado sob LEGAL_HOLD e não se apaga por DELETE de linha');
    END;

    BEGIN
        INSERT INTO temporalidade (classe, alvo, marco, prazo_meses, acao, fundamento,
                                   criado_por)
        VALUES ('Book publicado', 'BOOK', 'PUBLICACAO_DO_BOOK', 60, 'EXPURGAR',
                'tentativa de dar ao agendador o poder de apagar o book', 'teste');
        PERFORM teste_falhou('A08 · aceitou temporalidade para o book selado');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('A08 · o book também não — a retenção dele é do armazenamento, '
                         'e destruí-lo exige antes levantar o LEGAL_HOLD, que é ato humano');
    END;

    -- 4. Prazo que não é prazo ---------------------------------------------------
    BEGIN
        UPDATE temporalidade SET prazo_meses = 0 WHERE id = id_proposta;
        PERFORM teste_falhou('A08 · prazo de zero mês foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('A08 · prazo zero é recusado — entraria como número e sairia '
                         'como destruição de prova no instante em que ela nasce');
    END;

    -- 5. Fundamento vazio --------------------------------------------------------
    BEGIN
        UPDATE temporalidade SET fundamento = '   ' WHERE id = id_proposta;
        PERFORM teste_falhou('A08 · prazo sem fundamento foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('A08 · prazo sem fundamento é recusado — na auditoria, '
                         '"por que este prazo e não outro?" precisa ter resposta escrita');
    END;

    -- 6. REVISAR não é eliminação -------------------------------------------------
    BEGIN
        INSERT INTO expurgo (temporalidade_id, autorizado_em, alvo, acao, corte,
                             itens, executado_por)
        SELECT t.id, t.aprovado_em, t.alvo, 'REVISAR', CURRENT_DATE, 5, 'agendador'
        FROM   temporalidade t WHERE t.id = id_aprovada;
        PERFORM teste_falhou('A08 · uma revisão foi registrada como expurgo');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('LGPD-02 · REVISAR não entra no registro de eliminação — '
                         '"itens" passaria a significar apagados numa linha e listados '
                         'noutra, e contagem ambígua aqui é pior que contagem nenhuma');
    END;

    -- 7. O LOG DO EXPURGO NÃO RECRIA O QUE O EXPURGO DESTRUIU ----------------------
    BEGIN
        INSERT INTO expurgo_item (expurgo_id, identificador, marco_em)
        VALUES (id_lote, 'FULANO DE TAL — CPF 137.810.319-00', CURRENT_DATE);
        PERFORM teste_falhou('A08 · gravou nome e CPF no registro de eliminação');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('LGPD-02 · o item expurgado só aceita chave — provar que o CPF '
                         'foi eliminado guardando o CPF não elimina nada');
    END;

    INSERT INTO expurgo_item (expurgo_id, identificador, marco_em)
    VALUES (id_lote, '00000000-0000-4000-8000-000000000001', CURRENT_DATE - 1);
    INSERT INTO expurgo_item (expurgo_id, identificador, marco_em)
    VALUES (id_lote, '4711', CURRENT_DATE - 1);
    PERFORM teste_ok('LGPD-02 · uuid e inteiro passam — são as duas formas de chave '
                     'que o esquema usa');

    -- 8. Autorização posterior ao ato ---------------------------------------------
    BEGIN
        INSERT INTO expurgo (temporalidade_id, autorizado_em, alvo, acao, corte, itens,
                             executado_em, executado_por)
        SELECT t.id, now(), t.alvo, t.acao, CURRENT_DATE, 1,
               now() - INTERVAL '1 hour', 'agendador'
        FROM   temporalidade t WHERE t.id = id_aprovada;
        PERFORM teste_falhou('A08 · expurgo autorizado depois de executado foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('A08 · autorização posterior ao ato é recusada — seria '
                         'exatamente o que este registro existe para tornar impossível');
    END;

    -- 9. Itens negativos -----------------------------------------------------------
    BEGIN
        INSERT INTO expurgo (temporalidade_id, autorizado_em, alvo, acao, corte, itens,
                             executado_por)
        SELECT t.id, t.aprovado_em, t.alvo, t.acao, CURRENT_DATE, -1, 'agendador'
        FROM   temporalidade t WHERE t.id = id_aprovada;
        PERFORM teste_falhou('A08 · expurgo com itens negativos foi aceito');
    EXCEPTION WHEN check_violation THEN
        PERFORM teste_ok('A08 · itens negativos recusados; zero continua válido, e é a '
                         'diferença entre "nada venceu" e "não rodou"');
    END;

    -- 10. Um alvo, uma política -----------------------------------------------------
    BEGIN
        INSERT INTO temporalidade (classe, alvo, marco, prazo_meses, acao, fundamento,
                                   criado_por)
        VALUES ('Segunda opinião', 'CAMPO_EXTRAIDO', 'REGISTRO', 24, 'EXPURGAR',
                'outra regra para o mesmo alvo', 'teste');
        PERFORM teste_falhou('A08 · dois prazos para o mesmo alvo foram aceitos');
    EXCEPTION WHEN unique_violation THEN
        PERFORM teste_ok('A08 · um prazo por alvo — dois seriam duas respostas para '
                         '"quando se apaga isto?", e a aplicação escolheria uma sozinha');
    END;

    -- 11. APPEND-ONLY, AS DUAS PRIMITIVAS ---------------------------------------------
    -- O registro do expurgo é a ÚNICA prova que sobra depois que o dado sumiu.
    -- Se ele puder ser editado, a eliminação deixa de ser demonstrável — e uma
    -- eliminação que não se demonstra, perante a ANPD, não aconteceu.
    UPDATE expurgo SET itens = 999 WHERE id = id_lote;
    GET DIAGNOSTICS afetadas = ROW_COUNT;
    IF afetadas <> 0 OR (SELECT itens FROM expurgo WHERE id = id_lote) <> 0 THEN
        PERFORM teste_falhou('A08 · o registro de expurgo aceitou UPDATE');
    END IF;
    PERFORM teste_ok('A08 · o registro de expurgo não aceita UPDATE — a RULE descarta');

    DELETE FROM expurgo WHERE id = id_lote;
    IF NOT EXISTS (SELECT 1 FROM expurgo WHERE id = id_lote) THEN
        PERFORM teste_falhou('A08 · o registro de expurgo aceitou DELETE');
    END IF;
    PERFORM teste_ok('A08 · nem DELETE — apagar a prova da eliminação seria apagar a '
                     'única coisa que sobrou dela');

    DELETE FROM expurgo_item WHERE expurgo_id = id_lote;
    IF NOT EXISTS (SELECT 1 FROM expurgo_item WHERE expurgo_id = id_lote) THEN
        PERFORM teste_falhou('A08 · o item expurgado aceitou DELETE');
    END IF;
    PERFORM teste_ok('A08 · o item expurgado também é append-only');

END $$;

ROLLBACK;
