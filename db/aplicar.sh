#!/usr/bin/env bash
# Aplica migrations, carga inicial e testes de aceite do SGDF.
#
#   ./db/aplicar.sh                      # usa as variáveis PG* do ambiente
#   PGDATABASE=sgdf ./db/aplicar.sh
#   ./db/aplicar.sh --sem-carga          # só o esquema
#
# Provisório: substituir por Flyway/Liquibase quando a pendência A13 (stack)
# for decidida. A ordem e o conteúdo dos arquivos não mudam com essa troca.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COM_CARGA=1
[[ "${1:-}" == "--sem-carga" ]] && COM_CARGA=0

executar() {
    echo "→ $(basename "$1")"
    psql -v ON_ERROR_STOP=1 -q -f "$1"
}

for migracao in "$RAIZ"/db/migracoes/V*__*.sql; do
    [[ "$migracao" == *.rollback.sql ]] && continue
    executar "$migracao"
done

if [[ $COM_CARGA -eq 1 ]]; then
    for seed in "$RAIZ"/db/seed/V*__*.sql; do
        executar "$seed"
    done
fi

echo "→ testes de aceite"
saida="$(psql -v ON_ERROR_STOP=1 -f "$RAIZ/db/testes/T001__criterios_de_aceite.sql" 2>&1)" || {
    echo "$saida" | grep -E 'PASSOU|FALHOU|ERROR' || echo "$saida"
    echo "FALHA: os testes de aceite não passaram." >&2
    exit 1
}
echo "$saida" | grep -E 'PASSOU|FALHOU'
echo "$saida" | grep -cE 'PASSOU' | xargs -I{} echo "{} testes passaram."

echo "pronto."
