-- Rollback da V017. ATENÇÃO: a recertificação volta a enxergar só quem escreve,
-- e o papel AUDITORIA desaparece da própria revisão de acessos.
BEGIN;
DROP TABLE IF EXISTS acesso_observado;
COMMIT;
