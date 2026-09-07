# Achados do primeiro contato com documentos reais

**Massa analisada:** 20 documentos do OwnCloud da Engesoftware, competência 06/2026, extraídos com o `ExtratorPdfBox` da história F1-02.

> **Os documentos não estão no repositório.** São fiscais e reais; histórico de git é praticamente irreversível, e o cap. 19 manda massa de teste ficar em ambiente controlado. Foram usados para derivar os achados abaixo; o que se versiona são as correções, os testes com conteúdo sintético e este registro.

---

## 1. Cobertura

| Tipo canônico | Documento | Páginas | Extração |
|---|---|---|---|
| `CER.CND_RFB` | CND_RFB | 1 | nativa |
| `CER.CND_ESTADUAL` | CND_GDF | 1 | nativa |
| `CER.CNDT` | CNDT | 1 | nativa |
| `CER.CRF_FGTS` | CRF_FGTS | 1 | nativa |
| `INS.DCTFWEB` | DCTFWEB_COMPLETA | 8 | nativa |
| `INS.COMPROVANTE_PG` | COMPROVANTE_PG_INSS | 8 | nativa |
| `INS.COMPROVANTE_PG_IRRF` | COMPROVANTE_PG_IRRF | 6 | nativa |
| `FGT.GUIA` | GFDGUIA_DO_FGTS | 1 | nativa |
| `FGT.RELATORIO_DIGITAL` | RELATORIO_GUIA_DO_FGTS | 9 | nativa |
| `FGT.COMPROVANTE_PG` | COMPROVANTE_PG_FGTS | 1 | nativa |
| `CER.SICAF` | SICAF | 1 | nativa |
| `CER.CND_CIVEL_CRIMINAL` | CND cível e criminal (TJDFT) | 1 | nativa |
| `CER.CND_FALENCIA` | CND falências e recuperações (TJDFT) | 1 | nativa |

**Os 13 extraem com texto nativo.** Nenhum exigiu OCR — a contingência do cap. 8.2 não é o caminho comum, ao menos para o bloco corporativo.

**Os 10 tipos do critério de aceite da F1-03 estão cobertos.** Falta apenas repetir a coleta em mais duas competências fechadas, para a medição de precisão do cap. 19.

### Nomes de arquivo, como vieram do OwnCloud

```
27.09.2026__CERTIDA_O_NEGATIVA_DE_DISTRIBUIC_A_O_ESPECIAL__AC_O_ES_CI_VEIS_E_CRIMINAIS.pdf.pdf
22.09.2026__SISTEMA_DE_CADASTRAMENTO_UNIFICADO_DE_FORNECEDORES__SICAF.pdf
COMPROVANTE_PG_IRRF                                    (sem extensão)
GFDGUIA_DO_FGTS.pdf
```

Prefixo de data, acentos substituídos por underscore, extensão duplicada, extensão ausente. É a confirmação empírica de **D-07**: o nome do arquivo não é chave confiável, e o peso dele no score do cap. 8.3 tem de ser baixo. Note também que o nome do arquivo cível/criminal diz `NEGATIVA` e o documento é **positiva** — ver o achado A10.

---

## 2. Âncoras identificadas — insumo direto da F1-03

Todas verificadas contra o texto real, já normalizado (sem acento, minúsculas, separadores colapsados).

| Tipo | Âncora de título | Campos com rótulo estável |
|---|---|---|
| `CER.CND_RFB` | `certidao positiva com efeitos de negativa de debitos relativos aos tributos federais` | `Nome:`, `CNPJ:` |
| `CER.CND_ESTADUAL` | `governo do distrito federal` + `certidao negativa de debitos` | `CERTIDÃO Nº:`, `CNPJ:`, `Válida até` |
| `CER.CNDT` | `certidao negativa de debitos trabalhistas` + `banco nacional de devedores trabalhistas` | `Certidão nº:`, `Expedição:`, `Validade:` |
| `CER.CRF_FGTS` | `certificado de regularidade do fgts` | `Inscrição:`, `Validade:` (período), `Certificação Número:` |
| `INS.DCTFWEB` | `documento de arrecadacao de receitas federais` + `composicao do documento de arrecadacao` | `Período de Apuração`, `Data de Vencimento`, `Número do Documento`, `Valor Total do Documento` |
| `INS.COMPROVANTE_PG` | `comprovante de pagamento de darf` | `Codigo de Barras:`, `Data de Pagamento:`, `Valor Total:`, `Autenticacao:` |
| `FGT.GUIA` | `gfd - guia do fgts digital` | `Pagar este documento até`, `Identificador`, `Total da Guia` |
| `FGT.RELATORIO_DIGITAL` | `detalhe da guia a ser emitida` + `relacao de trabalhadores` | `Vencimento da Guia`, `Total da Guia (FGTS)`, `Qtd. Trabalhadores FGTS` |
| `FGT.COMPROVANTE_PG` | (sem título fixo — identificado por pareamento) | `Valor:`, `Data:`, `ID Transação:` |
| `CER.SICAF` | `sistema de cadastramento unificado de fornecedores` | `CNPJ:`, `Situação do Fornecedor:`, `Data de Vencimento do Cadastro:`, `Impedimento de Licitar:` |
| `CER.CND_CIVEL_CRIMINAL` | `distribuicao (especial - acoes civeis e criminais)` | `CERTIFICAMOS que`, `CONSTA`/`NADA CONSTA`, data da consulta |
| `CER.CND_FALENCIA` | `distribuicao (acoes de falencias e recuperacoes judiciais)` | idem |

As duas últimas vêm do mesmo emissor (TJDFT) e compartilham quase todo o texto. **A âncora que as distingue é o parêntese do título** — `(ESPECIAL - AÇÕES CÍVEIS E CRIMINAIS)` contra `(AÇÕES DE FALÊNCIAS E RECUPERAÇÕES JUDICIAIS)`. Sem ele, as duas colidiriam, do mesmo modo que as guias do FGTS no achado E-02.

---

## 3. Achado E-02 encerrado: as duas guias do FGTS são distinguíveis

A pendência técnica que sobrou do achado E-02 era: `FGT.GUIA` e `FGT.RELATORIO_DIGITAL` precisam de âncoras **mutuamente exclusivas**, já que o alias `GFD_GUIA_DO_FGTS` foi descartado dos dois. Os documentos reais resolvem:

| | `FGT.GUIA` | `FGT.RELATORIO_DIGITAL` |
|---|---|---|
| Título | `GFD - Guia do FGTS Digital` | `Detalhe da Guia a Ser Emitida` |
| Seção exclusiva | `Composição do Documento` | `Relação de Trabalhadores` |
| Páginas | 1 | 9 |
| CPFs no conteúdo | 0 | **160** |
| Função | o documento que se paga | o detalhamento por trabalhador |

As âncoras de título não têm interseção. **E-02 pode ser fechado.**

---

## 4. Achados que mudam o desenho

### Corrigidos nesta rodada (história F1-02)

| # | Achado | Efeito se não corrigido |
|---|---|---|
| **A1** | O título da CND da RFB quebra linha: `TRIBUTOS\nFEDERAIS` | Âncoras multi-palavra não casariam. Como os títulos **são** as âncoras do cap. 8.5, quase nenhuma funcionaria |
| **A2** | A CND da RFB traz **266 ocorrências de `U+00A0`** no corpo | Âncora com espaço comum não casaria |
| **A3** | Os comprovantes trazem ligadura `U+FB01` em "de**ﬁ**ciência" | Âncora com "fi" não casaria |
| **A4** | `COMPROVANTE_PG_IRRF` chegou **sem extensão** | Documento legítimo rejeitado como MIME divergente |
| **A5** | O comprovante do INSS tem 8 páginas, a 6ª **em branco** (sem texto e sem imagem) | Documento inteiro mandado a OCR e triagem — o falso positivo em massa do risco P01 |

### Abertos — afetam as validações da F1-03

| # | Achado | Consequência |
|---|---|---|
| **A6** | A guia do FGTS Digital traz o CNPJ **truncado na raiz**: `00.681.946`, sem `/0001-60` | **V3 reprovaria uma guia legítima.** Um regex de CNPJ completo não acha nada no documento |
| **A7** | O comprovante do SICOOB **mascara** o CNPJ: `**.681.946/0001-**` | **V3 reprovaria um comprovante legítimo** |
| **A8** | O CRF do FGTS traz **período**, não data: `Validade: 11/07/2026 a 09/08/2026` | V6 precisa extrair o **fim** do período. Ler o início venceria o documento cedo demais |
| **A9** | A validade do CRF é de **30 dias** (11/07 a 09/08) | A janela é curta: a régua precisa considerar que este documento vence dentro da própria competência |

**A6 e A7 juntos são o achado mais consequente em aberto.** Dois dos documentos centrais do bloco corporativo não expõem o CNPJ em formato completo. A validação de titularidade precisa comparar por **raiz do CNPJ** e aceitar máscara, comparando posição a posição com coringa — não igualdade de string. Sem isso, V3 reprova documento válido, que é o falso positivo que o risco P01 descreve.

### A10 — a natureza da certidão tem TRÊS valores, não dois

A premissa **R-01** diz: *"Certidão positiva com efeito de negativa = regular"*, e a validação **V5** do cap. 8.4 aceita `natureza ∈ {negativa, positiva c/ efeito de negativa}`. A massa real mostra um terceiro estado:

| Documento | Título | Situação |
|---|---|---|
| CND RFB | `CERTIDÃO POSITIVA COM EFEITOS DE NEGATIVA` | regular por R-01 |
| CND GDF | `CERTIDÃO NEGATIVA DE DÉBITOS` | regular |
| CNDT | `CERTIDÃO NEGATIVA DE DÉBITOS TRABALHISTAS` | regular |
| CND falências | `CERTIDÃO NEGATIVA DE DISTRIBUIÇÃO` — "NADA CONSTA" | regular |
| **CND cível e criminal** | **`CERTIDÃO POSITIVA DE DISTRIBUIÇÃO`** — "**CONSTA**" uma execução de título extrajudicial | **fora das duas categorias** |

**O problema.** Sob V5 como está escrita, essa certidão é **reprovada** — e ela está no OwnCloud, sendo usada no faturamento. Uma execução de título extrajudicial não impede contratar; a exigência contratual costuma ser *apresentar* a certidão, não que ela seja negativa.

Se o sistema reprovar automaticamente, trava o faturamento de um contrato regular. Se aprovar em silêncio, esconde um fato que alguém deveria ver.

**Proposta, para decisão da área demandante e do jurídico.** Extrair `natureza` com três valores — `NEGATIVA`, `POSITIVA_COM_EFEITO_NEGATIVA`, `POSITIVA` — e deixar o cadastro decidir, **por tipo documental**, se `POSITIVA` é aceitável. O padrão conservador seria: documento válido (existe, é do CNPJ certo, está vigente), exigência **atendida com ressalva**, e decisão humana registrada.

Isso segue o princípio 7 do cap. 1: *"o sistema confere completude e coerência documental; o ateste técnico permanece humano"*. Julgar se uma execução judicial impede faturar é juízo jurídico, não conferência documental.

**Também:** o nome do arquivo dessa certidão diz `CERTIDA_O_NEGATIVA`, e o documento é positiva. Mais uma razão para o nome ter peso baixo no score.

### A11 — o SICAF é um documento agregador

O SICAF traz, ele próprio, as validades de outras certidões:

```
Receita Federal e PGFN   Validade: 24/10/2026   Automática
FGTS                     Validade: 28/09/2026   Automática
Trabalhista              Validade: 27/02/2027   Automática
Receita Estadual/Distrital Validade: 04/11/2026
```

Duas consequências:

1. **Fonte de conferência cruzada.** As validades declaradas no SICAF podem ser confrontadas com as das certidões efetivamente anexadas — uma divergência indica documento desatualizado no book.
2. **`Ocorrência: Consta`** aparece no SICAF desta competência, mesmo com `Impedimento de Licitar: Nada Consta`. É outro campo de situação que a regra de reconhecimento deve extrair, e que cai no mesmo debate do achado A10.

O SICAF também traz `Data de Vencimento do Cadastro`, que é a validade do próprio documento — distinta das validades que ele lista.

### A12 — CRÍTICO · um documento pode ser legível e não conter dado algum

Dois dos três comprovantes de pagamento de folha (Itaú SISPAG) trazem **apenas os rótulos**:

```
SISPAG SALARIOS
Nome da empresa:
Agência:  Conta corrente:
Nome:
Agência:  Conta corrente:
Valor:
Informações fornecidas pelo pagador:
0BDD36626EF6D436B3E13B41525B3F7A7E9C01AE
```

603 caracteres no total. Sem nome de empresa, sem nome de funcionário, **sem valor**, sem agência, sem conta, sem data. Verificado: não há AcroForm, não há anotações, e a única imagem tem 5 915 pixels — é o logotipo, não uma digitalização.

**Por que isso é grave.** O documento passa em tudo que hoje se verifica:

| Verificação | Resultado |
|---|---|
| Antivírus, MIME, integridade (V1) | passa |
| Legibilidade (V2) — 603 caracteres, muito acima do mínimo de 50 | **passa** |
| Precisa de OCR? | **não** — tem camada de texto |

E se a regra de reconhecimento casar pelo título `SISPAG SALARIOS`, ele é **aceito como comprovante de pagamento** — satisfazendo uma exigência bloqueante com um documento que não prova pagamento nenhum. É exatamente o modo de falha que o sistema existe para evitar.

**Correção de desenho necessária: validação de completude de campos.** A regra de reconhecimento precisa declarar quais campos são **essenciais** para o tipo, e um documento reconhecido cujos campos essenciais não extraem **não satisfaz a exigência** — vai para triagem com o motivo "reconhecido, mas sem os dados que provam o fato".

Isso não existe hoje. V2 cobre legibilidade, V3 titularidade, V4 competência; nenhuma cobre "o documento tem o que precisa ter". Proposta de código: **V8 — completude de campos essenciais**.

### A13 — a extração de tabela quebra a associação entre linha e valor

Nos comprovantes em lote, o nome do funcionário ocupa duas linhas e o texto sai intercalado com a linha de dados:

```
[11] DOUGLAS VINISIOS
[12] 900059648 00000010225306072026 06/07/2026 CC 2.232,21
[13] NUNES SOUZA
[14] 900059649 00000010196806072026 RONI DA SILVA ROSA 06/07/2026 CC 5.935,22
```

Uma leitura linha a linha associa `DOUGLAS VINISIOS` a nenhum valor e `NUNES SOUZA` ao pagamento seguinte. Numa conciliação por profissional, isso **atribui o pagamento de uma pessoa a outra**.

A extração de tabela tem de agrupar glifos por **coluna (coordenada x)** e por **faixa de linha (coordenada y)**, não pela ordem de leitura. As posições já estão disponíveis desde a história F1-02 — falta o leitor que as use.

### A14 — a matrícula está embutida no número do cliente

O campo `Número do Cliente` do comprovante em lote tem 20 dígitos, e a estrutura é **matrícula à direita de 12 dígitos + `ddMMyyyy`**:

```
00000010225306072026  ->  matrícula 102253 + data 06072026
00000010196806072026  ->  matrícula 101968 + data 06072026
```

Confrontado com as matrículas do relatório do FGTS Digital: **11 de 11 casaram (100%)**.

**Consequência de desenho.** A conciliação por profissional deve usar **matrícula como chave de junção**, não o nome. Nome é chave ruim: há homônimos, grafias divergentes entre sistemas, abreviações, e ordem de nome trocada. A matrícula é estável, e está disponível nos dois lados.

O nome continua útil como **conferência secundária** — uma matrícula que casa mas com nome muito diferente é sinal de erro de cadastro, não de fraude, e merece alerta.

### A15 — CRÍTICO · a fonte perde caracteres, e o NUL quebra o banco

O relatório de VA/VR traz **caractere NUL no texto extraído**, no lugar de ligaduras que a fonte não mapeia para Unicode:

| Extraído | Deveria ser |
|---|---|
| `[NUL]nalidade` | `finalidade` |
| `[NUL]scal` | `fiscal` |
| `bene[NUL]ciários` | `beneficiários` |

**Dois estragos independentes.**

1. **O NUL quebra o PostgreSQL.** Coluna `text` recusa `0x00` — o `INSERT` em `campo_extraido` falharia com *invalid byte sequence*. Bug de produção garantido, e que só apareceria com esse tipo de documento.
2. **O "fi" some.** Uma âncora com "fi" nunca casaria — e "fi" é frequente no vocabulário fiscal: *certi**fi**cado*, *identi**fi**cação*, *noti**fi**cação*, ***fi**scal*. A âncora de `CER.CRF_FGTS` é justamente `Certificado de Regularidade`.

E o documento **parece perfeitamente legível**: tem texto de sobra, não exige OCR, nada denuncia a perda.

**Corrigido:** a extração descarta caracteres de controle — o caractere **e o glifo**, para o alinhamento continuar válido — e conta a perda. `TextoExtraido.extracaoDegradada()` sinaliza. Na massa de 19 documentos, apenas este acusa perda: 3 caracteres.

**Exigido de quem consome:** documento degradado vai a **triagem**, não a reprovação. O conteúdo existe; só não foi lido inteiro.

### A16 — o separador do CNPJ nem sempre é a barra

No mesmo relatório, o CNPJ do fornecedor de benefícios saiu assim:

```
CNPJ 32.223.020. 0001-18        (a barra virou ". ")
```

O padrão `\d{2}\.\d{3}\.\d{3}/\d{4}-\d{2}` não casa. O CNPJ da própria Engesoftware, no mesmo documento, saiu correto — a falha é do glifo da barra naquela fonte.

Somado aos achados **A6** (CNPJ truncado na raiz) e **A7** (CNPJ mascarado), fecha o quadro: **o reconhecimento de CNPJ não pode ser um regex único**. Precisa tolerar separador variante, truncamento e máscara, comparando por raiz e posição a posição.

### A17 — o contracheque é a peça-chave, e exige leitura por coordenada

O contracheque real (`FOL.CONTRACHEQUE`) extrai limpo e é o documento que liga profissional a benefício — exatamente a cadeia pedida. Estrutura por colaborador:

| Campo | Exemplo |
|---|---|
| Matrícula | `000101862` (com zeros à esquerda) |
| Nome, CPF, Cargo/Nível, Data de Admissão | — |
| Referência | `JUNHO/2026` — **por extenso**, não `06/2026` |
| Proventos / Descontos | duas colunas lado a lado |
| Totais | `TOTAL DE PROVENTOS`, `TOTAL DE DESCONTOS`, `LÍQUIDO A RECEBER` |
| Bases | `Salário Contratual`, `Sal. Contrib. INSS`, `Base Cálc. FGTS`, `FGTS Mês`, `Base Cálc. IRRF` |

**As rubricas de benefício aparecem como desconto**, com nomenclatura estável: `VALE ALIMENTACAO`, `VALE TRANSPORTE`, `TIT ASST MED HAPVIDA`, `DEP ASST MED HAPVIDA`, `TIT ASS ODT BRADESCO`, `DEP ODONT BRADESCO`, `ASSIST MED UNIMED`, `CO-PART ASST MED TIT`. O prefixo `TIT`/`DEP` distingue titular de dependente — essencial para a regra de cobertura, que precisa contar dependentes.

