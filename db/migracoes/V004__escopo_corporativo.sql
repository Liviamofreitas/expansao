-- =============================================================================
-- SGDF — V004 · Escopo corporativo e empresa emitente
-- Referência: cap. 7.1 (materialização), D-03, R-03, V3 do cap. 8.4.
-- História F0-07.
--
-- PROBLEMA QUE ESTA MIGRATION RESOLVE
--
-- O cap. 5.2 modela `exigencia` com `ciclo_id`, mas o cap. 7.1 determina que a
-- exigência de escopo CORPORATIVO é "1 por CNPJ por competência (compartilhada
-- entre ciclos, satisfeita uma única vez)". Uma linha não pode simultaneamente
-- pertencer a um ciclo e ser compartilhada por todos eles.
--
-- Duplicar a exigência corporativa em cada ciclo seria a saída fácil e está
-- errada: com 15 contratos, a mesma CND seria cobrada 15 vezes, apareceria 15
-- vezes na régua e o "satisfeita uma única vez" do documento deixaria de valer.
--
-- Solução: `exigencia` passa a ter dois modos de endereçamento, mutuamente
-- exclusivos e impostos por CHECK — ou pertence a um ciclo, ou pertence a
-- (empresa, competência). A view `exigencia_do_ciclo` reúne os dois para quem
-- consulta. Ver docs/ERRATA-V1.md, achado E-09.
--
-- Também cria `empresa`: o CNPJ do PRESTADOR. O cadastro tinha `cliente` (o
-- contratante) mas nenhum lugar para o CNPJ próprio, que é justamente o titular
-- das certidões e guias do bloco corporativo e o que a validação V3 confere.
-- R-03 antecipa N CNPJs (matriz/filial), então a tabela já nasce plural.
-- =============================================================================

BEGIN;

-- (24) empresa -----------------------------------------------------------------
CREATE TABLE empresa (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    razao_social   text        NOT NULL,
    cnpj           char(14)    NOT NULL,
    matriz         boolean     NOT NULL DEFAULT true,
    ativo          boolean     NOT NULL DEFAULT true,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    criado_por     text        NOT NULL,
    atualizado_em  timestamptz,
    atualizado_por text,
    CONSTRAINT empresa_cnpj_unico   UNIQUE (cnpj),
    CONSTRAINT empresa_cnpj_digitos CHECK (cnpj ~ '^[0-9]{14}$')
);
COMMENT ON TABLE empresa IS
    'CNPJ do prestador — titular das certidões e guias do bloco corporativo. '
    'Nasce vazia: nenhum CNPJ é inventado pela migration. Ver pendência A07 '
    '(recolhimento por CNPJ único ou por filial).';

-- Qual dos nossos CNPJs fatura cada contrato ----------------------------------
-- Nullable, mas exigido para ativar: mesmo tratamento dado aos clientes da carga
-- inicial, que entram inativos até o CNPJ real ser cadastrado.
ALTER TABLE contrato_servico
    ADD COLUMN empresa_id uuid REFERENCES empresa (id);

ALTER TABLE contrato_servico
    ADD CONSTRAINT contrato_ativo_exige_empresa
    CHECK (NOT ativo OR empresa_id IS NOT NULL);

CREATE INDEX ix_contrato_empresa ON contrato_servico (empresa_id);

-- Dois modos de endereçamento da exigência ------------------------------------
ALTER TABLE exigencia ALTER COLUMN ciclo_id DROP NOT NULL;

ALTER TABLE exigencia
    ADD COLUMN empresa_id  uuid REFERENCES empresa (id),
    ADD COLUMN competencia char(7);

ALTER TABLE exigencia
    ADD CONSTRAINT exigencia_endereco_exclusivo CHECK (
        (ciclo_id IS NOT NULL AND empresa_id IS NULL     AND competencia IS NULL) OR
        (ciclo_id IS NULL     AND empresa_id IS NOT NULL AND competencia IS NOT NULL)
    );

ALTER TABLE exigencia
    ADD CONSTRAINT exigencia_competencia_formato
    CHECK (competencia IS NULL OR competencia ~ '^[0-9]{4}-(0[1-9]|1[0-2])$');

-- A exigência corporativa é por empresa, nunca por profissional.
ALTER TABLE exigencia
    ADD CONSTRAINT exigencia_corporativa_sem_profissional
    CHECK (empresa_id IS NULL OR profissional_id IS NULL);

COMMENT ON COLUMN exigencia.empresa_id IS
    'Preenchido só no escopo CORPORATIVO. Cap. 7.1: uma exigência por CNPJ por '
    'competência, compartilhada entre todos os ciclos daquela empresa.';

-- A restrição original tratava todo NULL de ciclo_id como o mesmo valor
-- (NULLS NOT DISTINCT), o que faria duas empresas colidirem na mesma exigência
-- corporativa. Substituída por dois índices parciais, um para cada modo.
ALTER TABLE exigencia DROP CONSTRAINT exigencia_unica;

CREATE UNIQUE INDEX ux_exigencia_de_ciclo
    ON exigencia (ciclo_id, tipo_id, evento, profissional_id) NULLS NOT DISTINCT
    WHERE ciclo_id IS NOT NULL;

CREATE UNIQUE INDEX ux_exigencia_corporativa
    ON exigencia (empresa_id, competencia, tipo_id, evento)
    WHERE empresa_id IS NOT NULL;

COMMENT ON INDEX ux_exigencia_corporativa IS
    'É esta unicidade que faz valer o "satisfeita uma única vez" do cap. 7.1.';

CREATE INDEX ix_exigencia_corporativa_competencia
    ON exigencia (empresa_id, competencia) WHERE empresa_id IS NOT NULL;

-- Resolução: as exigências que valem para um ciclo -----------------------------
-- Quem pergunta "o que falta neste ciclo?" não deve precisar saber que existem
-- dois modos de endereçamento. A view esconde isso.
CREATE VIEW exigencia_do_ciclo AS
    SELECT c.id AS ciclo_id,
           e.id AS exigencia_id, e.tipo_id, e.evento, e.profissional_id,
           e.status, e.formatos_pendentes, e.prazo_calculado, e.criticidade,
           e.condicional_grupo, e.responsavel, e.origem, e.regra_id,
           e.empresa_id, e.competencia,
           'PROPRIA'::text AS procedencia
      FROM ciclo c
      JOIN exigencia e ON e.ciclo_id = c.id
UNION ALL
    SELECT c.id AS ciclo_id,
           e.id AS exigencia_id, e.tipo_id, e.evento, e.profissional_id,
           e.status, e.formatos_pendentes, e.prazo_calculado, e.criticidade,
           e.condicional_grupo, e.responsavel, e.origem, e.regra_id,
           e.empresa_id, e.competencia,
           'CORPORATIVA'::text AS procedencia
      FROM ciclo c
      JOIN contrato_servico cs ON cs.id = c.contrato_servico_id
      JOIN exigencia e ON e.empresa_id = cs.empresa_id
                      AND e.competencia = c.competencia;

COMMENT ON VIEW exigencia_do_ciclo IS
    'Exigências aplicáveis a um ciclo: as próprias mais as corporativas da '
    'empresa que fatura o contrato, na mesma competência. A coluna procedencia '
    'distingue as duas — a tela do cap. 12 mostra o bloco corporativo à parte.';

COMMIT;
