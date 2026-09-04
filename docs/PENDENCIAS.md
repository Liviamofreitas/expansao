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
| **A05** | Sistema de folha e layout | **Resolvido.** A **FOPAG** (`RELAÇÃO DA FOLHA DE PAGAMENTO`) é a folha estruturada, e `LeitorDeFopag` a lê por **código de rubrica** — `08305 VALE ALIMENTACAO`, `14000 BASE FGTS MES`, `17300 CUST TT VL ALIME`. Verificada contra o **RESUMO GERAL** que a própria folha imprime: **11 de 11 códigos conferem**. E contra os contracheques da mesma competência: **5 de 5** em proventos, descontos e líquido. | Nada bloqueante. O contrato de dados do cap. 14.3 continua desejável para receber a folha por arquivo em vez de PDF, mas a fase 1b tem a fonte de que precisava. |
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

## F1-03 · estado do critério de aceite

O motor de classificação está construído, cadastrado (`db/seed/V103`) e medido: **20 de 20 automáticas certas** sobre os 22 documentos reais de 06 e 07/2026, com os 2 restantes corretamente marcados como desconhecidos (são os documentos vazios do achado A12).

**O critério de aceite não está cumprido**, e a distinção importa: ele pede as certidões, DCTFWeb, DARF e guia FGTS de **três competências fechadas** com precisão ≥ 95%. Há uma competência, parcialmente duas — e as âncoras foram escritas lendo estes documentos, então o número mede o ajuste, não a generalização.

| O que falta | De quem depende |
|---|---|
| Massa de mais duas competências fechadas | **Área demandante.** Nenhuma linha de código substitui. Chegou a folha de um segundo contrato (DOCAS), que valeu como primeira medição de generalização — ver a Parte II dos achados. |
| Um DARF avulso (o tipo `INS.DARF` nunca foi exercitado) | Área demandante. |
| Documentos que não sejam de nenhum tipo, para medir falso positivo | Área demandante. |
| Pareamento família → tipo dos comprovantes bancários (R01/R02) | Engenharia — fase 1b. Ver a seção 10 dos achados. |
| Extração por coordenada ligada ao cadastro, para os campos tabulares | Engenharia. Ver o achado A20. |

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
| Fase 1b (conciliação de valores) | ~~A05~~ — **liberada** pela FOPAG. Falta o cadastro de tolerâncias das 11 regras (decisão da AP) e o recorte por centro de custo. |
| Selar book com prazo irreversível | **A08** — hoje sela em `LEGAL_HOLD`, o que é suficiente para operar |
| Entrar em produção | **A12** (RIPD) e **A11** (norma) |
| Ativar a fase 3 | **A10** |
| Carregar os 3 contratos faltantes | **A01** |
| Defender uma exigência perante o cliente | **A06** |

---

## Riscos aceitos com prazo — engenharia

| # | Risco | Contenção hoje | Some quando |
|---|---|---|---|
| ~~**RA-01**~~ | ~~Fila de triagem sem recorte por contrato~~ | — | **Fechado na F1-06.** A causa não era o recorte: faltava a candidatura, que liga documento a exigência e portanto a ciclo e contrato. Migração V009 + view `fila_de_triagem`. Ver achados § 31.1. |
| **RA-03** | O peso do alias no score (`RepositorioDeAlias.PESO_DO_ALIAS = 0,10`) está no código, não no cadastro — ao contrário dos limiares, que são por regra. | O valor é pequeno por construção (`Bonus` recusa acima de 0,20) e o bônus nunca elege sozinho: só soma depois de o conteúdo alcançar o limiar de triagem. | O cadastro de parâmetros receber o peso. Decisão de engenharia, não da área demandante. |
| **RA-04** | O painel de **desconhecidos** (tela 5) continua sem recorte por contrato — resíduo do RA-01 que a F1-06 não alcança. Um arquivo abaixo do limiar de triagem não é candidato de exigência nenhuma, logo não tem contrato por construção. | Nome e caminho mascarados (SEC-02); nenhum conteúdo é servido por ali. Exposto: a existência de um arquivo não reconhecido. | Não some por engenharia: um arquivo que o sistema não reconheceu não pertence a contrato algum até alguém dizer a que pertence. A tela é de organização, e é para isso que serve. |
| **RA-02** | Resíduo de tarjamento no book do cliente: `13781031900`, fragmento de número de DARF com DV de CPF coincidentemente válido. 178/180 tarjados. | Reportado como pendência em vez de aceito em silêncio. | Decisão da área demandante sobre o resíduo — ver achado A26. |
