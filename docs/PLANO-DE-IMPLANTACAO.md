# Plano de implantação — SGDF

**Estado do código:** 1176 testes Java e 129 SQL, todos passando. Fases 0, 1a,
1b e 3 implementadas, exceto F3-01 (modo sombra medido), que depende de massa.

**A pergunta que organiza este plano:** *"o sistema roda em produção"* e *"o
sistema tem autoridade sobre o faturamento"* são coisas diferentes, e só a
segunda precisa da norma interna. Tratá-las como uma coisa só adia o valor
inteiro esperando uma assinatura que a maior parte dele não exige.

---

## 1. Ondas

| Onda | O que o sistema faz | O que ele **não** faz | Gate |
|---|---|---|---|
| **0 — HML** | Tudo, com dados mascarados (cap. 17) | Tocar em dado real | **Nenhum.** Liberada |
| **1 — Produção em observação** | Varre, classifica, valida, concilia, mostra o painel e o book | **Não notifica ninguém. Não bloqueia faturamento.** Nenhuma área é cobrada, nenhum ciclo é travado | **A12** (RIPD) |
| **2 — Portão documental** | `PRONTO` passa a exigir toda bloqueante em PUBLICADO/DISPENSADO | Ainda não notifica | **A11** (norma) + **A10** (sustentação) |
| **3 — Cobrança** | Régua D-2/D+0/D+2/D+5 envia e-mail | — | **RA-06** (SMTP) + **F3-01** (divergência ≤ 2% em 2 ciclos) |

**A maior parte do valor está na Onda 1.** Ver o que falta antes do fechamento e
achar divergência de valor não exige autoridade nenhuma — exige que o sistema
esteja olhando. Na Onda 1 ele informa; a decisão continua humana, como já é hoje.

### Por que a Onda 1 não precisa da norma

A norma (A11) ampara o **portão**: o sistema recusar um faturamento é ato com
efeito sobre terceiros e precisa de respaldo formal. Na Onda 1 ele não recusa
nada — produz a mesma informação que uma pessoa produziria conferindo à mão, e
ninguém precisa de norma para conferir.

### Por que a Onda 1 precisa do RIPD

Porque ela trata **CPF real**: 23 dos 51 tipos documentais tratam dado pessoal, 4
deles sensível. O inventário já está pronto (`INVENTARIO-DADOS-PESSOAIS.md`) — é
a parte factual e volumosa. O que falta é avaliação de risco, base legal por
finalidade e aprovação. **É o caminho crítico e nenhuma engenharia o encurta.**

---

## 2. Gate de cada onda

### Onda 0 — HML · **liberada**

| # | Item | Estado |
|---|---|---|
| 0.1 | Imagem e orquestração | **Escritos** (`Dockerfile`, `docker-compose.yml`) — a imagem **ainda não foi construída** em lugar nenhum |
| 0.2 | Pipeline com testes, SCA e escaneamento | **Escrito** (`.github/workflows/ci.yml`) — **nunca executou** |
| 0.3 | Massa mascarada em HML (cap. 17) | **Pendente.** HML nunca recebe dado real sem mascaramento |

> **0.1 e 0.2 são artefatos não verificados.** Não há daemon Docker no ambiente
> onde foram escritos, e o pipeline nunca rodou. A primeira execução do CI é o
> teste deles — e é razoável que ela falhe uma ou duas vezes.

### Onda 1 — Produção em observação

| # | Item | Quem | Obrigatoriedade |
|---|---|---|---|
| 1.1 | **RIPD aprovado (A12)** | DPO + jurídico | **Obrigatório** — LGPD |
| 1.2 | Cofre provisionado e credenciais rotacionadas | Infra | **Obrigatório** — cap. 15 |
| 1.3 | Conta `svc-sgdf-leitura` criada, somente-leitura, escopo na pasta do contrato | Infra + TI | **Obrigatório** — cap. 14.1 |
| 1.4 | Grupos do IdP mapeados aos 7 papéis; MFA nos que veem escopo profissional | TI | **Obrigatório** — cap. 14.4 |
| 1.5 | Banco com PITR e teste de restauração (SEC-08) | Infra | **Obrigatório** |
| 1.6 | Bucket de evidências com versionamento e cópia imutável | Infra | **Obrigatório** — cap. 11.2 |
| 1.7 | ~~Rate limiting e lockout na API (SEC-06)~~ | ~~Engenharia~~ | **Construído** — V019 + `FiltroDeLimite`, achados § 49 |
| 1.8 | Runbook de operação e de DR (SEC-09) | Engenharia + infra | **Obrigatório** |
| 1.9 | Retenção e expurgo agendado (A08 / LGPD-02) | Engenharia + DPO | Desejável na Onda 1, obrigatório antes da Onda 3 |
| 1.10 | Fundamento contratual por exigência (A06) | Gestão de contratos | Desejável — sem ele a exigência não se defende perante o cliente |

