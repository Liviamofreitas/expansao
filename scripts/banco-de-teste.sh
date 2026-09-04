#!/usr/bin/env bash
# Sobe um PostgreSQL descartável e aplica migrations, carga e testes de banco.
#
#   ./scripts/banco-de-teste.sh              # sobe, aplica e diz o SGDF_JDBC
#   ./scripts/banco-de-teste.sh --recriar    # apaga o cluster antes
#
# Existe porque o cluster do contêiner não sobrevive entre sessões e o postgres
# recusa rodar como root — reconstruir isso de memória custa mais que o script.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BIN="$(ls -d /usr/lib/postgresql/*/bin | head -1)"
DADOS="${PGDATA_TESTE:-/tmp/pgdata}"
SOQUETE="${PGSOCK_TESTE:-/tmp/pgrun}"
PORTA="${PGPORT_TESTE:-5433}"

[[ "${1:-}" == "--recriar" ]] && rm -rf "$DADOS"

if [[ ! -s "$DADOS/PG_VERSION" ]]; then
    echo "→ criando o cluster em $DADOS"
    rm -rf "$DADOS"
    mkdir -p "$DADOS" "$SOQUETE"
    # O postgres recusa rodar como root; o contêiner já traz o usuário.
    chown postgres:postgres "$DADOS" "$SOQUETE"
    su postgres -c "$BIN/initdb -D $DADOS -U postgres --auth=trust" >/dev/null
fi

if ! su postgres -c "$BIN/pg_ctl -D $DADOS status" >/dev/null 2>&1; then
    echo "→ subindo na porta $PORTA"
    mkdir -p "$SOQUETE" && chown postgres:postgres "$SOQUETE"
    su postgres -c "$BIN/pg_ctl -D $DADOS -o '-k $SOQUETE -p $PORTA' -l $SOQUETE/pg.log -w start" \
        >/dev/null
fi

export PGHOST="$SOQUETE" PGPORT="$PORTA" PGUSER=postgres

psql -d postgres -qc "DROP DATABASE IF EXISTS sgdf WITH (FORCE)"
psql -d postgres -qc "CREATE DATABASE sgdf"
export PGDATABASE=sgdf

bash "$RAIZ/db/aplicar.sh"

echo
echo "SGDF_JDBC=jdbc:postgresql://localhost:$PORTA/sgdf?user=postgres"
