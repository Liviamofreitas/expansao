# Errata da V1 — inconsistências entre a documentação e o Anexo 1

**Verificação executada sobre:** `SGDF_Documentacao_Desenvolvimento_V1.docx` × `Anexo1_Diagnostico_Checklist_Faturamento.xlsx`
**Método:** conferência programática das contagens, chaves e referências cruzadas citadas no documento contra o conteúdo real da planilha.
**Status:** 10 achados verificados. **Quatro decididos e implementados** (E-01, E-02, E-07, E-08); três implementados sob leitura documentada, aguardando confirmação (E-06, E-09, E-10); três abertos, dependentes de pendências (E-03, E-04, E-05).

> As correções no **código** já estão aplicadas. As correções no **documento normativo** — que pertence à área demandante — estão especificadas seção por seção em [`CORRECOES-V1.1.md`](CORRECOES-V1.1.md). Enquanto ele não for aplicado, código e documento divergem, e o código é que está de acordo com as decisões.

> Nada aqui invalida o pacote. Três dos quatro achados são de numeração e contagem; um é de determinismo de classificação e tem efeito direto no maior volume documental do sistema. Todos são corrigíveis antes da sprint 0.

---

## E-01 — CRÍTICO · Os códigos R01–R12 significam coisas diferentes no documento e no anexo

> ****DECIDIDO** — a numeração canônica passa a ser a do Anexo 1. De-para completo em [`CORRECOES-V1.1.md`](CORRECOES-V1.1.md), seção C-01. As 12 regras estão carregadas em `V100`, todas em modo ALERTA por falta de tolerância, com a severidade pretendida em `modo_pretendido`.**

O capítulo 9 da documentação e a aba `REGRAS_CONCILIACAO` do Anexo 1 usam **a mesma faixa de identificadores para conjuntos de regras distintos**. Não é divergência de redação: é colisão de chave primária entre a especificação e a sua fonte canônica.

| ID | Documentação, cap. 9 | Anexo 1, aba `REGRAS_CONCILIACAO` |
|---|---|---|
| R01 | Guia de FGTS × comprovante de pagamento (pareamento) | **Cobertura do plano de saúde** |
| R02 | DCTFWeb × Σ DARF × comprovantes | **Cobertura do FGTS** |
| R03 | Certidões: validade ≥ data prevista da NF | **Cobertura do INSS** |
| R04 | Formatos pendentes = ∅ e coerência entre formatos | **Completude da equipe** |
| R05 | Alocados ⊆ folha, com contracheque | **Aderência da medição** |
| R06 | Evento derivado com conjunto documental completo | **Vigência das certidões** |
| R07 | Cobertura do plano de saúde | **Cobertura de VA/VR e VT** |
| R08 | Cobertura de VA/VR e VT | **Fechamento de eventos de rescisão** |
| R09 | Σ base FGTS × alíquota ≈ guia | **Fechamento de férias** |
| R10 | Σ bases ≈ DCTFWeb por grupo de receita | **Defasagem de competência** |
| R11 | CPFs do extrato FGTS ⊇ alocados ativos | **Vinculação ao empenho** |
| R12 | Medição ≤ empenho e = valor da NF | **Relógio D+3 (indicador)** |

**Efeito prático.** O modelo de dados (cap. 5.1) define `regra_conciliacao.codigo(R01..R12)` como identificador estável, e o cap. 16 exige que toda decisão automática seja reproduzível a partir da versão da regra. Com duas semânticas para o mesmo código: a história **F1-05** ("conciliação R01–R04") entrega, pela documentação, pareamento de guias e certidões; pelo anexo, entregaria planos de saúde, FGTS, INSS e completude de equipe — que são regras de **fase 1b**, dependentes da folha (pendência A05). Um desenvolvedor que abrir o anexo como fonte canônica constrói a coisa errada e a base de conhecimento do cap. 20 ("o que cada divergência significa") nasce ambígua.