**Por que a leitura linear não serve.** Uma linha com desconto e sem provento sai assim:

```
SALARIO  30,00     6.452,92 INSS MES       823,61
TIT ASS ODT BRADESCO        11,49
```

A segunda linha tem apenas um desconto, mas parece começar na coluna de proventos. Lida assim, um desconto de benefício vira provento e o líquido deixa de fechar.

**Resolvido** pelo `LeitorDeTabela`, que agrupa glifos por coordenada. Verificado contra o contracheque real: `TIT ASS ODT BRADESCO` e `VALE ALIMENTACAO` caem corretamente em desconto, com provento vazio.

**Duas armadilhas a mais neste documento:**

1. **Cada contracheque aparece duplicado** — é o layout de duas vias (empregado e empregador) na mesma página, encerrado por `Recebi em: __/__/____`. Somar sem deduplicar **dobra o líquido**, e a regra de conciliação contra os comprovantes reprovaria um pagamento correto.
2. **`Feliz Aniversário!!!`** aparece dentro da área da tabela. Texto decorativo no meio dos dados precisa ser descartado por posição, não por conteúdo — não há como listar todas as mensagens que o gerador de folha pode inserir.

### A18 — o comprovante em lote não é separável por coordenada

Ao contrário do contracheque, o comprovante de pagamento em lote **não** cede a nenhum dos dois métodos de detecção de coluna:

| Método | Resultado |
|---|---|
| Projeção vertical das linhas de dados | Acha só 3 separações para 6 colunas: os nomes longos atravessam as faixas dos números, e não sobra lacuna consistente |
| Fronteiras deduzidas do cabeçalho | Erra por 1 a 3 caracteres: os rótulos são muito mais largos que os dados, e a distribuição espacial do cabeçalho não corresponde à das linhas |

A causa é o achado **A13**: o nome ocupa duas linhas e invade a faixa dos números em algumas delas.

**A estratégia correta para este layout é outra** — reconhecer os campos pelo **formato do conteúdo** dentro da linha, que aqui é distintivo:

| Campo | Formato |
|---|---|
| Número do pagamento | 9 dígitos iniciados por `9000` |
| Número do cliente | 20 dígitos (matrícula + `ddMMyyyy`, achado A14) |
| Data | `dd/MM/yyyy` |
| Valor | decimal com vírgula |
| Nome | o que sobra, mais as linhas órfãs adjacentes |

Isso não é leitura de tabela; é extração por padrão posicional, e fica registrado como trabalho separado. **Não foi implementado** — a calibração por tentativa não convergia, e insistir teria custado mais do que entregar o leitor que já funciona.

> **Correção (A18 resolvido).** O diagnóstico acima estava errado, e o erro era de método: a projeção estava sendo calculada sobre a **página inteira**, onde o rodapé de atendimento e as linhas de texto corrido atravessam todas as faixas e apagam as lacunas. Restrita às linhas de dados, a projeção acha as seis colunas de forma **estável para qualquer largura mínima entre 4 e 10 pontos** — as fronteiras saem em x = 118,4 / 239,4 / 356,9 / 438,4 / 494,9.
>
> O que de fato exigia trabalho novo era outra coisa: o nome do funcionário ocupa **até três linhas visuais**, uma acima e uma abaixo da linha dos números, então o registro precisa ser agrupado por lacuna vertical (31 pontos entre registros, 6,5 a 13 dentro) antes de ser recortado por coluna — o mesmo mecanismo do achado A19.
>
> **O padrão posicional continuou necessário, mas para outra função.** Não para segmentar as colunas: para **delimitar a região de dados**. Delimitá-la pelo literal `Total Compromissos` é frágil — num comprovante sem esse rodapé o texto de atendimento entra nos dados e destrói a projeção, que foi exatamente como este achado se formou. A âncora `^\d{9}\b` (número do pagamento) marca o início de cada registro, e só os blocos que a contêm entram na projeção. O rodapé deixa de importar.
>
> **A verificação é o próprio documento.** O banco imprime `Total Compromissos: 11` e `Valor Total: R$ 60.363,05`. A leitura reproduz os dois exatamente — 11 registros, R$ 60.363,05. Uma extração que não reproduz o rodapé está errada e diz que está.
>
> **Ausência de rodapé é divergência, não conformidade.** `COMPROVANTE_PG_FOLHA_6` e `_7` (os documentos vazios do achado A12) leem zero pagamentos e não declaram totais. A primeira versão do registro respondia `CONFERE` — uma falha silenciosa perfeita. Um documento que não pôde ser conferido não confere: são coisas diferentes, e tratá-las como iguais é o pior resultado possível.

---

## 5. Correção de classificação de sigilo — evidência para o achado E-05

O E-05 registrou que `escopo` e `sigilo` foram **derivados**, não confirmados. A massa real corrige três derivações e confirma uma.

| Tipo | Sigilo derivado | Evidência real | Sigilo correto |
|---|---|---|---|
| `INS.DCTFWEB` | `INTERNO` | **1 CPF com DV válido** na página 7 (responsável pela transmissão) | **`PESSOAL`** |
| `INS.COMPROVANTE_PG` | `INTERNO` | **1 CPF com DV válido** na página 4 | **`PESSOAL`** |
| `INS.COMPROVANTE_PG_IRRF` | `INTERNO` | **1 CPF com DV válido** | **`PESSOAL`** |
| `FGT.RELATORIO_DIGITAL` | `PESSOAL` | **160 CPFs**, com nome e remuneração individual | `PESSOAL` — confirmado |

O ponto que a derivação não alcançava: **documento de escopo corporativo pode conter dado pessoal do representante**, não só do trabalhador. Um CPF de responsável pela transmissão é dado pessoal como qualquer outro.

**Consequência para LGPD-03 e a história F2-07:** o tarjamento antes do book do cliente precisa alcançar a DCTFWeb e os comprovantes de INSS/IRRF, que estavam fora do alvo por serem classificados como `INTERNO`.

Sem evidência (não analisados): `CER.SICAF`, `CER.CND_CIVEL_CRIMINAL`, `CER.CND_FALENCIA`, `INS.DARF`, `INS.PARCELAMENTO_*`, `FGT.EXTRATO`. Seguem derivados.

---

## 6. Nota sobre extração de CPF

O padrão `\d{3}\.\d{3}\.\d{3}-\d{2}` sozinho é insuficiente: números de código de barras e de autenticação produzem sequências com o mesmo formato. Todos os 162 casamentos desta massa tinham **DV válido**, mas isso é sorte da amostra, não garantia.

**Exigido na F1-03:** todo campo de CPF e CNPJ valida o dígito verificador antes de ser aceito. Um falso positivo de CPF num documento classificado como sem dado pessoal é uma falha de privacidade silenciosa.

---

## 7. A19 — a chave de junção partida entre duas linhas visuais

Na relação de benefícios da Flash (06/2026), cada beneficiário ocupa **seis linhas visuais**, e o CPF fica partido entre a segunda e a quinta, com o valor do benefício no meio:

```
y=617.4  x=457.1  Benefício
y=610.7  x=219.6  052.190.471-        Refeição e      de R$ 865,19)
y=604.7  x=47.7   ADRIANO LIN SOARES PERRUOLO         R$ 865,19
y=601.7  x=448.1  Custo de conta
y=598.7  x=242.7  40                  Alimentação
y=592.7  x=459.3  R$ 0,00
```

Não é o achado A13 (título que quebra linha) numa variação inofensiva: aqui quem quebra é **a chave de junção com a folha**. Lida linha a linha, a extração devolve zero CPFs — e a conciliação de cobertura responde "nenhum beneficiário", que é falso e parece verdadeiro.

Duas tentativas de reconstrução falharam, e o motivo de cada uma vale registrar:

| Tentativa | Por que falhou |
|---|---|
| Regex `(\d{3}\.\d{3}\.\d{3}-)\D*?(\d{2})` sobre o texto do bloco | O `\D*?` não atravessa o `865,19` que está entre os dois pedaços |
| Faixa de coluna fixa `x < 130` | Os dois fragmentos estão em **x diferentes** (219,6 e 242,7): a célula é centralizada, e o pedaço curto `40` fica mais à direita que o longo `052.190.471-` |

**O que funciona** é a combinação de três coisas já observáveis no documento, sem calibração por tentativa:

1. **Bloco por lacuna vertical** — 39 pontos entre beneficiários, 3 a 7 dentro do bloco (`agruparEmBlocos`);
2. **Colunas por projeção vertical** das linhas de dados, que acha as cinco faixas sem depender do cabeçalho — as fronteiras saem em x = 211,6 / 315,1 / 426,6 / 505,1, e a faixa do CPF acomoda os dois fragmentos;
3. **Leitura por bloco** (`lerBloco`), que junta por coluna o que está em linhas diferentes.

O mesmo mecanismo resolve o nome quebrado: `JAQUELINE DI CARLO ARAUJO` + `DUARTE` se reúnem porque ambos os pedaços estão na faixa do nome.

**Regra de junção:** os pedaços são unidos por **um espaço, nunca colados**. Colar produziria `ARAUJODUARTE`. Quem espera campo sem espaço interno — CPF, CNPJ, matrícula — retira o espaço no padrão do campo, onde a decisão é explícita.

---

## 8. Primeira conciliação real ponta a ponta — cadeia VA/VR de 06/2026

Documentos: `CONTRACHEQUE.pdf` (8 páginas), `RELACAO_VA_VR_062026.pdf`, `COMPROVANTE_PG_ALIMENTACAO.pdf`.

| Etapa | Resultado |
|---|---|
| **R07-a** — soma da relação × comprovante de pagamento | R$ 6.056,33 × R$ 6.056,33 → **CONFORME** |
| **R07-b** — cobertura: quem tem desconto na folha × quem consta na relação | 8 × 7 → **DIVERGENTE** |

A divergência: **`049.263.571-43` — ESTAFANY RIBEIRO AUGUSTO**, matrícula `000101683`, tem desconto `VALE ALIMENTACAO` de R$ 48,62 no contracheque de junho/2026 e **não aparece** na relação da Flash da mesma competência.

Isto **não é conclusão de erro** — é o que o sistema existe para produzir: uma pergunta com evidência anexada. As hipóteses a confirmar com a área demandante:

- crédito lançado em outra relação (outro lote, outro cartão, outra data de disponibilização);
- desconto indevido na folha;
- beneficiário omitido da remessa à Flash.

### O que esta conciliação corrigiu no desenho da regra R07

O desconto na folha (R$ 48,62 / 64,89 / 97,33) é a **coparticipação do empregado**; o crédito na relação é R$ 865,19 para todos. São grandezas diferentes e **não se comparam entre si**. A R07 concilia:

- **valor** — soma da relação contra o comprovante de pagamento (a única igualdade legítima);
- **cobertura** — o conjunto de CPFs de um lado contra o do outro.

Comparar desconto contra crédito acusaria divergência em **todos** os registros. Fica registrado como correção à matriz de regras: a R07 precisa declarar qual das duas comparações executa.

### Deduplicação obrigatória (achado A17, confirmado)

As 8 páginas do contracheque contêm **16 recibos** — duas vias por página, idênticas. A extração deduplica por matrícula. Sem isso, a folha reportaria 16 colaboradores e o líquido dobraria.

---

## 9. A05 destravado — a folha estruturada sai do contracheque

A pendência A05 registrava que a fase 1b estava parada por falta do **lado esperado** das conciliações: sem folha estruturada, R05, R07, R08, R09 e R10 não têm com o que comparar. A pendência dizia, corretamente, que "nenhuma linha de código a mais fecha isto".

Isso continua verdade sobre a **pergunta** — qual é o sistema de folha e se ele exporta o layout do cap. 14.3 segue sendo decisão de TI e da área demandante, e um export estruturado continua sendo melhor que reler PDF. O que mudou é o **bloqueio**: o contracheque já está no repositório, é fonte verificável, e o leitor de tabela já sabe lê-lo.

### Por que a leitura não é por coluna

O contracheque tem seis colunas — descrição, quantidade, valor, duas vezes — mas **a coluna de quantidade dos descontos fica vazia na maioria das folhas**. A projeção acha cinco separações numa competência e seis em outra, e uma lista fixa de nomes quebra na segunda.

A leitura é por **forma do conteúdo** dentro de cada metade: o último número com centavos é o valor, um número com centavos antes dele é a quantidade, e o que sobra é a descrição. É imune à contagem de colunas. A divisão entre as metades vem do cabeçalho — o segundo rótulo `Descrição` marca onde começam os descontos, e o rótulo é confiável *para isso*, ainda que não sirva para deduzir a fronteira das colunas de valor, que são alinhadas à direita.

### A conferência que o próprio documento carrega

Cada recibo imprime `TOTAL DE PROVENTOS`, `TOTAL DE DESCONTOS` e `LÍQUIDO A RECEBER`. Uma extração correta reproduz os três:

- Σ rubricas de provento = total de proventos impresso
- Σ rubricas de desconto = total de descontos impresso
- total de proventos − total de descontos = líquido impresso

**Nos 8 contracheques reais de 06/2026, os 8 fecham.** Um item que não fecha é acusado com os dois números, não somado.

### O que a folha entrega às regras de conciliação

| Regra | Insumo | Valor em 06/2026 |
|---|---|---|
| R05 | matrículas da competência | 8 |
| R08 | CPFs com desconto de `VALE ALIMENTACAO` | 8 |
| R09 | Σ base de cálculo do FGTS | R$ 49.693,18 |
| R10 | Σ salário de contribuição do INSS | R$ 49.693,18 |
| — | Σ líquidos | R$ 37.574,47 |

**R09 já é computável e o resultado é instrutivo:** 8% de R$ 49.693,18 = R$ 3.975,45; a soma do `FGTS Mês` impresso nos 8 recibos é R$ 3.975,44. **Delta de R$ 0,01**, produzido pelo arredondamento por colaborador. A tolerância de ±0,5% da R09 absorve; uma tolerância de R$ 0,01 sobre o total não absorveria. É evidência concreta de que a tolerância da R09 precisa ser **percentual, não absoluta** — e o cadastro atual (a decidir pela AP) deve refletir isso.

### Deduplicação obrigatória

Cada página traz o recibo **duas vezes**. Sem deduplicar por matrícula, a folha reportaria 16 colaboradores e o líquido dobraria para R$ 75.148,94.

---

## 10. F1-03 medida contra a massa real

O motor classifica por âncoras ponderadas, exatamente como o cap. 8.3 descreve. Três decisões que o texto do capítulo não fixa e que a massa exigiu:

| Decisão | Por quê |
|---|---|
| **O bônus nunca resgata** | Pasta e nome de arquivo somam, mas um documento cujo *conteúdo* não alcança o limiar de triagem é desconhecido mesmo com o nome perfeito. Sem isso, renomear um arquivo o classificaria — e o sistema existe para não depender do nome. O bônus é limitado a 0,20 no cadastro. |
| **Margem sobre o segundo colocado** | Score alto não basta se outro tipo marcou quase o mesmo. Dúvida vai para triagem, não para o book. Margem mínima 0,10. |
| **Âncora discriminante** | Quando existe, a sua ausência **elimina** o tipo em vez de descontar peso. Sem ela a certidão de falências e a de ações cíveis se classificam uma como a outra: compartilham o cabeçalho inteiro — mesmo tribunal, mesma fórmula, mesmas instâncias. |

### Resultado sobre 22 documentos reais de 06 e 07/2026

```
automáticas certas = 20    erradas = 0    triagem = 0    desconhecidos = 2
```

Os 2 desconhecidos são `COMPROVANTE_PG_FOLHA_6` e `_7` — os documentos vazios do achado A12. Não serem reconhecidos é o resultado certo: não há conteúdo para reconhecer. **Duas redes independentes pegam o mesmo problema** — o classificador porque nada casa, e V8 porque nenhum campo essencial aparece.

### A ressalva que este número exige

**As âncoras foram escritas lendo estes documentos.** Medir a precisão sobre eles mede o *ajuste*, não a *generalização*. O critério de aceite da F1-03 pede **três competências fechadas**; há uma, parcialmente duas. O número honesto a reportar é: *o motor e o cadastro estão prontos e a medição de aceite ainda não pode ser feita*.

Duas outras limitações, declaradas:

- **`INS.DARF` isolado não foi testado** — não há DARF avulso na massa.
- **Nenhum falso positivo foi medido**, porque não há na massa um documento que *não* seja de nenhum dos tipos. A margem e o limiar de triagem existem para isso, mas não foram exercitados contra ruído real.

### Duas correções que a massa impôs ao cap. 8.5

**1. A certidão da RFB é POSITIVA COM EFEITOS DE NEGATIVA.** Uma âncora que exigisse "negativa" no título recusaria a certidão válida que a empresa de fato tem.

**2. O comprovante bancário reproduz o DARF inteiro dentro dele.** O comprovante do Santander contém `composicao do documento de arrecadacao`, `documento de arrecadacao de receitas federais`, `periodo de apuracao` e `valor total do documento` — tudo o que a DCTFWeb contém. Com um discriminante tirado da composição, **os dois marcavam 1,00** e o comprovante ia para triagem como se fosse a declaração.

O que só existe na DCTFWeb é o **recibo de transmissão** — que é exatamente o que o cap. 8.5 já declarava como âncora do tipo. O capítulo estava certo; a primeira versão da regra é que tinha adivinhado.

### Comprovantes bancários: família, não tipo

O cap. 8.5 já registrava *"sem âncora fixa — identificado pelo pareamento"*. A massa confirma e agrava: **os comprovantes de INSS e de IRRF do Santander são textualmente indistinguíveis** — mesmo cabeçalho, mesma expressão, e o código de receita que os separaria não está impresso no comprovante.

Por isso as regras de comprovante identificam a **família** (`CMP.DARF`, `CMP.TRANSFERENCIA`, `CMP.BOLETO`, `CMP.LOTE_SALARIOS`), e ficam **fora do seed** de `regra_reconhecimento` de propósito. Quem decide qual obrigação o comprovante paga é o pareamento (R01/R02): valor, data e identificador contra a guia.

Uma regra que fingisse distinguir os dois classificaria metade deles errado **com score 1,00** — o pior resultado possível, porque não pediria triagem. **Falta construir o pareamento família → tipo**, que é trabalho de fase 1b.

---

## 11. A20 — campo rotulado em layout tabular não se extrai por regex de vizinhança

Ao cadastrar os campos de extração, metade dos padrões escritos como `rótulo[^0-9]{0,30}(valor)` não casou. O motivo é o mesmo dos achados A18 e A19, agora nos campos em vez das linhas:

```
gfd - guia do fgts digital
pagar este documento ate | cpf/cnpj do empregador | nome/razao social do empregador
20/07/2026               | 00.681.946             | engesoftware tecnologia s/a
```

