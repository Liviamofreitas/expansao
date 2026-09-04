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
