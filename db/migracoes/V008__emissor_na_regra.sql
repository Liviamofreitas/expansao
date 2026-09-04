-- =============================================================================
-- SGDF — V008 · Emissor na regra de reconhecimento
--
-- ORIGEM: a folha do segundo contrato trouxe uma relação de VA/VR da PLUXEE. A
-- regra cadastrada tinha sido escrita sobre a relação da FLASH, e as duas não
-- têm uma palavra em comum além do nome da empresa. O documento saiu como
-- NAO_RECONHECIDO — corretamente, porque nenhuma âncora casou.
--
-- Uma regra por tipo não alcança os dois layouts: ou fica genérica a ponto de
-- casar com qualquer coisa, ou casa com um e recusa o outro. A saída é permitir
-- REGRAS IRMÃS — mais de uma regra vigente para o mesmo tipo, cada uma de um
-- emissor. A decisão continua sendo o TIPO; o emissor entra na evidência, para
-- que a triagem saiba qual layout casou.
-- =============================================================================

BEGIN;

ALTER TABLE regra_reconhecimento
    ADD COLUMN emissor text;

COMMENT ON COLUMN regra_reconhecimento.emissor IS
    'Quem emite o documento, quando o mesmo tipo tem mais de um layout '
    '("FLASH", "PLUXEE", "ITAU"). NULL quando o tipo tem layout único.';

-- A unicidade passa a ser por (tipo, emissor, versão): sem isto, a segunda
-- regra irmã seria recusada pela restrição antiga.
ALTER TABLE regra_reconhecimento
    DROP CONSTRAINT regra_recon_versao_unica;

CREATE UNIQUE INDEX ux_regra_recon_versao
    ON regra_reconhecimento (tipo_id, coalesce(emissor, ''), versao);

COMMENT ON INDEX ux_regra_recon_versao IS
    'coalesce porque NULL não colide com NULL num índice único, e duas regras '
    'sem emissor para o mesmo tipo e versão seriam duplicata, não regras irmãs.';

COMMIT;
