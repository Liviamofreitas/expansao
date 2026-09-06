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
| **A13** | Stack de desenvolvimento | **Java 21 + Spring Boot.** A função de prazo foi portada e passa nos mesmos 32 casos da suíte, provando que a decisão não invalida o construído. | [`adr/ADR-001-stack.md`](adr/ADR-001-stack.md), `app/.../matriz/` |
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
| **RA-03** | O peso do alias no score (`RepositorioDeAlias.PESO_DO_ALIAS = 0,10`) está no código, não no cadastro — ao contrário dos limiares, que são por regra. A F0-05 tirou a matriz do `psql`, mas o peso do alias não é regra de matriz e continua fora. | O valor é pequeno por construção (`Bonus` recusa acima de 0,20) e o bônus nunca elege sozinho: só soma depois de o conteúdo alcançar o limiar de triagem. | O cadastro de parâmetros receber o peso. Decisão de engenharia, não da área demandante. |
| **RA-04** | O painel de **desconhecidos** (tela 5) continua sem recorte por contrato — resíduo do RA-01 que a F1-06 não alcança. Um arquivo abaixo do limiar de triagem não é candidato de exigência nenhuma, logo não tem contrato por construção. | Nome e caminho mascarados (SEC-02); nenhum conteúdo é servido por ali. Exposto: a existência de um arquivo não reconhecido. | Não some por engenharia: um arquivo que o sistema não reconheceu não pertence a contrato algum até alguém dizer a que pertence. A tela é de organização, e é para isso que serve. |
| **RA-05** | Depois do **D+5** a régua fica em silêncio: o cap. 11.1 não define marco seguinte. Uma pendência que sobrevive ao escalonamento para a DAF deixa de gerar aviso. | Continua visível no painel do ciclo e conta como bloqueio de publicação. Exposto: ninguém é *lembrado*. | **Decisão da área demandante:** definir o marco seguinte (repetição semanal? escalonamento a diretoria?). Inventar aqui seria criar régua que ninguém aprovou. Ver achados § 32.3. |
| **RA-06** | Não há transporte de e-mail: a F1-09 entrega a régua, não o envio. | Estrutural e deliberado — ver achados § 32.2. Desligar `notificacao.modo_sombra` **falha** em vez de fingir envio. | A F3-01 medir divergência ≤ 2% por dois ciclos (cap. 19). Só então o transporte é um colaborador novo, e o modo sombra vira decisão de governança registrada na trilha. |
| **RA-07** | Nada roda sozinho: a abertura de ciclo (cap. 7.6), a varredura (cap. 8.1), a derivação de eventos (cap. 7.2), as regras de conciliação (cap. 9) e a régua de notificação (cap. 11) existem, são idempotentes e têm teste — mas **nenhum agendador as chama**. | Nada dispara sozinho, então nada acontece errado sozinho. Todas são reexecutáveis sem duplicar. | Os agendadores serem ligados. Engenharia — as regras e a persistência estão prontas; o que falta é o disparo, e ligá-lo antes da F3-01 (modo sombra medido) seria pôr o sistema a operar sem calibragem. |
| **RA-08** | A segregação de funções compara **strings de identidade**. Se a mesma pessoa autenticar com dois identificadores (o `sub` do OIDC hoje, um e-mail amanhã), as três camadas de SoD concordam que são duas pessoas e ela aprova a própria exceção. | Hoje o identificador vem de um único claim (`sub`) do provedor. Nenhum caminho do sistema cria um segundo identificador para a mesma pessoa. | O provedor de identidade garantir identificador estável e único por pessoa, e o cadastro não admitir contas duplicadas. **Não se resolve no SGDF.** Ver achados § 35.5. |
| **RA-09** | Os códigos de rubrica de **13º, férias e rescisão da FOPAG** não estão no de-para: a massa só tem uma competência de folha mensal comum, sem esses eventos, e inventá-los seria adivinhar. | A detecção pelo **contracheque** funciona por descrição normalizada e cobre os três eventos. O que falta é o mapeamento por código, que só importa quando a fonte for a FOPAG. | A área demandante informar os códigos, ou uma competência com 13º/férias/rescisão entrar na massa. Cadastro, não código — ver achados § 37.6. |
| **RA-10** | `LeitorDeFopag` ainda carrega os nove códigos estruturais em constante Java, embora eles agora existam em `rubrica_de_para`. | Os valores são idênticos e vieram da mesma leitura da FOPAG real; o leitor é verificado contra a massa. | O leitor passar a receber o de-para do cadastro. Não foi feito nesta história para não desestabilizar um leitor verificado contra documento real por uma mudança sem ganho imediato. |
| **RA-02** | Resíduo de tarjamento no book do cliente: `13781031900`, fragmento de número de DARF com DV de CPF coincidentemente válido. 178/180 tarjados. | Reportado como pendência em vez de aceito em silêncio. | Decisão da área demandante sobre o resíduo — ver achado A26. |
| **RA-11** | O cap. 15.1 **não nomeia** quem registra a NF nem quem move o ciclo pelo cap. 6.2 — nomeia apenas `REGISTRAR_ATESTE`. Deixar as duas sem permissão as tornaria impossíveis (negar é o padrão do `Autorizador`); concedê-las a todos contradiria o capítulo. | Leitura conservadora declarada em código: `CONDUZIR_CICLO` vai a **PUBLICADOR_FIN** (publica o faturamento) e **APROVADOR_DAF** (visão global). O GESTOR_CONTRATO registra o ateste e **não** move o ciclo — mover para FECHADO não é o que o capítulo lhe dá. | A DAF confirmar ou corrigir a atribuição. É uma linha no `Autorizador`, não uma mudança de arquitetura. |
| **RA-12** | A cláusula `WHERE` que repete a pré-condição de transição dentro do `UPDATE` **não está medida pela suíte**: removê-la não derruba nenhuma das 57 asserções. | Ela cobre a janela entre a leitura em Java e a gravação; reproduzi-la exigiria pausar o código dentro da transação, o que este código não expõe — e expor só para o teste seria pior que não medir. Registrado em achados § 41.2 em vez de apresentado como garantia verificada. | Um teste de concorrência real (duas conexões, ponto de sincronização) ou a aceitação explícita de que a garantia é do banco, não do teste. Engenharia. |
| **RA-13** | V1 trata "a extensão mente sobre o conteúdo" e "não há extensão" como o mesmo fato, e reprova os dois. Um documento real da massa — `COMPROVANTE_PG_IRRF`, sem `.pdf`, como está no repositório de origem — é um PDF válido por assinatura binária e é descartado sem chegar nem ao painel de organização. | Nenhuma. O documento se perde silenciosamente para quem não lê a trilha. Medido em achados § 42.4. | **Decisão da DAF.** Recomendação registrada e não aplicada: sem extensão, aceitar o MIME da assinatura e gravar o fato; extensão que contradiz a assinatura continua reprovando. Afrouxar validação de segurança não é decisão de engenharia. |
| **RA-14** | V2 reprova o documento inteiro quando **uma** página fica abaixo de 50 caracteres. O comprovante de INSS real tem 8 páginas e a 6ª tem 25 — uma página de separação quase em branco, normal numa folha de comprovantes. | O documento é classificado corretamente e vai para triagem, onde uma pessoa vê o motivo. Não some. | Decisão sobre o critério: reprovar só quando a proporção de páginas pobres passar de um limiar, ou quando a primeira página for pobre. Precisa de mais massa para calibrar — com um caso não se escolhe limiar. |
