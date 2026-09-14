-- ===========================================================================
-- SGDF — V107 · Parâmetros de classificação (pendência RA-03)
--
-- O peso do alias no score saiu de constante Java e passou a ser cadastro.
-- O cap. 1, princípio 2, pede isso de todo valor ajustável; a F0-05 já tinha
-- tirado os limiares do código-fonte e este ficou para trás.
--
-- POR QUE 0,10, E POR QUE ESCOPO GLOBAL.
--
-- A faixa de triagem é 0,70–0,95. Um bônus de 0,10 a fecha pela metade: um
-- documento que o CONTEÚDO já colocou em 0,86 passa a decidir sozinho; um em
-- 0,72 continua indo para a fila. O alias corrobora, não elege — e é essa a
-- diferença entre "o nome ajuda a confirmar" e "o nome classifica", que é
-- justamente o que o sistema existe para não fazer.
--
-- GLOBAL e não por contrato: o alias é aprendido por TIPO DOCUMENTAL, que é
-- global, e o que o peso mede é quanto se pode confiar no nome do arquivo.
-- Um peso por contrato sugeriria que o mesmo alias vale mais num cliente que
-- noutro, o que ninguém decidiu. Estreitar agora é reversível; alargar depois
-- de alguém ter cadastrado valores por contrato, não.
-- ===========================================================================

BEGIN;

INSERT INTO parametro (chave, escopo, valor, descricao, criado_por)
VALUES (
    'classificacao.peso_do_alias', 'GLOBAL', '0.10'::jsonb,
    'RA-03: quanto um alias confirmado na triagem soma ao score do tipo. '
    'Faixa aceita: 0 a 0,20 — acima disso o nome do arquivo passaria a '
    'classificar sozinho. Zero desliga o bônus: o mesmo padrão volta à fila '
    'todo mês, e a promessa da F1-06 deixa de valer.',
    'carga-inicial')
ON CONFLICT (chave) WHERE escopo = 'GLOBAL' DO NOTHING;

COMMIT;
