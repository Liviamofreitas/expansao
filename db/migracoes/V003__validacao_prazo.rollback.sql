BEGIN;
ALTER TABLE regra_exigibilidade DROP CONSTRAINT IF EXISTS regra_prazo_bem_formado;
ALTER TABLE contrato_servico    DROP CONSTRAINT IF EXISTS contrato_prazo_bem_formado;
DROP FUNCTION IF EXISTS prazo_bem_formado(jsonb);
COMMIT;