No texto linear isso vira `... pagar este documento ate cpf/cnpj do empregador nome/razao social do empregador 20/07/2026 00.681.946 engesoftware ...`. **Os rótulos vêm todos primeiro e os valores todos depois.** Nenhuma janela de vizinhança alcança o valor sem atravessar os outros rótulos, e alargá-la faz o padrão capturar o campo errado.

Casam por vizinhança apenas os campos cujo rótulo é seguido imediatamente do valor **na mesma linha física** — `cnpj: 00.681.946/0001-60`, `validade: 25/11/2026`, `valor total do documento 2.505.979,03`.

**Consequência aplicada no cadastro (`db/seed/V103`):** só foram declarados como `campos` os padrões **verificados contra o documento real**, e só esses podem ser `campos_essenciais`. Declarar essencial um campo que a regra não consegue extrair reprovaria **todo** documento do tipo — o modo de falha contra o qual a própria migração V007 adverte, e que a restrição `regra_recon_essenciais_validos` impede no banco.

**Fica em aberto:** os campos de layout tabular (competência da DCTFWeb, vencimento e identificador da guia do FGTS, validade da CND estadual) precisam de extração por coordenada, com `LeitorDeTabela`, e não por regex. O mecanismo existe e está testado; falta ligá-lo ao cadastro, que hoje só sabe expressar padrão de texto.

---

# Parte II — a folha de um segundo contrato (DOCAS, 06/2026)

Cinco documentos de **outro contrato-serviço**: FOPAG, contracheques, comprovante de pagamento de salário, relação de VA/VR e relação de plano de saúde. É a primeira massa que o sistema não tinha visto ao ser construído — e por isso a primeira medição de **generalização**, e não de ajuste.

## 12. O que generalizou e o que não

Passando os cinco pelo que já estava pronto, sem tocar em nada:

| Documento | Resultado | Leitura |
|---|---|---|
| Contracheque | **AUTOMÁTICA**, tipo certo | As âncoras do contracheque atravessam contratos |
| Relação VA/VR (Pluxee) | NÃO RECONHECIDO | A regra tinha sido escrita sobre a relação da **Flash** |
| FOPAG | NÃO RECONHECIDO | Não havia regra — o tipo nunca tinha aparecido |
| Relação plano de saúde | NÃO RECONHECIDO | Idem |
| Comprovante de pagamento | NÃO RECONHECIDO | Regra escrita sobre o Sicoob; este é Itaú |

Três dos quatro não-reconhecidos eram **ausência de cadastro**, que é o comportamento correto. O quarto — a relação da Pluxee — expôs um limite do **modelo**, não do cadastro.

## 13. A23 — o mesmo tipo documental com emissores diferentes

`BEN.RELACAO_VA_VR` tem duas relações reais na massa, e elas **não têm uma palavra em comum** além do nome da empresa:

| | Flash | Pluxee |
|---|---|---|
| Título | `relatorio de transacao` | `relatorio de pedido - visao do colaborador` |
| Estrutura | bloco de 6 linhas por beneficiário | uma linha por beneficiário |
| Chave | CPF | matrícula |
| Total | `soma dos beneficios` | `subtotal` / `total dos produtos` |

Uma regra por tipo não alcança as duas: **ou fica genérica a ponto de casar com qualquer coisa, ou casa com uma e recusa a outra.** A saída é permitir **regras irmãs** — mais de uma regra vigente para o mesmo tipo, cada uma de um emissor (migração `V008`).

**A decisão continua sendo o tipo.** O emissor entra na evidência, para que a triagem saiba qual layout casou. E duas regras irmãs pontuando alto **não é empate**: sem essa redução, a margem sobre o segundo colocado mandaria para triagem justamente os documentos que o cadastro aprendeu a reconhecer melhor.

O mesmo vale para os comprovantes bancários: `CMP.TRANSFERENCIA` passou a ter regra do Sicoob e do Itaú.

**Depois do cadastro: 5 de 5 automáticas, com o tipo certo.** A massa antiga continua em 20 de 20.

## 14. A21 — o recibo que continua na página seguinte

O contracheque de quem teve férias **não cabe numa página**:

```
Fls 1/2 …  FERIAS 1 OCORRENCIA 7.036,18
           Continua...            Continua...
           LÍQUIDO A RECEBER      ← rótulo sem valor

Fls 2/2 …  MED 1/3 FERIAS 1 OC  878,44   (+ 5 rubricas)
           TOTAL DE PROVENTOS 31.440,69
           LÍQUIDO A RECEBER   7.320,49
```

O leitor deduplicava as duas vias da página por matrícula (achado A17) — e, com isso, **descartava a continuação como se fosse a via repetida**. Metade dos proventos se perdia.

A distinção entre as duas situações está impressa: `Fls 1/1` é recibo único, `1/2` e `2/2` são partes do mesmo. A chave de deduplicação passou a ser **matrícula + número da folha**: mesma folha é via repetida e se descarta, folha diferente é continuação e se une.

**Um bug adjacente que isso revelou:** a via era recortada a partir do cabeçalho `Matrícula Nome`, mas o `Fls` é impresso **acima** dele. O recorte precisava começar no título `Recibo de Pagamento` — a via é o recibo, não o miolo dele.

O resultado importa: 20.148,32 (folha 1/2) + 11.292,37 (folha 2/2) = **31.440,69**, o total impresso. Antes: 20.148,32 e "não fecha".

## 15. A22 — espaçamento entre letras quebra âncoras irrecuperavelmente

A FOPAG imprime o rótulo do centro de custo com *tracking*:

```
CENTRO D  E   C   U  S   T  O   :          104501 - DOCAS - OUTSOURCING
```

O número de espaços entre as letras é **variável** (2, 3, 3, 2, 3, 2). O limite entre `DE` e `CUSTO` **não é recuperável**: nenhuma contagem de espaços o distingue dos espaços internos.

**Regra adotada: não ancorar num título com tracking quando o documento oferece alternativa** — e a FOPAG oferece várias (`relacao da folha de pagamento`, `resumo geral`, `funcionario admissao situacao`). Para quando não oferecer, existe `Ancora.semEspacos`, que casa contra o texto sem espaço nenhum: a ambiguidade some porque a informação perdida deixa de ser necessária.

## 16. A24 — o identificador do pagamento tem código de empresa

O achado A14 registrou que o número do cliente é *matrícula + ddMMyyyy*. A massa do segundo contrato corrige:

```
001 | 000101963 | 03072026
 ^        ^          ^
 |        |          data do pagamento
 |        matrícula na folha (9 dígitos)
 código da empresa — o mesmo "001-Engesoftware Tecnologia S/A" que encabeça a FOPAG
```

A primeira implementação tirava a data e removia os zeros à esquerda do que sobrava. **Funcionou nos comprovantes do Santander por acaso**: ali o código da empresa é `000` e some junto com os zeros da matrícula. No comprovante do Itaú o código é `001`, e o resultado era `1000101963` — uma matrícula que não existe. A conciliação por matrícula acusaria ausência do colaborador **com os dois documentos corretos**.

## 17. A05 fechado de verdade — a FOPAG

A pendência A05 pedia a folha estruturada. O contracheque servia; a FOPAG é a fonte certa, por dois motivos:

**1. Código de rubrica.** A FOPAG imprime `08305 VALE ALIMENTACAO`; o contracheque imprime só a descrição. A descrição varia de grafia entre competências e entre sistemas; o código não.

**2. Coluna RESULTADOS.** Traz bases e totais já codificados, onde o contracheque tem um rodapé de rótulos posicionais:

| Código | Descrição | No contracheque |
|---|---|---|
| `10000` | TOTAL PROVENTOS | "TOTAL DE PROVENTOS" |
| `10200` | LIQUIDO A RECEBER | "LÍQUIDO A RECEBER" |
| `12200` | BASE INSS LIM TETO | "Sal. Contrib. INSS" |
| `13300` | BASE LIQ IRRF MES | "Base Cálc. IRRF" |
| `14000` | BASE FGTS MES | "Base Cálc. FGTS" |
| `17300` | CUST TT VL ALIME | *não existe* |
| `17305` | CUST EMP VL ALIME | *não existe* |

### A conferência mais forte disponível: o RESUMO GERAL

A FOPAG consolida cada rubrica sobre todos os colaboradores. A leitura colaborador a colaborador tem de reproduzir esse total. **Onze de onze códigos conferem exatamente**, incluindo `10000` (95.422,91), `14000` (84.456,43) e `08305` (24,49).

### Uma armadilha que só a FOPAG revela

O `Base Cálc. FGTS` do contracheque e o `14000 BASE FGTS MES` da FOPAG **não são a mesma grandeza**. Para o colaborador com férias:

```
contracheque (rodapé)  26.193,39
FOPAG (14000)          20.916,25
delta                   5.277,14  =  ADTO 13 SAL FERIAS
```

Somados sobre a folha: 89.733,57 pelo contracheque contra 84.456,43 pela FOPAG. **Usar um pelo outro na R09 produziria divergência falsa de R$ 5.277,14.** O código diz exatamente qual base é; o rótulo do contracheque, não. É o argumento decisivo para a FOPAG como fonte de registro.

### O que a FOPAG acrescenta à R08

`17300 CUST TT VL ALIME` = `17305 CUST EMP` + a rubrica de desconto `08305`. Confere em todos os colaboradores (604,80 = 598,75 + 6,05). A R08 deixa de comparar coparticipação com crédito — grandezas diferentes, como a Parte I já tinha registrado — e passa a comparar **custo total contra a relação do fornecedor**.

## 18. A cadeia por profissional, fechada ponta a ponta

O requisito original — *"ver o nome do profissional na folha, achar o comprovante de pagamento dele de salário, e validar os benefícios do contracheque no relatório de benefícios"* — executado sobre documentos reais:

```
comprovante de transferência (Itaú SISPAG)
   valor pago    = 13.225,67
   identificador = 00100010196303072026
                 → empresa 001, matrícula 101963, data 03/07/2026

folha (FOPAG)
   000101963 HERMES LIMA DE OLIVEIRA   líquido (10200) = 13.225,67

   [1] salário:        pago 13.225,67 × líquido 13.225,67   → CONFORME
   [2] VA:             folha (17300) 604,80 × relação 604,80 → CONFORME
   [3] coparticipação: 17300 = 17305 + 08305                → CONFORME
```

## 19. As conciliações do contrato, e a divergência que apareceu

| Conciliação | Resultado |
|---|---|
| FOPAG × contracheques (duas fontes independentes) | **5 de 5 conferem** em proventos, descontos e líquido |
| R08 valor: relação Pluxee × custo total na folha | **DIVERGENTE — R$ 144,00** |
| R07 cobertura: rateio do plano × rubrica na folha | **CONFORME** — 4 × 4 |
| Relação Pluxee consigo (subtotal) | CONFERE — R$ 2.592,00 |
| Rateio do plano consigo | CONFERE — R$ 990,37 |

**A divergência:** matrícula **100787 — SIDHARTHA BEZERRA DE SOUZA**. A relação da Pluxee credita R$ 604,80; a folha calcula R$ 460,80. Delta de **R$ 144,00 = 5 × R$ 28,80**, e R$ 28,80 é o `19630 VLR DIARIO VA` da própria folha — **cinco dias**.

O contexto que o sistema já tem: este colaborador teve `LICENCA PATERNIDADE` e 23 dias de salário no mês. O pedido à Pluxee foi feito em **22/05/2026**, antes da competência. A hipótese é que o pedido foi emitido pelo mês cheio e a folha aplicou a redução depois.

Isto **não é conclusão de erro** — é uma pergunta com evidência anexada, para a área demandante:

- o pedido cobre período diferente do da folha, e a diferença se acerta na competência seguinte?
- o crédito foi a maior e há valor a recuperar?
- a redução por licença paternidade não deveria ter sido aplicada ao VA?

### Uma correção de escopo que isto impõe

O `centro de custo 104501 - DOCAS - OUTSOURCING` aparece em todos os cinco documentos. A FOPAG é emitida **por empresa**, não por contrato — as páginas se agrupam por centro de custo. O recorte de um ciclo de faturamento é o **centro de custo**, e o leitor precisa filtrar por ele antes de somar, ou a conciliação de um contrato incluirá colaboradores de outro. Nesta massa há um só centro de custo, então a questão não apareceu; numa folha completa, apareceria em todo lugar. Fica registrado como trabalho pendente.

---

# Parte III — as pendências de engenharia fechadas

## 20. V3 fechada — titularidade por raiz e com máscara (achados A6, A7, A16)

Os achados marcavam A6 e A7 como *"o mais consequente em aberto"*. O CNPJ aparece em três formas que a comparação por igualdade recusa:

| Documento | Como aparece | |
|---|---|---|
| Guia do FGTS Digital | `00.681.946` | truncado na raiz (A6) |
| Comprovante SICOOB | `**.681.946/0001-**` | mascarado (A7) |
| Rodapé da Flash | `32.223.020. 0001-18` | um **terceiro ponto** onde deveria haver barra, e um espaço (A16) |

A comparação passou a ser **posição a posição, com coringa**, sobre dígitos sem separador. Nos nove documentos reais testados, **os nove aprovam** — inclusive os dois que a igualdade recusaria.

**Três decisões que valem registrar:**

1. **Um CNPJ de terceiro no documento não reprova.** A relação da Flash traz o CNPJ da Flash no rodapé; o comprovante do SICOOB traz o do destinatário. O que reprova é *nenhum* dos CNPJs do documento poder ser o da empresa — não a presença de outros.

2. **Evidência parcial fica no registro.** `**.681.946/0001-**` esconde dois dígitos da raiz: cem raízes casariam com ele. Aprovar é certo — recusar reprovaria um comprovante legítimo — mas o resultado carrega `evidencia_parcial` dizendo em que a aprovação se apoiou. O mesmo para a raiz sozinha, que prova a empresa e não o estabelecimento.

3. **Exigir CNPJ completo é decisão do cadastro, não da validação.** Se um tipo é exigido por estabelecimento, a raiz não basta — e quem diz isso é o cadastro.

## 21. A20 fechado — extração por coordenada ligada ao cadastro

O cadastro só sabia expressar padrão de texto. Agora `regra_reconhecimento.campos` aceita dois modos:

| Modo | Quando | Exemplo |
|---|---|---|
| `TEXTO` | rótulo seguido do valor na mesma linha física | `cnpj: 00.681.946/0001-60` |
| `ABAIXO_DO_ROTULO` | tabela — rótulos numa linha, valores na de baixo | tudo o que faltava |

**Os campos que estavam em aberto agora saem:**

```
GFD guia do FGTS   vencimento    '20/07/2026'
                   cnpj          '00.681.946'
                   identificador '0126071549847969-2'
                   competência   '06/2026'
                   valor         '119.301,51'
DCTFWeb            competência   'Junho/2026'
                   nº documento  '07.16.26196.1378103-1'
```

**Duas regras que a massa impôs ao mecanismo:**

**A linha de valores é ancorada na PRIMEIRA coluna.** "A primeira linha abaixo com conteúdo nesta faixa" não basta: na DCTFWeb, o rótulo solto `Pagar este documento até` fica *entre* o cabeçalho e a linha dos números, e caía na faixa da terceira coluna. O `Número do Documento` saía como `"Pagar este documento até"`.

**Coluna numérica é alinhada à DIREITA, e o cadastro declara.** Os valores crescem para a esquerda a partir da borda; a fronteira deduzida do início do rótulo os corta. Declarada à esquerda, a coluna `FGTS Total` devolvia `0,00` — o valor da coluna vizinha — em vez de `119.301,51`.

**Rótulo sozinho na linha tem a faixa da própria extensão.** Usar a linha inteira faria o cabeçalho do bloco seguinte, impresso entre o rótulo e o seu valor, ser tomado como valor.

## 22. A25 — data por extenso, e a armadilha do decreto

A certidão negativa do GDF **não imprime a validade em `dd/mm/aaaa`**:

```
Certidão expedida conforme Decreto Distrital nº 23.873 de 04/07/2003, gratuitamente.
Válida até 27 de agosto de 2026. *
```

Duas coisas ao mesmo tempo: a validade está **por extenso**, e o documento contém uma data numérica que é do **decreto**, de 2003.

Um extrator que pegasse "a primeira data numérica do documento" daria uma certidão **vencida há vinte anos** — e V5 reprovaria um documento perfeitamente válido, que é o falso positivo do risco P01.

**Regra adotada:** extração de data é sempre **ancorada num rótulo**, e a janela para no fim da frase. O formato por extenso entrou no catálogo do cadastro como `data_por_extenso`.

## 23. Recorte por centro de custo

A FOPAG é emitida **por empresa**, não por contrato: as páginas se agrupam por centro de custo. O leitor passou a carregar o centro de custo em cada item, e `FolhaDeCompetencia.doCentroDeCusto` recorta antes de somar.

Sem isso, a conciliação de um contrato incluiria colaboradores de outro — e a divergência apareceria em **todas** as regras de valor, com os dois lados corretos.

---

## 24. O motor de conciliação — fase 1b

Com a folha estruturada e os campos extraíveis, as regras do cap. 9 deixam de ser inertes. O motor separa o que é **código** do que é **cadastro**: a lógica de cada regra é código revisado; a **tolerância** e o **modo** são cadastro que a área demandante ajusta sem nova versão do sistema.

### O pareamento, que é o que faltava para os comprovantes

Os comprovantes bancários não se distinguem por conteúdo — os do INSS e do IRRF do Santander são textualmente idênticos. O cap. 8.5 já dizia o que fazer: *"identificado pelo pareamento: valor + data + identificador da obrigação"*.

Um comprovante pareia quando o valor bate dentro da tolerância **e** (o identificador coincide **ou** o pagamento ocorreu até o vencimento mais a carência). O `ou` não é frouxidão: **o comprovante real do FGTS é um PIX** e não carrega o identificador da guia. Exigi-lo reprovaria um pagamento legítimo.

Duas regras de ordem que a implementação exigiu:

- **Identificador primeiro, valor e data depois.** O pareamento por identificador é certo; o por valor e data é inferência. Na ordem inversa, a inferência consome o comprovante que casaria exatamente com outra obrigação.
- **Um comprovante pareia uma vez só.** Dois pagamentos do mesmo valor são dois pagamentos; reusar um esconderia uma obrigação não paga. É o achado A17 outra vez — contar duas vezes o mesmo documento produz um resultado bonito e falso.

**Um defeito que o teste pegou:** duas obrigações de mesmo valor e vencimento são `record`s **iguais**, e o pareamento por igualdade tratava as duas como uma — dando uma delas por paga sem comprovante nenhum. O pareamento passou a ser por posição.

### Resultado sobre os documentos reais

| Regra | Resultado |
|---|---|
| **R01** guia do FGTS × comprovante | **CONFORME** — R$ 119.301,51, pareado por valor e data |
| **R08** VA/VR × folha (contrato DOCAS) | **DIVERGENTE** — R$ 144,00 na matrícula 100787 |
| **R09** base FGTS × guia | **NÃO APLICÁVEL** — e este é o resultado importante |

