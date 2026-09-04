#!/usr/bin/env bash
# Compila e roda os testes do módulo Java sem depender de Maven.
#
# Provisório: enquanto o espelho Maven interno não existir (D-06, ambiente sem
# internet), este script é o que roda no CI. Quando existir, vira `mvn test` e
# os casos migram para JUnit sem mudar o que verificam.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ALVO="${TMPDIR:-/tmp}/sgdf-classes"

rm -rf "$ALVO"
mkdir -p "$ALVO"

echo "→ compilando"
javac -Xlint:all -d "$ALVO" $(find "$RAIZ/app/src" -name '*.java')

echo "→ testes de coleta (F1-01)"
java -cp "$ALVO" br.com.engesoftware.sgdf.coleta.TestesDeColeta
