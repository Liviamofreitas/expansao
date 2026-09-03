# Errata da V1 — inconsistências entre a documentação e o Anexo 1

**Verificação executada sobre:** `SGDF_Documentacao_Desenvolvimento_V1.docx` × `Anexo1_Diagnostico_Checklist_Faturamento.xlsx`
**Método:** conferência programática das contagens, chaves e referências cruzadas citadas no documento contra o conteúdo real da planilha.
**Status:** achados **verificados**, ainda **não corrigidos**. Correção pendente de decisão da área demandante (a regra de leitura do documento proíbe resolver por suposição).

> Nada aqui invalida o pacote. Três dos quatro achados são de numeração e contagem; um é de determinismo de classificação e tem efeito direto no maior volume documental do sistema. Todos são corrigíveis antes da sprint 0.

---

## E-01 — CRÍTICO · Os códigos R01–R12 significam coisas diferentes no documento e no anexo

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

Na aba `CATALOGO_DOCUMENTOS`, a nomenclatura `GFD_GUIA_DO_FGTS` aparece como alias de **dois** códigos:

- `FGT.GUIA`
- `FGT.RELATORIO_DIGITAL`

O cap. 5.1 define `tipo_alias.texto_normalizado` com normalização "sem acento, minúsculas, separadores colapsados" — sob essa regra as duas entradas colapsam para a mesma chave `gfd_guia_do_fgts`. O cap. 8.3 usa o alias como **bônus de score** na classificação determinística; um arquivo cujo nome contenha esse padrão recebe o bônus para dois tipos concorrentes ao mesmo tempo.

**Efeito prático.** Empate de score na família FGTS — a de maior volume mensal e a que alimenta R01/R02 em qualquer das duas numerações. O resultado provável é queda permanente para a fila de triagem (bom) ou vínculo automático no tipo errado se outro sinal desempatar (ruim, e silencioso). Fere o princípio 3 do cap. 1 (decisão automática determinística) e ameaça a meta de precisão ≥ 95% da história **F1-03**.

**Ação recomendada:** decidir a qual tipo o alias pertence e remover do outro; se ambos os documentos realmente circulam com esse nome, a desambiguação tem de vir de âncora de conteúdo, não de alias — e a regra de reconhecimento dos dois tipos precisa declarar âncoras mutuamente exclusivas. **Exigido:** a carga inicial deve rejeitar alias normalizado duplicado entre tipos distintos (restrição de unicidade em `tipo_alias.texto_normalizado`), para que o problema não se repita por cadastro.

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

## Resumo para a reunião de kickoff

| ID | Severidade | Bloqueia | Decisão necessária de |
|---|---|---|---|
| E-01 | Crítico | Escrita das histórias F1-05, F2-04 e do cadastro `regra_conciliacao` | Área demandante + arquitetura |
| E-02 | Alto | Meta de precisão da F1-03; carga inicial da F0-03 | Gestão de Contratos (AP) |
| E-03 | Médio | Aceite da F0-03 | Arquitetura (redação do critério) |
| E-04 | Baixo | Carga da fase 0 — já coberto por A03/A04 | Gestão de Contratos |

**E-01 e E-02 merecem entrar na mesma frente das pendências A03, A13 e A05**, porque são baratos de resolver agora (decisão de numeração e uma linha de catálogo) e caros de resolver depois — E-01 depois de escrito o código de conciliação, E-02 depois de calibrados os limiares em modo sombra.
