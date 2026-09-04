# Estado das pendências A01–A13

**Atualizado em 2026-09-04.** Substitui a leitura do cap. 23 do documento normativo, que reflete o estado da V1.

Sete pendências estão **resolvidas** — decisão tomada, implementada e testada. Duas estão **encaminhadas**: o trabalho técnico está pronto e falta o insumo externo. Quatro **não são resolvíveis por engenharia**: dependem de conferência documental, de decisão jurídica ou de alocação de pessoas, e nenhuma quantidade de código as fecha.

Esta distinção importa. Declarar "todas resolvidas" quando quatro dependem de terceiros trocaria uma pendência visível por um risco invisível — que é exatamente o modo de falha que o cap. 22 chama de P08.

---

## Resolvidas

| ID | Pendência | Decisão | Onde está |
|---|---|---|---|
| **A03** | BNB e TJCE usam a mesma matriz nas duas modalidades? | Matriz **separada por contrato-serviço**. Cada um recebe sua própria cópia das regras do cliente, com `alvo = CONTRATO`. Cenário conservador de R-04: consolidar depois é barato, separar depois exigiria migração. | `dados/contratos_servico.csv`, `db/seed/V102`, teste `T003` |
| **A04** | Os 3 contratos-serviço da CAIXA são idênticos? | Mesma decisão de A03. Os 3 entram como contratos-serviço distintos, cada um com sua matriz. | idem |
| **A07** | Recolhimento por CNPJ único ou por filial? | O modelo suporta N desde a `V004`: a tabela `empresa` é plural e `contrato_servico.empresa_id` diz qual CNPJ fatura cada contrato. A carga entra com a empresa **inativa** e CNPJ marcador — nenhum CNPJ é inventado. Confirmar quantos são é cadastro, não modelagem. | `db/migracoes/V004`, teste `T003` |
| **A08** | Tabela de temporalidade do book | **Retenção sem tempo determinado**, implementada como `LEGAL_HOLD`: protege indefinidamente e é **reversível** por papel autorizado. `COMPLIANCE` (irreversível) existe mas não é o padrão. **Ver a ressalva abaixo.** | `db/migracoes/V006`, teste `T003` |
| **A09** | Repositório-mestre quando o tipo existe no OwnCloud e no Jira | Declarado **por tipo, no cadastro**. Derivado da coluna `REPOSITORIO` da matriz: 41 tipos OwnCloud, 7 Jira, 3 com OwnCloud como mestre e Jira como alternativa — os 3 fiscais, e a leitura vem do próprio cap. 14.2, que chama o Jira de "fonte complementar". | `db/migracoes/V006`, `V100`, teste `T003` |
| **A13** | Stack de desenvolvimento | **Java 21 + Spring Boot.** A função de prazo foi portada e passa nos mesmos 32 casos da suíte, provando que a decisão não invalida o construído. | [`adr/ADR-001-stack.md`](adr/ADR-001-stack.md), `especificacao/prazo/java/` |
| **A02** | Natureza de "APOIO A GESTÃO" | Não aparece na matriz do Anexo 1 como cliente, e nenhuma das 176 linhas a referencia. Tratada como **atividade interna, fora do escopo de faturamento por medição** — se fosse contrato com checklist, teria linhas. Reversível: basta acrescentar um contrato-serviço ao CSV. | `dados/contratos_servico.csv` (ausência deliberada) |

### Ressalva registrada sobre A08

A decisão pedida foi "retenção sem tempo determinado". Ela está implementada, mas há uma tensão que precisa ser avaliada pelo DPO antes da produção, e não por engenharia:

Guardar dado pessoal por prazo indeterminado é tratamento sem termo final definido. Isso tensiona o **art. 6º, III** (necessidade), o **art. 16** (eliminação após a finalidade) e o **art. 18, IV e VI** (direito do titular à eliminação). Os books contêm CPF, remuneração e — em 4 tipos — dado de saúde.

A finalidade aqui tem termo natural: a prescrição quinquenal do art. 7º, XXIX da Constituição.

**Por isso o padrão é `LEGAL_HOLD` e não `COMPLIANCE`.** Os dois protegem a evidência; só o primeiro deixa a decisão reversível. Migrar para `COMPLIANCE` é caminho sem volta — nem a conta raiz reduz o prazo — e deve acontecer uma única vez, depois da tabela de temporalidade aprovada. O detalhamento está em [`INVENTARIO-DADOS-PESSOAIS.md`](INVENTARIO-DADOS-PESSOAIS.md), seção 4.

