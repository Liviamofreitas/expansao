# Correções a aplicar no documento normativo — V1 → V1.1

**O que é este documento.** Os achados da errata foram decididos e implementados no código. O que ainda não mudou é o **documento normativo** (`SGDF_Documentacao_Desenvolvimento_V1.docx`), que pertence à área demandante. Este arquivo é o patch: seção por seção, o que trocar e por quê.

Enquanto ele não for aplicado, **o código e o documento divergem** — e o código é que está de acordo com as decisões tomadas. Aplicar isto é o que devolve o documento à posição de fonte normativa.

| Achado | Decisão | Estado no código | Estado no documento |
|---|---|---|---|
| E-01 | Numeração canônica = Anexo 1 | Implementado | **Cap. 9 a reescrever** |
| E-02 | Alias em colisão não pertence a nenhum tipo | Implementado | Nota no cap. 8.3 |
| E-03 | Critério da F0-03 corrigido para 59 | — | **Cap. 18 a corrigir** |
| E-06 | Semântica do offset por âncora | Implementado | **Cap. 7.3 a explicitar** |
| E-07 | Facultativos contam como não úteis | Implementado | Nota no cap. 7.3 |
| E-08 | Âncora `FIM_COMPETENCIA` | Implementado | **Cap. 7.3 a estender** |
| E-09 | Endereçamento duplo da exigência + tabela `empresa` | Implementado | **Cap. 5.1 e 5.2 a estender** |
| E-10 | Menor prazo; interseção para alocação | Implementado | Cap. 7.1 a explicitar |
| E-04, E-05 | — | Isolado em CSV | Aguarda A03, A04 e revisão do complemento |

---

## C-01 · Cap. 9 — renumerar as regras de conciliação (E-01)

**Decisão:** a numeração do **Anexo 1** (aba `REGRAS_CONCILIACAO`) é a canônica. É a fonte declarada da carga inicial (D-10) e já carrega as colunas de tolerância e exceção a preencher.

### De-para do cap. 9 atual para a numeração canônica

| Cap. 9 V1 | Descreve | Passa a ser | Nome canônico |
|---|---|---|---|
| R01 | Guia de FGTS × comprovante (pareamento) | **R02** | Cobertura do FGTS |
| R02 | DCTFWeb = Σ DARF, cada um com comprovante | **R03** | Cobertura do INSS |
| R03 | Certidões: validade ≥ data prevista da NF | **R06** | Vigência das certidões |
| R04 | `formatos_pendentes = ∅` e coerência entre formatos | **R13** (nova) | Completude de formatos |
| R05 | Alocados ⊆ folha, cada um com contracheque | **R04** | Completude da equipe |
| R06 | Todo evento derivado com conjunto documental completo | **R14** (nova) | Fechamento de eventos derivados |
| R07 | Cobertura do plano de saúde | **R01** | Cobertura do plano de saúde |
| R08 | Cobertura de VA/VR e VT | **R07** | Cobertura de VA/VR e VT |
| R09 | Σ base FGTS × alíquota ≈ guia | **R02** | Cobertura do FGTS |
| R10 | Σ bases ≈ DCTFWeb por grupo de receita | **R03** | Cobertura do INSS |
| R11 | CPFs do extrato FGTS ⊇ alocados ativos | **R02** | Cobertura do FGTS |
| R12 | Medição ≤ empenho e = valor da NF | **R11** | Vinculação ao empenho |

**Três observações que a conversão revelou.**

1. **Colapso de granularidade.** As regras R01, R09 e R11 do cap. 9 viram todas a canônica **R02**; R02 e R10 viram **R03**. A numeração do anexo é mais grossa: uma regra canônica por *obrigação*, enquanto o cap. 9 tem uma por *verificação*. Não é perda — as verificações continuam sendo o conteúdo da regra — mas o campo `logica` de R02 e R03 precisa reunir as três/duas verificações que hoje estão separadas.

2. **Duas regras do cap. 9 não têm casa canônica** e ficam como **R13** e **R14**, a acrescentar ao Anexo 1. Nenhuma das duas é dispensável: R13 (completude de formatos) sustenta D-05, e R14 é o que fecha os eventos derivados da folha (cap. 7.2).

