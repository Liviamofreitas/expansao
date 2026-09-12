-- =============================================================================
-- SGDF — V018 · Execução de job (pendência RA-07)
--
-- ORIGEM: a abertura de ciclo, a varredura, a conciliação e a régua existem, são
-- idempotentes e têm teste — e nenhum agendador as chama. Esta migração não liga
-- nada: ela cria o que torna possível saber SE ligou.
--
-- O MODO DE FALHA QUE ESTE REGISTRO EXISTE PARA IMPEDIR.
--
-- Um agendador morto é silencioso. Sem registro de execução, "a varredura rodou
-- e não achou arquivo novo" e "a varredura não rodou" produzem exatamente o
-- mesmo estado observável: nenhum documento novo, nenhuma pendência nova,
-- nenhum erro. O painel fica verde, os indicadores do cap. 21 ficam bonitos, e
-- ficam assim JUSTAMENTE PORQUE nada aconteceu.
--
-- É o mesmo padrão que já mordeu duas vezes nesta base: a F0-05, onde "nada
-- mudou" também acontece quando nada é encontrado (achados § 33.2), e a
-- completude por primeira conferência, que daria 100% por a régua não rodar
-- (achados § 43.4). Num sistema cujo trabalho é notar o que falta, um
-- componente cuja falha se parece com sucesso é o pior defeito possível.
--
-- Uma linha por tentativa, inclusive pela que não fez nada: `itens = 0` com
-- resultado SUCESSO é informação, e é diferente de linha nenhuma.
-- =============================================================================

BEGIN;

CREATE TABLE execucao_de_job (
    id           bigserial PRIMARY KEY,
    job          text        NOT NULL,
    iniciada_em  timestamptz NOT NULL DEFAULT now(),
    terminada_em timestamptz,
    resultado    text CHECK (resultado IN ('SUCESSO', 'FALHA', 'CONCORRENTE')),
    itens        integer,
    detalhe      text,
    instancia    text        NOT NULL,
    CONSTRAINT job_nome_substantivo CHECK (btrim(job) <> ''),
    -- Terminou sem dizer como é pior que não ter terminado: a linha ficaria
    -- indistinguível de uma execução em andamento, e o alerta de job travado
    -- dispararia para sempre sobre algo que já acabou.
    CONSTRAINT job_termino_com_resultado CHECK (
        (terminada_em IS NULL AND resultado IS NULL)
     OR (terminada_em IS NOT NULL AND resultado IS NOT NULL)),
    -- FALHA sem detalhe manda alguém procurar sem nada. SUCESSO exige a
    -- contagem, porque é ela que distingue "rodou e achou zero" de "rodou".
    CONSTRAINT job_falha_com_detalhe CHECK (
        resultado <> 'FALHA' OR (detalhe IS NOT NULL AND btrim(detalhe) <> '')),
    CONSTRAINT job_sucesso_com_contagem CHECK (
        resultado <> 'SUCESSO' OR itens IS NOT NULL)
);

CREATE INDEX ix_job_ultima ON execucao_de_job (job, iniciada_em DESC);
CREATE INDEX ix_job_em_andamento ON execucao_de_job (job, iniciada_em)
    WHERE terminada_em IS NULL;

COMMENT ON TABLE execucao_de_job IS
    'RA-07: sem isto, "rodou e não achou nada" e "não rodou" são o mesmo estado '
    'observável — e o segundo deixa o painel verde por nada ter acontecido.';
COMMENT ON COLUMN execucao_de_job.resultado IS
    'CONCORRENTE = acordou e outra instância tinha o lock. É fato útil: '
    'distingue agendador morto de agendador vivo e disputado.';
COMMENT ON COLUMN execucao_de_job.instancia IS
    'Quem executou. Com duas instâncias, é o que permite ver qual delas parou.';

COMMIT;
