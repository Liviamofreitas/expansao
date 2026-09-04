# ADR-001 · Stack de desenvolvimento

**Pendência:** A13 · **Status:** aceita · **Data:** 2026-09-04
**Decisores:** TI · **Consultados:** arquitetura

## Contexto

O cap. 4 do documento normativo lista a arquitetura de referência como agnóstica de linguagem e deixa a escolha para a sprint 0 (R-04). Enquanto ela não saía, as histórias F0-01, F0-02 e F0-03 — autenticação e cadastros — ficaram paradas, e toda a fase 1 atrás delas.

As regras de negócio já construídas foram deliberadamente entregues como **suítes de conformidade agnósticas** (`especificacao/prazo`, `especificacao/materializacao`) exatamente para não prejulgar esta decisão.

## Decisão

**Java 21 + Spring Boot** para a API e os workers.

## Razões

**A extração de PDF com posições decide a fase 1a.** A história F1-02 exige que os campos extraídos exibam a região de origem no documento — é o que torna auditável de onde cada valor foi lido, e o que sustenta a exigência do cap. 16 de que toda decisão automática seja reproduzível. **PDFBox** é a biblioteca mais madura para isso, e a que tem o comportamento mais previsível em PDFs de origem heterogênea, que é exatamente o cenário descrito em D-07 ("arquivos reais não seguem a nomenclatura do checklist").

**O ambiente é offline (D-06).** Um repositório Maven interno com espelho é caminho conhecido e bem suportado para operação sem internet. O mesmo vale para o Tesseract via wrapper nativo, exigido pela contingência de OCR do cap. 8.2.

**A operação é assíncrona e de longa duração.** Workers separados da API (cap. 4.1), fila com dead-letter, retentativa com backoff e processamento de arquivo hostil em processo isolado são padrões que o ecossistema Spring cobre sem invenção.

**Sustentação (A10).** A escolha só se sustenta se a equipe conseguir manter o sistema depois do go-live. Se houver um padrão corporativo diferente, ele deve prevalecer sobre os argumentos acima — o cap. 22 registra "sistema vira legado sem dono" como risco P08.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| **.NET 8** | Adequada, especialmente se a casa operasse Windows Server. PdfPig cobre texto com posições, mas com ecossistema menor que PDFBox para o caso de PDFs heterogêneos. |
| **Node 20 + TypeScript** | Menor curva se a equipe fosse de front, mas extração de PDF com posições e integração com Tesseract são mais frágeis em Node — e são o coração da fase 1a. O risco recai exatamente sobre a parte mais difícil do sistema. |

## Consequências

**O que não muda.** As suítes de conformidade continuam valendo como estão. Elas são o critério de pronto da implementação: `especificacao/prazo/casos.json` (32 casos) e `especificacao/materializacao/casos.json` (18 casos).

**Prova de que a decisão não invalida o construído.** A função de prazo foi portada para Java 21 em `especificacao/prazo/java/Prazo.java` e passa nos **mesmos 32 casos**, com resultados idênticos aos da implementação de referência em Python:

```bash
python3 especificacao/prazo/verificar.py \
    --comando 'java especificacao/prazo/java/Prazo.java'
```

Esse arquivo não é código de produção — numa aplicação Spring Boot ele vira um componente com Jackson no lugar do parser de JSON caseiro, que existe só para o arquivo rodar sem dependências. Serve como ponto de partida e como prova de que a suíte é de fato agnóstica.

**O que precisa acontecer na sprint 0.**

| Item | Observação |
|---|---|
| Repositório Maven interno com espelho | Pré-requisito de D-06 (sem internet) |
| Versão do Spring Boot e BOM de dependências | Fixar, com SCA no pipeline (SEC-07) |
| Imagem base de contêiner fixada e escaneada | SEC-07 |
| Migrations: Flyway ou Liquibase | Hoje `db/aplicar.sh` faz o papel; a ordem e o conteúdo dos arquivos não mudam com a troca |
| Integração OIDC com o IdP corporativo | F0-01 |

**O que fica em aberto.** A escolha do runtime não resolve A10 (time de sustentação). O cap. 20 mantém o gate: a fase 3 não é ativada sem dono nomeado.

---

## Nota posterior (F0-04/F0-06/F0-07)

O arquivo `especificacao/prazo/java/Prazo.java` citado acima **não existe mais**. O
porte que ele demonstrava virou código de produção em
`app/src/main/java/br/com/engesoftware/sgdf/matriz/`, junto com a materialização
do cap. 7.1, e as duas suítes normativas (32 + 18 casos) passaram a ser lidas
diretamente por `TestesDeMatriz`, que roda em toda execução de
`scripts/testar-app.sh`.

Manter as duas versões seria manter duas implementações da mesma regra — e a
avulsa não rodava em suíte automatizada nenhuma, só sob `verificar.py --comando`
executado à mão. O texto acima fica como está: é o registro da decisão na época
em que foi tomada, e a evidência que ele cita foi verificada.