---

## Encaminhadas — falta o insumo externo

| ID | Pendência | O que já está pronto | O que falta, e de quem |
|---|---|---|---|
| **A05** | Sistema de folha e layout | O **contrato de dados** está especificado (cap. 14.3) e a fase 1a foi construída sem depender dele (D-10): as regras que precisam de folha ficam `NAO_APLICAVEL` com motivo, nunca reprovadas. As 7 regras de conciliação de fase 1B estão cadastradas e inertes. | Qual é o sistema de folha e se ele exporta o layout mínimo. **TI e AP.** Nenhuma linha de código a mais fecha isto — depende de um sistema que ainda não foi nomeado. |
| **A12** | RIPD e inventário de dados pessoais | O **inventário está gerado**: 23 dos 51 tipos tratam dado pessoal, 4 deles sensível; onde cada dado vive e com que proteção. É a parte factual e volumosa do RIPD. | A avaliação de risco, a base legal por finalidade e a aprovação. **DPO e jurídico.** | 

---

## Não resolvíveis por engenharia

Estas quatro dependem de conferência documental, decisão de negócio ou alocação de pessoas. Registrá-las como abertas é o resultado correto.

| ID | Pendência | Por que engenharia não fecha | Bloqueia |
|---|---|---|---|
| **A01** | Checklists de SEFAZ-CE, CGM-CE e SEBRAE GO | São **documentos que não existem no pacote**. Inventá-los produziria exigências falsas para três contratos reais — o pior resultado possível num sistema cuja função é provar regularidade. O carregador já aceita novos clientes: basta acrescentá-los ao CSV e regenerar. | Carga completa. Os outros 12 contratos-serviço não esperam. |
| **A06** | Fundamento contratual por exigência | É a **cláusula que justifica cada uma das 176 exigências**. Está no texto dos contratos, que não estão no pacote. O campo `regra_exigibilidade.fundamento` existe e aceita o valor; preenchê-lo é leitura contratual. | Nada tecnicamente. Mas uma exigência sem fundamento é uma exigência que ninguém consegue defender quando o cliente questiona. |
| **A10** | Time de sustentação pós-go-live | Alocação de pessoas. | **Ativação da fase 3** — gate já imposto pelo cap. 20 e mantido. |
| **A11** | Consolidação das políticas + AP no RACI | Publicação de norma interna. | A norma que ampara o portão documental. Sem ela, o sistema bloqueia faturamento sem respaldo formal. |

### Sobre a contagem de "15 contratos"

O cabeçalho do documento fala em 15 pastas de contrato no OwnCloud; a carga produziu **12** contratos-serviço a partir dos 8 clientes do Anexo 1 mais o desdobramento de D-02 (CAIXA 3, BNB 2, TJCE 2).

A diferença de 3 corresponde, muito provavelmente, aos três clientes de **A01** — SEFAZ-CE, CGM-CE e SEBRAE GO — cujos checklists ainda não foram incorporados. É uma hipótese coerente com os números, **não um fato verificado**: confirmar exige olhar as pastas.

---

## Achados da errata

Estado dos 10 achados de [`ERRATA-V1.md`](ERRATA-V1.md):

| Estado | Achados |
|---|---|
| Decididos e implementados | E-01, E-02, E-07, E-08 |
| Implementados sob leitura documentada, aguardando confirmação | E-06, E-09, E-10 |
| Resolvidos pela carga | **E-04** (era A03/A04) |
| Abertos | **E-03** (correção de texto, em `CORRECOES-V1.1.md`), **E-05** (confirmar sigilo e escopo dos 51 tipos) |

---

## O que efetivamente bloqueia o quê, hoje

| Quer fazer | Precisa de |
|---|---|
| Fase 1a completa (varredura, reconhecimento, book) | Nada. **Liberada.** |
| Fase 1b (conciliação de valores) | **A05** |
| Selar book com prazo irreversível | **A08** — hoje sela em `LEGAL_HOLD`, o que é suficiente para operar |
| Entrar em produção | **A12** (RIPD) e **A11** (norma) |
| Ativar a fase 3 | **A10** |
| Carregar os 3 contratos faltantes | **A01** |
| Defender uma exigência perante o cliente | **A06** |