3. **A canônica R10 (Defasagem de competência) duplica a validação V4** do cap. 8.4. São o mesmo controle em camadas diferentes: V4 reprova o documento na validação unitária; R10 o veria de novo na conciliação. **Recomendação:** manter V4 como o controle efetivo e marcar R10 no cadastro como já coberta, para não gerar divergência duplicada na tela. A decisão é da área demandante.

**Também:** a canônica **R12 (Relógio D+3)** está classificada como *Indicador* no anexo, não como regra bloqueante — e é a mesma coisa que o indicador "NF em D+3" do cap. 21. Carregada com `modo_pretendido = 'ALERTA'`.

### Estado atual da carga

As 12 regras estão em `db/seed/V100__carga_inicial.sql`, todas em `modo = 'ALERTA'`, porque a coluna *Tolerância (preencher)* do anexo está vazia e o cap. 9 determina que regra sem tolerância opera em alerta. A severidade pretendida ficou em `modo_pretendido`. A view `regra_conciliacao_a_parametrizar` lista as **11** que aguardam tolerância para poder bloquear — é a lista de trabalho da área demandante.

---

## C-02 · Cap. 7.3 — semântica do offset, âncora nova e facultativos

### C-02.1 Explicitar a semântica por âncora (E-06)

Acrescentar ao cap. 7.3, após a tabela de campos:

> A semântica do `offset` depende da âncora:
>
> | Âncora | Contagem | Leitura |
> |---|---|---|
> | `INICIO_COMPETENCIA` | **Ordinal** | "o N-ésimo dia (útil ou corrido) da competência". `offset ≥ 1`; não existe dia zero. |
> | `FIM_COMPETENCIA` | **Aditiva**, base no último dia | `offset = 0` significa o próprio fim do mês. Em dia útil, **rola para trás**. |
> | `ATESTE`, `SOLICITACAO_FATURAMENTO`, `EVENTO` | **Aditiva**, base no evento | "base + N dias". Em dia útil, a base rola para frente se não for útil. |

Isto não muda comportamento: é a única leitura que satisfaz os dois exemplos já presentes no capítulo (`{INICIO_COMPETENCIA, CORRIDO, 21}` = "até o dia 21" e `{ATESTE, CORRIDO, 3}` = "D+3"). Está sem registro, e é um erro de um dia em 95 das 176 linhas da matriz se alguém ler diferente.

### C-02.2 Acrescentar a âncora `FIM_COMPETENCIA` (E-08)

No domínio do campo `ancora`, acrescentar `FIM_COMPETENCIA` com a nota:

> `FIM_COMPETENCIA` expressa "até o fim do mês". Em dia útil o arredondamento é **para trás**, não para frente: o último dia útil de abril tem de cair em abril — rolar para frente cairia em maio e mudaria a competência do prazo. É o inverso das âncoras de evento, e é intencional.

**Recadastro necessário:** oito linhas da matriz pedem o dia 30 ou 31 da competência, que não existe em alguns meses. Estão listadas com o prazo corrigido em [`dados/correcoes_matriz.csv`](../dados/correcoes_matriz.csv), a aplicar ao Anexo 1.

### C-02.3 Nota sobre ponto facultativo (E-07)

Acrescentar ao cap. 7.3:

> Carnaval (segunda e terça) e Corpus Christi são ponto facultativo federal, não feriado. **Decisão: contam como dias não úteis.** O prazo fica mais longo, mas nunca vence num dia em que não há ninguém para responder à cobrança. Estão no calendário marcados com `[FACULTATIVO]` na descrição, e a decisão é reversível por cadastro.
>
> **Feriados municipais** não estão na carga inicial: dependem do município de cada contrato e não constam de nenhuma fonte do pacote. Cadastrá-los é pré-requisito para que o prazo em dia útil valha para contratos cujo município tenha feriado local.

---

## C-03 · Cap. 5.1 e 5.2 — exigência corporativa e empresa emitente (E-09)

