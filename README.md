# SGDF — Sistema de Gestão Documental do Faturamento

Automatiza a conferência documental do faturamento por medição na **Engesoftware Tecnologia S.A.**: a cada competência, para cada contrato-serviço, o sistema materializa a lista de documentos exigidos, varre os repositórios existentes, identifica cada arquivo **pelo conteúdo**, valida, concilia documentos com seus comprovantes, publica o conjunto aprovado como *book* de faturamento em repositório imutável e comunica as áreas — cobrando o que falta ou confirmando o fechamento.

> **Estado:** documentação V1 consolidada, base para início do desenvolvimento.
> **Fases 0 e 1a liberadas.** Fase 1b condicionada à pendência **A05** (sistema de folha e layout).
> **Classificação da informação:** Interno — Restrito.

---

## O que há neste repositório

| Caminho | Conteúdo |
|---|---|
| [`docs/SGDF_Documentacao_Desenvolvimento_V1.docx`](docs/SGDF_Documentacao_Desenvolvimento_V1.docx) | **Documento normativo.** 23 capítulos. É a fonte oficial. |
| [`docs/SGDF_Documentacao_Desenvolvimento_V1.md`](docs/SGDF_Documentacao_Desenvolvimento_V1.md) | Conversão fiel do `.docx` para Markdown — leitura no navegador, busca e **diff entre versões**. Cópia de conveniência: em caso de divergência, o `.docx` prevalece. |
| [`docs/anexos/Anexo1_Diagnostico_Checklist_Faturamento.xlsx`](docs/anexos/Anexo1_Diagnostico_Checklist_Faturamento.xlsx) | **Anexo 1** — catálogo canônico (51 tipos), matriz normalizada (176 exigências), divergências e campos a criar. Fonte da carga inicial. |
| [`docs/anexos/Anexo2_SGDF_Layout_Prototipo.html`](docs/anexos/Anexo2_SGDF_Layout_Prototipo.html) | **Anexo 2** — protótipo navegável das 5 telas e tokens de design da identidade Engesoftware. Abrir direto no navegador. |
| [`docs/ERRATA-V1.md`](docs/ERRATA-V1.md) | **Ler antes de codificar.** Quatro inconsistências verificadas entre o documento e o Anexo 1, uma delas crítica. |
| [`dados/*.csv`](dados/) | As 8 abas do Anexo 1 exportadas em CSV UTF-8 — insumo direto da *migration* de carga inicial e revisável em *code review*. Gerados a partir do `.xlsx`; **não editar à mão**. |
| [`dados/complemento_tipo_documental.csv`](dados/complemento_tipo_documental.csv) | `escopo`, `sigilo`, `formatos` e `condicional_grupo` — campos que o modelo exige e o Anexo 1 não carrega (achado E-05). **Este é editável à mão**, e é onde a área demandante confirma os valores. |
| [`db/`](db/) | Esquema, carga inicial e testes de aceite. Ver abaixo. |
| [`tools/`](tools/) | Geradores. Nada aqui é executado em produção. |
| [`especificacao/`](especificacao/) | Suítes de conformidade **normativas** + implementações de referência: [`prazo/`](especificacao/prazo/) e [`materializacao/`](especificacao/materializacao/). Ver abaixo. |

---

## Banco de dados

O esquema é a única camada da fase 0 que **não depende da pendência A13** (stack), por isso é por onde o desenvolvimento começou. PostgreSQL 16.

```
db/
├── aplicar.sh                              executa tudo, na ordem, e falha se um teste falhar
├── migracoes/
│   ├── V001__esquema_inicial.sql           as 23 tabelas do cap. 5
│   ├── V002__auditoria_append_only.sql     F0-08 e SEC-03
│   └── *.rollback.sql                      reversão (cap. 17: migrations reversíveis)
├── seed/
│   └── V100__carga_inicial.sql             GERADO — não editar à mão
└── testes/
    └── T001__criterios_de_aceite.sql       16 testes dos critérios verificáveis no banco
```

**Aplicar:**

