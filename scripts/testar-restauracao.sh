#!/usr/bin/env bash
# Teste de restauração — SEC-08 (trimestral) e SEC-09 (simulação anual).
#
#   ./scripts/testar-restauracao.sh                 # usa as variáveis PG* do ambiente
#   PGDATABASE=sgdf ./scripts/testar-restauracao.sh
#
# POR QUE ESTE SCRIPT EXISTE, E NÃO SÓ O RUNBOOK.
#
# Um runbook que nunca foi executado é ficção: ele descreve o que alguém acredita
# que aconteceria. E um teste de restauração que verifica "o banco subiu" atesta
# a metade que não importa — restaurar o DADO não é restaurar o CONTROLE.
#
# As RULEs de append-only, os REVOKE, os 34 índices parciais e os 120 CHECK não
# são dados: são o que faz o sistema valer. Um `pg_restore --no-privileges`, um
# dump só de dados sobre esquema recriado à mão, ou um template desatualizado
# perdem os três SEM NENHUM SINTOMA. O banco sobe, o painel abre, a trilha aceita
# INSERT — e aceita UPDATE também.
#
# Este script restaura de verdade, cronometra, e roda T013. Se as garantias não
# sobreviverem, ele SAI COM ERRO — um teste de restauração que passa sobre uma
# restauração incompleta é pior que nenhum.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ORIGEM="${PGDATABASE:-sgdf}"
ALVO="${SGDF_DB_RESTAURACAO:-sgdf_restaurado}"
DUMP="${TMPDIR:-/tmp}/sgdf-dr-$(date +%Y%m%d-%H%M%S).dump"

echo "=== Teste de restauração — SEC-08 / SEC-09 ==="
echo "origem: $ORIGEM   alvo: $ALVO"
echo

# --- 1. Cópia ----------------------------------------------------------------
# -Fc (custom) e NÃO texto puro: o formato custom preserva privilégios e objetos
# que o plain perde com facilidade, e permite restaurar em paralelo.
echo "→ 1/4 copiando ($ORIGEM)"
t0=$(date +%s)
pg_dump -Fc -d "$ORIGEM" -f "$DUMP"
t1=$(date +%s)
echo "   $(du -h "$DUMP" | cut -f1) em $((t1 - t0))s"

# --- 2. Restauração ----------------------------------------------------------
echo "→ 2/4 restaurando em $ALVO"
psql -d postgres -qc "DROP DATABASE IF EXISTS $ALVO WITH (FORCE)"
psql -d postgres -qc "CREATE DATABASE $ALVO"
# SEM --no-privileges e SEM --no-owner: são exatamente as duas opções que fazem
# a restauração perder os REVOKE do V002 sem dizer nada.
pg_restore -d "$ALVO" --exit-on-error "$DUMP"
t2=$(date +%s)
echo "   restaurado em $((t2 - t1))s"

# --- 3. As garantias sobreviveram? -------------------------------------------
echo "→ 3/4 verificando as GARANTIAS, não os dados"
PGDATABASE="$ALVO" psql -v ON_ERROR_STOP=1 -q \
    -f "$RAIZ/db/testes/T013__garantias_apos_restauracao.sql" 2>&1 \
    | grep -E 'PASSOU|FALHOU' | sed 's/^psql.*NOTICE:  //' | sed 's/^/   /'

# --- 4. O dado bate? ---------------------------------------------------------
echo "→ 4/4 conferindo a contagem das tabelas críticas"
for tabela in log_auditoria ciclo exigencia documento book; do
    a=$(psql -d "$ORIGEM" -qtAc "SELECT count(*) FROM $tabela")
    b=$(psql -d "$ALVO"   -qtAc "SELECT count(*) FROM $tabela")
    if [[ "$a" != "$b" ]]; then
        echo "   FALHOU: $tabela tem $a na origem e $b no restaurado" >&2
        exit 1
    fi
    printf '   ok  %-14s %s linha(s)\n' "$tabela" "$a"
done
t3=$(date +%s)

echo
echo "=== Resultado ==="
echo "Tempo total: $((t3 - t0))s  (cópia $((t1 - t0))s, restauração $((t2 - t1))s,"
echo "             verificação $((t3 - t2))s)"
echo
# O RTO do SEC-09 é de 8 h e inclui provisionar infraestrutura, restaurar,
# reconfigurar segredos e validar. Só o tempo de banco é uma FRAÇÃO dele — dizer
# que o RTO está atendido porque a restauração levou 40 s seria medir a parte
# fácil e declarar o todo.
echo "SEC-09: o RTO de 8 h cobre provisionar, restaurar, reconfigurar segredos e"
echo "        validar. O número acima é SÓ o banco — registre-o no relatório da"
echo "        simulação junto com os outros tempos, não no lugar deles."
echo
echo "Cópia guardada em: $DUMP"
echo "Remova-a quando terminar: ela contém dado pessoal em claro."