### Onda 2 — Portão documental

| # | Item | Quem |
|---|---|---|
| 2.1 | **Norma interna publicada (A11)**, com a AP no RACI | Governança |
| 2.2 | **Time de sustentação nomeado (A10)** | Gestão |
| 2.3 | Tolerâncias das 11 regras de conciliação cadastradas | AP |
| 2.4 | Checklists de SEFAZ-CE, CGM-CE e SEBRAE GO (A01) | Gestão de contratos |

### Onda 3 — Cobrança automática

| # | Item | Quem |
|---|---|---|
| 3.1 | Relay SMTP com remetente dedicado, SPF/DKIM (RA-06) | Infra |
| 3.2 | **F3-01:** divergência ≤ 2% por **dois ciclos consecutivos** | Engenharia + conferência manual |
| 3.3 | Destinatários por (contrato × família) cadastrados, com substituto | AP + gestão |
| 3.4 | Adesão explícita por contrato (F3-02) | DAF, contrato a contrato |

> **F3-01 é gate duro e não é formalidade.** Um falso negativo — o sistema diz
> completo e havia pendência — em exigência bloqueante impede a ativação
> *independentemente do percentual* (cap. 19). Metade da medição já está feita:
> precisão de classificação em **100%** no bloco corporativo (achados § 42).

---

## 3. Sequência recomendada

```
hoje        ├─ pedir o RIPD ao DPO ...................... caminho crítico
            ├─ provisionar cofre, conta de serviço, IdP
            └─ rodar o CI pela primeira vez e corrigir

+1 semana   ├─ HML de pé com massa mascarada
            └─ construir SEC-06 (rate limiting) e o runbook

+2 semanas  ├─ carga dos 12 contratos em produção
            └─ abrir a primeira competência em OBSERVAÇÃO

+2 ciclos   └─ medir F3-01 contra a conferência manual

depois      └─ Ondas 2 e 3, na ordem dos gates
```

**O que trava, se travar, é o RIPD.** Tudo o mais corre em paralelo.

---

## 4. Rollback

O desenho torna o retorno barato, e vale dizer por quê.

| Onda | Como se volta | Custo |
|---|---|---|
| 1 | Desligar a aplicação. O banco e o bucket ficam; nada foi alterado na origem | **Nenhum dado se perde.** O SGDF só **lê** o OwnCloud (conta somente-leitura) |
| 2 | Reverter o ciclo de PRONTO por transição registrada (cap. 6.2) | Trilha guarda quem, quando e por quê |
| 3 | `POST /notificacao/contratos/{id}/desativar` — **exige só ver o painel** | Imediato. A porta de saída é deliberadamente mais larga que a de entrada (achados § 44.4) |

**O sistema nunca escreve na origem.** Essa é a propriedade que faz o rollback da
Onda 1 ser desligar um processo.

---

## 5. Primeiros 30 dias — o que observar

| Sinal | Onde | O que significa |
|---|---|---|
| `GET /api/jobs/saude` com job silencioso | Painel | **O agendador parou.** É o único sintoma: todos os outros se parecem com saúde (achados § 46) |
| Classificados sem exigência | Painel do ciclo | Matriz incompleta, ou documento a mais (achados § 47) |
| Exceções aprovadas subindo | Indicador KRI | Controle sendo contornado |
| Precisão de triagem < 90% | Indicador | Regras de reconhecimento desatualizadas |
| Documentos em `achado_de_organizacao` | Painel de organização | Cópias de conflito na origem — risco de o book sair com versão errada |

**Não observe "o painel está verde".** Um painel verde e um sistema parado são
indistinguíveis sem a saúde dos jobs. Foi por isso que ela existe.

---

## 6. O que este plano não resolve

| Pendência | Por quê |
|---|---|
| **RA-16** — a recertificação cobre acesso *observado*; conta que nunca entrou não aparece | Exige leitura do diretório (SCIM/LDAP). Não se resolve no SGDF |
| **RA-08** — a SoD compara strings de identidade | Exige identificador estável e único por pessoa no IdP |
| **RA-13** — V1 descarta arquivo sem extensão | Afrouxar validação de segurança é decisão da DAF, não de engenharia |
| **A06** — fundamento contratual | Leitura dos contratos, que não estão no pacote |

Todas estão em `PENDENCIAS.md` com contenção e responsável.
