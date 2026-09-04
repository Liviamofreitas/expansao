-- =============================================================================
-- SGDF — V009 · Candidatura de triagem
--
-- ORIGEM: o resíduo RA-01. A fila de triagem era lida de `documento` sozinho —
-- `status_triagem = 'PENDENTE'` — e `documento` não tem contrato, porque o
-- arquivo é varrido antes de ser classificado. Consequência: quem tinha TRIAR
-- via a EXISTÊNCIA de arquivos de qualquer contrato, e o recorte do cap. 15.1
-- não tinha por onde ser aplicado.
--
-- A causa não era o recorte; era o modelo. O cap. 6.1 diz que EM_TRIAGEM é
-- estado da EXIGÊNCIA, não do documento: "EM_TRIAGEM → RECEBIDO · Confirmação
-- humana (gera alias)". Faltava a linha que diz DE QUE EXIGÊNCIA aquele
-- documento é candidato. Sem ela não há como mover a exigência na confirmação,
-- nem como saber de que ciclo — logo, de que contrato — a fila é.
--
-- `vinculo_exigencia_documento` não serve: ele afirma que o documento SATISFAZ
-- a exigência, o que é justamente o que ainda não se sabe. Candidatura e
-- vínculo são coisas diferentes, e confundi-las faria toda candidatura contar
-- como entrega enquanto ninguém a examinasse.
-- =============================================================================

BEGIN;

CREATE TABLE candidatura (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    exigencia_id     uuid          NOT NULL REFERENCES exigencia (id) ON DELETE CASCADE,
    documento_id     uuid          NOT NULL REFERENCES documento (id) ON DELETE CASCADE,
    tipo_proposto_id uuid          NOT NULL REFERENCES tipo_documental (id),
    score            numeric(4, 3) NOT NULL CHECK (score BETWEEN 0 AND 1),
    motivo           text          NOT NULL,
    regra_recon_id   uuid          REFERENCES regra_reconhecimento (id),
    situacao         text          NOT NULL CHECK (situacao IN (
                         'ABERTA', 'CONFIRMADA', 'RECLASSIFICADA', 'ILEGIVEL', 'REJEITADA')),
    aberta_em        timestamptz   NOT NULL DEFAULT now(),
    decidida_em      timestamptz,
    decidida_por     text,
    decisao_motivo   text,

    CONSTRAINT candidatura_unica UNIQUE (exigencia_id, documento_id),

    -- Decisão sem quem e sem quando não é decisão: é um estado que ninguém
    -- consegue explicar depois (cap. 16).
    CONSTRAINT candidatura_decidida_completa CHECK (
        (situacao = 'ABERTA'  AND decidida_em IS NULL     AND decidida_por IS NULL)
     OR (situacao <> 'ABERTA' AND decidida_em IS NOT NULL AND decidida_por IS NOT NULL)),

    -- Cap. 12: "ilegível devolve a PENDENTE com motivo". Devolver a exigência
    -- para PENDENTE sem dizer por quê transforma a fila num lugar de onde as
    -- coisas somem — quem entrega de novo não sabe o que corrigir.
    CONSTRAINT candidatura_recusa_com_motivo CHECK (
        situacao NOT IN ('ILEGIVEL', 'REJEITADA')
        OR (decisao_motivo IS NOT NULL AND btrim(decisao_motivo) <> ''))
);

COMMENT ON TABLE candidatura IS
    'Cap. 8.3: limiar_triagem <= score < limiar_auto. Candidatura NÃO é vínculo — '
    'o vínculo (documento satisfaz exigência) só nasce da confirmação humana.';
COMMENT ON COLUMN candidatura.motivo IS
    'Por que está em triagem: a margem sobre o segundo colocado, o empate, o OCR. '
    'Cap. 12 exige que a fila mostre o motivo de estar em triagem.';
COMMENT ON COLUMN candidatura.regra_recon_id IS
    'Versão da regra que produziu o score. Cap. 16: a decisão automática é '
    'reproduzível a partir de documento (hash) + versão da regra.';

CREATE INDEX ix_candidatura_aberta ON candidatura (aberta_em)
    WHERE situacao = 'ABERTA';
CREATE INDEX ix_candidatura_documento ON candidatura (documento_id);
CREATE INDEX ix_candidatura_exigencia ON candidatura (exigencia_id);

-- Uma exigência com duas candidaturas abertas é uma pergunta ambígua para quem
-- tria: "qual destes dois é o documento?". É legítimo — dois arquivos parecidos
-- na mesma pasta — e por isso NÃO há restrição impedindo. O que a fila faz é
-- mostrar as duas juntas, para que a escolha seja consciente.

-- -----------------------------------------------------------------------------
-- A fila, já recortável por contrato.
--
-- É esta view que fecha o RA-01: candidatura → exigencia → ciclo →
-- contrato_servico_id. O recorte do cap. 15.1 passa a ter sobre o que incidir.
-- -----------------------------------------------------------------------------
CREATE VIEW fila_de_triagem AS
SELECT c.id                    AS candidatura_id,
       c.documento_id,
       c.exigencia_id,
       ci.contrato_servico_id,
       ci.competencia,
       d.nome_arquivo,
       d.caminho,
       t.codigo                AS tipo_proposto,
       t.sigilo,
       c.score,
       c.motivo,
       c.aberta_em
FROM   candidatura c
JOIN   exigencia e        ON e.id = c.exigencia_id
JOIN   ciclo ci           ON ci.id = e.ciclo_id
JOIN   documento d        ON d.id = c.documento_id
JOIN   tipo_documental t  ON t.id = c.tipo_proposto_id
WHERE  c.situacao = 'ABERTA';

COMMENT ON VIEW fila_de_triagem IS
    'Fecha o RA-01: a fila passa a ter contrato, porque a candidatura tem exigência.';

COMMIT;
