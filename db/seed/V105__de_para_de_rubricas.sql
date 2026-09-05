-- =============================================================================
-- SGDF — De-para de rubricas da FOPAG da Engesoftware
--
-- PROCEDÊNCIA: todos os códigos abaixo foram LIDOS da FOPAG real de 06/2026 que
-- está na massa de teste — nenhum foi suposto. Os sete primeiros são os totais e
-- bases que o LeitorDeFopag já usava em constante Java; migrá-los para cá é o
-- que o cap. 7.2 pede ("código de rubrica é cadastro, não código-fonte").
--
-- As linhas SEM código mapeiam por descrição, e existem para a folha derivada do
-- CONTRACHEQUE, que não imprime código (achado A05). São propositalmente poucas:
-- só os papéis que a derivação de eventos do cap. 7.2 precisa reconhecer.
--
-- O QUE NÃO ESTÁ AQUI: os códigos das rubricas de 13º, férias e rescisão da
-- FOPAG. A massa tem uma competência de folha mensal comum, sem esses eventos —
-- então os códigos deles não foram observados, e inventá-los seria adivinhar.
-- Registrado em PENDENCIAS: são cadastro da área demandante.
-- =============================================================================

INSERT INTO rubrica_de_para (sistema, codigo, descricao_normalizada, descricao_original,
                             papel, vigencia_ini, criado_por)
VALUES
    -- Totais e bases, com código, lidos da FOPAG 06/2026 -------------------
    ('FOPAG-ENGESOFTWARE', '10000', NULL, 'TOTAL DE PROVENTOS', 'TOTAL_PROVENTOS',
     '2020-01-01', 'carga-inicial'),
    ('FOPAG-ENGESOFTWARE', '10100', NULL, 'TOTAL DE DESCONTOS', 'TOTAL_DESCONTOS',
     '2020-01-01', 'carga-inicial'),
    ('FOPAG-ENGESOFTWARE', '10200', NULL, 'LIQUIDO A RECEBER', 'LIQUIDO',
     '2020-01-01', 'carga-inicial'),
    ('FOPAG-ENGESOFTWARE', '12200', NULL, 'BASE INSS ATE O TETO', 'BASE_INSS',
     '2020-01-01', 'carga-inicial'),
    ('FOPAG-ENGESOFTWARE', '13300', NULL, 'BASE DE CALCULO DO IRRF', 'BASE_IRRF',
     '2020-01-01', 'carga-inicial'),
    ('FOPAG-ENGESOFTWARE', '14000', NULL, 'BASE DE CALCULO DO FGTS', 'BASE_FGTS',
     '2020-01-01', 'carga-inicial'),
    ('FOPAG-ENGESOFTWARE', '14300', NULL, 'FGTS DO MES', 'FGTS_MES',
     '2020-01-01', 'carga-inicial'),
    ('FOPAG-ENGESOFTWARE', '17300', NULL, 'CUSTO TOTAL VALE ALIMENTACAO',
     'VALE_ALIMENTACAO', '2020-01-01', 'carga-inicial'),
    ('FOPAG-ENGESOFTWARE', '17305', NULL, 'CUSTO EMPRESA VALE ALIMENTACAO',
     'VALE_ALIMENTACAO', '2020-01-01', 'carga-inicial'),

    -- Sem código: a folha derivada do contracheque (A05) -------------------
    ('CONTRACHEQUE', NULL, 'INSS MES', 'INSS Mês', 'INSS', '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'IRRF MES', 'IRRF Mês', 'IRRF', '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'VALE TRANSPORTE', 'Vale Transporte', 'VALE_TRANSPORTE',
     '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'VALE ALIMENTACAO', 'Vale Alimentação', 'VALE_ALIMENTACAO',
     '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'PLANO DE SAUDE', 'Plano de Saúde', 'PLANO_DE_SAUDE',
     '2020-01-01', 'carga-inicial'),

    -- Gatilhos de evento do cap. 7.2, por descrição. Os nomes seguem a grafia
    -- usual do contracheque; a junção é sem acento e sem caixa.
    ('CONTRACHEQUE', NULL, 'ADIANTAMENTO 13 SALARIO', 'Adiantamento 13º Salário',
     'ADIANTAMENTO_13', '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'FERIAS', 'Férias', 'FERIAS', '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'ABONO PECUNIARIO', 'Abono Pecuniário', 'FERIAS',
     '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'AVISO PREVIO INDENIZADO', 'Aviso Prévio Indenizado',
     'RESCISAO', '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'MULTA FGTS 40', 'Multa FGTS 40%', 'RESCISAO',
     '2020-01-01', 'carga-inicial'),
    ('CONTRACHEQUE', NULL, 'SALDO DE SALARIO', 'Saldo de Salário', 'RESCISAO',
     '2020-01-01', 'carga-inicial');
