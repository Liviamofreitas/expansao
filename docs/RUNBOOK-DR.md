# Runbook de continuidade e recuperação — SGDF

**SEC-09:** RTO 8 h / RPO 4 h fora do fechamento; runbook documentado; simulação
anual.
**SEC-08:** backups com PITR e replicação; **teste de restauração trimestral**.

> **Este runbook tem uma parte verificada e uma parte não verificada, e elas
> estão separadas de propósito.** Um runbook que não distingue as duas convida
> quem o lê a confiar no todo — e a hora de descobrir que um passo era suposição
> é a pior possível.

---

## 1. O que está verificado

`./scripts/testar-restauracao.sh` copia, restaura e **verifica as garantias**, e
foi executado com sucesso. Ele falha quando a restauração está incompleta —
verificado por quebra deliberada (§ 3).

## 2. O que NÃO está verificado

| Item | Por quê |
|---|---|
| PITR e WAL archiving | Configuração de infraestrutura; não existe neste ambiente |
| RPO de 4 h | Depende do intervalo de arquivamento de WAL, que ainda não está definido |
| RTO de 8 h ponta a ponta | Só o tempo de banco foi medido. Provisionar, reconfigurar segredos e validar não foram |
| Replicação do bucket e cópia imutável | Não há bucket provisionado |
| Restauração em **infraestrutura nova** | O teste restaura no mesmo servidor. Um desastre real não tem esse servidor |

**A simulação anual do SEC-09 é o que fecha esta coluna.** Até ela acontecer, o
item está *parcialmente* atendido, e o relatório de conformidade deve dizer isso.

---

## 3. Restaurar o dado não é restaurar o controle

É o ponto que motivou o script inteiro.

Um backup que devolve todas as linhas e um banco que sobe passa em qualquer
verificação ingênua — e pode ter perdido exatamente o que faz o sistema valer:

- as **2 RULEs** que tornam `log_auditoria` append-only (V002);
- os **REVOKE** que tiram `UPDATE`/`DELETE` da role da aplicação;
- os **34 índices parciais**, que são unicidade *condicional* e não otimização:
  `ux_excecao_aberta` é o que impede duas exceções em aberto para a mesma
  exigência;
- os **120 CHECK**, que são a última rede quando o serviço erra;
- `excecao_sod`, que impede aprovar a própria exceção.

`pg_restore --no-privileges`, um dump só de dados sobre esquema recriado à mão,
ou um template desatualizado perdem isso **sem nenhum sintoma**. O sistema sobe,
o painel abre, a trilha aceita `INSERT` — e aceita `UPDATE` também.

### O defeito que a quebra deliberada achou no próprio teste

A primeira versão de `T013` verificava só *"`sgdf_app` não pode UPDATE"*. Com
`--no-privileges` a role fica **sem privilégio nenhum** — e a asserção passa,
satisfeita pela ausência total. O teste atestava um controle sobre um banco em
que a aplicação não conseguia nem ler.

**Uma negativa satisfeita pelo vazio não prova nada.** O que prova é o par: *pode
o que deve poder* **e** *não pode o que não deve*. Corrigido, a quebra derruba
com a mensagem certa.

---

## 4. Procedimento

### 4.1 Antes (mensal, 10 min)

```bash
# O backup existe e é legível? "Existe" e "restaura" são coisas diferentes.
PGDATABASE=sgdf ./scripts/testar-restauracao.sh
```

Falhou → **é incidente**, não tarefa adiável: o backup não serve e ninguém sabia.

### 4.2 Durante o desastre

| Passo | Ação | Verificação |
|---|---|---|
| 1 | Declarar o incidente e registrar a hora | O relógio do RTO começa **aqui**, não quando alguém abre o terminal |
| 2 | Provisionar banco na infraestrutura alternativa | — |
| 3 | Restaurar o último backup íntegro + WAL até o ponto desejado | `pg_restore --exit-on-error`, **sem** `--no-privileges` e **sem** `--no-owner` |
| 4 | **Rodar `T013`** | As garantias sobreviveram? Se não, **pare**: subir assim é operar sem os controles |
| 5 | Reconfigurar segredos a partir do cofre | Nenhum segredo vem do backup — eles nunca estiveram lá |
| 6 | Subir a aplicação com `sgdf.agendador.ativo=false` | Ver 4.3 |
| 7 | Conferir `GET /api/jobs/saude` e o painel | — |
| 8 | Religar o agendador | — |
| 9 | Registrar os tempos de cada passo | É o que permite afirmar o RTO no próximo relatório |

### 4.3 O agendador sobe desligado, e isto não é zelo

Uma instância restaurada com o agendador ligado começa a varrer, abrir ciclos e
**cobrar áreas** antes de alguém ter olhado se a restauração está correta. Se o
ponto de restauração for anterior ao que a operação já tinha feito, o sistema
recobra o que já foi entregue — e a primeira coisa que a organização vê do
desastre é um e-mail errado.

A ordem é: restaurar → verificar → **olhar** → religar.

### 4.4 Depois

- Apagar as cópias: **elas contêm dado pessoal em claro** (23 dos 51 tipos
  tratam dado pessoal, 4 sensível).
- Registrar o resultado no relatório do SEC-08.
- O que falhou vira item; o que demorou mais que o previsto vira revisão do RTO.

---

## 5. O que o backup não recupera

| Item | Onde vive | Como se recupera |
|---|---|---|
| Segredos | Cofre | Cofre. **Nunca** estiveram no backup |
| Documentos originais | Bucket de evidências | Replicação do bucket (SEC-08), não o banco |
| Books publicados | Bucket, com retenção imutável | Idem — e a imutabilidade é o que impede que o desastre os leve junto |
| Pastas de origem | OwnCloud | **Não é nosso.** O SGDF só lê; a recuperação é da TI |

**O SGDF nunca escreve na origem.** É o que faz o desastre dele não ser desastre
dos documentos.

---

## 6. Cadência

| Quando | O quê | Quem |
|---|---|---|
| Mensal | `./scripts/testar-restauracao.sh` | Sustentação |
| Trimestral | Teste de restauração com relatório (SEC-08) | Sustentação + infra |
| Anual | **Simulação de desastre em infraestrutura nova** (SEC-09) | Infra + DAF |

A simulação anual é a única que exercita o passo 2 — e é o passo em que o RTO
realmente se decide.