```bash
createdb sgdf && PGDATABASE=sgdf ./db/aplicar.sh
```

O script é seguro para rodar num banco vazio ou num já semeado; os testes rodam em transação e revertem, sem deixar massa.

**Regenerar a carga inicial** (após editar o Anexo 1 ou o complemento):

```bash
python3 tools/exportar_anexo1.py       # xlsx -> dados/*.csv
python3 tools/gerar_carga_inicial.py   # dados/*.csv -> db/seed/V100__carga_inicial.sql
```

### O que a carga inicial traz

| | |
|---|---|
| Modalidades | 4 (`OUTSOURCING`, `SUSTENTACAO`, `ENTREGA`, `MISTO`) |
| Clientes | 8, **inativos**, com CNPJ marcador — o Anexo 1 não carrega CNPJ e nenhum foi inventado |
| Tipos documentais | 51, completos |
| Aliases legados | 59 carregados; 1 emitido comentado por colisão (achado E-02) |
| Versão da matriz | 1.0 |

### O que a carga inicial deliberadamente **não** traz

| Tabela | Por quê |
|---|---|
| `contrato_servico` | A coluna `CONTRATO` do Anexo 1 é **cliente**, não contrato-serviço (achado E-04). Os 15 contratos-serviço de D-02 dependem de **A03** e **A04**. |
| `regra_exigibilidade` | Depende de `contrato_servico` para as regras de alvo `CONTRATO`. |
| `regra_conciliacao` | Os códigos `R01..R12` têm semânticas conflitantes entre o cap. 9 e o Anexo 1 (achado **E-01**). Semear uma das duas seria escolher por suposição. |

Essas três ausências são a tradução, em código, da regra de leitura: o que depende de decisão pendente fica visível e vazio, não preenchido por palpite.

### Controles impostos no banco, não só no serviço

| Controle | Referência | Como |
|---|---|---|
| Trilha de auditoria append-only | F0-08, SEC-03 | `REVOKE UPDATE/DELETE` **e** `RULE ... DO INSTEAD NOTHING` — a permissão sozinha seria reaberta por um `GRANT` posterior |
| `book` e `versao_matriz` append-only | cap. 10, 5.1 | `REVOKE` na role da aplicação |
| Segregação de funções na exceção | F0-09 | `CHECK (aprovador <> solicitante)` |
| Regra sem tolerância não bloqueia | cap. 9 | `CHECK (tolerancia IS NOT NULL OR modo = 'ALERTA')` |
| Alias aponta para um único tipo | achado E-02 | `UNIQUE (texto_normalizado)` **global** |
| Máx. 1 notificação por área/ciclo/dia | cap. 11.2 | `UNIQUE (ciclo_id, tipo, destinatario, data_referencia)` |
| CPF nunca em claro | SEC-02 | `cpf_cifrado bytea` + `cpf_hash` (HMAC, não hash simples — o espaço de CPF é enumerável) |

Os 16 testes em `T001` verificam cada um deles tentando violá-los.

---

## Prazo estruturado (história F0-06)

O prazo do cap. 7.3 não é um número: é `{ancora, tipo_dia, offset}`. O cálculo é a função mais sensível do sistema — erra em um dia e a régua de cobrança inteira desloca.

Como a stack ainda depende de **A13**, a entrega é uma **suíte de conformidade agnóstica de linguagem**, não uma implementação de produção:

```
especificacao/prazo/
├── casos.json      NORMATIVO — 27 casos com entrada, saída esperada e o porquê
├── referencia.py   implementação de referência (o oráculo, não código de produção)
└── verificar.py    executa a suíte
```

```bash
python3 especificacao/prazo/verificar.py            # verifica a de referência
python3 especificacao/prazo/verificar.py --comando './minha-impl'
```

Quando A13 for decidida, a implementação definitiva é considerada pronta quando passa nestes 27 casos. Basta expor um executável que leia um caso em JSON no stdin e escreva `{"data": ..., "avisos": [...]}` ou `{"erro": "CODIGO"}` no stdout.