### R09 e o falso positivo que o motor recusa produzir

A guia do FGTS é da **empresa inteira**: 157 trabalhadores, R$ 119.301,51. A folha do ciclo é o recorte de um centro de custo: 5 colaboradores, R$ 84.456,43 de base.

Rodadas uma contra a outra, dariam **divergência de R$ 115 mil com os dois documentos corretos** — comparando populações diferentes. O motor recusa a comparação e diz por quê, em vez de produzir o veredito. Recusar é o resultado certo; produzir seria o falso positivo do risco P01.

**Consequência para o cadastro:** cada regra precisa declarar de que escopo é a folha que ela consome. Regras corporativas (R09, R10) exigem a folha completa da competência; regras de contrato (R05, R07, R08) usam o recorte.

### O princípio 1 do cap. 1, executável

Uma regra cadastrada em modo `BLOQUEIO` **mas sem tolerância** opera em `ALERTA` e não trava faturamento. Está no código, no banco (restrição `regra_conc_sem_tolerancia_nao_bloqueia`) e agora num teste que verifica os dois caminhos.

### A tolerância da R09 tem de ser percentual

Registrado na Parte I e agora exercitado: 8% da soma das bases deu R$ 3.975,45 e a soma do FGTS impresso deu R$ 3.975,44 — **um centavo**, de arredondamento por colaborador. Com oito colaboradores o erro é um centavo; com oitocentos, não é. Uma tolerância absoluta calibrada nesta folha reprovaria a próxima.

---

# Parte IV — fechando a lógica da fase 1a

## 25. As oito validações unitárias (F1-04)

O cap. 8.4 define V1–V7; V8 foi acrescentada pelo achado A12. Todas as oito existem agora, e três carregam correções que a massa real impôs.

| # | O que confere | Falha gera |
|---|---|---|
| V1 | antivírus, MIME × extensão, tamanho | REJEITADO (segurança) |
| V2 | camada de texto, ou OCR confirmado | REJEITADO (ilegível) |
| V3 | titularidade por raiz e com máscara | REJEITADO (titularidade) |
| V4 | competência após a defasagem do cap. 7.4 | REJEITADO (competência) |
| V5 | vigência na data da NF e natureza aceita | REJEITADO (vigência) |
| V6 | formatos exigidos presentes e coerentes | exigência parcial |
| V7 | hash inédito para a exigência | ignorado com registro |
| V8 | campos essenciais presentes e bem formados | REJEITADO (incompleto) |

**V1 — antivírus indisponível não é antivírus limpo.** Quando o clamd não responde, o documento fica **sem veredito** (`NAO_APLICAVEL`), não aprovado. Tratar indisponibilidade como aprovação abriria a porta exatamente quando a porta não está sendo vigiada.

**V2 e V8 medem coisas diferentes, e as duas são necessárias.** O comprovante vazio do achado A12 **passa em V2** — ele tem texto, são 603 caracteres de rótulos. Quem o pega é V8. Uma validação que perguntasse "tem texto?" e outra que perguntasse "tem conteúdo?" parecem redundantes até se encontrar o documento que responde sim à primeira e não à segunda.

**V4 sem defasagem reprovaria toda guia de FGTS.** A guia de um ciclo de julho traz competência 06/2026, e está certa: a defasagem do tipo é M−1 (cap. 7.4). E a competência aparece como `06/2026` na guia e `Junho/2026` na DCTFWeb e na FOPAG — aceitar só a numérica deixaria metade dos documentos sem competência.

**V5 e o terceiro valor da natureza (achado A10).** O cap. 8.4 escreve V5 com duas categorias. A certidão cível e criminal real do TJDFT é `CERTIDÃO POSITIVA DE DISTRIBUIÇÃO`, com uma execução constando — e está sendo usada no faturamento. Reprová-la automaticamente travaria um contrato regular; aprová-la em silêncio esconderia o fato. O cadastro decide, por tipo, se `POSITIVA` é aceitável; quando é, o resultado sai aprovado **com ressalva registrada**. É o princípio 7 do cap. 1: julgar se uma execução impede faturar é juízo jurídico, não conferência documental.

**V5 avisa antes de vencer (achado A9).** O CRF do FGTS vale 30 dias. Uma certidão que vence dez dias depois da NF é aprovada — e o resultado carrega o aviso, porque a régua de cobrança precisa saber antes de o prazo estourar.

**V6 é a única cuja falha não reprova o documento.** O PDF entregue está certo; o que falta é a planilha. A exigência fica parcial. E a parte que pega o erro real é a coerência de competência: quando o PDF é de junho e a planilha de maio, **cada arquivo é válido sozinho e o conjunto não é**.

**V7 é a mesma armadilha do A17, num terceiro lugar.** A varredura é recursiva e o mesmo PDF aparece solto na raiz do mês e dentro da pasta do cliente, com nomes diferentes. Contá-lo duas vezes faria uma exigência de dois formatos parecer satisfeita por um arquivo só. Contar duas vezes o mesmo documento produz um resultado bonito e falso — na folha, no pareamento e aqui.

## 26. R02, R03 e R04 (F1-05)

**R02 encadeia duas conferências, e a mensagem as distingue.** Declarar R$ 1.000 e emitir R$ 900 em DARF é **erro de apuração**; emitir R$ 1.000 e pagar R$ 900 é **inadimplência**. Quem resolve cada uma é uma área diferente, e uma mensagem que misturasse as duas mandaria o problema para a pessoa errada.

**R03 é V5 elevada ao conjunto**, e o conjunto tem uma propriedade que nenhuma certidão sozinha tem: **a primeira a vencer manda**. Um book com seis certidões vigentes e uma vencendo em três dias não está confortável — está a três dias de não poder ser reemitido. R03 devolve qual vence primeiro.

**R04 é V6 elevada ao ciclo**, e a mudança de altitude muda para que a resposta serve. V6 diz que *esta* exigência está parcial; R04 diz que *o ciclo* não pode ser publicado e quantas exigências faltam — separando bloqueantes de não bloqueantes. Quem lê a primeira é quem entrega o documento; quem lê a segunda é quem decide fechar a competência.

---

## 27. Persistência — o que já funcionava parava de existir ao fim do processo

Até aqui o domínio lia documentos e produzia vereditos, e **nada gravava**. O esquema tinha 26 tabelas com restrições que funcionam, o domínio tinha 67 classes testadas, e não havia uma linha ligando os dois. Não era encanamento: `validacao_documento`, `conciliacao` e `log_auditoria` existem exatamente para tornar as decisões auditáveis (cap. 16).

### JDBC direto, e por quê (ADR-002)

O esquema **impõe regras de negócio**, não só forma: a `RULE` que torna a auditoria append-only, o `CHECK` que impede reprovação sem motivo, o que força modo ALERTA sem tolerância, o que impede aprovador igual a solicitante, uma função `IMMUTABLE` usada em `CHECK`, índices parciais e `UNIQUE NULLS NOT DISTINCT`.

Nada disso é expressável em anotações de mapeamento, e um ORM com geração de esquema desfaria parte na primeira execução, em silêncio. **O banco é a fonte da verdade estrutural; o código a consome** — a mesma razão pela qual as restrições foram escritas no banco em vez de só no serviço.

### O defeito que o teste pegou: transação que não aninha

`emTransacao` abria a transação, fazia o trabalho e comitava. Quando um repositório é chamado **de dentro** de outra unidade de trabalho, a transação interna comitava o trabalho da externa — e o rollback externo não tinha mais o que desfazer.

O teste era exatamente esse: gravar um documento dentro de um bloco que falha em seguida, e conferir que nada ficou. Falhou. `emTransacao` passou a **participar** da transação existente quando já está dentro de uma, em vez de abrir e comitar a própria.

É a diferença entre *"tudo ou nada"* e *"quase tudo"*, e a segunda não sustenta a auditoria: um campo extraído apontando para um documento que não existe quebra o rastro exatamente onde ele é mais necessário.

### O que os testes verificam, contra o PostgreSQL real

Não contra banco em memória: o que interessa testar são as restrições do esquema, e um H2 não as teria. Testar contra um banco sem as garantias seria testar outra coisa.

| Verificação | Por que importa |
|---|---|
| Documento é idempotente por hash | A varredura é recursiva e passa várias vezes por dia; duplicar faria V7 ver dois registros onde há um arquivo |
| Campos guardam a região de origem | Critério de aceite da F1-02 e base do cap. 16 |
| Reprocessar substitui o campo, não duplica | Regra nova dá valor novo, não dois valores |
| Reprovação sem motivo é recusada **pelo banco** | Rede de segurança para o caso de um caminho de código esquecer |
| Histórico não é substituído | Regra nova não apaga por que a regra antiga recusou |
| Conciliação **conforme** também é gravada | Cap. 9: um "está tudo certo" sem os números não permite conferir |
| Caractere de controle é escapado | Achado A15: PostgreSQL recusa NUL em `text` e em `jsonb` |
| Transação desfaz tudo ou nada | O defeito acima |

---

## 28. F1-08 — a publicação do book

Critério de aceite do cap. 18: *"objeto imutável (delete negado); hash confere; índice legível"*. Os três estão verificados, e o terceiro é verificado **lendo o PDF gerado com o extrator do próprio sistema** — gerar um PDF que abre não prova que ele tem o conteúdo certo.

### A recusa que a LGPD exige e que ninguém tinha construído

O cap. 10 manda que tipos com sigilo `PESSOAL` ou `PESSOAL_SENSIVEL` passem pelo redator antes da cópia para o book do cliente. **O redator é a história F2-07 e ainda não existe.**

Diante disso há três caminhos, e dois são inaceitáveis:

| Caminho | Consequência |
|---|---|
| Publicar o original no book do cliente | **Vaza dado pessoal.** A massa real tem um relatório do FGTS com 160 CPFs, nomes e remuneração individual |
| Omitir a peça em silêncio | Entrega um book incompleto que **parece completo** |
| **Recusar e dizer qual peça exige tarjamento** | O que o montador faz |

Executado sobre os documentos reais do bloco corporativo:

```
== book do cliente, sem tarjar
   RECUSOU: [FGT.RELATORIO_DIGITAL (PESSOAL), INS.DCTFWEB (PESSOAL)]

== book interno, original íntegro
   001 CER.CNDT               PUBLICO_CLIENTE   af5ed90fec72
   002 CER.CND_RFB            PUBLICO_CLIENTE   5d9809aafac8
   003 CER.CRF_FGTS           PUBLICO_CLIENTE   0a9e3d8ae00c
   004 INS.DCTFWEB            PESSOAL           930ae83c9e73
   005 FGT.GUIA               INTERNO           dfb7e89a433a
   006 FGT.RELATORIO_DIGITAL  PESSOAL           0894ebf8ed85
```

A `INS.DCTFWEB` só está entre as pessoais porque a **seção 5** deste documento corrigiu a classificação dela: o recibo de transmissão carrega o CPF do responsável. Sem aquela correção, ela iria para o book do cliente sem tarja.

Quando o redator existir, ele marcará `tarjado = true` e a recusa deixará de acontecer sozinha. Até lá, **ela é a diferença entre um sistema que respeita a LGPD e um que a menciona na documentação**.

### Quatro decisões de desenho, e por quê

**A interface do armazenamento não tem método de remover.** Uma interface com um método de apagar e um comentário dizendo "não use" é um convite; uma sem ele é uma garantia estrutural. Há um teste que verifica a ausência por reflexão — porque a garantia é estrutural e uma refatoração poderia desfazê-la sem que ninguém notasse.

**O hash do conjunto é o hash dos hashes, na ordem.** A definição está num lugar só porque o cliente precisa reproduzi-la com o book na mão, e *"de algum jeito somamos os hashes"* não é reproduzível. Depende da ordem de propósito: duas publicações com as mesmas peças em ordem diferente são books diferentes, porque a numeração é parte do que se entrega.

**Família desconhecida vai para o fim, não para o começo.** Uma família nova aparecendo antes das certidões mudaria a numeração de todo o book — e o índice de um book já entregue remete a números que deixariam de corresponder.

**O manifesto é gravado por último.** Ele declara o conjunto completo; gravá-lo antes das peças criaria, por um instante, um book que se declara completo e não está — e num bucket imutável esse instante não se corrige, só se versiona.

### Registro no banco e objeto no bucket são dois sistemas

Não há transação distribuída. A ordem escolhida é **publicar no bucket primeiro, registrar depois**: se a segunda falhar, existe um book no bucket sem linha no banco — visível e recuperável. A ordem inversa produziria uma linha afirmando um book que não existe, que é o erro que ninguém percebe.

---

## 29. F2-06 e F2-07 — o mascaramento e a tarja

### A falha clássica que o redator existe para não cometer

Desenhar um retângulo preto sobre o texto **não é tarjar**: o texto continua na camada de conteúdo e sai inteiro em qualquer copiar-e-colar. Documentos públicos já vazaram exatamente assim, e um sistema que faz isso entrega ao cliente um arquivo que *parece* tarjado.

Aqui a tarja **remove os glifos do fluxo de conteúdo**. O que some, some do arquivo. E o único teste que prova alguma coisa é: extrair o texto do PDF tarjado e verificar que o CPF não está mais lá.

### Sobre os documentos reais

| Documento | CPFs | Tarjados | Restantes |
|---|---|---|---|
| `RELATORIO_GUIA_DO_FGTS` | 160 | **160** | — |
| `CONTRACHEQUE` (8 páginas, 2 vias) | 16 | 16 | — |
| `C2_CONTRACHEQUE` | 12 | 12 | — |
| `RELACAO_VA_VR_062026` | 7 | 7 | — |
| `DCTFWEB_COMPLETA` | 2 reais | 2 | `13781031900` |
| **Book do cliente completo** | **180** | **178** | 2 (o mesmo fragmento) |

### A26 — o redator e o extrator discordam sobre o que é contíguo

O que resta na DCTFWeb **não é um CPF**: é um fragmento do número do documento `07.16.26196.1378103-1` que, colado pelo extrator, forma onze dígitos com DV válido por coincidência.

A causa é estrutural e vale registrar: **o redator trabalha sobre o fluxo de conteúdo e quem copia do PDF recebe a ordem de leitura**. O extrator insere espaços onde há lacuna entre glifos, e esses espaços criam fronteiras de onze dígitos que não existem no fluxo. Nesse caso a diferença protege — o redator não apaga dígitos de um número de documento. Mas o contrário também é possível.

Por isso **o redator confere o próprio trabalho pelo mesmo caminho que um vazamento tomaria**: extrai o texto do resultado e lista o que ainda sai com forma de CPF e DV válido. O que restar não é necessariamente falha — mas **fica na lista**, porque decidir que uma sequência com DV válido não é CPF é juízo, e juízo silencioso é o que faz um vazamento passar.

### Máscara parcial, e por que não esconder tudo

`***.190.471-**` — os seis do meio ficam. Quem opera precisa distinguir dois colaboradores numa lista de pendências; esconder tudo tornaria a tela inútil e **levaria alguém a consultar o CPF em claro em outro lugar**, que é como o controle vira teatro. É a mesma forma que os documentos bancários reais já usam (achado A7).

### Só mascara o que tem DV válido

Um número de protocolo com onze dígitos casa com o padrão de CPF. Mascará-lo esconderia informação que a mensagem precisa ter — o oposto do que se quer. É a exigência da seção 6, agora virada em código nos dois lados: na máscara de tela e no redator de PDF.

### O que isto destravou

A história F1-08 recusava publicar o book do cliente enquanto houvesse peça `PESSOAL` sem tarja. **Com o preparador, o book do cliente é montado e publicado** — sete peças, três tarjadas, com a pendência da DCTFWeb visível para quem publica.

---

## 30. F0-01 — a fronteira HTTP, e o recorte que o cliente desligava sozinho

A história F0-01 tem um critério de aceite curto: *"papel errado recebe 403 em
endpoint protegido"*. Implementá-lo expôs três coisas que os testes de
`Autorizador` não podiam pegar, porque não é o autorizador que erra nelas.

### 30.1 O alvo da decisão vinha de quem estava sendo julgado

O painel nasceu assim:

```java
public Painel painel(@PathVariable UUID cicloId,
                     @RequestParam(required = false) UUID contrato) {
    Ator ator = exigir(Permissao.VER_PAINEL, Autorizador.Alvo.doContrato(contrato));
```

O recorte por contrato do cap. 15.1 é real — `Autorizador` o aplica e há teste
para ele. Mas o `contrato` chegava **do próprio cliente**, e era opcional. Um
`PUBLICADOR_FIN` do contrato A pedindo o painel de um ciclo do contrato B só
precisava **omitir o parâmetro**: com `contrato == null`, a comparação de escopo
não tem contra o que comparar e a decisão passa.

Não é um defeito do autorizador. É a diferença entre autorizar sobre o alvo
declarado e autorizar sobre o alvo real. O contrato passou a vir do dado —
`ConsultaDoPainel.contratoDoCiclo(cicloId)`, a coluna `ciclo.contrato_servico_id`
— e o parâmetro deixou de existir. Ler essa coluna antes de decidir não revela
nada: o identificador do contrato vai para o `Autorizador` e nunca para a
resposta.

**Generalização, para o resto das telas:** *todo `Alvo` tem de ser resolvido a
partir do identificador do recurso, nunca recebido pronto.* Onde o alvo é
parâmetro de entrada, o recorte é uma sugestão.

### 30.2 Ciclo inexistente responde igual a ciclo alheio

Resolvido o contrato pelo ciclo, apareceu a pergunta seguinte: e se o ciclo não
existe? A resposta natural seria 404. Ela conta a quem não pode ver o ciclo que
**ele existe** — que é metade do que se está protegendo, e o suficiente para
enumerar ciclos de contratos alheios um UUID por vez. Ciclo inexistente e ciclo
alheio devolvem o mesmo 403, com motivos diferentes **no log**.

### 30.3 Negar depois de consultar já é ter lido

O terceiro é de ordem, e é o que motivou a forma dos testes. Um controlador que
consulta e só então decide devolve o 403 correto — e já leu o dado que ia negar,
deixando na trilha do cap. 16 uma leitura que não devia ter acontecido.

Um banco de mentira que devolvesse vazio passaria nos dois casos. Por isso
`ConexaoDeMentira` **explode** em qualquer consulta que o teste não registrou:

```
AssertionError: consulta ao banco que este teste não esperava —
a decisão de autorização deveria ter vindo antes: SELECT d.id, d.nome_arquivo, ...
```

Verificado por quebra deliberada: neutralizando o `throw new AcessoNegado` em
`PainelController.exigir`, **6 dos 14 testes falham** — e falham com essa
mensagem, não com "esperava 403". O teste está medindo a ordem, não só o
resultado.

### 30.4 O que ficou coberto

28 asserções em `TestesDeWeb`, sem subir o Spring: JWT de verdade traduzido em
`Ator`, o controlador de verdade, um banco que acusa se for tocado cedo demais.