O cap. 5.2 modela `exigencia` com `ciclo_id`, e o cap. 7.1 determina que a exigência de escopo corporativo é "compartilhada entre ciclos, satisfeita uma única vez". Uma linha não faz as duas coisas.

**Acrescentar ao cap. 5.1**, na tabela de cadastro:

> | `empresa` | id, razao_social, cnpj, matriz, ativo | CNPJ do **prestador** — titular das certidões e guias do bloco corporativo, e o que a validação V3 confere. Plural desde o início (R-03: modelo suporta N). |

E em `contrato_servico`, acrescentar `empresa_id` aos campos, com a regra: *contrato só pode ser ativado com empresa emitente definida*.

**Substituir a linha de `exigencia` no cap. 5.2** por:

> | `exigencia` | id, **ciclo_id?**, **empresa_id?**, **competencia?**, tipo_id, evento, profissional_id?, status, formatos_pendentes, prazo_calculado, responsavel_id, origem, regra_id | Dois modos de endereçamento **mutuamente exclusivos**: ou `ciclo_id` (escopos CONTRATO e PROFISSIONAL), ou `(empresa_id, competencia)` (escopo CORPORATIVO). Único por `(empresa, competência, tipo, evento)` no modo corporativo — é essa unicidade que faz valer o "satisfeita uma única vez" do cap. 7.1. A view `exigencia_do_ciclo` reúne os dois para consulta. |

---

## C-04 · Cap. 7.1 — duas regras que faltam (E-10)

Acrescentar ao passo 3 da resolução:

> **Prazo da exigência corporativa.** Ela é uma só, mas o prazo vem de regras que podem variar por contrato. Vale o **menor** prazo entre os contratos que a exigem: a certidão precisa estar lá quando o primeiro ciclo precisa dela. Prazos ainda indefinidos (aguardando evento) não entram no cálculo.
>
> **Profissional alocado ativo na competência.** Vale a **interseção**: basta um dia de sobreposição entre o período de alocação e o mês. Quem foi desligado no dia 3 trabalhou três dias e tem contracheque, encargos e rescisão a comprovar; quem entrou no dia 20 idem. Excluir qualquer um dos dois deixaria passar exatamente o caso que a responsabilidade subsidiária alcança.

---

## C-05 · Cap. 8.3 — alias em colisão (E-02)

Acrescentar:

> Um alias normalizado pertence a **no máximo um** tipo canônico; a unicidade é global e imposta no banco. Um nome que serve a dois documentos não é sinal confiável e daria bônus de score ao tipo errado metade das vezes — nesse caso o alias é descartado dos dois, e a desambiguação vem das âncoras de conteúdo, que precisam ser mutuamente exclusivas.
>
> Na carga inicial isso ocorreu com `GFD_GUIA_DO_FGTS`, que aparecia em `FGT.GUIA` e `FGT.RELATORIO_DIGITAL`. **Pendência técnica:** as regras de reconhecimento desses dois tipos precisam de âncoras que os distingam, senão ambos dependerão de triagem.

---

## C-06 · Cap. 18 — critério de aceite da F0-03 (E-03)

O critério cita "51 tipos e 69 aliases". O número de aliases não fecha por nenhum critério de contagem: são 72 brutos, 61 pares distintos, 60 normalizados — e **59** carregáveis depois de descartar a colisão de E-02.

**Substituir por um critério derivado da regra, não de uma contagem manual:**

> A migration importa os 51 tipos e todos os aliases da coluna `NOMENCLATURAS_ATUAIS`, deduplicados pela normalização do cap. 5.1, descartando os que colidirem entre tipos distintos. Na carga atual isso resulta em 59 aliases e 1 descarte registrado.

Contagem manual em critério de aceite falha na primeira execução por erro do próprio critério, e custa uma rodada de investigação antes de alguém desconfiar do enunciado.

---

## O que continua aberto

