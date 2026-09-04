-- ===========================================================================
-- SGDF — V100 · Carga inicial a partir do Anexo 1
-- GERADO por tools/gerar_carga_inicial.py — NÃO EDITAR À MÃO.
-- Regenerar:  python3 tools/gerar_carga_inicial.py
--
-- História F0-03. Cap. 17: seed como migration de dados auditável.
-- Idempotente: ON CONFLICT DO NOTHING em toda inserção.
-- ===========================================================================

BEGIN;

-- --- modalidades (cap. 5.1) ------------------------------------------------
INSERT INTO modalidade (codigo, nome, criado_por) VALUES
    ('OUTSOURCING', 'Outsourcing com alocação de profissionais', 'carga-inicial-anexo1'),
    ('SUSTENTACAO', 'Sustentação de sistemas', 'carga-inicial-anexo1'),
    ('ENTREGA',     'Entrega por produto ou escopo fechado', 'carga-inicial-anexo1'),
    ('MISTO',       'Misto', 'carga-inicial-anexo1')
ON CONFLICT (codigo) DO NOTHING;

-- --- clientes (8) --------------------------------------------
-- CNPJ e esfera pendentes de cadastro: o Anexo 1 não os carrega. O CNPJ é
-- obrigatório e único, então cada cliente entra com um marcador que a tela de
-- cadastro (F0-02) substitui. Nenhum CNPJ é inventado.
INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) VALUES ('BNB', '00000000000001', 'PUBLICA', false, 'carga-inicial-anexo1') ON CONFLICT (cnpj) DO NOTHING;
INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) VALUES ('CEF', '00000000000002', 'PUBLICA', false, 'carga-inicial-anexo1') ON CONFLICT (cnpj) DO NOTHING;
INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) VALUES ('DOCAS', '00000000000003', 'PUBLICA', false, 'carga-inicial-anexo1') ON CONFLICT (cnpj) DO NOTHING;
INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) VALUES ('SEFAZ RS', '00000000000004', 'PUBLICA', false, 'carga-inicial-anexo1') ON CONFLICT (cnpj) DO NOTHING;
INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) VALUES ('SESCOOP', '00000000000005', 'PUBLICA', false, 'carga-inicial-anexo1') ON CONFLICT (cnpj) DO NOTHING;
INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) VALUES ('TERRACAP', '00000000000006', 'PUBLICA', false, 'carga-inicial-anexo1') ON CONFLICT (cnpj) DO NOTHING;
INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) VALUES ('TJ CE', '00000000000007', 'PUBLICA', false, 'carga-inicial-anexo1') ON CONFLICT (cnpj) DO NOTHING;
INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) VALUES ('Telebrás', '00000000000008', 'PUBLICA', false, 'carga-inicial-anexo1') ON CONFLICT (cnpj) DO NOTHING;

-- --- versão inicial da matriz ---------------------------------------------
INSERT INTO versao_matriz (numero, publicada_por, motivo) VALUES
    ('1.0', 'carga-inicial-anexo1', 'Carga inicial do catálogo canônico a partir do Anexo 1')
ON CONFLICT (numero) DO NOTHING;

-- --- tipos documentais (51) ------------------------------------
-- escopo, sigilo, formatos e condicional_grupo vêm de
-- dados/complemento_tipo_documental.csv: são DERIVADOS, não confirmados
-- (coluna CONFIRMADO=NAO). Ver docs/ERRATA-V1.md, achado E-05.
-- repositorio_mestre vem da coluna REPOSITORIO da matriz (A09).
INSERT INTO tipo_documental
    (codigo, nome, familia, escopo, evento, defasagem, formatos, criticidade,
     sigilo, fonte_mestre, condicional_grupo, repositorio_mestre,
     repositorios_alternativos, criado_por)