| Verifica | Por quê |
|---|---|
| Grupo do diretório sem papel correspondente → 403 | O caso realista: alguém entra num grupo novo do AD e ninguém mapeou o papel. Omissão não vira leitura. |
| Requisição sem token → 403 | |
| `GESTOR_CONTRATO` em `/triagem` → 403; `PUBLICADOR_FIN` passa | O critério de aceite, literal |
| Ciclo de contrato alheio → 403 | § 30.1 |
| Ciclo inexistente → 403, não 404 | § 30.2 |
| Negar não consulta o banco | § 30.3 |
| Do ciclo negado lê-se só o contrato, nunca o conteúdo | § 30.3 |
| Barra segmentada e motivo de bloqueio (F1-07) | Exigência bloqueante **e** conciliação divergente — quem resolve cada uma é outra pessoa |
| `PUBLICADOR_FIN` vê o contracheque na fila, não o conteúdo | Cap. 15.1: ver que a exigência existe é uma coisa; abrir é outra |
| CPF no nome de arquivo e no caminho sai mascarado | SEC-02 — a massa real mostrou nome digitado por gente |
| Contrato mal formado no token é descartado e não abre o recorte | |
| `AcessoNegado` é 403 e **não** carrega `reason` | O motivo no corpo confirmaria a existência do contrato a quem não pode vê-lo |

### 30.5 Resíduo assumido — a fila de triagem não tem recorte por contrato

`documento` não tem coluna de contrato, e não pode ter: o arquivo é varrido
antes de ser classificado, e é a exigência que o liga a um contrato. Enquanto
está na fila de triagem ele não pertence a contrato nenhum, então não há por
onde recortar.

O que contém o risco hoje: o conteúdo é vedado por sigilo (§ 30.4) e nome e
caminho saem mascarados. O que fica exposto a quem tem `TRIAR` é **a existência
de um arquivo ainda não classificado**, de qualquer contrato. Aceitável na fase
1a; a recortar quando a triagem ganhar escrita (F1-06), porque aí a confirmação
já cria o vínculo. Registrado em `PENDENCIAS.md`.

---

## 31. F1-06 — a triagem que ensina, e o modelo que faltava

Critério de aceite: *"confirmar tira da fila, cria alias e o mesmo padrão não
retorna"*. São três promessas, e elas falham separadamente — por isso têm testes
separados.

### 31.1 O RA-01 não era um problema de recorte; era um modelo faltando

A fila era lida de `documento` sozinho (`status_triagem = 'PENDENTE'`), e
`documento` não tem contrato porque o arquivo é varrido antes de ser
classificado. A leitura natural era "falta uma coluna de contrato". Não falta: o
cap. 6.1 diz que **EM_TRIAGEM é estado da exigência**, não do documento —
*"EM_TRIAGEM → RECEBIDO · Confirmação humana (gera alias)"*. O que faltava era a
linha que diz **de que exigência** aquele documento é candidato.

Sem ela não havia como mover a exigência na confirmação, nem como saber de que
ciclo — logo, de que contrato — a fila é.

`vinculo_exigencia_documento` não servia: ele afirma que o documento **satisfaz**
a exigência, que é justamente o que ainda não se sabe. Candidatura e vínculo são
coisas diferentes, e confundi-las faria toda candidatura contar como entrega
enquanto ninguém a examinasse — a mesma família de erro de A17, V7 e do
pareamento: **contar duas vezes, ou cedo demais, produz um resultado bonito e
falso.**

A V009 criou `candidatura` e a view `fila_de_triagem`. O recorte passou a ser um
`IN (...)` sobre `ciclo.contrato_servico_id`, no SQL — não um filtro em memória
depois: filtrar depois significa que a consulta leu as linhas dos outros
contratos, e uma leitura que aconteceu não deixa de ter acontecido porque o
resultado foi descartado. **RA-01 fechado.**

### 31.2 "Cria alias" era verdade; "o mesmo padrão não retorna" era fé

Gravar o alias na confirmação não cumpre a terceira promessa sozinho. Sem alguém
que **leia** `tipo_alias` e transforme o alias em bônus, o mês seguinte
reclassifica o mesmo arquivo com a mesma pontuação e ele volta para a fila — a
confirmação teria virado um registro que ninguém consulta, e quem tria aprenderia
que triar não adianta.

Faltava `RepositorioDeAlias.doNome(...)`, que fecha o laço. Verificado por quebra
deliberada: forçando-o a devolver `Bonus.nenhum()`, cai exatamente uma asserção —
*"depois de confirmar, o arquivo do mês seguinte já chega com bônus"*.

O peso é 0,10, e a escolha é o ponto: fecha a faixa de triagem (0,70–0,95) pela
metade. Um documento que o conteúdo já pôs em 0,86 passa a decidir sozinho; um em
0,72 continua indo para a fila. **O alias corrobora; não elege.**

### 31.3 O padrão do nome, e o CPF que ele carregava

`1041601__CONTRACHEQUE.pdf` não pode ser guardado inteiro: o arquivo do mês
seguinte é `1041602__CONTRACHEQUE.pdf` e o padrão não voltaria a casar. O que
sobrevive é `contracheque`.

Ao escrever a regra de extração apareceu a razão mais forte para derrubar **todo**
dígito. A massa real trouxe `052.190.471-40 folha.pdf` — nome digitado por gente,
com CPF dentro. `tipo_alias` é tabela de configuração: consultada em toda
classificação, listada em toda tela de cadastro, e em inventário de dado pessoal
nenhum. Se o padrão preservasse dígitos, ela viraria um repositório silencioso de
CPF, fora do cap. 17. Dígitos dentro de palavras caem também, senão
`folha05219047140.pdf` passaria inteiro.

Padrão com menos de 5 caracteres significativos é recusado — e a recusa **é
informada a quem confirmou**. O cap. 12 manda que confirmar *"registra alias e
informa"*; "não registrei, porque" é a metade mais útil das duas.

### 31.4 A colisão de alias é aviso, não exceção

A unicidade de `tipo_alias` é global (achado E-02). Se o padrão aprendido já
aponta para outro tipo, o alias não é gravado — mas **a confirmação vale do mesmo
jeito**. Desfazer a decisão de quem triou por causa de um efeito colateral de
cadastro seria punir a pessoa por um problema que não é dela.

### 31.5 A transação engolia a recusa do domínio

Encontrado pelo teste "decidir duas vezes é recusado", que falhou com
`FalhaDePersistencia: transação desfeita` em vez de `CandidaturaJaDecidida`.

`Sgdf.emTransacao` desfazia e **reembrulhava** toda `RuntimeException`. Correto
para falha de banco; errado para recusa de domínio: transformava *"você não pode
fazer isso"* em *"o banco falhou"*. A camada web devolveria 500 onde o certo é 409
(alguém decidiu antes) ou 422 (o pedido não faz sentido) — e o `TratamentoDeErro`
nunca seria acionado. Agora desfaz e repassa com o tipo original; só `SQLException`
é embrulhada.

### 31.6 As três decisões, e por que não são variações da mesma

| Decisão | Vínculo | Exigência | Aprende | Motivo |
|---|---|---|---|---|
| **Confirmar** | cria | EM_TRIAGEM → **RECEBIDO** | sim | não exige — concordar não é corrigir |
| **Reclassificar** | **não cria** | EM_TRIAGEM → **PENDENTE** | sim, para o tipo *escolhido* | obrigatório |
| **Ilegível** | não cria | EM_TRIAGEM → **PENDENTE** | **não** | obrigatório |

Reclassificar diz o que o documento **é**, não que esta exigência foi satisfeita:
ela continua sem o documento que esperava. E reclassificar para o tipo que o motor
já propôs é recusado — a diferença entre concordar e corrigir é o que mede o
acerto do motor (risco P01, modo sombra).

Ilegível não aprende nada: gravar um alias a partir de um arquivo que ninguém
conseguiu ler é transformar palpite em conhecimento permanente.

### 31.7 A primeira escrita humana do sistema

Até aqui tudo era reproduzível a partir do documento e da versão da regra. A
triagem é a primeira decisão que uma pessoa toma e que muda estado — e por isso
`TrilhaDeAuditoria` nasce agora, gravando **na mesma transação do efeito**.
Registrar fora dela produziria as duas metades erradas: trilha de uma decisão
desfeita, ou decisão gravada sem trilha.

### 31.8 Cobertura

52 asserções em `TestesDeTriagem` (21 sem banco, 31 contra o PostgreSQL real),
12 em `T005`, 5 novas na fronteira HTTP — a escrita é autorizada pelo contrato **da
candidatura**, porque ler a fila recortada não protege a escrita: quem soubesse o
UUID confirmaria pela URL.

---

## 32. F1-09 — a restrição que declarava uma garantia que não dava

Critério de aceite: *"régua registra o que enviaria, nada é enviado"*.

### 32.1 A unicidade diária permitia três e-mails

A V001 criou, com o comentário *"cap. 11.2: máximo 1 e-mail por área por ciclo
por dia. A consolidação é forçada pelo banco, não confiada ao agendador"*:

```sql
UNIQUE (ciclo_id, tipo, destinatario, data_referencia)
```

Com `tipo` na chave, a mesma pessoa podia receber no mesmo dia e no mesmo ciclo
uma PREVENTIVA (algo vence em 48 h), uma COBRANCA (algo venceu hoje) e um
ESCALONAMENTO (algo venceu há dois dias) — **três e-mails**, e o banco aceitaria
os três. A regra do capítulo é por área e por dia, sem qualificar por tipo.

O comentário afirmava uma garantia que a restrição não dava, que é a forma mais
cara de erro deste tipo: quem lê o esquema para conferir a regra encontra a
afirmação e **para de procurar**.

A V010 corrige para `(ciclo_id, destinatario, data_referencia)`. Verificado por
quebra deliberada: repondo a restrição da V001, `T006` falha com
*"a mesma pessoa recebeu DOIS e-mails no mesmo dia"*.

**Consequência de projeto, e é a parte que importa:** a consolidação não é por
momento da régua, é por **pessoa**. Um aviso diário por destinatário, com o que
vence, o que venceu e o que escalou, tudo junto. O `tipo` da linha passou a ser o
momento mais grave presente — informação, não chave.

### 32.2 "Nada é enviado" não pode ser um booleano

O modo sombra do cap. 11.2 é descrito como estado da PRD, e existe a coluna
`notificacao.modo_sombra`. A leitura fácil seria: um `if` antes do envio.

Um `if` é exatamente o que não sustenta a promessa. Alguém o inverte — numa
configuração, num merge, num deploy — e o sistema começa a cobrar as áreas sem
que ninguém tenha decidido isso. Então, como no `ArmazenamentoImutavel` da F1-08:
**não existe transporte no código.** Nenhuma classe do pacote `notificacao` nem o
`RepositorioDeNotificacao` importa rede, correio ou HTTP, e nenhum expõe método de
envio. O teste lê os fontes para provar que continua assim, porque um teste de
comportamento só mostraria que naquele caminho nada saiu — o que se quer garantir
é que não existe caminho.

Daí decorre a decisão que fecha o laço: **se alguém desligar
`notificacao.modo_sombra` antes de existir transporte, a execução falha.** Gravar
as linhas como enviadas seria registrar um envio que não aconteceu — a área não
recebe, o sistema jura que mandou, e a discussão seguinte não tem como ser
resolvida.

O parâmetro nasce **ligado**, e ausente também conta como ligado: um sistema
recém-instalado, sem ninguém ter decidido nada, não pode começar cobrando.

### 32.3 O marco é exato, e o silêncio depois do D+5 é declarado

`D-2`, `D+0`, `D+2`, `D+5`. Exatos, não "a partir de": com "a partir de", uma
pendência vencida há dez dias produziria dez cobranças acumuladas por dia, e a
consolidação do cap. 11.2 existiria para conter um problema que a própria régua
criou.

O custo disso é real e está registrado: **depois do D+5 a régua fica em
silêncio.** O capítulo não define marco seguinte. A pendência continua visível no
painel, mas ninguém é lembrado. Inventar aqui um "repete a cada N dias" seria
criar régua que ninguém aprovou — está em `PENDENCIAS.md` como decisão da área
demandante.

### 32.4 Falta de cadastro não é silêncio

Uma pendência cujo titular não está cadastrado simplesmente não seria cobrada. O
painel a mostraria pendente e a área juraria não ter sido avisada — **as duas
versões certas**, e ninguém saberia por quê.

Por isso a régua devolve duas listas: os avisos e as pendências que ficaram sem
dono. Uma execução "sem erro" que cobrou 12 pessoas e deixou 4 pendências órfãs
parece bem-sucedida e não é.

### 32.5 Duas pessoas no mesmo papel é uma pergunta sem resposta

A régua pergunta *"quem é o titular da família X do contrato Y no dia D"*. Com
vigências sobrepostas há duas respostas, e a consulta escolheria uma pela ordem
física das linhas — cobrando silenciosamente a pessoa errada, e escalando para o
gestor de quem nunca foi avisado. `destinatario` usa uma restrição de exclusão
(`daterange` com `&&`) que recusa a sobreposição no cadastro, que é onde o erro é
barato de corrigir.

### 32.6 O que o aviso não tem onde carregar

Exigência de escopo PROFISSIONAL é uma por trabalhador. Um aviso que as listasse
nominalmente mandaria nome de gente para a caixa de uma área — e a caixa de uma
área não é destinatário de dado pessoal individual.

`Aviso.Item` carrega **quantidade**, e `PendenciaAberta` não tem campo de
profissional. Não há o que vazar. O teste fixa a lista exata de campos do
registro: acrescentar um quebra a compilação do teste e força a decisão a ser
tomada de novo, em vez de passar numa revisão distraída.

Duas restrições no cadastro de templates seguem a mesma linha: um corpo que fale
em anexo é recusado (cap. 11.2 manda link autenticado), e o template de
FECHAMENTO sem o rodapé de não-substituição do ateste também — sem ele o e-mail
sugere que o sistema atesta a medição.

### 32.7 Cobertura

42 asserções em `TestesDeNotificacao` (30 sem banco, 12 contra o PostgreSQL
real) e 14 em `T006`.

---

## 33. F0-04, F0-06 e F0-07 — o motor que faltava embaixo de tudo

Eu tinha recomendado os CRUDs de cadastro como próximo passo. Ao abrir o código
para começar, o gap real era outro e maior: **a materialização de exigências não
existia em Java.** O `app/` tinha coleta, extração, classificação, validação,
conciliação, book, triagem e notificação — tudo operando *sobre* exigências que
nenhum código do sistema jamais criou. Nos testes, elas eram inseridas à mão.

A regra existia como implementação de referência em Python
(`especificacao/materializacao/referencia.py`) com 18 casos normativos, e o prazo
idem com 32. Ambas sem contraparte de produção.

### 33.1 Os testes não inventam casos — eles leem as suítes

`TestesDeMatriz` abre `especificacao/prazo/casos.json` e
`especificacao/materializacao/casos.json` e roda os 50 casos contra o código de
produção. O cabeçalho das suítes diz por que isso importa: *"divergência é defeito
da implementação, não da suíte — corrigir a suíte exige decisão da área
demandante"*.

Escrever casos próprios seria o contrário: eu escolheria o que testar a partir do
que implementei, e as duas coisas concordariam por construção.

### 33.2 O caso ERRO-07, e onde ele realmente mora

A suíte tem um caso cuja descrição é *"booleano não é inteiro. Em linguagens onde
`true == 1`, aceitar isto produziria um prazo silenciosamente errado"*. Java não
é uma dessas linguagens — `Boolean` não é `Integer`, e a assinatura tipada já
protege quem chama com valores tipados. Minha primeira versão do teste falhou
mesmo assim, e por um motivo mais interessante que o caso: **eu tinha escrito
`no.get("offset").asInt()`**, e Jackson converte `true` em 1.

O perigo não está no cálculo; está na **fronteira**. `regra_exigibilidade.prazo` é
`jsonb`, e qualquer leitor distraído que chame `asInt()` reproduz exatamente o
defeito. A correção foi criar um único lugar estrito — `Prazo.Cadastrado.deValores`
— que recusa `Boolean`, `Double` e `String`, e usá-lo **nos dois** leitores: o do
teste e o do `RepositorioDaMatriz` que lê o jsonb. `5.0` também é recusado, não
convertido: converter esconderia que alguém digitou um número onde a coluna espera
outro.

### 33.3 A garantia da F0-05 mora numa linha, e o primeiro teste dela era fraco

*"Alterar regra não afeta ciclo aberto"* é sustentado por
`WHERE r.versao_matriz_id = <a versão que o ciclo congelou>` — nunca "a versão
vigente". Publicar a 2.0 enquanto abril está aberto não muda uma exigência de
abril, porque abril continua perguntando pela 1.0.

Escrevi o teste como "publica a 2.0, rematerializa, verifica que o prazo não
mudou". Ele passou. Depois **quebrei o repositório de propósito**, trocando a
leitura pela versão corrente — e o teste continuou passando.

O motivo: `ORDER BY publicada_em DESC LIMIT 1` devolve a versão mais recente de
*todo* o banco, que nos testes é a de outro fixture, cujas regras não alcançam
este contrato. Zero regras aplicáveis → nada criado → "o prazo não mudou" →
verde. **"Nada mudou" também acontece quando nada foi encontrado.**

Reescrito: as exigências são **apagadas** depois de publicada a 2.0, e a
rematerialização precisa **recriá-las** com o prazo da 1.0. A afirmação passou a
ser sobre o que foi produzido, não sobre o que deixou de ser. Com a quebra
reposta, agora falha — e falha na asserção certa.

### 33.4 `ON CONFLICT` sobre índice parcial precisa repetir o `WHERE`

A V004 substituiu a restrição `exigencia_unica` por dois índices únicos
**parciais**: `ux_exigencia_de_ciclo` (onde `ciclo_id IS NOT NULL`) e
`ux_exigencia_corporativa` (onde `empresa_id IS NOT NULL`). Um `ON CONFLICT` que
não repete o `WHERE` do índice não o infere, e o PostgreSQL recusa.

O detalhe que torna isso perigoso: a recusa só aparece **quando há conflito** —
ou seja, na segunda execução. A abertura de ciclo do cap. 7.6 roda por agendador,
e agendador repete; o erro apareceria em produção no segundo disparo, não no
primeiro.

### 33.5 A exigência corporativa, e o `xmax`

Cap. 7.1: a corporativa é *"1 por CNPJ por competência, compartilhada entre
ciclos, satisfeita uma única vez"*. Com 15 contratos, duplicá-la faria a mesma CND
ser cobrada 15 vezes.

O `INSERT ... ON CONFLICT DO UPDATE` devolve a linha nos dois casos, e contar toda
linha devolvida como "criada" faria o painel dizer que há 15 CNDs onde há uma. O
`RETURNING id, (xmax = 0) AS inserida` distingue criação de reaproveitamento.

O prazo, no conflito, fica com o **menor** (`LEAST`): se um cliente quer a CND no
5º dia útil e outro no 10º, a certidão compartilhada precisa estar lá no 5º —
ERRATA E-10.

### 33.6 Uma duplicata removida