**O critério de aceite da F0-06** — *"'5º dia útil' de 04/2026 calculado corretamente com feriado cadastrado"* — é o caso `F0-06-01`: a Sexta-Feira Santa cai em 03/04/2026 e desloca o resultado de 07 para **08/04/2026**. O caso `F0-06-02` é o controle sem feriado, que prova que o calendário está de fato sendo consultado.

### Três decisões que o cap. 7.3 não fecha

A suíte não existe só para testar: ela **fixa** as decisões que o documento deixou em aberto, de forma mensurável.

| Achado | Questão | Como está resolvido | Caso na suíte |
|---|---|---|---|
| **E-06** | `offset` é ordinal ("dia 21") ou aditivo ("D+3")? Os dois exemplos do documento exigem regras diferentes | Ordinal para `INICIO_COMPETENCIA`, aditivo para âncoras de evento — a única leitura que satisfaz ambos | `DOC-01`, `DOC-02` |
| **E-07** | Carnaval e Corpus Christi são ponto facultativo, não feriado. Contam como dia útil? | Entram marcados `[FACULTATIVO]`, removíveis por cadastro | `FACULTATIVO-01/02` |
| **E-08** | 8 linhas da matriz pedem o dia 30 ou 31; fevereiro não tem | Ajusta para o último dia do período e devolve `AJUSTE_FIM_DE_PERIODO`. Travar violaria o princípio 1 do cap. 1 | `E-08-01` a `E-08-04` |

### Calendário

`db/seed/V101__calendario_feriados.sql` traz 120 linhas (2025–2032): nacionais fixos, móveis derivados da Páscoa e estaduais de CE e RS. **Feriados municipais não estão lá** — não constam de nenhuma fonte do pacote e precisam ser cadastrados antes de qualquer prazo em dia útil valer para contratos cujo município tenha feriado local.

---

## Abertura de ciclo (história F0-07)

O cap. 7.1 é decomposto em duas partes, e a separação não é estética:

1. **Resolver** (função pura) — dado contrato, competência, matriz, tipos e alocações, decidir *quais* exigências existem e com que prazo. Toda a regra de negócio vive aqui, testável sem banco, sem relógio e sem fila.
2. **Persistir** (efeito) — gravar o resultado e congelar a versão da matriz no ciclo. Mecânico.

```
especificacao/materializacao/     18 casos — a resolução
db/testes/T002__abertura_de_ciclo.sql   11 testes — a persistência
```

```bash
python3 especificacao/materializacao/verificar.py
```

**O critério de aceite** — *"No 1º dia útil, 15 ciclos abertos; exigência corporativa única compartilhada"* — está partido entre os dois: a contagem e o compartilhamento em `T002`, a decisão de quais exigências existem na suíte.

### A exigência corporativa (achado E-09)

O cap. 5.2 modela `exigencia.ciclo_id`; o cap. 7.1 diz que a corporativa é "compartilhada entre ciclos, satisfeita uma única vez". Uma linha não faz as duas coisas.

A `V004` resolve dando a `exigencia` **dois modos de endereçamento mutuamente exclusivos** — ou um ciclo, ou `(empresa, competência)` — e uma view que reúne os dois:

```sql
SELECT * FROM exigencia_do_ciclo WHERE ciclo_id = ...;
--   procedencia = 'PROPRIA'      as do próprio ciclo
--   procedencia = 'CORPORATIVA'  as do CNPJ, na mesma competência
```

A migration também cria `empresa` — o CNPJ do **prestador**, que o cadastro não tinha e que é o titular de tudo no bloco corporativo. Nasce vazia: nenhum CNPJ é inventado.

### Duas decisões que o cap. 7.1 não fecha (achado E-10)

| Questão | Adotado | Por quê |
|---|---|---|
| Prazo da corporativa compartilhada, quando os contratos pedem prazos diferentes | O **menor** | Ela precisa estar lá quando o primeiro ciclo precisa dela |
| O que é "alocado ativo na competência" | **Interseção** — um dia de sobreposição basta | Quem saiu no dia 3 tem contracheque, encargos e rescisão a comprovar. É o caso que a responsabilidade subsidiária alcança |