VALUES
    ('OPE.RELATORIO_MEDICAO', 'Relatório técnico de medição / atividades', 'Operação e medição', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Operação', NULL, 'JIRA', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('OPE.PLANILHA_MEDICAO', 'Planilha de faturamento (memória de cálculo)', 'Operação e medição', 'CONTRATO', 'MENSAL', 'M', '["pdf", "xlsx"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Operação', NULL, 'JIRA', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('OPE.AUTORIZACAO_FATURAMENTO', 'Autorização formal de faturamento', 'Operação e medição', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Operação', NULL, 'JIRA', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('OPE.ATESTE_RECEBIMENTO', 'Ateste / Termo de recebimento do cliente', 'Operação e medição', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Cliente', NULL, 'JIRA', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('OPE.EMPENHO', 'Nota de empenho vigente', 'Operação e medição', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Cliente', NULL, 'JIRA', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('OPE.RELACAO_ALOCADOS', 'Relação de empregados alocados no contrato', 'Operação e medição', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Operação', NULL, 'JIRA', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('OPE.DOC_TECNICA', 'Documentação técnica dos sistemas desenvolvidos', 'Operação e medição', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'NAO_BLOQUEANTE', 'PUBLICO_CLIENTE', 'Operação', NULL, 'JIRA', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('OPE.FOLHA_PONTO', 'Folha de ponto / ponto eletrônico', 'Operação e medição', 'PROFISSIONAL', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('OPE.MOVIMENTACAO_PESSOAL', 'Movimentação de pessoal do contrato', 'Operação e medição', 'CONTRATO', 'EVENTUAL', 'M', '["pdf"]'::jsonb, 'NAO_BLOQUEANTE', 'PUBLICO_CLIENTE', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FIS.NOTA_FISCAL', 'Nota fiscal de serviço', 'Fiscal', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '["JIRA"]'::jsonb, 'carga-inicial-anexo1'),
    ('FIS.NF_XML', 'XML da nota fiscal', 'Fiscal', 'CONTRATO', 'MENSAL', 'M', '["xml"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '["JIRA"]'::jsonb, 'carga-inicial-anexo1'),
    ('FIS.CARTA_ENTREGA_NF', 'Carta de entrega da nota fiscal', 'Fiscal', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '["JIRA"]'::jsonb, 'carga-inicial-anexo1'),
    ('CER.CND_RFB', 'CND Receita Federal / Dívida Ativa da União', 'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('CER.CRF_FGTS', 'Certificado de Regularidade do FGTS (CRF)', 'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('CER.CNDT', 'Certidão Negativa de Débitos Trabalhistas (CNDT)', 'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('CER.SICAF', 'Consulta SICAF', 'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('CER.CND_ESTADUAL', 'Certidão de regularidade fiscal estadual/distrital', 'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('CER.CND_CIVEL_CRIMINAL', 'Certidão negativa de distribuição cível e criminal', 'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('CER.CND_FALENCIA', 'Certidão negativa de falência e recuperação judicial', 'Certidões e regularidade', 'CORPORATIVO', 'MENSAL', 'VIGENCIA_NF', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PUBLICO_CLIENTE', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FOL.FOPAG', 'Folha de pagamento analítica', 'Folha', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FOL.COMPROVANTE_PG', 'Comprovante de pagamento da folha', 'Folha', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FOL.CONTRACHEQUE', 'Contracheque / recibo de pagamento', 'Folha', 'PROFISSIONAL', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('DEC.FOPAG_13', 'Folha de pagamento do 13º salário', '13º salário', 'CONTRATO', '13O', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('DEC.COMPROVANTE_PG_13', 'Comprovante de pagamento do 13º salário', '13º salário', 'CONTRATO', '13O', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FGT.GUIA', 'Guia FGTS Digital', 'FGTS', 'CORPORATIVO', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FGT.COMPROVANTE_PG', 'Comprovante de pagamento do FGTS', 'FGTS', 'CORPORATIVO', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FGT.EXTRATO', 'Extrato analítico do FGTS por trabalhador', 'FGTS', 'PROFISSIONAL', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FGT.RELATORIO_DIGITAL', 'Relatórios do FGTS Digital (individualização)', 'FGTS', 'PROFISSIONAL', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FGT.GUIA_RESCISORIA', 'Guia FGTS rescisória (GRRF)', 'FGTS', 'PROFISSIONAL', 'RESCISAO', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FGT.COMPROVANTE_PG_GRRF', 'Comprovante de pagamento da GRRF', 'FGTS', 'PROFISSIONAL', 'RESCISAO', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('INS.DCTFWEB', 'DCTFWeb completa', 'INSS e tributos', 'CORPORATIVO', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('INS.DARF', 'DARF de recolhimento (DCTFWeb)', 'INSS e tributos', 'CORPORATIVO', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('INS.COMPROVANTE_PG', 'Comprovante de pagamento do INSS', 'INSS e tributos', 'CORPORATIVO', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('INS.COMPROVANTE_PG_IRRF', 'Comprovante de pagamento do IRRF', 'INSS e tributos', 'CORPORATIVO', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('INS.PARCELAMENTO_TABELA', 'Tabela resumo dos parcelamentos INSS', 'INSS e tributos', 'CORPORATIVO', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'NAO_BLOQUEANTE', 'INTERNO', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('INS.PARCELAMENTO_COMPROV', 'DARF e comprovante dos parcelamentos INSS', 'INSS e tributos', 'CORPORATIVO', 'MENSAL', 'M_MENOS_1', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('BEN.RELACAO_PLANO_SAUDE', 'Relação de beneficiários do plano de saúde', 'Benefícios', 'PROFISSIONAL', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL_SENSIVEL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('BEN.COMPROVANTE_PLANO_SAUDE', 'Comprovante de pagamento do plano de saúde', 'Benefícios', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL_SENSIVEL', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('BEN.RELACAO_VA_VR', 'Relação de vale-alimentação/refeição', 'Benefícios', 'PROFISSIONAL', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('BEN.COMPROVANTE_VA_VR', 'Comprovante de pagamento do VA/VR', 'Benefícios', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('BEN.RELACAO_VT', 'Relação de vale-transporte', 'Benefícios', 'PROFISSIONAL', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', 'VT_ADESAO', 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('BEN.COMPROVANTE_VT', 'Comprovante de pagamento do VT', 'Benefícios', 'CONTRATO', 'MENSAL', 'M', '["pdf"]'::jsonb, 'BLOQUEANTE', 'INTERNO', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('BEN.TERMO_NAO_ADESAO_VT', 'Termo de não adesão ao VT', 'Benefícios', 'PROFISSIONAL', 'EVENTUAL', 'EVENTO', '["pdf"]'::jsonb, 'NAO_BLOQUEANTE', 'PESSOAL', 'DP', 'VT_ADESAO', 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FER.AVISO', 'Aviso de férias', 'Férias', 'PROFISSIONAL', 'FERIAS', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FER.RECIBO', 'Recibo de férias', 'Férias', 'PROFISSIONAL', 'FERIAS', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('FER.COMPROVANTE_PG', 'Comprovante de pagamento de férias', 'Férias', 'PROFISSIONAL', 'FERIAS', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('RES.AVISO_PREVIO', 'Aviso prévio', 'Rescisão', 'PROFISSIONAL', 'RESCISAO', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('RES.TRCT', 'TRCT / Termo de rescisão', 'Rescisão', 'PROFISSIONAL', 'RESCISAO', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('RES.ASO_DEMISSIONAL', 'ASO demissional', 'Rescisão', 'PROFISSIONAL', 'RESCISAO', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL_SENSIVEL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('RES.COMPROVANTE_PG', 'Comprovante de pagamento da rescisão', 'Rescisão', 'PROFISSIONAL', 'RESCISAO', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL', 'Financeiro', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1'),
    ('ADM.DOCUMENTACAO', 'Documentação admissional', 'Admissão', 'PROFISSIONAL', 'ADMISSAO', 'EVENTO', '["pdf"]'::jsonb, 'BLOQUEANTE', 'PESSOAL_SENSIVEL', 'DP', NULL, 'OWNCLOUD', '[]'::jsonb, 'carga-inicial-anexo1')
ON CONFLICT (codigo) DO NOTHING;

-- --- aliases legados (59 distintos de 72 brutos) -------------------
-- Cap. 8.3: o alias dá bônus de score ao reconhecimento.
INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado, origem, criado_por)
VALUES
    ((SELECT id FROM tipo_documental WHERE codigo = 'RES.ASO_DEMISSIONAL'), 'ASO_DEMISSIONAL', 'aso_demissional', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.AUTORIZACAO_FATURAMENTO'), 'AUTORIZAÇÃO_DE_FATURAMENTO', 'autorizacao_de_faturamento', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FER.AVISO'), 'AVISO_DE_FERIAS', 'aviso_de_ferias', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'RES.AVISO_PREVIO'), 'AVISO_PREVIO', 'aviso_previo', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FIS.CARTA_ENTREGA_NF'), 'CARTA_NOTA_FISCAL', 'carta_nota_fiscal', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'CER.CND_CIVEL_CRIMINAL'), 'CND_CIVEL_E_CRIMINAL', 'cnd_civel_e_criminal', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'CER.CND_FALENCIA'), 'CND FALENCIA E RECUP JUD', 'cnd_falencia_e_recup_jud', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'CER.CND_ESTADUAL'), 'CND_GDF', 'cnd_gdf', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'CER.CND_RFB'), 'CND_RFB', 'cnd_rfb', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'CER.CNDT'), 'CNDT', 'cndt', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'DEC.COMPROVANTE_PG_13'), 'COMPROVANTE_PG_13º SALARIO', 'comprovante_pg_13o_salario', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.COMPROVANTE_VA_VR'), 'COMPROVANTE_PG_ALIMENTAÇÃO', 'comprovante_pg_alimentacao', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FER.COMPROVANTE_PG'), 'COMPROVANTE_PG_FERIAS', 'comprovante_pg_ferias', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FGT.COMPROVANTE_PG'), 'COMPROVANTE_PG_FGTS', 'comprovante_pg_fgts', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FOL.COMPROVANTE_PG'), 'COMPROVANTE_PG_FOLHA', 'comprovante_pg_folha', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FGT.COMPROVANTE_PG_GRRF'), 'COMPROVANTE_PG_GRRF', 'comprovante_pg_grrf', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'INS.COMPROVANTE_PG'), 'COMPROVANTE_PG_INSS', 'comprovante_pg_inss', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'INS.COMPROVANTE_PG_IRRF'), 'COMPROVANTE_PG_IRRF', 'comprovante_pg_irrf', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.COMPROVANTE_PLANO_SAUDE'), 'COMPROVANTE_PG_PLANO_DE_SAUDE', 'comprovante_pg_plano_de_saude', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'RES.COMPROVANTE_PG'), 'COMPROVANTE_PG_RECSISÃO', 'comprovante_pg_recsisao', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'RES.COMPROVANTE_PG'), 'COMPROVANTE_PG_RESCISÃO', 'comprovante_pg_rescisao', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.COMPROVANTE_VT'), 'COMPROVANTE_PG_TRANSPORTE', 'comprovante_pg_transporte', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.COMPROVANTE_VA_VR'), 'COMPROVANTE_PG_VA_VR', 'comprovante_pg_va_vr', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.COMPROVANTE_VT'), 'COMPROVANTE_PG_VT', 'comprovante_pg_vt', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'INS.PARCELAMENTO_COMPROV'), 'COMPROVANTES_PARCELAMENTOS', 'comprovantes_parcelamentos', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FOL.CONTRACHEQUE'), 'CONTRACHEQUES', 'contracheques', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'CER.CRF_FGTS'), 'CRF_FGTS', 'crf_fgts', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'INS.DCTFWEB'), 'DCTFWEB_COMPLETA', 'dctfweb_completa', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'INS.DARF'), 'DCTFWEB_DARF', 'dctfweb_darf', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'ADM.DOCUMENTACAO'), 'DOC_ADMISSAO', 'doc_admissao', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.DOC_TECNICA'), 'DOCUMENTAÇÃO_TÉCNICA_DOS_SISTEMAS_DESENVOLVIDOS', 'documentacao_tecnica_dos_sistemas_desenvolvidos', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.AUTORIZACAO_FATURAMENTO'), 'E-MAIL_AUTORIZACAO_EMISSAO_NF', 'e_mail_autorizacao_emissao_nf', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.EMPENHO'), 'EMPENHO', 'empenho', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FGT.EXTRATO'), 'EXTRATO_FGTS', 'extrato_fgts', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FGT.RELATORIO_DIGITAL'), 'FGTS-DETALHE_DA_GUIA', 'fgts_detalhe_da_guia', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.FOLHA_PONTO'), 'FOLHAS_DE_PONTO', 'folhas_de_ponto', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FOL.FOPAG'), 'FOPAG', 'fopag', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'DEC.FOPAG_13'), 'FOPAG_13º', 'fopag_13o', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'DEC.FOPAG_13'), 'FOPAGO_13º SALÁRIO', 'fopago_13o_salario', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FGT.GUIA_RESCISORIA'), 'GUIA_GFD', 'guia_gfd', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.MOVIMENTACAO_PESSOAL'), 'MOVIMENTACAO_DE_PESSOAL', 'movimentacao_de_pessoal', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FIS.NOTA_FISCAL'), 'NOTA _FISCAL', 'nota_fiscal', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.PLANILHA_MEDICAO'), 'PLANILHA_DE_FATURAMENTO', 'planilha_de_faturamento', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FER.RECIBO'), 'RECIBO_DE_FERIAS', 'recibo_de_ferias', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.RELACAO_VA_VR'), 'RELACAO_DE_VA_VR', 'relacao_de_va_vr', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.RELACAO_VT'), 'RELAÇÃO_DE_VT', 'relacao_de_vt', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.RELACAO_ALOCADOS'), 'RELAÇÃO_DOS_EMPREGADOS_ALOCADOS_NO_CONTRATO_PARA_PRESTAÇÃO_DE_SERVIÇOS', 'relacao_dos_empregados_alocados_no_contrato_para_prestacao_de_servicos', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.RELACAO_PLANO_SAUDE'), 'RELACAO_PLANO_DE_SAÚDE', 'relacao_plano_de_saude', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.RELATORIO_MEDICAO'), 'RELATORIO_DE_ATIVIDADES', 'relatorio_de_atividades', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.RELATORIO_MEDICAO'), 'RELATÓRIO_DE_ATIVIDADES_DESENVOLVIDAS_POR _CADA_UM_DOS_POSTOS_DE_TRABALHO', 'relatorio_de_atividades_desenvolvidas_por_cada_um_dos_postos_de_trabalho', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.RELATORIO_MEDICAO'), 'RELATORIO_DE_FATURAMENTO', 'relatorio_de_faturamento', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.RELATORIO_MEDICAO'), 'RTA_RELATORIO_TECNICO_ATIVIDADES_DE_FATURAMENTO', 'rta_relatorio_tecnico_atividades_de_faturamento', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'CER.SICAF'), 'SICAF', 'sicaf', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'INS.PARCELAMENTO_TABELA'), 'TABELA_RESUMO_PARCELAMENTOS', 'tabela_resumo_parcelamentos', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'BEN.TERMO_NAO_ADESAO_VT'), 'TERMO_DE_ADESÃO_VT', 'termo_de_adesao_vt', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.ATESTE_RECEBIMENTO'), 'TERMO_DE_RECEBIMENTO', 'termo_de_recebimento', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'RES.TRCT'), 'TRCT', 'trct', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'OPE.ATESTE_RECEBIMENTO'), 'TRD', 'trd', 'LEGADO', 'carga-inicial-anexo1'),
    ((SELECT id FROM tipo_documental WHERE codigo = 'FIS.NF_XML'), 'XML', 'xml', 'LEGADO', 'carga-inicial-anexo1')
