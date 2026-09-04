# Achados do primeiro contato com documentos reais

**Massa analisada:** 10 documentos do OwnCloud da Engesoftware, competência 06/2026, extraídos com o `ExtratorPdfBox` da história F1-02.

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

**Os 10 extraem com texto nativo.** Nenhum exigiu OCR — a contingência do cap. 8.2 não é o caminho comum, ao menos para o bloco corporativo.

**Faltam para fechar o critério da F1-03:** `CER.SICAF`, `CER.CND_CIVEL_CRIMINAL`, `CER.CND_FALENCIA`, e mais duas competências fechadas.

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