---

## Ordem de leitura sugerida

1. **README** (este arquivo) — mapa do pacote.
2. **`docs/ERRATA-V1.md`** — o que está inconsistente e ainda não foi decidido.
3. **Documentação, caps. 1–3** — escopo, glossário e as 14 decisões/premissas com efeito direto no código (D-01 a R-04).
4. **Cap. 4–6** — arquitetura de referência, modelo de dados e as máquinas de estado da exigência e do ciclo.
5. **Cap. 7–9** — o motor: exigibilidade, prazo estruturado, defasagem, reconhecimento determinístico, validações V1–V7 e conciliação.
6. **Cap. 18** — backlog, em ordem de dependência. É por onde a sprint 0 começa.
7. **Anexo 2** no navegador, junto com o cap. 12 — as telas são requisito normativo, não sugestão.

---

## Regra de leitura (vale para todo o pacote)

- Tudo marcado como **"padrão"** ou **"parâmetro"** é valor inicial ajustável em cadastro.
- Tudo marcado como **"exigido"** ou **"obrigatório"** é requisito de aceitação.
- Dúvida de interpretação se resolve **com a área demandante, antes de codificar** — nunca por suposição silenciosa.

---

## Princípios de projeto (cap. 1, em ordem de precedência)

1. O sistema **nunca trava o faturamento por falta de configuração própria**: tipo sem regra de reconhecimento cai em conferência manual; regra sem tolerância opera em modo alerta.
2. Todo comportamento variável é **metadado editável**, nunca código.
3. Toda decisão automática é **determinística, auditável e explicável** — sem classificadores estatísticos na decisão de conformidade.
4. A identificação usa o **conteúdo** do documento; nome de arquivo e pasta são apenas reforço de confiança.
5. A varredura é **somente-leitura na origem**; a escrita ocorre apenas no repositório de evidências.
6. **Bloqueio sempre explicado**: toda ação desabilitada exibe o motivo.
7. O sistema confere completude e coerência documental; **o ateste técnico da medição permanece humano**.

---

## Perfis de acesso (cap. 15.1)

`ADMIN_SISTEMA` · `CURADOR_MATRIZ` · `PUBLICADOR_AP` · `PUBLICADOR_FIN` · `GESTOR_CONTRATO` · `APROVADOR_DAF` · `AUDITORIA`, mais duas identidades de serviço com escopos disjuntos (`svc-sgdf-leitura`, `svc-sgdf-publica`).

Segregação de funções imposta no serviço: **quem solicita exceção não aprova a própria exceção**; `ADMIN_SISTEMA` configura mas não vê conteúdo de documento de escopo profissional.

---

## Antes do kickoff — o que precisa ir na frente

Estas pendências destravam o resto; as demais correm em paralelo ao desenvolvimento.

| ID | Pendência | Bloqueia | Responsável |
|---|---|---|---|
| **A03** | BNB e TJCE usam a mesma matriz nas duas modalidades? | Fase 0 — verificação prévia urgente (≈30 min de checagem; risco de responsabilidade subsidiária) | Gestão de Contratos |
| **A13** | Stack de desenvolvimento e infraestrutura alocada | Sprint 0 | TI |
| **A05** | Sistema de folha e layout de extração | Início da fase 1b (único bloqueio real) | TI e AP |
| **E-01** | Colisão da numeração R01–R12 entre documento e Anexo 1 | Escrita das histórias de conciliação | Área demandante + arquitetura |

A lista completa das 13 pendências está no cap. 23 da documentação; as inconsistências (E-01 a E-04) estão na errata.

---

## Regenerar os CSV a partir do Anexo 1


Os arquivos em `dados/` são derivados. Ao receber uma nova versão do `.xlsx`, substitua o anexo e rode:

```bash
pip install openpyxl
python3 tools/exportar_anexo1.py
```

O diff dos CSV mostra exatamente o que mudou no catálogo e na matriz entre as versões — que é o controle de mudança da carga inicial exigido pelo cap. 17 (*seed* como migration de dados auditável).
