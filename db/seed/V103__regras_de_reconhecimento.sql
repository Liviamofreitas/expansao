-- =============================================================================
-- SGDF — Carga das regras de reconhecimento (história F1-03) e dos campos
-- essenciais de V8.
--
-- PROCEDÊNCIA: toda âncora e todo padrão de campo abaixo foi LIDO NO DOCUMENTO
-- REAL da massa de 06 e 07/2026, no texto já normalizado (sem acento, em
-- minúsculas, com espaços colapsados). Nenhum foi suposto a partir do nome do
-- tipo. O que estava suposto — a tabela ilustrativa do cap. 8.5 — é justamente
-- o que a massa corrigiu em dois pontos, anotados nos comentários.
--
-- LIMITE DECLARADO: as regras foram escritas LENDO estes documentos. Medir a
-- precisão sobre eles mede o ajuste, não a generalização. O critério de aceite
-- da F1-03 pede três competências fechadas; há uma (parcialmente duas). A
-- medição atual está em docs/ACHADOS-MASSA-REAL.md, seção 10, com a ressalva.
-- =============================================================================

BEGIN;

-- Idempotência: recarregar substitui a versão 1 em vez de duplicar.
DELETE FROM regra_reconhecimento WHERE criado_por = 'carga-inicial-f1-03';

INSERT INTO regra_reconhecimento (tipo_id, ancoras, campos, campos_essenciais, versao, criado_por)
SELECT t.id, v.ancoras, v.campos, v.essenciais, 1, 'carga-inicial-f1-03'
FROM (VALUES

-- ---------------------------------------------------------------------------
-- Certidões
-- ---------------------------------------------------------------------------
-- CORREÇÃO DO CAP. 8.5: o documento real é uma certidão POSITIVA COM EFEITOS DE
-- NEGATIVA. Uma âncora que exigisse "negativa" no título recusaria a certidão
-- válida que a empresa de fato tem.
('CER.CND_RFB',
 '[{"expressao": "tributos federais e a divida ativa da uniao", "peso": 3, "discriminante": true},
   {"expressao": "procuradoria-geral da fazenda nacional", "peso": 2},
   {"expressao": "secretaria da receita federal do brasil", "peso": 2},
   {"expressao": "certidao (negativa|positiva com efeitos de negativa)", "peso": 1}]'::jsonb,
 '[{"nome": "cnpj",     "padrao": "cnpj:? ?(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})", "grupo": 1},
   {"nome": "validade", "padrao": "valida ate (\\d{2}/\\d{2}/\\d{4})", "grupo": 1},
   {"nome": "natureza", "padrao": "certidao (negativa|positiva com efeitos de negativa)", "grupo": 1}]'::jsonb,
 '[{"campo": "cnpj",     "formato": "cnpj",     "motivo": "V3 confere a titularidade: certidão de outro CNPJ não prova a regularidade desta empresa"},
   {"campo": "validade", "formato": "data",     "motivo": "V5 confere se a certidão cobre a data prevista da nota fiscal"},
   {"campo": "natureza", "formato": "natureza", "motivo": "V5 só aceita negativa ou positiva com efeito de negativa"}]'::jsonb),

('CER.CNDT',
 '[{"expressao": "certidao negativa de debitos trabalhistas", "peso": 3, "discriminante": true},
   {"expressao": "justica do trabalho", "peso": 2},
   {"expressao": "certidao no?:", "peso": 1},
   {"expressao": "validade:", "peso": 1}]'::jsonb,
 '[{"nome": "cnpj",     "padrao": "cnpj:? ?(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})", "grupo": 1},
   {"nome": "validade", "padrao": "validade: (\\d{2}/\\d{2}/\\d{4})", "grupo": 1},
   {"nome": "numero",   "padrao": "certidao no:? ?([\\d/]+)", "grupo": 1},
   {"nome": "natureza", "padrao": "certidao (negativa|positiva)", "grupo": 1}]'::jsonb,
 '[{"campo": "cnpj",     "formato": "cnpj", "motivo": "V3 confere a titularidade da certidão trabalhista"},
   {"campo": "validade", "formato": "data", "motivo": "V5 confere a vigência contra a data prevista da nota fiscal"}]'::jsonb),