`especificacao/prazo/java/Prazo.java` era um porte avulso, escrito como prova da
ADR-001 e marcado "não é código de produção". Com o porte de verdade em `app/`,
ele virou uma segunda implementação da mesma regra — e a única que **não rodava
em suíte automatizada nenhuma**, só sob `verificar.py --comando` executado à mão.
Removido; a ADR-001 ganhou uma nota posterior em vez de ter o texto reescrito,
porque o registro de uma decisão é o que ela foi na época.

### 33.7 Cobertura

82 asserções em `TestesDeMatriz`: 32 casos da suíte de prazo, 18 da suíte de
materialização, 12 sobre o que as suítes não cobrem (prazo corporativo, vigência
contra o mês, alocação de um dia) e 20 contra o PostgreSQL real (gravação,
idempotência, F0-05, compartilhamento corporativo, trilha).

As duas suítes em Python continuam passando 32/32 e 18/18 contra a referência.

---

## 34. F0-05, F0-02 e F0-03 — o rascunho, e a mudança que não é mudança

### 34.1 O rascunho não pode ser um estado da versão

A saída natural para "rascunho → publicação" seria acrescentar
`situacao IN ('RASCUNHO','PUBLICADA')` a `versao_matriz` e virar o valor no
clique. Não dá — e a impossibilidade é informativa.

`versao_matriz` é append-only (a V002 revoga UPDATE e DELETE) exatamente porque
`ciclo.versao_matriz_id` congela a versão da época e `regra_exigibilidade` aponta
para ela. **Uma linha que muda de estado é uma linha que muda**, e a garantia da
F0-05 depende de que não mude.

Então o rascunho é uma área de trabalho separada e mutável. Publicar não promove o
rascunho: publicar **cria** uma versão nova, copiando da base tudo o que o
rascunho não toca e aplicando os itens. A base fica intacta — que é a mesma
garantia provada do outro lado, em `TestesDeMatriz`, por quem *lê* a matriz.

A versão nova é **completa**, não um delta: `regra_exigibilidade` é lida por
`versao_matriz_id = ?`, e uma versão que só contivesse as mudanças faria o ciclo
aberto nela enxergar três regras onde a matriz tem 176.

### 34.2 O gate do DAF, e a leitura conservadora declarada

Cap. 12: *"bloqueante pede aprovação DAF"*. O capítulo não diz em que sentido.
Adotado o conservador, e está declarado no código: **qualquer item que toque uma
criticidade BLOQUEANTE exige DAF** — criar, tornar-se, deixar de ser, e
**remover**.

Remover conta porque acrescentar exigência bloqueante *aperta* o portão e remover
uma *afrouxa*. Das duas, a que deixa o faturamento passar sem documento é a
segunda — seria estranho que a mudança perigosa fosse a que dispensa aprovação.

### 34.3 Escrever o que já valia não é mudança — e o teste é que estava errado

O primeiro `bloqueanteExigeDaf` alterava uma regra cujo **tipo** já é
`BLOQUEANTE`, escrevendo `BLOQUEANTE` explicitamente na regra, e esperava que o
gate pedisse DAF. O gate não pediu, e estava certo.

`regra_exigibilidade.criticidade` nula significa "herda a do tipo" (V001). A
criticidade **efetiva** era bloqueante antes e continuava depois: tornar
explícito o valor herdado não muda nada. Se o gate contasse isso como mudança,
toda edição de rotina passaria a exigir o APROVADOR_DAF, e a aprovação viraria
carimbo — que é o modo como um controle de segurança morre sem ninguém notar.

A comparação, por isso, é sempre `coalesce(regra.criticidade, tipo.criticidade)`
dos dois lados. Comparar as nulas diretamente diria "nada mudou" quando o tipo por
trás é bloqueante. O caso virou teste próprio.

Verificado por quebra deliberada: forçando o gate a devolver lista vazia,
**9 asserções caem**.

### 34.4 O aviso do cap. 12 deixou de ser uma frase

O capítulo pede um "aviso fixo: alterações não travam o faturamento". Como o
ciclo congela a versão, isso é **demonstrável** — então a análise do rascunho e a
resposta da publicação devolvem a lista **nomeada** dos ciclos abertos que
continuam na versão antiga, com contrato e competência. Aviso fixo é uma frase
que ninguém lê; a lista é evidência.

### 34.5 F0-02 — por que a chave tem três colunas

Critério de aceite: *"CAIXA cadastrada como 3 contratos-serviço distintos;
unicidade (cliente, número, serviço)"*. O mesmo número de contrato, do mesmo
cliente, existe três vezes — um por **serviço**. Um contrato guarda-chuva com três
serviços tem três ciclos por competência, três pastas de origem e três medições, e
colapsá-los perderia duas delas.

### 34.6 F0-03 — o alias do cadastro usa a normalização da triagem

`RepositorioDeCadastro.cadastrarAlias` chama o mesmo `PadraoDeNome` que a
confirmação em triagem usa. Se cada um normalizasse do seu jeito, o alias digitado
à mão e o aprendido seriam textos diferentes para o mesmo padrão — a unicidade
global do achado E-02 deixaria de valer e o bônus de nome seria concedido duas
vezes ao mesmo documento.

Tipo se **desativa**, nunca se apaga: `tipo_alias`, `regra_exigibilidade` e as
exigências já materializadas o referenciam, e apagar quebraria a leitura de
ciclos antigos.

### 34.7 O cadastro sai do `psql`

Até aqui, destinatários, tolerâncias, tipos e regras entravam por SQL direto — e
um ajuste feito assim não deixa quem, quando nem por quê. Todo método de
`RepositorioDeCadastro` e de `RepositorioDeRascunho` grava na trilha **na mesma
transação do efeito**, inclusive a tentativa **negada** de publicar sem DAF, que é
o registro que mostra alguém insistindo.

### 34.8 Cobertura

31 asserções em `TestesDeRascunho` (7 sem banco, 24 contra o PostgreSQL),
15 em `TestesDeCadastro` e 12 em `T007`.

---

## 35. F0-09 — a negativa que sumia, e as três camadas medidas

Critério de aceite: *"solicitante não consegue aprovar a própria exceção;
aprovação exige APROVADOR_DAF"*.

### 35.1 A tabela não tinha onde registrar um "não"

`excecao` nasceu na V001 com `aprovador`, `aprovado_em` e a restrição de
segregação de funções. Faltava o estado NEGADA: nulo em `aprovador` significa
"ainda não decidida", e depois de o DAF recusar a linha continuava parecendo
pendente.

As consequências não são estéticas. A exigência fica esperando por uma decisão já
tomada; quem solicitou pede de novo sem saber que foi negado, e sem saber por quê
— que é a informação que faria a próxima solicitação ser melhor; e a trilha do
cap. 16 guarda as aprovações e perde as recusas, que são justamente as decisões
que alguém questiona depois.

**A migração expôs um defeito da própria migração.** O `DEFAULT 'SOLICITADA'`
punha esse valor em toda linha existente, **inclusive nas que já tinham
aprovador** — e essas passariam a dizer "ainda não decidida" sobre uma decisão
tomada. A V012 teria criado a ambiguidade que veio remover. Descoberto porque a
nova restrição recusou as linhas do `T001`; corrigido com um `UPDATE` de backfill
antes da restrição.

### 35.2 A negativa era gravada dentro da transação que a negativa aborta

O bug do dia, e o teste que o encontrou afirmava sobre a trilha.

`decidir` registrava a recusa por SoD e em seguida levantava a exceção — as duas
coisas dentro do mesmo `emTransacao`. O rollback levava junto o registro de que
alguém tentou. **O fato mais auditável do fluxo somia exatamente por ser
negativo.**

A correção move as checagens de SoD para fora do bloco transacional, com o
registro da recusa em transação própria. Sem a asserção sobre a trilha, o caminho
pareceria correto para sempre: a exceção era levantada, a mensagem estava certa, e
nada indicava que o log não existia.

### 35.3 A entrega que chega enquanto a exceção espera

Uma exceção espera decisão humana, e nesse meio-tempo o documento pode chegar.
Aprovar sobre um estado que já não é o atual empurraria a exigência de RECEBIDO
para DISPENSADO — **apagando do book um documento que existe**.

A aprovação relê a exigência e recusa quando ela saiu de
`PENDENTE|DIVERGENTE|REJEITADO`, dizendo que a entrega chegou. A decisão foi
tomada sobre um estado que mudou, e a resposta certa é dizer isso, não aplicá-la.

### 35.4 As três camadas de SoD, medidas em vez de afirmadas

O comentário do repositório diz que a segregação é verificada em três lugares e
que cada um cobre uma classe diferente de erro. Isso é fácil de escrever e fácil
de estar errado, então foi medido, derrubando uma camada por vez:

| Cenário | Resultado |
|---|---|
| Java **e** banco | 25/25 |
| Só Java (restrição `excecao_sod` removida) | **25/25** — o serviço segura sozinho |
| Só banco (checagem em Java neutralizada) | **24/25** — o banco recusa; cai só a asserção da trilha |
| Nenhum dos dois | **o critério de aceite quebra**: o solicitante aprova a própria exceção |

A leitura é precisa: a contribuição da camada Java **não é a proteção** — o banco
já a dá. É a **mensagem** e o **registro da tentativa**. Vale saber qual é qual
antes de alguém "simplificar" a duplicação.

### 35.5 O que a SoD não cobre

A comparação é entre strings de identidade. Se a mesma pessoa autenticar com dois
identificadores — o `sub` do OIDC hoje, um e-mail amanhã — as três camadas
concordam que são duas pessoas. A defesa está no provedor de identidade, não aqui.
Registrado como **RA-08**.

### 35.6 Cobertura

25 asserções em `TestesDeExcecao` e 8 em `T008`. Com isso a **fase 0 fecha**:
F0-01 a F0-09, todas com critério de aceite exercitado.

---

## 36. F1-10 — o nome não bastava, e o cliente WebDAV não baixava o que tem espaço

Critério de aceite: *"cópia em conflito aparece sinalizada e nunca vinculada"*.

### 36.1 Metade do critério do cap. 8.1 não era verificada

O capítulo define a cópia de conflito por **"padrão de nome + hash duplicado"**.
A F1-01 implementou só o nome: `PoliticaDeArquivos` reconhecia
`(conflicted copy …)` e o arquivo era descartado antes de qualquer download.

Só o nome erra na direção perigosa. `Relatório (conflicted copy 2026-06-30).pdf`
**pode ser a única versão que sobrou** — se a sincronização substituiu o original
por uma cópia vazia ou antiga, a "cópia" é o documento. Descartá-la pelo nome o
perde em silêncio.

Então a cópia de conflito passou a ser a **única exceção** à regra "filtrar antes
de baixar": ela é baixada, passa pelo antivírus como qualquer outra, é hasheada —
e mesmo assim nunca vira documento. Para todo o resto (temporário, oculto,
extensão errada, tamanho) o nome continua bastando, e baixar seria desperdício.

Com o hash, o alerta fica acionável:

| Nome de conflito | Hash | Severidade | O que o painel diz |
|---|---|---|---|
| sim | igual a um documento conhecido | INFORMATIVO | "cópia redundante: o mesmo conteúdo já está no sistema" |
| sim | inédito | **ATENÇÃO** | "pode ser a única versão que sobrou — conferir antes de apagar" |

### 36.2 "Nunca vinculada" virou propriedade estrutural

Marcar `documento.status_triagem = 'CONFLITO'` seria mais barato — e seria a
maneira de, um dia, uma cópia acabar vinculada: bastaria uma consulta esquecer o
filtro.

O achado vive em `achado_de_organizacao`, que **não é** `documento`. Como
`vinculo_exigencia_documento` referencia `documento`, não existe consulta capaz de
vincular um achado. **Não há filtro para alguém esquecer.**

O `documento_original_id` aponta para o documento cujo hash coincide — o
*original*, nunca a cópia — e o banco recusa afirmar duplicata sem hash: sem ter
comparado conteúdo, a única coisa que se sabe é que o nome parecia de conflito.

### 36.3 O defeito que isto desenterrou: o WebDAV não baixava nome com espaço

Ao rodar o primeiro teste de ponta a ponta com uma cópia de conflito:

```
IllegalArgumentException: Illegal character in path at index 17:
/BNB/2026/04/guia (conflicted copy 2026-04-01).pdf
```

`CaminhoRemoto.canonicalizar` **decodifica** o href do PROPFIND — precisa, para
validar `..` e caracteres de controle. `ClienteWebDav` então entregava esse
caminho decodificado a `URI.resolve`, que rejeita espaço, parêntese e acento.

**Não é um problema de cópias de conflito.** É um defeito da F1-01 que atingia
qualquer arquivo legítimo com espaço no nome — e a massa real tem vários;
`052.190.471-40 folha.pdf` é um nome de verdade. Passou despercebido porque todos
os testes de varredura usavam nomes sem espaço. A cópia de conflito só o
encontrou porque **tem espaço e parêntese por construção**.

Corrigido com o construtor de sete argumentos de `URI`, que faz o quoting certo —
montar a string à mão erraria em `+` e `%`. Regressão fixada com dois nomes reais.
Verificado por quebra deliberada: voltando a `base.resolve`, a suíte estoura.

### 36.4 Duas telas, dois problemas

`/api/desconhecidos` e `/api/organizacao` ficaram separados de propósito: ali
estão arquivos que o motor **não reconheceu** (score abaixo do limiar); aqui,
arquivos que ele reconheceu como **não sendo evidência**. O primeiro é falta de
regra; o segundo é bagunça de pasta, e quem resolve cada um é outra pessoa.

O painel de organização **tem** recorte por contrato — ao contrário do de
desconhecidos (RA-04) — porque o achado nasce de uma varredura, e a varredura é de
um contrato.

### 36.5 Cobertura

23 asserções em `TestesDeOrganizacao` e 11 novas em `TestesDeColeta` (conflito
baixado e hasheado, conflito infectado rejeitado, nome com espaço e parêntese).
Com isso a **fase 1a fecha**: F1-01 a F1-10.

---

## 37. F2-02 — a proibição que virou assinatura

Critério de aceite: *"rescisão na folha de teste instancia as 6 exigências do
conjunto, **só para aquela matrícula**"*.

### 37.1 "Nunca por calendário" não pode ser um comentário

O cap. 7.2 abre com *"executada quando a folha estruturada da competência chega.
**Nunca por calendário**"*, e o cap. 5.2 repete no comentário de
`exigencia.origem`. É a decisão D-04, e é fácil de violar sem perceber: uma
rotina noturna que "abre as exigências de rescisão do mês" parece útil e cobra
documento de quem não foi demitido.

A proteção não é um sinalizador — alguém o inverte. É a **assinatura**:

```java
detectar(FolhaDeCompetencia folha, DeParaDeRubricas dePara,
         Map<String, Movimentacao> movimentacoes)
```

Não há data, não há relógio, não há `Clock`. Derivar por calendário exigiria
mudar a assinatura, o que quebra a compilação de quem chama. O teste fixa a lista
exata de parâmetros: se ela mudar, alguém terá de decidir de novo em vez de
passar numa revisão distraída.

A competência sai da própria folha; as datas de evento saem da movimentação.
Ambas são **dado**, não relógio — ler a data de desligamento que alguém registrou
é o oposto de derivar por calendário.

### 37.2 O de-para saiu do código-fonte

O cap. 7.2 termina com *"os códigos de rubrica são cadastro (tabela de-para por
sistema de folha), não código-fonte"*. Os nove códigos que o `LeitorDeFopag`
carregava em constante Java — todos lidos da FOPAG real de 06/2026 — foram para
`rubrica_de_para`. A razão é operacional: com o código em constante, "o RH mudou
o plano de contas" vira release de software.

