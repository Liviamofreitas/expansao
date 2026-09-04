-- =============================================================================
-- SGDF — Templates de notificação (cap. 11.1 e 11.2)
--
-- Um por momento, vigente. O texto é sóbrio de propósito: o e-mail cobra uma
-- entrega, não explica o sistema. Três coisas que os templates NÃO fazem, e as
-- restrições da V010 recusam se alguém tentar:
--
--   * não falam em anexo — cap. 11.2, "somente link autenticado com expiração";
--   * o fechamento carrega o rodapé de não-substituição do ateste (cap. 11.1),
--     sem o qual o e-mail sugere que o sistema atesta a medição;
--   * nenhum cita pessoa: o corpo lista TIPO e QUANTIDADE, e quem tem acesso vê
--     quais atrás da autenticação. Exigência de escopo profissional é uma por
--     trabalhador, e listá-las nominalmente mandaria nome de gente para a caixa
--     de uma área.
--
-- A versão faz parte do hash gravado em notificacao.conteudo_hash. Corrigir o
-- texto de uma versão já usada invalidaria a trilha em silêncio — por isso
-- corrigir é publicar 1.1 e apagar o `vigente` da 1.0.
-- =============================================================================

INSERT INTO template_notificacao (momento, versao, assunto, corpo, vigente, criado_por)
VALUES
    ('PREVENTIVA', '1.0',
     'SGDF · {quantidade} documento(s) vencem em 48 horas',
     E'Os documentos abaixo vencem em 48 horas.\n\n{itens}\n'
     'Acesse o painel para conferir o que já foi recebido: {link}\n\n'
     'Esta é a única mensagem do dia sobre este ciclo.',
     true, 'carga-inicial'),

    ('COBRANCA', '1.0',
     'SGDF · {quantidade} documento(s) com prazo vencido',
     E'Os documentos abaixo estão com o prazo vencido.\n\n{itens}\n'
     'O ciclo não pode ser publicado enquanto houver exigência bloqueante pendente.\n'
     'Painel: {link}',
     true, 'carga-inicial'),

    ('ESCALONAMENTO', '1.0',
     'SGDF · escalonamento — {quantidade} documento(s) pendentes',
     E'Os documentos abaixo continuam pendentes após o prazo e foram escalonados.\n\n'
     '{itens}\n'
     'Os itens marcados [cópia] são informativos: a responsabilidade pela entrega '
     'permanece com o titular.\n'
     'Painel: {link}',
     true, 'carga-inicial'),

    ('RESOLVIDA', '1.0',
     'SGDF · {quantidade} pendência(s) resolvida(s)',
     E'As pendências abaixo foram resolvidas e estão encerradas.\n\n{itens}\n'
     'Nenhuma ação é necessária. Painel: {link}',
     true, 'carga-inicial'),

    ('FECHAMENTO', '1.0',
     'SGDF · ciclo pronto para faturamento',
     E'O ciclo alcançou o estado PRONTO: todas as exigências bloqueantes foram '
     'atendidas ou dispensadas.\n\n{itens}\n'
     'Painel e book publicado: {link}\n\n'
     '---\n'
     'Este comunicado não substitui o ateste da medição pelo gestor do contrato.',
     true, 'carga-inicial');
