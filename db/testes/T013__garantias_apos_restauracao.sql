-- =============================================================================
-- SGDF — SEC-08 e SEC-09 · as garantias sobreviveram à restauração?
--
-- ESTE TESTE EXISTE PORQUE RESTAURAR O DADO NÃO É RESTAURAR O CONTROLE.
--
-- Um backup que devolve todas as linhas e um banco que sobe passa em qualquer
-- verificação ingênua — e pode ter perdido exatamente o que faz o sistema valer:
--
--   * as RULEs que tornam log_auditoria append-only (V002);
--   * os REVOKE que tiram UPDATE e DELETE da role da aplicação;
--   * os 34 índices parciais, que são regra de unicidade condicional e não
--     otimização — `ux_excecao_aberta` é o que impede duas exceções em aberto
--     para a mesma exigência;
--   * os 120 CHECK, que são a última rede quando o serviço erra.
--
-- Um `pg_dump --data-only` restaurado sobre um esquema recriado à mão, um
-- `pg_restore` com `--no-privileges`, ou um `CREATE DATABASE` a partir de um
-- template desatualizado produzem as três perdas SEM NENHUM SINTOMA. O sistema
-- sobe, o painel abre, a trilha aceita INSERT — e aceita UPDATE também.
--
-- Rodar isto é parte do teste de restauração trimestral do SEC-08. Um relatório
-- de teste que diz "restaurado com sucesso" e não roda isto atesta a metade que
-- não importa.
-- =============================================================================

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE OR REPLACE FUNCTION teste_ok(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE NOTICE 'PASSOU: %', descricao; END $$;

CREATE OR REPLACE FUNCTION teste_falhou(descricao text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'FALHOU: %', descricao; END $$;

DO $$
DECLARE v_n integer; v_id bigint;
BEGIN
    -- 1. A trilha continua append-only ------------------------------------------
    SELECT count(*) INTO v_n FROM pg_rules
    WHERE schemaname = 'public' AND tablename = 'log_auditoria'
      AND rulename IN ('log_auditoria_sem_update', 'log_auditoria_sem_delete');
    IF v_n <> 2 THEN
        PERFORM teste_falhou('SEC-09 · as RULEs de append-only da trilha NÃO sobreviveram '
                             '(encontradas ' || v_n || ' de 2). A trilha aceita alteração, '
                             'e o cap. 16 deixou de valer sem nenhum sintoma');
    END IF;
    PERFORM teste_ok('SEC-09 · as duas RULEs de append-only da trilha sobreviveram');

    -- E não basta existirem: têm de FUNCIONAR.
    INSERT INTO log_auditoria (ator, papel, acao, objeto_tipo, resultado)
    VALUES ('teste.dr', 'AUDITORIA', 'TESTE_RESTAURACAO', 'teste', 'SUCESSO')
    RETURNING id INTO v_id;
    UPDATE log_auditoria SET resultado = 'ERRO' WHERE id = v_id;
    IF (SELECT resultado FROM log_auditoria WHERE id = v_id) <> 'SUCESSO' THEN
        PERFORM teste_falhou('SEC-09 · o UPDATE na trilha SURTIU EFEITO');
    END IF;
    PERFORM teste_ok('SEC-09 · e funcionam: o UPDATE é silenciosamente descartado');

    DELETE FROM log_auditoria WHERE id = v_id;
    IF NOT EXISTS (SELECT 1 FROM log_auditoria WHERE id = v_id) THEN
        PERFORM teste_falhou('SEC-09 · o DELETE na trilha SURTIU EFEITO');
    END IF;
    PERFORM teste_ok('SEC-09 · o DELETE também');

    -- 2. Os privilégios negados continuam negados --------------------------------
    --
    -- O REVOKE é a rede que vale quando alguém conecta com a role da aplicação
    -- por fora do serviço. pg_restore --no-privileges o descarta em silêncio.
    -- AS DUAS METADES, E A SEGUNDA SÓ APARECEU NA QUEBRA DELIBERADA.
    --
    -- A primeira versão verificava só "sgdf_app NÃO pode UPDATE". Com
    -- `pg_restore --no-privileges` a role fica sem privilégio NENHUM — e a
    -- asserção passa, satisfeita pela ausência total. O teste atestava um
    -- controle sobre um banco em que a aplicação nem consegue LER.
    --
    -- Uma negativa satisfeita pelo vazio não prova nada. O que prova é o par:
    -- pode o que deve poder, e não pode o que não deve.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sgdf_app') THEN
        IF NOT has_table_privilege('sgdf_app', 'log_auditoria', 'SELECT')
        OR NOT has_table_privilege('sgdf_app', 'log_auditoria', 'INSERT') THEN
            PERFORM teste_falhou('SEC-09 · a role da aplicação perdeu SELECT/INSERT na '
                                 'trilha: a restauração descartou os privilégios em bloco '
                                 '(pg_restore --no-privileges) e o sistema não sobe. Uma '
                                 'verificação que só olhasse o UPDATE negado passaria aqui');
        END IF;
        PERFORM teste_ok('SEC-09 · a role da aplicação MANTÉM SELECT/INSERT na trilha');

        IF has_table_privilege('sgdf_app', 'log_auditoria', 'UPDATE')
        OR has_table_privilege('sgdf_app', 'log_auditoria', 'DELETE') THEN
            PERFORM teste_falhou('SEC-09 · a role da aplicação recuperou UPDATE/DELETE '
                                 'sobre a trilha');
        END IF;
        PERFORM teste_ok('SEC-09 · e continua sem UPDATE/DELETE — as duas metades juntas');
    ELSE
        -- Dizer que não deu para verificar é melhor que passar em silêncio: em
        -- produção esta role EXISTE, e o item não pode ser dado como conferido.
        PERFORM teste_ok('SEC-09 · (role sgdf_app ausente neste ambiente — o privilégio '
                         'NÃO foi verificado; em produção este item é obrigatório)');
    END IF;

    -- 3. Os índices parciais continuam lá ----------------------------------------
    --
    -- Eles não são otimização: são unicidade CONDICIONAL. ux_excecao_aberta é o
    -- que impede duas exceções em aberto para a mesma exigência, e a sua ausência
    -- só aparece no dia em que duas pessoas decidem o mesmo caso.
    SELECT count(*) INTO v_n FROM pg_indexes
    WHERE schemaname = 'public' AND indexdef LIKE '%WHERE%';
    IF v_n < 30 THEN
        PERFORM teste_falhou('SEC-09 · só ' || v_n || ' índices parciais — eram 34. '
                             'Índice parcial aqui é regra de unicidade condicional, e a '
                             'ausência só aparece quando duas linhas conflitantes entram');
    END IF;
    PERFORM teste_ok('SEC-09 · os índices parciais sobreviveram (' || v_n || ')');

    PERFORM 1 FROM pg_indexes WHERE indexname = 'ux_excecao_aberta';
    IF NOT FOUND THEN
        PERFORM teste_falhou('SEC-09 · ux_excecao_aberta sumiu: duas exceções em aberto '
                             'para a mesma exigência voltam a ser possíveis');
    END IF;
    PERFORM teste_ok('SEC-09 · inclusive ux_excecao_aberta, nomeadamente');

    -- 4. Os CHECK continuam lá ---------------------------------------------------
    SELECT count(*) INTO v_n FROM pg_constraint WHERE contype = 'c';
    IF v_n < 110 THEN
        PERFORM teste_falhou('SEC-09 · só ' || v_n || ' restrições CHECK — eram 120. '
                             'São a última rede quando o serviço erra');
    END IF;
    PERFORM teste_ok('SEC-09 · as restrições CHECK sobreviveram (' || v_n || ')');

    -- 5. A restrição de SoD, nomeadamente ----------------------------------------
    PERFORM 1 FROM pg_constraint WHERE conname = 'excecao_sod';
    IF NOT FOUND THEN
        PERFORM teste_falhou('SEC-09 · excecao_sod sumiu: quem solicita volta a poder '
                             'aprovar a própria exceção, e as três camadas viram duas');
    END IF;
    PERFORM teste_ok('SEC-09 · excecao_sod continua impedindo aprovar a própria exceção');

    -- 6. O esquema está completo -------------------------------------------------
    SELECT count(*) INTO v_n FROM pg_tables WHERE schemaname = 'public';
    IF v_n < 30 THEN
        PERFORM teste_falhou('SEC-09 · só ' || v_n || ' tabelas: a restauração está '
                             'incompleta');
    END IF;
    PERFORM teste_ok('SEC-09 · o esquema tem ' || v_n || ' tabelas');
END $$;

ROLLBACK;