**Agravante de faseamento.** A distribuição por fase também não sobrevive à sobreposição: no documento, R01–R04 são fase 1a (independentes de folha); no anexo, os equivalentes numéricos exigem `FOL.FOPAG`, que só existe na 1b.

**Ação recomendada (obrigatória antes da sprint 0):** adotar **uma** numeração canônica e reemitir a outra fonte. Sugestão conservadora: preservar os IDs do **Anexo 1**, porque é a fonte declarada da carga inicial (D-10) e porque a aba já tem colunas de tolerância e exceção a preencher; renumerar o cap. 9 para essa base, prefixando as regras que só existem no documento. Alternativa: adotar prefixos disjuntos (`RC-nn` no anexo × `RV-nn` no documento). O que não é aceitável é publicar as duas faixas iguais.

---

## E-02 — ALTO · Alias `GFD_GUIA_DO_FGTS` registrado para dois tipos canônicos

> ****DECIDIDO** — o alias não pertence a nenhum dos dois tipos. Um nome que serve a dois documentos não é sinal confiável e daria bônus de score ao tipo errado metade das vezes. Descartado dos dois na carga; a desambiguação passa a depender das âncoras de conteúdo, que precisam ser mutuamente exclusivas.**

Na aba `CATALOGO_DOCUMENTOS`, a nomenclatura `GFD_GUIA_DO_FGTS` aparece como alias de **dois** códigos:

- `FGT.GUIA`
- `FGT.RELATORIO_DIGITAL`

O cap. 5.1 define `tipo_alias.texto_normalizado` com normalização "sem acento, minúsculas, separadores colapsados" — sob essa regra as duas entradas colapsam para a mesma chave `gfd_guia_do_fgts`. O cap. 8.3 usa o alias como **bônus de score** na classificação determinística; um arquivo cujo nome contenha esse padrão recebe o bônus para dois tipos concorrentes ao mesmo tempo.

**Efeito prático.** Empate de score na família FGTS — a de maior volume mensal e a que alimenta R01/R02 em qualquer das duas numerações. O resultado provável é queda permanente para a fila de triagem (bom) ou vínculo automático no tipo errado se outro sinal desempatar (ruim, e silencioso). Fere o princípio 3 do cap. 1 (decisão automática determinística) e ameaça a meta de precisão ≥ 95% da história **F1-03**.

**Ação recomendada:** decidir a qual tipo o alias pertence e remover do outro; se ambos os documentos realmente circulam com esse nome, a desambiguação tem de vir de âncora de conteúdo, não de alias — e a regra de reconhecimento dos dois tipos precisa declarar âncoras mutuamente exclusivas.

> **ENCERRADO.** A pendência técnica que restava — âncoras mutuamente exclusivas para os dois tipos — foi resolvida com documentos reais. `FGT.GUIA` tem o título `GFD - Guia do FGTS Digital` e a seção `Composição do Documento`; `FGT.RELATORIO_DIGITAL` tem `Detalhe da Guia a Ser Emitida` e `Relação de Trabalhadores`. Sem interseção. Ver [`ACHADOS-MASSA-REAL.md`](ACHADOS-MASSA-REAL.md), seção 3. **Exigido:** a carga inicial deve rejeitar alias normalizado duplicado entre tipos distintos (restrição de unicidade em `tipo_alias.texto_normalizado`), para que o problema não se repita por cadastro.

---

## E-03 — MÉDIO · Critério de aceite da F0-03 usa uma contagem de aliases que não se confirma

A história **F0-03** tem como critério de aceite: *"Importação dos 51 tipos e 69 aliases do Anexo 1 por migration"*. O número de tipos confere; o de aliases não fecha por nenhum critério de contagem:

| Critério de contagem | Total |
|---|---|
| Aliases brutos (separados por `;` na coluna `NOMENCLATURAS_ATUAIS`) | **72** |
| Pares `(tipo, alias normalizado)` distintos | **61** |
| Aliases normalizados distintos no catálogo inteiro | **60** |
| Citado no documento | 69 |