| Achado | Depende de |
|---|---|
| **E-04** | Pendências **A03** e **A04** — a composição real dos 15 contratos-serviço |
| **E-05** | Revisão de [`dados/complemento_tipo_documental.csv`](../dados/complemento_tipo_documental.csv): `escopo` e `sigilo` das 51 linhas, todas com `CONFIRMADO=NAO` |
| **C-01 obs. 3** | Decisão sobre a sobreposição entre a canônica R10 e a validação V4 |
| Tolerâncias | As 11 regras em `regra_conciliacao_a_parametrizar` |

---

## C-11 · Acrescentar a validação V8 ao cap. 8.4

**Origem:** achado A12 de [`ACHADOS-MASSA-REAL.md`](ACHADOS-MASSA-REAL.md).

**O que falta no texto atual.** As sete validações do cap. 8.4 cobrem segurança, legibilidade, titularidade, competência, vigência, completude de formatos e unicidade. **Nenhuma pergunta se o documento tem conteúdo.**

Dois comprovantes reais do Itaú (SISPAG SALÁRIOS) trazem 603 caracteres de texto nativo que são apenas os rótulos — `Nome da empresa:`, `Agência:`, `Conta corrente:`, `Nome:`, `Valor:` — e nenhum valor. Eles:

| Validação | Resultado |
|---|---|
| V1 antivírus, MIME, tamanho | passa |
| V2 legibilidade por contagem de caracteres | passa |
| V3 CNPJ | não se aplica (o documento não traz CNPJ) |
| V4 competência | não se aplica |
| V5 vigência | não se aplica |
| V6 formatos exigidos | passa |
| V7 hash inédito | passa |

Seriam anexados a uma exigência como prova de pagamento e entrariam no book.

**Texto a acrescentar na tabela do cap. 8.4:**

> | V8 | Campos essenciais presentes e com o formato declarado | REJEITADO (incompleto) |

**Definição de campo essencial**, para o cap. 8.5: é o campo que alguma outra validação ou regra de conciliação **consome** — não "todo campo que a regra extrai". Sem ele, o documento não pode ser validado nem conciliado, o que o torna, para efeito do portão documental, equivalente a não ter sido entregue. A derivação:

| Consumidor | Campo | Torna essencial em |
|---|---|---|
| V3 | `cnpj` | tipos de escopo corporativo |
| V4 | `competencia` | tipos com defasagem por competência |
| V5 | `validade`, `natureza` | certidões |
| R01, R02 | `valor` | guias e comprovantes |

**Limite deliberado.** V8 **não** confere se o valor está certo — isso é conciliação e depende de outro documento. V8 confere se o valor **existe e tem forma de valor**. É a diferença entre *"este comprovante não bate com a guia"* e *"este arquivo não é um comprovante"*.

**Tipo sem campos essenciais declarados não é reprovado**, e sim registrado como `NAO_APLICAVEL` com motivo — cap. 1, princípio 1. Mas o registro é obrigatório: sem ele ninguém descobre que um tipo está passando sem ser conferido.

---

## C-12 · Registrar o veredito das validações unitárias

**Lacuna encontrada ao implementar V8.** A máquina de estados do cap. 6.1 leva `RECEBIDO → REJEITADO` "por falha unitária", mas **o modelo de dados não tinha onde gravar qual validação falhou e por quê**. O motivo existia apenas na mensagem de notificação, que não é registro auditável.

O cap. 16 exige que toda decisão automática seja reproduzível a partir de documento (hash) + versão da regra. Sem esse registro, uma reprovação não é reproduzível: ninguém consegue dizer, seis meses depois, por que aquele documento foi recusado — e é exatamente essa a pergunta que um cliente faz quando questiona uma pendência.

**Acrescentar ao cap. 5 (modelo de dados):** tabela `validacao_documento` (implementada em `db/migracoes/V007`), com `documento_id`, `codigo` (V1..V8), `resultado`, `motivo`, `detalhe`, `regra_recon_id` e `executada_em`. Histórico: reprocessar **acrescenta** linha, nunca substitui — regra nova não apaga por que a regra antiga recusou. A view `validacao_vigente` devolve a última execução de cada validação.

Duas restrições no banco, e não só no serviço:

- reprovar sem motivo não é gravável;
- `NAO_APLICAVEL` também exige motivo — "não se aplica" é uma decisão, não a ausência de uma.