('CER.CRF_FGTS',
 '[{"expressao": "certificado de regularidade do fgts", "peso": 3, "discriminante": true},
   {"expressao": "caixa economica federal", "peso": 2},
   {"expressao": "inscricao:", "peso": 1},
   {"expressao": "lei 8\\.036", "peso": 1}]'::jsonb,
 '[{"nome": "inscricao",    "padrao": "inscricao: ?(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})", "grupo": 1},
   {"nome": "validade_ini", "padrao": "validade: ?(\\d{2}/\\d{2}/\\d{4})", "grupo": 1},
   {"nome": "validade_fim", "padrao": "validade: ?\\d{2}/\\d{2}/\\d{4} a (\\d{2}/\\d{2}/\\d{4})", "grupo": 1}]'::jsonb,
 '[{"campo": "inscricao",    "formato": "cnpj", "motivo": "V3 confere a titularidade do certificado do FGTS"},
   {"campo": "validade_fim", "formato": "data", "motivo": "V5 confere o fim da vigência contra a data prevista da nota fiscal"}]'::jsonb),

-- O número da certidão do GDF é extraível; a validade NÃO é — ver o achado A20.
('CER.CND_ESTADUAL',
 '[{"expressao": "subsecretaria da receita", "peso": 3, "discriminante": true},
   {"expressao": "secretaria de estado de economia", "peso": 2},
   {"expressao": "certidao negativa de debitos", "peso": 2},
   {"expressao": "cf/df", "peso": 1}]'::jsonb,
 '[{"nome": "cnpj",   "padrao": "cnpj:? ?(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})", "grupo": 1},
   {"nome": "numero", "padrao": "certidao no:? ?(\\d+)", "grupo": 1}]'::jsonb,
 '[{"campo": "cnpj", "formato": "cnpj", "motivo": "V3 confere a titularidade da certidão estadual/distrital"}]'::jsonb),

('CER.SICAF',
 '[{"expressao": "sistema de cadastramento unificado de fornecedores", "peso": 3, "discriminante": true},
   {"expressao": "sicaf", "peso": 2},
   {"expressao": "situacao do fornecedor", "peso": 2}]'::jsonb,
 '[{"nome": "cnpj",     "padrao": "cnpj:? ?(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})", "grupo": 1},
   {"nome": "situacao", "padrao": "situacao do fornecedor: (\\w+)", "grupo": 1}]'::jsonb,
 '[{"campo": "cnpj",     "formato": "cnpj",  "motivo": "V3 confere de quem é a declaração do SICAF"},
   {"campo": "situacao", "formato": "texto", "motivo": "a declaração sem a situação do fornecedor não prova nada"}]'::jsonb),

-- As duas certidões do TJDFT compartilham o cabeçalho inteiro — mesmo tribunal,
-- mesma fórmula, mesmas instâncias. Só a expressão da distribuição as separa,
-- e por isso ela é discriminante nas duas.
('CER.CND_CIVEL_CRIMINAL',
 '[{"expressao": "acoes civeis e criminais", "peso": 3, "discriminante": true},
   {"expressao": "certidao (negativa|positiva) de distribuicao", "peso": 2},
   {"expressao": "1a e 2a instancias", "peso": 1},
   {"expressao": "tribunal de justica", "peso": 1}]'::jsonb,
 '[{"nome": "cnpj",     "padrao": "cpf/cnpj de: [^0-9]{0,60}(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})", "grupo": 1},
   {"nome": "natureza", "padrao": "certidao (negativa|positiva) de distribuicao", "grupo": 1}]'::jsonb,
 '[{"campo": "cnpj",     "formato": "cnpj",     "motivo": "V3 confere contra quem a distribuição foi consultada"},
   {"campo": "natureza", "formato": "natureza", "motivo": "V5 precisa saber se a certidão é negativa ou positiva"}]'::jsonb),

('CER.CND_FALENCIA',
 '[{"expressao": "falencias e recuperacoes judiciais", "peso": 3, "discriminante": true},
   {"expressao": "certidao (negativa|positiva) de distribuicao", "peso": 2},
   {"expressao": "1a e 2a instancias", "peso": 1},
   {"expressao": "tribunal de justica", "peso": 1}]'::jsonb,
 '[{"nome": "cnpj",     "padrao": "cpf/cnpj de: [^0-9]{0,60}(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})", "grupo": 1},
   {"nome": "natureza", "padrao": "certidao (negativa|positiva) de distribuicao", "grupo": 1}]'::jsonb,
 '[{"campo": "cnpj",     "formato": "cnpj",     "motivo": "V3 confere contra quem a distribuição foi consultada"},
   {"campo": "natureza", "formato": "natureza", "motivo": "V5 precisa saber se a certidão é negativa ou positiva"}]'::jsonb),