**Efeito prático.** É um critério de aceite objetivo — portanto testável, portanto vai **falhar na primeira execução** independentemente da qualidade da implementação. Critério de aceite que falha por erro do próprio critério custa uma rodada de investigação e corrói a confiança no backlog.

**Ação recomendada:** substituir o número por um critério derivado da regra, não de uma contagem manual: *"a migration importa os 51 tipos e todos os aliases da coluna `NOMENCLATURAS_ATUAIS`, deduplicados pela normalização do cap. 5.1, sem colisão entre tipos distintos"*. Se o número absoluto for desejado no critério, o valor correto após a correção de **E-02** é **60**.

---

## E-04 — BAIXO · "8 checklists" e "15 pastas de contrato" não se refletem na matriz

O cabeçalho do documento declara base de análise de *"176 exigências de 8 checklists"* e *"estrutura real do OwnCloud (15 pastas de contrato)"*. A matriz confirma 176 linhas e 8 contratos, mas a distribuição é desigual e nenhum contrato aparece desdobrado por modalidade:

| Contrato na matriz | Linhas |
|---|---|
| TJ CE | 41 |
| CEF | 34 |
| DOCAS | 24 |
| SESCOOP | 24 |
| BNB | 18 |
| Telebrás | 12 |
| TERRACAP | 12 |
| SEFAZ RS | 11 |

A decisão **D-02** afirma que "CAIXA desdobra em 3; BNB e TJCE em 2 cada", e a história **F0-02** tem como critério de aceite "CAIXA cadastrada como 3 contratos-serviço distintos" — mas a matriz traz `CEF` como linha única, sem coluna que separe os desdobramentos. A coluna `CONTRATO` do Anexo 1 é, portanto, **cliente**, não contrato-serviço.

Também há divergência de nomenclatura entre as fontes: o catálogo e a matriz usam `CEF`; o documento usa `CAIXA`. E `SEFAZ RS` aparece na matriz, enquanto a pendência **A01** trata de `SEFAZ-CE` — são entidades diferentes, e vale confirmar que não houve troca.

**Efeito prático.** Baixo agora, alto se ignorado: a carga inicial da fase 0 não tem como derivar os 15 contratos-serviço a partir do anexo. Isto **não é defeito da planilha** — é exatamente o conteúdo das pendências **A03** e **A04**, ainda abertas. O registro aqui serve para que a migration de carga não seja escrita assumindo que a coluna `CONTRATO` já resolve o desdobramento.

**Ação recomendada:** tratar como parte da resolução de A03/A04; acrescentar ao Anexo 1 uma coluna `CONTRATO_SERVICO` antes da carga; e padronizar `CEF` × `CAIXA` no catálogo de clientes.

---

## E-05 — MÉDIO · `escopo` e `sigilo` são exigidos pelo modelo e não existem no Anexo 1

O cap. 5.1 define `tipo_documental` com dois campos que governam comportamento crítico e que o catálogo do Anexo 1 não carrega:

| Campo | Governa | Coluna equivalente no Anexo 1 |
|---|---|---|
| `escopo` (`CORPORATIVO`/`CONTRATO`/`PROFISSIONAL`) | Quantas exigências o cap. 7.1 materializa: uma por CNPJ, uma por ciclo, ou uma por profissional alocado | `GRANULARIDADE` — só tem `Contrato` e `Profissional`; **não tem `Corporativo`** |
| `sigilo` (`PUBLICO_CLIENTE`/`INTERNO`/`PESSOAL`/`PESSOAL_SENSIVEL`) | O tarjamento antes da cópia para o book do cliente (cap. 10, LGPD-03, história F2-07) | **nenhuma** |

**Efeito prático em `escopo`.** A decisão **D-03** diz que "certidões e encargos são por CNPJ", e o cap. 12 fala em "bloco corporativo (estado dos **12 tipos** por CNPJ)" — mas esse conjunto de 12 não está enumerado em lugar nenhum, e nenhuma leitura das famílias produz exatamente 12. Sem o campo, o cap. 7.1 materializa uma certidão por ciclo em vez de uma por CNPJ, e o mesmo documento é cobrado 15 vezes.