**Duas formas de mapear, e a segunda existe por causa do A05.** A FOPAG imprime o
código; o contracheque não. Como a folha da fase 1b é derivada dos contracheques
enquanto o export do cap. 14.3 não existe, um de-para só por código não
reconheceria nada na fonte que temos. A linha aceita mapear por código **ou** por
descrição normalizada — e o código, quando existe, vence: a descrição varia de
grafia entre competências ("Vale Alimentação", "VALE ALIMENTACAO", "V.
ALIMENTACAO"), o código não.

Rubrica fora do cadastro vira `OUTRA`, **não erro**: a folha tem dezenas de linhas
e o de-para só precisa das que alguma regra usa. Tratar o desconhecido como erro
faria a chegada de uma rubrica nova travar a competência inteira, contra o
princípio 1 do cap. 1.

### 37.3 Admissão não se supõe pela primeira aparição

Tentador: "quem aparece na folha desta competência e não estava na anterior foi
admitido". Erra em toda migração de sistema, que faz todo mundo aparecer de uma
vez — e instanciaria `ADM.DOCUMENTACAO` para o quadro inteiro.

Admissão só é afirmada quando a **movimentação** traz a data e ela cai na
competência. Sem a data, não há evento — e isso é dizer menos, não errar mais.

### 37.4 O outro lado do critério de aceite

"Instancia as 6" e "só para aquela matrícula" falham de formas diferentes, e as
duas importam. Instanciar de menos deixa passar documento que o cliente cobra;
instanciar para todo mundo enche o painel de exigências que nunca serão
atendidas, **porque o documento não existe** — e um painel cheio de pendências
impossíveis é um painel que ninguém olha.

Verificado por quebra deliberada: trocando a matrícula do evento por qualquer
alocado, **4 asserções caem**.

Matrícula que a folha traz e o contrato não tem vira **alerta**, não exigência: a
folha e o cadastro divergindo é fato a conferir, e criar a cobrança mesmo assim
inventaria uma pendência sem dono.

### 37.5 Uma expectativa minha estava errada

O teste do prazo esperava `2026-04-27` — cinco dias úteis após 20/04. O valor
correto é **28/04**: 21/04 é Tiradentes, está na carga do calendário, e a
contagem pula. Eu havia contado sem o feriado. A correção deixou o teste melhor
do que se tivesse passado de primeira: agora ele prova que o calendário da UF
está sendo consultado de verdade.

### 37.6 O que ficou de fora, e por quê

Os códigos de 13º, férias e rescisão **da FOPAG** não estão no seed. A massa tem
uma competência de folha mensal comum, sem esses eventos — os códigos deles nunca
foram observados, e inventá-los seria adivinhar. O de-para por descrição cobre a
detecção pelo contracheque; os códigos da FOPAG são cadastro da área demandante.
Registrado como **RA-09**.

### 37.7 Cobertura

31 asserções em `TestesDeEventos` (22 sem banco, 9 contra o PostgreSQL).

---

## 38. F2-03 — "faltam 3 de 42" parece uma subtração e não é

Critério de aceite: *"«faltam 3 contracheques de 42» calculado e exibido"*.

### 38.1 Quatro baldes, não dois

O número é fácil; o difícil é que ele seja **verdade**. Quatro situações
diferentes se escondem atrás de "falta", e juntá-las manda a pessoa errada atrás
da coisa errada:

| Balde | Estados | Quem age |
|---|---|---|
| **Entregue** | RECEBIDO, VALIDADO, CONCILIADO, PUBLICADO | ninguém |
| **Ausente** | PENDENTE, **REJEITADO** | a área que entrega — é o que a AP cobra |
| **Com problema** | EM_TRIAGEM, DIVERGENTE | outra mesa: chegou e travou |
| **Dispensada** | DISPENSADO | ninguém — o DAF já decidiu |

Duas escolhas dentro disso merecem ser ditas:

**REJEITADO conta como ausente.** O documento chegou e foi recusado — a
exigência continua sem documento válido, e quem entrega precisa entregar de novo.
Contá-lo como "entregue" faria o painel dizer que está tudo lá.

**EM_TRIAGEM e DIVERGENTE não contam como falta.** Chegaram. Cobrar a área por
eles é cobrar quem já entregou — e o efeito prático é a área parar de responder à
cobrança, porque ela deixou de ser confiável.

Contar a **dispensada** como falta é o pior dos quatro: faz alguém correr atrás
de um documento formalmente dispensado, contra a decisão do APROVADOR_DAF que a
F0-09 protegeu com três camadas de segregação.

Verificado por quebra deliberada: juntando os quatro num balde só, **3 asserções
caem**.

### 38.2 O grupo condicional tinha de entrar na conta

Cap. 7.5: exigências do mesmo grupo são satisfeitas por qualquer uma — o termo de
não adesão satisfaz o vale-transporte **daquele** profissional. Contar sem isso
exibiria *"faltam 12 relações de VT"* com os 12 termos de não adesão entregues ao
lado, e o painel estaria mentindo sobre o trabalho que resta.

O agrupamento é **por profissional**: um termo de fulano não satisfaz o VT de
sicrano. Sem essa cláusula a conta erraria para o lado oposto, dando por
satisfeito o que ninguém entregou.

### 38.3 O grupo que vale é o da exigência, não o do tipo

Os três testes de grupo falharam na primeira execução. A causa foi o fixture, e
ela é informativa: `exigencia.condicional_grupo` é uma **cópia**, feita na
materialização, e a consulta lê a cópia — não o valor atual de
`tipo_documental`. Meu fixture só tinha preenchido o tipo.

É a mesma lógica do `versao_matriz_id` congelado (F0-05): o ciclo responde pela
regra da época, e mudar o cadastro hoje não reescreve o que já foi
materializado. Virou teste próprio — tirar o VT do grupo no cadastro, depois de o
ciclo materializar, não muda a completude daquele ciclo.

### 38.4 A contagem é para todos; a lista nominal, não

"Faltam 3 de 42" pode aparecer para quem vê o painel. *"Faltam os contracheques
de 100787, 100792 e 100801"* é dado pessoal: matrícula identifica uma pessoa. A
lista só é preenchida para quem tem `VER_DOCUMENTO_PROFISSIONAL` — a mesma
permissão que decide abrir o contracheque.

E ela vem vazia também quando não falta ninguém, de propósito: se viesse vazia só
por falta de permissão, "não posso ver" e "não há" ficariam indistinguíveis.

### 38.5 Cobertura

20 asserções em `TestesDeCompletude`, contra o PostgreSQL real.

---

## 39. F2-04 — comparar conjuntos é o erro; comparar períodos é a regra

Critério de aceite: *"admitido no meio do mês não gera falso positivo"*.

### 39.1 A diferença de conjuntos acusa toda a rotatividade

O cap. 9 escreve a R05 como continência de conjuntos:
*"conjunto(matrículas em OPE.RELACAO_ALOCADOS) ⊆ conjunto(matrículas na folha)"*.
Implementada literalmente, ela acusa divergência **a cada admissão e a cada
desligamento** — num contrato de 42 pessoas com rotatividade normal, vários por
mês, todo mês.

É o risco **P01** ("falso positivo em massa → abandono") na sua forma mais
previsível: a regra estaria certa segundo a letra e inútil na operação, porque
depois do terceiro mês ninguém olha mais.

O que decide não é estar nos dois conjuntos: é a **alocação intersetar a
competência** — o mesmo critério do achado E-10 que a materialização já usava.
A própria linha do capítulo prevê isso na coluna de tolerância
("exceções: admitidos/desligados no mês, janela pró-rata"); o que faltava era
tratá-la como parte da lógica, não como nota de rodapé.

Verificado por quebra deliberada: removendo a janela e voltando à diferença de
conjuntos pura, o teste do critério de aceite falha.

### 39.2 As duas faltas não são a mesma coisa

A leitura de conjuntos esconde que a R05 é **assimétrica**:

| Situação | Leitura | Quem age |
|---|---|---|
| Alocado o mês inteiro, ausente da folha | trabalhou e não foi pago, ou a folha está incompleta | AP / folha |
| **Na folha do contrato, sem alocação** | alguém foi pago por este contrato sem estar nele | **gestor — custo no contrato errado** |
| Alocado por janela parcial, ausente da folha | admitido no fim ou desligado no início | ninguém: ressalva |

A segunda é a mais grave e a que a diferença de conjuntos mais disfarça — ela
aparece como "sobrou um nome", e o que pode significar é **custo alocado ao
contrato errado**, que é dinheiro no lugar errado, não papel faltando. Por isso a
mensagem manda *conferir se o custo está no contrato certo*, e não cobrar
documento.

Conforme **com ressalva** continua conforme: a ressalva é observação, não
pendência. Escondê-la seria pior — quem confere precisa saber que houve
movimentação no mês.

### 39.3 A R05 também é uma regra de população

Como a R09 (achado da fase 1b), a R05 compara a equipe **de um contrato**.
Rodá-la sobre a folha da empresa acusaria todo colaborador dos outros contratos
como "na folha sem alocação" — dezenas de divergências com os dois lados
corretos. A regra se declara NÃO APLICÁVEL e diz que precisa do recorte por
centro de custo.

### 39.4 R06: incompleto não é atrasado, e a diferença é o dia de hoje

Um conjunto de rescisão aberto ontem está incompleto e não devia acusar nada — o
prazo é do evento, e o evento acabou de acontecer. A regra só diverge quando o
prazo **passou**.

Isso obriga a R06 a conhecer uma data de referência, e ela é **parâmetro
explícito**: uma regra que lesse o relógio deixaria de ser reproduzível, e o
cap. 16 exige que reexecutar uma conciliação de seis meses atrás sobre os mesmos
documentos dê o mesmo resultado.

**A carência do ASO tem escopo.** O cap. 9 dá +10 dias corridos ao ASO
demissional — o exame depende de agenda de clínica, que não obedece ao prazo do
TRCT. Mas a carência vale **só quando o ASO é o que falta**: se faltam o TRCT e o
ASO, o conjunto já está atrasado pelo TRCT, e estender o prazo por causa do ASO
esconderia o atraso do outro documento.

### 39.5 Cobertura

18 asserções novas em `TestesDeConciliacao` (73 no total da suíte).

---

## 40. F2-05 — contar certo e cobrar errado é pior que os dois errados juntos

Critério de aceite: *"termo de não adesão satisfaz a exigência de VT do
profissional"*.

### 40.1 A F2-03 tinha corrigido a metade visível

A completude já não dizia *"faltam 12 relações de VT"* com os 12 termos entregues
ao lado — mas fazia isso **na consulta**, e a exigência de VT continuava
`PENDENTE` no banco.

Esse estado é lido por mais três lugares: a régua de cobrança, o bloqueio de
publicação do book e a pendência aberta. **A tela dizia que estava tudo bem e o
e-mail saía assim mesmo** — que é pior do que os dois errados juntos, porque
quando os dois erram alguém percebe.

Corrigir isso em cada consulta seria repetir a mesma regra em três lugares que
ninguém lembra de manter juntos. A satisfação do grupo virou um **fato gravado**:
a irmã vai para DISPENSADO e a pendência dela fecha, dentro da mesma transação
que vinculou o documento — se a vinculação for desfeita, a satisfação vai junto.

### 40.2 DISPENSADO já significava outra coisa

Marcar a irmã como DISPENSADO esbarra num problema que valia a pena expor: o
cap. 6.1 só chega a DISPENSADO por um caminho — *"exceção aprovada · Aprovador
DAF"*. É decisão de governança, com motivo, evidência e segregação de funções
(F0-09).

A substituição condicional não é nada disso: é rotina, decidida pelo próprio
documento que chegou. Pôr as duas no mesmo estado, sem distinção, teria dois
efeitos:

- o auditor que lê o book não consegue dizer se um documento foi **formalmente
  dispensado pela DAF** ou apenas **substituído pela alternativa**;
- o indicador de exceções aprovadas do cap. 21 passaria a contar substituições de
  vale-transporte.

`exigencia.dispensa_motivo` separa as duas (`EXCECAO` / `CONDICIONAL`), e a
restrição impede DISPENSADO sem motivo. Com backfill antes da restrição — a lição
da V012, que quase repeti.

E a distinção ganha uso imediato: na completude, `CONDICIONAL` conta como
**entregue** (a alternativa foi entregue) e `EXCECAO` continua contando como
**dispensada**.

### 40.3 A pendência fecha como ENTREGA

O enum tem `ENTREGA | EXCECAO | CANCELAMENTO`. Cancelamento diria que a obrigação
deixou de existir, e ela foi **cumprida** — por outro documento. Entrega é o que
aconteceu.

### 40.4 `IS NOT DISTINCT FROM`, e não `=`

O grupo é por profissional no escopo PROFISSIONAL e por ciclo no escopo de
contrato, onde `profissional_id` é nulo. Comparar com `=` não casaria nada quando
os dois lados são nulos — a condicional de contrato ficaria **silenciosamente sem
efeito**, e o sintoma seria uma exigência que nunca é satisfeita sem nenhum erro
em lugar nenhum.

Verificado por quebra deliberada: removendo a cláusula por profissional, um termo
de fulano passa a satisfazer o VT de sicrano e 2 asserções caem.

### 40.5 Cobertura

12 asserções novas em `TestesDeCompletude` (32 na suíte).

## 41. F3-03 e F3-04 — o indicador que melhora porque o dado piorou

O cap. 6.2 descreve o ciclo numa linha e a linha esconde duas coisas diferentes:
um conjunto de estados e uma ordem entre eles. O `CHECK` do V001 já garantia o
primeiro — `status` é um dos oito — e não garantia nada do segundo. Um ciclo
podia ir de ABERTO a FECHADO num salto, e chegar a FATURADO sem ateste nenhum.

### 41.1 A circularidade que não existe, e os dois portões que existem

Ler o cap. 6.2 e o cap. 13 juntos sugere um nó: PRONTO exige toda bloqueante em
PUBLICADO, publicar o book exige o ciclo pronto, e nada anda. A ordem desfaz — o
book publica sobre exigências **CONCILIADAS**, e é a publicação que move
CONCILIADO → PUBLICADO (cap. 6.1, última linha). São dois portões, e a diferença
importa:

| Pergunta | Quem responde | Aceita |
|---|---|---|
| Posso publicar o book? | `ConsultaDoPainel.motivosDeBloqueio` | PUBLICADO, DISPENSADO, **CONCILIADO** |
| Posso declarar o ciclo PRONTO? | `RepositorioDeCiclo.bloqueantesEmAberto` | PUBLICADO, DISPENSADO |

Usar o portão frouxo no lugar do rígido deixaria a medição sair ao cliente com
documento ainda não publicado no book — exatamente o que a F3-04 existe para
impedir. Quebra deliberada: fazendo `bloqueantesEmAberto` aceitar CONCILIADO,
**2 asserções caem**, e nenhuma delas é do painel.

### 41.2 O que a suíte mede, e o que ela não mede

Cada transição é checada duas vezes: em Java, com mensagem, e dentro do próprio
`UPDATE`, como cláusula do `WHERE`. As quebras deliberadas mediram cada camada
isolada:

| Quebra | Resultado | O que isso diz |
|---|---|---|
| Sem a guarda Java de PRONTO | 53/55 | O banco continua barrando; a recusa perde o tipo e a **lista do que falta** — 2 asserções caem, 2 nem rodam |
| Sem o `WHERE` do banco | 57/57 | **A suíte não mede esta cláusula.** Ver abaixo |
| `bloqueantesEmAberto` aceitando CONCILIADO | 55/57 | O portão frouxo no lugar do rígido |
| Denominador = atestados, não faturados | 55/57 | O ciclo que ainda tem prazo entraria como descumprimento |
| Tabela de transições aberta | 52/57 | 5 asserções caem — é a tabela que impede os saltos |
| Ateste sobrescrevível | 55/57 | Mover o marco depois de o relógio começar |

A segunda linha está registrada e não foi corrigida. O que o `WHERE` cobre é a
janela entre a leitura em Java e a gravação, e reproduzir essa janela num teste
exigiria pausar o código **dentro** da transação — o que o código não expõe, e
expor só para o teste seria pior que não medir. A cláusula fica pelo que o banco
garante, não pelo que o teste prova; dizê-lo aqui evita que ela seja lida como
garantia verificada.

### 41.3 O indicador que melhora porque o dado piorou

Removendo a guarda de Java, o banco aceitava FATURADO sem `ateste_em`. O efeito
não é um estado feio no painel: é uma distorção silenciosa do KPI do cap. 21.

O denominador do D+3 filtra por `ateste_em IS NOT NULL`. Um ciclo faturado sem
ateste **sai do numerador e do denominador ao mesmo tempo** — e o ciclo com mais
chance de ter sido remendado à mão é justamente o que mais demorou. O percentual
sobe porque o pior caso desapareceu da conta.

A V016 fecha isso no banco, que é por onde um `UPDATE` manual em produção passa
sem ver o Java:

- `ciclo_ateste_antes_de_atestado` — ATESTADO/FATURADO/FECHADO exigem `ateste_em`
- `ciclo_nf_antes_de_faturado` — FATURADO/FECHADO exigem `nf_emitida_em`
- `ciclo_nf_nao_antecede_ateste` — NF anterior ao ateste satisfaria "dentro de
  D+3" com folga negativa

**Sem backfill, e é deliberado.** A V015 preencheu antes de restringir porque a
linha antiga tinha um valor correto e conhecido. Aqui não há: um ciclo em
FATURADO sem ateste não tem data "provável", e escrever `now()` no lugar
produziria um D+3 calculado sobre uma data fabricada. Se houver linha assim, a
migração falha — e falhar é a resposta certa.

Medido em três configurações:

| Configuração | Resultado |
|---|---|
| Só a V016 (sem a guarda Java), **antes** de traduzir o SQLState | 50/52 — o ciclo fica protegido, mas a recusa chega como `FalhaDePersistencia` |
| Nem V016 nem guarda Java | 52/57 — **5 asserções caem**: o ciclo fatura sem ateste |
| Só a V016, **depois** de traduzir 23514 → `TransicaoInvalida` | 57/57 |

A linha do meio é a que justifica a migração. A primeira é a que justifica a
tradução: sem ela, uma recusa legítima virava HTTP 500 — "o sistema falhou" onde
o certo é "o pedido é inválido" —, e quem investigasse procuraria defeito no
banco. É a mesma lição do `Sgdf.emTransacao` que reembrulhava recusa de domínio,
aparecendo agora do lado do SQLState.

A terceira linha diz uma coisa desconfortável e verdadeira: para ATESTADO e
FATURADO, o `WHERE` em Java é **provadamente redundante** com a V016. Fica
porque a mensagem nomeia o marco que falta, enquanto a do banco nomeia a
restrição — mas a garantia é do banco, não dele.

### 41.4 O que um CHECK não consegue garantir

Nenhuma restrição enxerga o valor **anterior** da linha, então ABERTO → FECHADO
continua passando no banco desde que os marcos estejam gravados. A ordem vive no
`EstadoDoCiclo`, e o T009 afirma isso explicitamente — para que ninguém leia a
V016 como se fosse a máquina de estados.

### 41.5 Registrar não é transitar

`registrarAteste` grava o ateste e **não** move o ciclo. Fundir os dois
apagaria a diferença entre "o cliente atestou" e "nós consideramos o ciclo
atestado" — e é do primeiro fato, não do segundo, que o relógio do D+3 conta.
Pelo mesmo motivo o ateste não se sobrescreve: corrigir a data é reabrir o
ciclo, não editar o marco depois de o relógio ter começado.

### 41.6 Um `?AND` que só aparecia num caminho

O `WHERE` extra de PRONTO era um bloco de texto Java concatenado ao SQL base. O
bloco remove a indentação comum de **todas** as linhas, inclusive a primeira, e
o resultado era `c.status = ?AND NOT EXISTS` — recusado pelo Postgres com
*trailing junk after parameter*. As outras duas pré-condições eram literais com
espaço e funcionavam. Uma suíte que exercitasse só ATESTADO e FATURADO nunca
veria o defeito.

### 41.7 Cobertura

57 asserções em `TestesDeCiclo` (15 delas sem banco, sobre a tabela de
transições), 7 novas em `TestesDeWeb` (as duas permissões separadas e o
agregado que não sai recortado) e 8 no `T009__marcos_do_ciclo.sql`.
Total: **946 Java, 113 SQL**.

## 42. O pipeline ponta a ponta, e o gabarito que media o nome da pasta

Cada etapa — varredura, extração, classificação, validação, conciliação —
existia e era verificada isoladamente. Nada as ligava. O `Pipeline` liga as que
não precisam de ciclo, e a ligação tem um retorno próprio: permite rodar o
sistema sobre os documentos reais e **medir** a precisão de classificação que o
cap. 19 exige. É metade da F3-01, e a metade que não depende de conferência
manual nova.

### 42.1 O resultado

Sobre os 27 documentos reais de 06 e 07/2026, com gabarito humano:

| Recorte | Arquivos | Acertos | Erros | Abstenções | Precisão | Cobertura |
|---|---:|---:|---:|---:|---:|---:|
| **Bloco corporativo** (cap. 19) | 10 | 10 | 0 | 0 | **100,0%** | 100,0% |
| Massa completa | 27 | 24 | 0 | 3 | 100,0% | 88,9% |

**A meta do cap. 19 — ≥ 95% no bloco corporativo antes da 1b — está atingida,
com folga e com cobertura total.** E o número que mais importa não é a precisão:
é o **zero na coluna de erros**. Nenhum documento da massa seria vinculado à
obrigação errada, que é o único jeito de o sistema satisfazer uma exigência com
o papel de outra.

As três abstenções são todas explicáveis, e nenhuma é falha de classificação:

| Arquivo | Por quê |
|---|---|
| `COMPROVANTE_PG_IRRF` | **Sem extensão.** V1 barra antes de abrir — ver 42.4 |
| `COMPROVANTE_PG_FOLHA_6/7` | O SISPAG **vazio** do achado A12: rótulos sem valores. Não reconhecer é a resposta certa |

### 42.2 O gabarito estava errado, e errado do jeito mais fácil

A primeira medição deu **78,3%** de precisão e **cinco erros**, todos em
comprovantes bancários, todos com score 1,00. Antes de mexer nas regras, fui ler
os documentos. O classificador estava certo nos cinco:

| Arquivo | Gabarito que eu escrevi | O que o documento diz | Certo |
|---|---|---|---|
| `COMPROVANTE_PG_FGTS` | CMP.BOLETO | `tipo pagamento: pix copia e cola`, destinatário CEF | CMP.TRANSFERENCIA |
| `COMPROVANTE_PG_ALIMENTACAO` | CMP.TRANSFERENCIA | `pagamento de boleto`, `linha digitavel: 34191…` | CMP.BOLETO |
| `COMPROVANTE_PG_VA_VR_1` e `_2` | CMP.TRANSFERENCIA | idem | CMP.BOLETO |
| `C2_COMPROVANTE_PG_FOLHA` | CMP.LOTE_SALARIOS | transferência CC→CC para **um** beneficiário | CMP.TRANSFERENCIA |

Eu montei o gabarito a partir dos **nomes dos arquivos** — que é exatamente
aquilo de que a medição existe para não depender. Um gabarito tirado do nome não
mede o classificador: mede a convenção de nomes da pasta, e pune o sistema
justamente quando ele lê o documento melhor do que quem o nomeou.

O caso do FGTS é o mais instrutivo: "FGTS se paga por guia, guia tem código de
barras, logo é boleto" é uma inferência razoável e falsa — a Engesoftware pagou
por PIX. A regra que sobrou disso vale para o resto do sistema: **quando o
gabarito e o documento discordam, abra o documento antes de mexer na regra.**

### 42.3 A medição que media a coisa errada

Corrigido o gabarito, restava uma abstenção estranha: o `COMPROVANTE_PG_INSS`
aparecia como "sistema não afirmou nada" tendo sido classificado `CMP.DARF` com
decisão AUTOMÁTICA. A causa: eu media a precisão pelo **encaminhamento** do
pipeline, e o encaminhamento mistura classificação com validação — V2 reprovou
uma página quase em branco entre as oito, o documento foi para triagem, e a
abstenção foi debitada do classificador.

O cap. 19 mede *precisão de classificação*. Quem afirmou o tipo foi o
classificador, e é a `Decisao` que diz se ele afirmou. Corrigido, o INSS volta a
ser o acerto que sempre foi.

**A massa real achou este defeito; a suíte de unidade não.** Só depois de
encontrá-lo escrevi o teste que o guarda — e a quebra deliberada mostra a
assimetria com precisão:

| Quebra | Suíte de unidade | Massa real |
|---|---|---|
| V1 depois da extração (arquivo infectado é aberto) | 27/30 — **3 caem** | 4/4 — a massa não tem arquivo infectado |
| Abstenção contada como acerto | 25/30 — **5 caem** | 3/4 — **a massa pega** |
| Precisão lida do encaminhamento (o defeito original) | 29/30 — **1 cai**, o teste que escrevi depois | 4/4 |

A terceira linha é o registro honesto: sem o teste que a descoberta produziu,
essa quebra passaria pela suíte inteira. É o argumento para a massa continuar
sendo rodada, e não substituída por fixtures.

### 42.4 Um documento real sem extensão, barrado antes de ser lido

`COMPROVANTE_PG_IRRF` — sem `.pdf`, exatamente como está no repositório de
origem — é reprovado por V1: *"extensão '' não está entre as aceitas [xls, csv,
pdf, xlsx]"*. O arquivo é um PDF válido: a assinatura binária diz isso, e a
assinatura é evidência mais forte que a extensão.

