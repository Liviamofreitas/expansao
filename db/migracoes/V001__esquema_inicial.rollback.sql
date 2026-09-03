-- Reversão da V001. Destrutiva: remove o esquema inteiro. Só em DEV.
BEGIN;
DROP TABLE IF EXISTS log_auditoria, pendencia, notificacao, book, excecao,
    conciliacao, vinculo_exigencia_documento, campo_extraido, documento,
    exigencia, ciclo, alocacao, profissional, regra_conciliacao,
    regra_reconhecimento, regra_exigibilidade, versao_matriz, tipo_alias,
    tipo_documental, contrato_servico, calendario_feriados, modalidade,
    cliente CASCADE;
COMMIT;
