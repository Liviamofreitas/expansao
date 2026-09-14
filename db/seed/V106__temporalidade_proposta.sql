-- ===========================================================================
-- SGDF — V106 · Tabela de temporalidade PROPOSTA (pendência A08)
--
-- TODAS AS LINHAS ENTRAM SEM APROVAÇÃO, E É ASSIM QUE TÊM DE ENTRAR.
--
-- `aprovado_em` nulo significa: a proposta existe, está escrita, tem
-- fundamento — e não autoriza apagar coisa alguma. O expurgo recusa cada uma
-- delas pelo nome até que o jurídico e o DPO aprovem, o que é um INSERT de
-- `aprovado_em`/`aprovado_por` e não um deploy.
--
-- Semear um prazo já aprovado aqui seria engenharia decidindo quanto tempo se
-- guarda prova trabalhista e dado pessoal de ±890 pessoas. Não é.
--
-- Idempotente: ON CONFLICT (alvo) DO NOTHING. Reaplicar a carga NÃO reverte
-- uma aprovação já dada — isso seria um deploy revogando decisão jurídica em
-- silêncio, e o silêncio é o problema.
-- ===========================================================================

BEGIN;

INSERT INTO temporalidade (classe, alvo, marco, prazo_meses, acao, fundamento, criado_por)
VALUES

-- 1 -------------------------------------------------------------------------
('Registro de acesso ao sistema',
 'ACESSO_OBSERVADO', 'REGISTRO', 12, 'EXPURGAR',
 'Marco Civil da Internet, art. 15 (guarda mínima de 6 meses dos registros de '
 'acesso a aplicação) e ISO/IEC 27001 A.8.15. Proposta de 12 meses: cobre um '
 'ciclo inteiro de recertificação anual (SEC-10), que é o uso legítimo do dado, '
 'e para de crescer depois disso. Guardar mais é manter um mapa de quem viu o '
 'quê sem finalidade que o justifique.',
 'proposta-a08'),

-- 2 -------------------------------------------------------------------------
('Notificação da régua de cobrança',
 'NOTIFICACAO', 'REGISTRO', 60, 'EXPURGAR',
 'Evidência de que a área foi avisada da pendência (cap. 11.1). Acompanha a '
 'prescrição quinquenal do art. 7º, XXIX da CF: enquanto a obrigação que ela '
 'cobrou puder ser discutida, o aviso é prova de diligência. A notificação não '
 'contém dado do titular nem anexo — só destinatário interno e link (cap. 11.2), '
 'então o risco de retenção aqui é baixo e o de eliminação precoce é maior.',
 'proposta-a08'),

-- 3 -------------------------------------------------------------------------
-- REVISAR, NÃO EXPURGAR. A escolha é deliberada e é a mais importante desta
-- carga: nenhum documento fiscal sai por decisão de agendador. Vencido o prazo,
-- ele vira lista para uma pessoa olhar. O custo de guardar demais é retenção; o
-- de apagar cedo é perder a prova no meio de uma reclamatória.
('Documento comprobatório coletado',
 'DOCUMENTO', 'DESLIGAMENTO_DO_PROFISSIONAL', 60, 'REVISAR',
 'Art. 7º, XXIX da CF: a prescrição corre da EXTINÇÃO DO CONTRATO DE TRABALHO, '
 'não da competência — um documento de 2020 de quem se desligou em 2029 ainda é '
 'prova em 2034. Ação REVISAR e não EXPURGAR porque parte do acervo é de escopo '
 'CONTRATO e CORPORATIVO, sem profissional e portanto sem marco computável: '
 'esses NÃO entram na lista e continuam guardados. Fixar EXPURGAR aqui daria ao '
 'agendador o poder de destruir evidência fiscal.',
 'proposta-a08'),

-- 4 -------------------------------------------------------------------------
('Cadastro do profissional',
 'PROFISSIONAL', 'DESLIGAMENTO_DO_PROFISSIONAL', 60, 'ANONIMIZAR',
 'Art. 16 da LGPD: eliminação após a finalidade, salvo guarda por obrigação '
 'legal. Anonimizar e não apagar porque a linha é referenciada por alocação e '
 'exigência — apagá-la quebraria a rastreabilidade do cap. 16 de documentos que '
 'ainda existem. Anonimizada, o fato histórico sobrevive e o titular some, que é '
 'exatamente o que o art. 16 permite.',
 'proposta-a08')

ON CONFLICT (alvo) DO NOTHING;

-- CAMPO_EXTRAIDO ficou de fora de propósito: ele segue o documento de origem e
-- não tem vida própria. Declarar prazo para ele criaria a possibilidade de um
-- documento sobreviver aos valores que foram lidos dele — e o documento sem os
-- campos não sustenta a conciliação do cap. 9, que é o que ele existe para
-- sustentar. Quando o expurgo de DOCUMENTO existir, ele leva os campos junto.

COMMIT;