V1 hoje trata dois fatos diferentes como o mesmo: **a extensão mente sobre o
conteúdo** (um `.pdf` que é um executável — o ataque que a validação existe para
barrar) e **não há extensão** (não há o que mentir). O efeito do segundo é um
documento legítimo descartado sem chegar nem ao painel de organização.

**Não alterei V1.** Afrouxar uma validação de segurança é decisão de governança,
não de engenharia, e a leitura conservadora é a que fica valendo até a DAF
decidir. Registrado como **RA-13**, com a recomendação: sem extensão, aceitar o
MIME da assinatura e gravar o fato; extensão que contradiz a assinatura continua
reprovando.

Fica também **RA-14**: V2 reprova um documento de 8 páginas porque a página 6
tem 25 caracteres. Numa folha de comprovantes, uma página de separação quase em
branco é normal — e reprovar o conjunto por causa dela cobra reenvio de um
documento que está inteiro.

### 42.5 O que o pipeline deliberadamente não faz

Ele para antes de tudo que precisa de um ciclo. Não vincula exigência
(vincular depende da matriz materializada), não roda V4, V5 nem V3 (comparam o
documento com a competência, a data da NF e o CNPJ do ciclo — sem ciclo não há
com o que comparar, e rodá-las contra um valor inventado produziria vereditos
falsos), e não grava. As três que ficam — V1, V2 e V7 — não são uma lista
arbitrária: são exatamente as que se decidem **só com o arquivo**.

Duas propriedades estruturais valem a pena registrar:

- **O nome do arquivo não chega ao classificador.** A assinatura de
  `Classificador.classificar` recebe `TextoExtraido`, e nada mais. Verificado
  dando a uma CND o nome `CONTRACHEQUE_DO_FULANO.pdf`: o tipo não muda.
- **V1 roda antes de o PDFBox abrir o arquivo.** A prova não é o veredito
  REPROVADO — é que o documento barrado sai sem texto e sem classificação.
  Invertida a ordem, 3 asserções caem.

### 42.6 Cobertura

30 asserções em `TestesDePipeline` (sem banco e sem massa) e 4 em
`MedirPrecisao`, que se pula com um aviso quando `SGDF_MASSA` não é informado —
silêncio esconderia que a precisão não foi medida.
Total: **980 Java** (976 sem a massa), **113 SQL**.

## 43. F3-05 — "monitorar" não é uma meta, e o CSV é uma superfície de ataque

Critério de aceite: *"KPI/KRI calculados por competência, exportáveis em CSV"*.
Os nove indicadores do cap. 21, mais o endpoint de auditoria do cap. 13 — que
usa a mesma máquina de exportação e é onde o risco mora.

### 43.1 Quatro situações, não duas

Metade da tabela do cap. 21 não tem alvo: "tendência ↑", "tendência ↓" e
"monitorar" pedem que se olhe a série, não que se bata um número. Um
`boolean atingiu()` obrigaria cada KRI a responder sim ou não, e **qualquer das
duas respostas seria inventada** — pior, a resposta "sim" faria o painel
declarar sucesso sobre "exceções aprovadas", cuja alta significa exatamente
controle contornado.

| Situação | Quando |
|---|---|
| `ATINGIDA` / `NAO_ATINGIDA` | há meta e há população |
| `MONITORADO` | KRI: a leitura é a série |
| `SEM_POPULACAO` | não houve o que contar — nem sucesso nem falha |

`Meta.monitorar()` recusa receber limite: se há número a atingir, o sentido é
outro. Quebra deliberada tratando MONITORADO como ATINGIDA: **1 asserção cai**.

E há uma assimetria que parece inconsistência e não é: **razão sem população
devolve nulo; contagem zero devolve zero**. "Zero de zero" não afirma nada — um
mês que começou hoje não descumpriu o D+3. "Nenhuma exceção aprovada nesta
competência" é informação, e devolver nulo a esconderia. A diferença é que na
segunda *houve o que contar*. Quebra devolvendo 0% no lugar do nulo: **4
asserções caem**.

### 43.2 Cada indicador tem uma população, e é sempre ali que a conta erra

O erro nunca aparece como número absurdo. Aparece como número bonito.

| Indicador | O que ficou fora do denominador, e por quê |
|---|---|
| NF em D+3 | Ciclos não faturados. Incluí-los faria o percentual cair no começo do mês e subir sozinho no fim — mediria o calendário |
| Precisão de classificação | **ILEGÍVEL.** "Este documento está ilegível" não é veredito sobre o *tipo* — é sobre a digitalização. Somá-la debitaria do classificador um defeito de scanner, e a métrica pioraria quando a origem mandasse fotocópia ruim. REJEITADA entra: dizer "não é desta exigência" **é** julgar a sugestão |

Quebra somando ILEGÍVEL ao denominador: **2 asserções caem**. O teste prova a
exclusão de forma positiva — trocando as duas ILEGÍVEL por REJEITADA, o
denominador *muda*, o que só acontece se a distinção for real.

**Folga da certidão é o mínimo, não a média.** Nove certidões com 60 dias e uma
com 1 dão média 54 — e é a de um dia que vence antes de o cliente pagar. O
risco é do pior caso, então o indicador tem de ser o pior caso. Quebra usando
`avg` no lugar de `min`: **2 asserções caem**.

### 43.3 Um filtro que lia zero e dizia "não havia"

A primeira versão da folga da certidão filtrava `t.familia = 'CERTIDAO'`. A
carga real grava **"Certidões e regularidade"**. A consulta lia zero linha, o
`min()` devolvia nulo, e o indicador respondia `SEM_POPULACAO` — que se lê como
*"não havia certidão nesta competência"* e não como *"meu filtro está errado"*.

O conserto não foi corrigir a string: foi parar de usar rótulo de exibição como
chave. Um nome de família muda por decisão de quem cadastra, e a consulta
quebraria em silêncio de novo. O filtro passou a ser **ter `validade_extraida`**
— que é a propriedade que o indicador realmente precisa, porque é o vencimento
que cria o risco.

### 43.4 A completude que daria 100% por nada ter acontecido

O capítulo pede "exigências satisfeitas na 1ª varredura pós-prazo". Não há
registro de varredura por exigência, e a leitura óbvia — "pendência nunca
escalonada" — seria **pior que não medir**: quem escalona é a régua de
notificação, ela não roda (RA-07), e o indicador daria **100% justamente porque
nada aconteceu**.

A leitura adotada não depende de agendador nenhum: *documento vinculado até a
data do prazo*. É observável só com o que está gravado, responde à mesma
pergunta — chegou a tempo ou foi preciso cobrar — e está declarada no campo
`observacao`, que viaja junto do número no JSON e no CSV.

### 43.5 O CSV executa fórmula, e o vetor foi medido em vez de suposto

Excel, LibreOffice e Google Sheets tratam célula iniciada por `=`, `+`, `-`,
`@` ou tabulação como **fórmula**. `=cmd|'/c calc'!A1` chega a executar comando
no Windows. O texto entra por um campo legítimo — o motivo que o cap. 16 exige
que exista — e sai pela exportação que o cap. 13 exige que exista; nenhuma das
duas está errada, o que faltava era o escape na fronteira.

**Aspas não resolvem:** `"=1+1"` continua fórmula, porque as aspas são
consumidas pelo parser de CSV antes de o interpretador de fórmulas ver o
conteúdo. A defesa é o apóstrofo, que o Excel lê como "isto é texto".

**E prefixar tudo seria pior:** a coluna de valores viraria texto e a planilha
perderia soma e ordenação — que é o motivo de alguém exportar CSV. Só a célula
iniciada por caractere perigoso é neutralizada.

Fui medir qual campo está *de fato* exposto hoje, em vez de repetir a ameaça
genérica:

| Campo | Chega ao início da célula? |
|---|---|
| `detalhe` (onde vivem os motivos escritos por pessoas) | **Não** — sai como JSON e já começa com `{"`. O envelope o desarma **por acidente, não por projeto** |
| `ator` | **Sim.** Vem do `sub` do provedor de identidade — externo. Verificado: um ator `=cmd|'/c calc'!A1` sai como `'=cmd|'/c calc'!A1` |

O escape fica na fronteira e não no campo, e é o que faz a defesa sobreviver à
próxima exportação — a hora em que alguém acrescentar uma coluna de motivo em
texto puro é exatamente a hora em que ninguém vai lembrar disto. Quebra
removendo a neutralização: **10 asserções caem**.

O negativo é o caso incômodo: `-3` precisa ser neutralizado (o Excel lê `-` como
início de fórmula) e isso custa a soma da coluna. Entre executar fórmula e
perder a soma de uma coluna que hoje não tem negativo nenhum, a escolha é óbvia
— e fica registrada em vez de descoberta depois.

### 43.6 Uma permissão que existia e não abria nada

`AUDITAR` estava no `Autorizador` desde a F0-01 e **nenhum endpoint a usava**.
Uma permissão sem uso é pior que uma permissão faltando: ela aparece na matriz
de acesso, passa na recertificação (F3-06) e não dá acesso a nada — a
organização acredita ter um controle que não tem. O `/auditoria` do cap. 13
fecha isso, e os testes confirmam que nem APROVADOR_DAF nem ADMIN_SISTEMA a
abrem: visão global de configuração não é visão global de trilha.

Duas decisões que vêm junto:

- **O filtro é obrigatório.** A trilha cresce sem limite (append-only, V002), e
  um `GET /auditoria` sem recorte devolveria a tabela inteira. Não é carga: é
  que uma exportação completa da trilha, num sistema que registra CPF em detalhe
  de decisão, é o pior arquivo possível para sair sem justificativa. Quebra
  removendo a exigência: **2 asserções caem**.
- **Exportar a trilha entra na trilha.** Sem isso, a única operação que produz
  um arquivo com o histórico inteiro seria a única que não deixa registro — e
  quem investigasse um vazamento não saberia quem baixou o quê. O registro grava
  o **filtro**, não o conteúdo: reconstrói o recorte sem duplicar o dado pessoal
  dentro da própria trilha. Quebra removendo o registro: **2 asserções caem**.

### 43.7 Cobertura

60 asserções em `TestesDeIndicadores` (15 sem banco, sobre CSV e metas) e 4
novas em `TestesDeWeb`. Total: **1044 Java** (1040 sem a massa), **113 SQL**.

## 44. F3-02 — a permissão perigosa não se herda, e a flag falha na troca

Critério de aceite: *"régua completa D-2/D+0/D+2/D+5; máx. 1 e-mail/área/dia"*.
A régua e a consolidação vieram na F1-09 — e a consolidação é imposta pelo
banco (`notificacao_diaria_unica` + `ON CONFLICT DO NOTHING`), não confiada ao
agendador. O que faltava é **quem decide, e por contrato**.

### 44.1 Duas chaves, e não uma com escopo

A tentação é resolver `notificacao.modo_sombra` por escopo — contrato, senão
cliente, senão global — como qualquer outro parâmetro. Seria errado de um jeito
silencioso: alguém desligando o modo sombra **globalmente para testar um
contrato** ativaria a cobrança de **todos**, e o sintoma apareceria como e-mails
saindo para áreas que nunca souberam que o sistema começou a cobrá-las.

| Chave | Escopo | Natureza |
|---|---|---|
| `notificacao.modo_sombra` | GLOBAL | chave de **desligar**: enquanto ligada, nada sai de contrato nenhum |
| `notificacao.envio_ativo` | CONTRATO | **adesão explícita**: só o contrato cadastrado envia |

**A regra que faz isso valer: o que é seguro herda, o que é perigoso não.** Um
contrato sem parâmetro próprio está em sombra, e nenhuma configuração de cliente
ou global o tira de lá. É a mesma decisão do `Autorizador` — negar é o padrão, e
o padrão não se alcança por omissão de quem configurou. Ativar exige portanto
**duas decisões independentes**: desligar a chave global e aderir contrato a
contrato.

Quebra deliberada fazendo a adesão herdar: **2 asserções caem**.

### 44.2 A ordem dos portões, validada pela suíte da história anterior

O portão de "sem transporte" vem **antes** do de adesão, e a ordem foi escolhida
por um motivo que só aparece quando se olha o que a F1-09 já garantia.

Desligar a chave global é uma **declaração de intenção de enviar**. Checar a
adesão primeiro faria o caso "global desligada, nenhum contrato aderido" cair em
`SEM_ADESAO` — a régua gravaria em sombra, ninguém diria nada, e quem desligou a
chave passaria a esperar e-mails que nunca sairiam. Silenciar uma intenção
explícita é a mesma confiança falsa que a F1-09 recusou.

Quebra invertendo a ordem: **2 asserções caem na F3-02 — e 2 caem na F1-09**. A
suíte da história anterior é quem prova que a ordem nova preserva a garantia
antiga. Foi o resultado mais útil das quatro quebras.

### 44.3 A flag que falha no uso dá dias de confiança falsa

O `RepositorioDeNotificacao` já recusava executar sem transporte. Mas recusava
**na execução**, que acontece um dia depois de alguém virar a chave. Nesse
intervalo:

- quem virou acredita ter ativado a cobrança;
- a área acredita que será cobrada;
- as duas crenças são falsas ao mesmo tempo, e nada no sistema diz isso.

`RepositorioDeParametro.ativarEnvio` recusa **no momento de ligar**, e não grava
nada: não fica um contrato meio ligado. A recusa na execução continua lá como
segunda barreira — alguém ainda pode editar `parametro` por SQL.

| Quebra | F3-02 | F1-09 |
|---|---|---|
| Adesão herdando do escopo global | 24/26 | 42/42 |
| Adesão checada antes do transporte | 24/26 | **40/42** |
| Recusa no uso em vez de na troca | **23/26** | 42/42 |
| Tentativa negada dentro da transação que a desfaz | 24/26 | 42/42 |

A última linha é a lição da F0-09 repetida: a negativa é o fato mais auditável
do fluxo — é ela que mostra alguém insistindo — e some exatamente por ser
negativa, levada pelo rollback da própria recusa.

### 44.4 Ligar pede mais que desligar

Ativar exige `CONFIGURAR_SISTEMA` e motivo de ao menos 20 caracteres; desativar
exige apenas ver o painel, e não pede motivo.

A assimetria é deliberada. Ligar faz o sistema começar a mandar e-mail para
pessoas; desligar apenas devolve o contrato ao estado seguro. Exigir o mesmo
papel nas duas pontas criaria a situação em que **quem percebe o problema não
pode pará-lo** — e a primeira coisa que se quer numa emergência é a porta de
saída aberta. Atrito na direção segura não protege ninguém.

### 44.5 O que continua faltando, e não é código

`EXISTE_TRANSPORTE` é uma **constante Java** e não um parâmetro de banco: um
parâmetro dizendo que há transporte não faria transporte existir, e a constante
garante que o dia em que alguém a trocar é o dia em que o compilador obriga a
olhar para os quatro pontos que dependem dela. RA-06 continua aberta e continua
gated na F3-01 — a régua só deve sair da sombra depois de a divergência medida
ficar ≤ 2% por dois ciclos (cap. 19).

Um resíduo conhecido, aceito: com `ON CONFLICT DO NOTHING`, uma segunda execução
no mesmo dia com aviso **mais grave** (uma pendência criada à tarde cujo D+0 é
hoje) é descartada, e a área recebe só o preventivo da manhã. Trocar por um
`DO UPDATE` que promove o aviso seria correto hoje — nada foi enviado ainda — e
errado no dia em que houver transporte, porque atualizaria uma linha já enviada.
Fica o comportamento que continua certo depois.

### 44.6 Cobertura

26 asserções em `TestesDeAtivacao` (11 sem banco, sobre a decisão) e 3 novas em
`TestesDeWeb`. Total: **1073 Java** (1069 sem a massa), **113 SQL**.