ON CONFLICT (texto_normalizado) DO NOTHING;

-- ---------------------------------------------------------------------
-- ALIASES DESCARTADOS POR COLISÃO (achado E-02, decidido)
--
-- Cada um destes nomes aparecia como alias de mais de um tipo canônico.
-- Decisão: não pertencem a nenhum. Um nome que serve a dois documentos
-- não é sinal confiável, e o cap. 8.3 já identifica pelo conteúdo — o
-- nome é apenas reforço. Atribuí-lo a um dos dois daria bônus de score
-- ao tipo errado metade das vezes.
--
-- A desambiguação tem de vir das âncoras de conteúdo dos tipos
-- envolvidos, que precisam ser mutuamente exclusivas (cap. 8.5).
-- ---------------------------------------------------------------------
--   'gfd_guia_do_fgts' aparecia em: FGT.GUIA, FGT.RELATORIO_DIGITAL

-- --- regras de conciliação (12) ------------------------------
-- Numeração canônica do Anexo 1, aba REGRAS_CONCILIACAO (decisão sobre E-01).
--
-- Todas entram em modo ALERTA: a coluna 'Tolerância (preencher)' do anexo
-- está vazia, e o cap. 9 determina que regra sem tolerância cadastrada opera
-- em alerta. A restrição regra_conc_sem_tolerancia_nao_bloqueia (V001) impõe
-- isso no banco. A severidade que o anexo PRETENDE fica em modo_pretendido;
-- a view regra_conciliacao_a_parametrizar lista o que falta parametrizar.
INSERT INTO regra_conciliacao
    (codigo, nome, tipos_envolvidos, logica, tolerancia, modo, modo_pretendido,
     fase, criado_por)