**Efeito prático em `sigilo`.** Ausente, o tarjamento não tem gatilho: contracheques e ASO seguiriam íntegros para o book do cliente. É a falha que o risco **P05** descreve.

**Como está tratado no código.** O campo é `NOT NULL` no esquema — não aceita omissão. Os valores foram **derivados de forma conservadora** e isolados em [`dados/complemento_tipo_documental.csv`](../dados/complemento_tipo_documental.csv), com a coluna `CONFIRMADO=NAO` em todas as 51 linhas. O critério de derivação:

- `escopo`: `CORPORATIVO` para as famílias de certidões, INSS e as guias mensais de FGTS (15 tipos); o resto segue `GRANULARIDADE`.
- `sigilo`: `PESSOAL_SENSIVEL` onde há dado de saúde (ASO demissional, plano de saúde); `PESSOAL` para escopo profissional e folha; `PUBLICO_CLIENTE` para certidões, fiscal e operação; `INTERNO` no restante.

A direção da derivação é deliberadamente restritiva: superestimar o sigilo causa tarjamento a mais (revisável); subestimar vaza dado pessoal (irreversível).

**Ação recomendada:** a área demandante revisa o CSV e marca `CONFIRMADO=SIM`. Nada além de editar o CSV e rodar `python3 tools/gerar_carga_inicial.py` é necessário — é cadastro, não código, como exige o princípio 2 do cap. 1.

---

## E-06 — ALTO · O `offset` do prazo tem duas semânticas, decididas pela âncora

O cap. 7.3 dá dois exemplos que nenhuma regra única satisfaz:

| Exemplo do documento | Leitura que o satisfaz |
|---|---|
| `{INICIO_COMPETENCIA, CORRIDO, 21}` = "até o dia 21" | **ordinal** — o 21º dia do mês |
| `{ATESTE, CORRIDO, 3}` = "D+3 da política" | **aditiva** — base + 3 dias |

Sob contagem aditiva, o primeiro daria dia 22 (`1 + 21`). Sob contagem ordinal, o segundo daria o dia do ateste mais dois. O mesmo campo, portanto, significa coisas diferentes conforme a âncora.

**Efeito prático.** É um erro de um dia — a classe de defeito que passa por revisão de código, passa por teste feito pelo mesmo desenvolvedor que escreveu a regra, e só aparece quando um prazo real vence. Em 95 das 176 linhas do Anexo 1 a âncora é "Início da competência"; um deslocamento sistemático de um dia em todas elas desloca a régua de cobrança inteira.

**Como está tratado no código.** A implementação de referência adota a única leitura que satisfaz os dois exemplos do próprio documento — ordinal para `INICIO_COMPETENCIA`, aditiva para `ATESTE`, `SOLICITACAO_FATURAMENTO` e `EVENTO` — e os dois exemplos do documento estão na suíte de conformidade como os casos `DOC-01` e `DOC-02`. Os dados do Anexo 1 sustentam a leitura: as 95 linhas de "Início da competência" usam limites de 1 a 31 (ordinais de dia do mês) e as 81 de "Ateste do cliente" usam limite 0 (D+0, "SOB FATURAMENTO").

**Ação recomendada:** confirmar a leitura e registrá-la no cap. 7.3 como tabela explícita de semântica por âncora. Se a área demandante decidir o contrário, o que muda é a suíte — e todos os prazos de âncora ordinal deslocam um dia.

---

## E-07 — MÉDIO · Carnaval e Corpus Christi não são feriados, mas ninguém trabalha

> ****DECIDIDO** — Carnaval e Corpus Christi contam como dias **não úteis**. O prazo fica mais longo, mas nunca vence num dia em que não há ninguém para responder. As 24 linhas seguem marcadas `[FACULTATIVO]`, e a decisão é reversível por cadastro.**