-- ---------------------------------------------------------------------------
-- INSS e tributos
-- ---------------------------------------------------------------------------
-- CORREÇÃO ENCONTRADA NA MASSA: o comprovante bancário REPRODUZ O DARF INTEIRO
-- dentro dele — "composicao do documento de arrecadacao", "periodo de
-- apuracao", "valor total do documento". Um discriminante tirado da composição
-- faz os dois marcarem 1,00 e o comprovante vira DCTFWeb. O que só existe na
-- DCTFWeb é o RECIBO DE TRANSMISSÃO, que é o que o cap. 8.5 já declarava.
('INS.DCTFWEB',
 '[{"expressao": "dctfweb|recibo de entrega", "peso": 3, "discriminante": true},
   {"expressao": "documento de arrecadacao de receitas federais", "peso": 2},
   {"expressao": "periodo de apuracao", "peso": 1},
   {"expressao": "composicao do documento de arrecadacao", "peso": 1}]'::jsonb,
 '[{"nome": "cnpj",        "padrao": "(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2})", "grupo": 1},
   {"nome": "valor_total", "padrao": "valor total do documento ([\\d.]+,\\d{2})", "grupo": 1}]'::jsonb,
 '[{"campo": "cnpj",        "formato": "cnpj",  "motivo": "V3 confere de quem é a declaração"},
   {"campo": "valor_total", "formato": "valor", "motivo": "R02 concilia o total da DCTFWeb com a soma dos DARF pagos"}]'::jsonb),

-- ---------------------------------------------------------------------------
-- FGTS
-- ---------------------------------------------------------------------------
('FGT.GUIA',
 '[{"expressao": "guia do fgts digital", "peso": 3, "discriminante": true},
   {"expressao": "valor a recolher", "peso": 2},
   {"expressao": "identificador", "peso": 1},
   {"expressao": "pagar este documento ate", "peso": 1}]'::jsonb,
 '[{"nome": "valor", "padrao": "valor a recolher[^0-9]{0,40}([\\d.]+,\\d{2})", "grupo": 1}]'::jsonb,
 '[{"campo": "valor", "formato": "valor", "motivo": "R01 concilia o valor da guia com o comprovante de pagamento"}]'::jsonb),

('FGT.RELATORIO_DIGITAL',
 '[{"expressao": "relacao de trabalhadores", "peso": 3, "discriminante": true},
   {"expressao": "detalhe da guia a ser emitida", "peso": 2},
   {"expressao": "qtd\\. trabalhadores fgts", "peso": 2},
   {"expressao": "nome trabalhador", "peso": 1}]'::jsonb,
 '[{"nome": "total_guia",    "padrao": "total da guia \\(fgts\\): ([\\d.]+,\\d{2})", "grupo": 1},
   {"nome": "trabalhadores", "padrao": "qtd\\. trabalhadores fgts: (\\d+)", "grupo": 1},
   {"nome": "vencimento",    "padrao": "vencimento da guia: (\\d{2}/\\d{2}/\\d{4})", "grupo": 1}]'::jsonb,
 '[{"campo": "total_guia",    "formato": "valor",   "motivo": "R09 concilia o total do relatório com a guia e com a base da folha"},
   {"campo": "trabalhadores", "formato": "inteiro", "motivo": "R11 confere a cobertura dos trabalhadores alocados"}]'::jsonb),

-- ---------------------------------------------------------------------------
-- Folha e benefícios
-- ---------------------------------------------------------------------------
-- Os campos do contracheque NÃO são extraíveis por regex de vizinhança: o
-- documento é tabular e a leitura correta é a do LeitorDeContracheque, por
-- coordenada. Ver o achado A20. Aqui ficam só as âncoras.
('FOL.CONTRACHEQUE',
 '[{"expressao": "recibo de pagamento", "peso": 3, "discriminante": true},
   {"expressao": "total de proventos", "peso": 2},
   {"expressao": "total de descontos", "peso": 2},
   {"expressao": "liquido a receber", "peso": 2}]'::jsonb,
 '[]'::jsonb, '[]'::jsonb),

('BEN.RELACAO_VA_VR',
 '[{"expressao": "discriminacao dos beneficios", "peso": 3, "discriminante": true},
   {"expressao": "relatorio de transacao", "peso": 2},
   {"expressao": "total de bene ?ciarios", "peso": 2},
   {"expressao": "disponibilizacao do beneficio", "peso": 1}]'::jsonb,
 '[{"nome": "soma",          "padrao": "soma dos beneficios:[^0-9]{0,60}r\\$ ([\\d.]+,\\d{2})", "grupo": 1},
   {"nome": "beneficiarios", "padrao": "total de bene ?ciarios:? ?(\\d+)", "grupo": 1}]'::jsonb,
 '[{"campo": "soma",          "formato": "valor",   "motivo": "R08 concilia a soma da relação com o comprovante de pagamento"},
   {"campo": "beneficiarios", "formato": "inteiro", "motivo": "R08 confere a cobertura contra quem tem a rubrica na folha"}]'::jsonb)

) AS v(codigo, ancoras, campos, essenciais)
JOIN tipo_documental t ON t.codigo = v.codigo;

COMMIT;