VALUES
    ('R01', 'Cobertura do plano de saúde', '[]'::jsonb, 'Profissional na relação sem desconto em folha, ou desconto sem cobertura | fontes: BEN.RELACAO_PLANO_SAUDE x rubrica de desconto na FOL.FOPAG x BEN.COMPROVANTE_PLANO_SAUDE', NULL, 'ALERTA', 'BLOQUEIO', '1B', 'carga-inicial-anexo1'),
    ('R02', 'Cobertura do FGTS', '[]'::jsonb, 'Recolhimento sem individualização por trabalhador; base divergente | fontes: FOL.FOPAG (base) x FGT.GUIA x FGT.EXTRATO x FGT.COMPROVANTE_PG', NULL, 'ALERTA', 'BLOQUEIO', '1B', 'carga-inicial-anexo1'),
    ('R03', 'Cobertura do INSS', '[]'::jsonb, 'Guia paga em valor diferente do apurado na folha | fontes: FOL.FOPAG x INS.DCTFWEB x INS.DARF x INS.COMPROVANTE_PG', NULL, 'ALERTA', 'BLOQUEIO', '1B', 'carga-inicial-anexo1'),
    ('R04', 'Completude da equipe', '[]'::jsonb, 'Profissional faturado sem evidência de vínculo ou pagamento | fontes: OPE.RELACAO_ALOCADOS x FOL.FOPAG x FOL.CONTRACHEQUE (por profissional)', NULL, 'ALERTA', 'BLOQUEIO', '1B', 'carga-inicial-anexo1'),
    ('R05', 'Aderência da medição', '[]'::jsonb, 'Horas/postos faturados sem lastro de medição | fontes: OPE.RELATORIO_MEDICAO x OPE.FOLHA_PONTO x OPE.PLANILHA_MEDICAO', NULL, 'ALERTA', 'BLOQUEIO', '1A', 'carga-inicial-anexo1'),
    ('R06', 'Vigência das certidões', '[]'::jsonb, 'Certidão vencida no momento do ateste ou da emissão | fontes: CER.* com data de validade na data de emissão da NF', NULL, 'ALERTA', 'BLOQUEIO', '1A', 'carga-inicial-anexo1'),
    ('R07', 'Cobertura de VA/VR e VT', '[]'::jsonb, 'Benefício pago sem relação, ou relação sem comprovante | fontes: BEN.RELACAO_* x desconto/crédito na folha x comprovante x termo de não adesão', NULL, 'ALERTA', 'BLOQUEIO', '1B', 'carga-inicial-anexo1'),
    ('R08', 'Fechamento de eventos de rescisão', '[]'::jsonb, 'Rescisão no período sem conjunto documental completo | fontes: RES.TRCT x RES.COMPROVANTE_PG x FGT.GUIA_RESCISORIA x RES.ASO_DEMISSIONAL', NULL, 'ALERTA', 'BLOQUEIO', '1B', 'carga-inicial-anexo1'),
    ('R09', 'Fechamento de férias', '[]'::jsonb, 'Férias concedidas sem recibo ou sem comprovante de pagamento | fontes: FER.AVISO x FER.RECIBO x FER.COMPROVANTE_PG x FOL.FOPAG', NULL, 'ALERTA', 'BLOQUEIO', '1B', 'carga-inicial-anexo1'),
    ('R10', 'Defasagem de competência', '[]'::jsonb, 'Documento anexado da competência errada | fontes: Todo documento de FGTS/INSS/IRRF deve referir-se a M-1', NULL, 'ALERTA', 'BLOQUEIO', '1A', 'carga-inicial-anexo1'),
    ('R11', 'Vinculação ao empenho', '[]'::jsonb, 'Faturamento acima do saldo de empenho | fontes: OPE.EMPENHO x valor da OPE.PLANILHA_MEDICAO x FIS.NOTA_FISCAL', NULL, 'ALERTA', 'BLOQUEIO', '1A', 'carga-inicial-anexo1'),
    ('R12', 'Relógio D+3', '[]'::jsonb, 'Descumprimento da meta institucional de emissão em D+3 | fontes: OPE.ATESTE_RECEBIMENTO (data) x FIS.NOTA_FISCAL (data de emissão)', NULL, 'ALERTA', 'ALERTA', '1A', 'carga-inicial-anexo1')
ON CONFLICT (codigo, versao) DO NOTHING;

-- --- registro da própria carga na trilha (cap. 5.2) -----------------------
INSERT INTO log_auditoria (ator, papel, acao, objeto_tipo, resultado, detalhe)
VALUES ('carga-inicial-anexo1', 'MIGRACAO', 'CARGA_INICIAL', 'catalogo', 'SUCESSO',
        '{"tipos": 51, "aliases": 59, "aliases_descartados": 1, "clientes": 8, "regras_conciliacao": 12, "fonte": "Anexo 1 v1"}'::jsonb);

COMMIT;