O cap. 7.3 manda contar dia útil pelo `calendario_feriados` da UF, mas nada no pacote diz o que fazer com **ponto facultativo federal**: Carnaval (segunda e terça) e Corpus Christi não são feriados nacionais por lei, e na prática não há expediente.

A escolha não é neutra e vai nos dois sentidos:

| Decisão | Efeito no prazo | Risco |
|---|---|---|
| Contar como **não úteis** | Prazo mais longo | Cobrança mais tarde; menos margem antes do faturamento |
| Contar como **úteis** | Prazo mais curto | O prazo vence e a régua dispara num dia em que não há ninguém para responder |

**Como está tratado no código.** As 24 linhas facultativas entram no calendário com o prefixo `[FACULTATIVO]` na descrição, para que sejam identificáveis e removíveis por cadastro sem tocar em código. A suíte tem o par `FACULTATIVO-01`/`FACULTATIVO-02`, que mede a diferença: com Carnaval, o 13º dia útil de 02/2026 é 20/02; sem, é 18/02.

**Ação recomendada:** decisão da DAF, aplicada por cadastro. Vale a mesma pergunta para os **feriados municipais**, que não estão no calendário porque não constam de nenhuma fonte do pacote — e que precisam ser cadastrados antes de qualquer prazo em dia útil valer para contratos cujo município tenha feriado local.

---

## E-08 — ALTO · Oito linhas da matriz pedem um dia do mês que não existe

> ****DECIDIDO** — criada a âncora `FIM_COMPETENCIA`, que expressa a intenção real de "ENTRE DIA 30 A 31". As 8 linhas a recadastrar estão em [`dados/correcoes_matriz.csv`](../dados/correcoes_matriz.csv). O ajuste `AJUSTE_FIM_DE_PERIODO` permanece como rede de segurança para cadastro antigo, mas deixa de aparecer na operação normal.**

Na aba `MATRIZ_EXIGIBILIDADE`, oito linhas têm âncora "Início da competência" com limite maior que o número de dias de alguns meses:

| Linha | Contrato | Tipo | Limite | Prazo original | Falha em |
|---|---|---|---|---|---|
| L026, L027 | CEF | `INS.PARCELAMENTO_*` | 31 | "ENTRE DIA 30 A 31" | fev, abr, jun, set, nov |
| L015, L051, L071, L108, L109, L174 | BNB, CEF, DOCAS, SESCOOP, TJ CE | `INS.COMPROVANTE_PG`, `INS.COMPROVANTE_PG_IRRF` | 30 | "ENTRE DIA 20 A 30" | fevereiro |

**Efeito prático.** Sob leitura ordinal estrita, a resolução do prazo não tem resposta e a abertura do ciclo falha — em fevereiro, para seis contratos. Um sistema que não abre o ciclo de fevereiro é um sistema que não funciona no fechamento de fevereiro.

**Como está tratado no código.** O princípio 1 do cap. 1, primeiro na ordem de precedência, diz que o sistema nunca trava o faturamento por falta de configuração própria. A resolução, portanto, **ajusta para o último dia do período e devolve o aviso `AJUSTE_FIM_DE_PERIODO`**. O ajuste antecipa a data, nunca a adia — prazo mais curto é seguro para o cumprimento — e o aviso existe para que a tela e a trilha mostrem que houve ajuste. Ajuste silencioso seria pior que o estouro. Casos `E-08-01` a `E-08-04` e `BISSEXTO-02` na suíte.

**Ação recomendada:** confirmar o ajuste, ou introduzir uma âncora `FIM_COMPETENCIA` no cap. 7.3 — que é o que "ENTRE DIA 30 A 31" provavelmente quer dizer ("até o fim do mês"). A segunda opção é mais limpa e elimina o aviso; exige alterar o domínio de âncoras e a `V003`.

---

## E-09 — CRÍTICO · O modelo do cap. 5.2 não comporta a exigência corporativa do cap. 7.1

Dois capítulos do mesmo documento se contradizem:

| Capítulo | O que diz |
|---|---|
| 5.2 | `exigencia \| id, **ciclo_id**, tipo_id, evento, profissional_id?, status, …` |
| 7.1 | "escopo CORPORATIVO gera 1 exigência por CNPJ por competência (**compartilhada entre ciclos, satisfeita uma única vez**)" |

Uma linha não pode pertencer a um ciclo e, ao mesmo tempo, ser compartilhada por todos eles.

**Efeito prático.** A saída fácil — duplicar a exigência corporativa em cada ciclo — está errada e é a que um desenvolvedor adota sem pensar, porque o modelo do cap. 5.2 empurra para ela. Com 15 contratos, a mesma CND passaria a ser cobrada 15 vezes, apareceria 15 vezes na régua de notificação e o "satisfeita uma única vez" deixaria de valer. É exatamente o oposto do que a decisão **D-03** pretende ("coleta única com replicação nos books").

**Um segundo buraco, do mesmo tamanho.** O cadastro tem `cliente` (o contratante) e **nenhuma tabela para o CNPJ do prestador** — que é o titular das certidões e guias do bloco corporativo, o que a validação **V3** confere e o que **R-03** diz que precisa suportar N (matriz e filiais).

**Como está tratado no código.** A migration `V004`:

- cria a tabela `empresa` (24ª do modelo), **vazia** — nenhum CNPJ é inventado;
- acrescenta `contrato_servico.empresa_id`, exigido para ativar o contrato;
- dá a `exigencia` dois modos de endereçamento mutuamente exclusivos, impostos por `CHECK`: ou `ciclo_id`, ou `(empresa_id, competencia)`;
- cria `ux_exigencia_corporativa`, que é o que faz valer o "satisfeita uma única vez";
- cria a view `exigencia_do_ciclo`, para que quem pergunta "o que falta neste ciclo?" não precise saber que existem dois modos.

Verificado em `db/testes/T002`: uma linha corporativa, quinze ciclos a enxergando.

**Ação recomendada:** incorporar ao cap. 5.2 do documento. A pendência **A07** (recolhimento por CNPJ único ou por filial) continua valendo — o modelo já suporta N, como R-03 antecipa.

---

## E-10 — MÉDIO · Duas regras que o cap. 7.1 não define

A materialização precisa de duas respostas que o documento não dá. Nenhuma tem resposta óbvia, e ambas mudam o que o sistema cobra.

**1. Qual o prazo de uma exigência corporativa compartilhada?**

Ela é uma só, mas o prazo vem de uma regra que pode variar por contrato. Se o BNB exige a CND no 5º dia útil e a CEF no 10º, qual vale para a CND compartilhada?

Adotado o **menor prazo** entre os contratos que a exigem — a CND precisa estar lá quando o primeiro ciclo precisa dela. Prazos ainda indefinidos (aguardando evento) não entram no cálculo.

**2. O que é "profissional alocado ativo na competência"?**

O cap. 7.1 usa a expressão sem defini-la. As leituras possíveis divergem em quem entra:

| Critério | Quem entra |
|---|---|
| **Interseção** (adotado) | Qualquer sobreposição entre a alocação e o mês |
| Ativo no último dia | Exclui quem foi desligado durante o mês |
| Ativo no primeiro dia | Exclui quem foi admitido durante o mês |

Adotada a **interseção**. Quem foi desligado no dia 3 trabalhou três dias, tem contracheque, encargos e rescisão a comprovar; quem entrou no dia 20 idem. Deixar qualquer um dos dois de fora é deixar passar exatamente o caso que a responsabilidade subsidiária alcança — e é o caso que mais aparece, porque admissão e desligamento raramente coincidem com a virada do mês.

Casos `MAT-08` (interseção) e `MAT-09` (sem alocado, gera alerta) na suíte.

**Ação recomendada:** confirmar as duas leituras e registrá-las no cap. 7.1.

---

## Como cada achado está tratado no repositório

| ID | Tratamento no código | Ainda pendente |
|---|---|---|
| E-01 | `regra_conciliacao` existe no esquema mas **não é semeada**. A restrição `regra_conc_sem_tolerancia_nao_bloqueia` impõe o cap. 9 (sem tolerância ⇒ modo ALERTA). | A numeração canônica |
| E-02 | Alias descartado dos dois tipos; âncoras de conteúdo mutuamente exclusivas identificadas em documento real | **Nada** — encerrado |
| E-03 | — | Redação do critério de aceite da F0-03. O número correto hoje é **59** aliases carregáveis (60 menos a colisão) |
| E-04 | `contrato_servico` e `regra_exigibilidade` **não são semeadas**: a coluna `CONTRATO` do anexo é cliente. Os 8 clientes entram inativos (`ativo=false`), com CNPJ marcador a substituir no cadastro. | A03 e A04 |
| E-05 | Valores derivados isolados em `dados/complemento_tipo_documental.csv`, todos com `CONFIRMADO=NAO`. A massa real corrigiu 3 derivações e confirmou 1 — ver [`ACHADOS-MASSA-REAL.md`](ACHADOS-MASSA-REAL.md), seção 5 | Revisão da área demandante nos 47 tipos restantes |
| E-06 | Leitura que satisfaz os dois exemplos do documento, fixada nos casos `DOC-01`/`DOC-02` da suíte | Confirmação e registro no cap. 7.3 |
| E-07 | Facultativos marcados com `[FACULTATIVO]` no calendário, removíveis por cadastro; par de casos mede a diferença | Decisão da DAF + feriados municipais |
| E-08 | Ajuste para o último dia do período com aviso `AJUSTE_FIM_DE_PERIODO`; 5 casos na suíte | Confirmar o ajuste ou criar âncora `FIM_COMPETENCIA` |
| E-09 | `V004`: tabela `empresa`, endereçamento duplo com `CHECK`, unicidade corporativa e view `exigencia_do_ciclo`. Verificado em `T002` | Incorporar ao cap. 5.2; pendência A07 |
| E-10 | Menor prazo entre contratos; interseção para alocação ativa. Casos `MAT-08`/`MAT-09` | Confirmar e registrar no cap. 7.1 |

---

## Resumo para a reunião de kickoff

| ID | Severidade | Bloqueia | Decisão necessária de |
|---|---|---|---|
| E-01 | Crítico | Escrita das histórias F1-05, F2-04 e do cadastro `regra_conciliacao` | Área demandante + arquitetura |
| E-02 | Alto | Meta de precisão da F1-03; carga inicial da F0-03 | Gestão de Contratos (AP) |
| E-03 | Médio | Aceite da F0-03 | Arquitetura (redação do critério) |
| E-04 | Baixo | Carga da fase 0 — já coberto por A03/A04 | Gestão de Contratos |
| E-05 | Médio | Materialização por CNPJ (cap. 7.1) e tarjamento (F2-07) | Gestão de Contratos + DAF |
| E-06 | Alto | Semântica do prazo — erro de um dia em 95 das 176 linhas | Área demandante + arquitetura |
| E-07 | Médio | Cômputo de dia útil; feriados municipais | DAF |
| E-08 | Alto | Abertura de ciclo em fevereiro para 6 contratos | Área demandante |
| E-09 | Crítico | Modelo da exigência corporativa e CNPJ do prestador | Arquitetura + AP (A07) |
| E-10 | Médio | Prazo compartilhado e critério de alocação ativa | Área demandante |

**E-01 e E-02 merecem entrar na mesma frente das pendências A03, A13 e A05**, porque são baratos de resolver agora (decisão de numeração e uma linha de catálogo) e caros de resolver depois — E-01 depois de escrito o código de conciliação, E-02 depois de calibrados os limiares em modo sombra.
