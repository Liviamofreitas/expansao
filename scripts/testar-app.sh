#!/usr/bin/env bash
# Compila e roda os testes do módulo Java.
#
# Provisório: usa o classpath resolvido pelo Maven mas roda os executores
# próprios em vez de surefire, porque JUnit ainda não foi adotado (ver pom.xml).
# Quando for, isto vira `mvn test` e os casos migram sem mudar o que verificam.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ALVO="${TMPDIR:-/tmp}/sgdf-classes"
CP_ARQUIVO="${TMPDIR:-/tmp}/sgdf-cp.txt"

if [[ ! -f "$CP_ARQUIVO" || "${RAIZ}/app/pom.xml" -nt "$CP_ARQUIVO" ]]; then
    echo "→ resolvendo dependências"
    (cd "$RAIZ/app" && mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_ARQUIVO")
fi
CP="$(cat "$CP_ARQUIVO")"

rm -rf "$ALVO"
mkdir -p "$ALVO"

echo "→ compilando"
javac -Xlint:all -encoding UTF-8 -cp "$CP" -d "$ALVO" $(find "$RAIZ/app/src" -name '*.java')

# PDFBox emite avisos de cache de fonte na primeira execução; são ruído de
# ambiente, não do código, e não devem esconder o resultado dos testes.
filtrar() { grep -viE 'picked up|^(WARNING|INFO|SEVERE):|^[A-Z][a-z]{2} [0-9]{2}, [0-9]{4}'; }

echo "→ coleta (F1-01)"
java -cp "$ALVO:$CP" br.com.engesoftware.sgdf.coleta.TestesDeColeta | filtrar

echo "→ extração (F1-02)"
java -cp "$ALVO:$CP" br.com.engesoftware.sgdf.extracao.TestesDeExtracao | filtrar

echo "→ leitura de tabela"
java -cp "$ALVO:$CP" br.com.engesoftware.sgdf.extracao.TestesDeTabela | filtrar

echo "→ leitores de documento (A18)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" br.com.engesoftware.sgdf.documento.TestesDeDocumento | filtrar

echo "→ validações unitárias (V8)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" br.com.engesoftware.sgdf.validacao.TestesDeValidacao | filtrar

echo "→ book (F1-08)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" br.com.engesoftware.sgdf.book.TestesDeBook | filtrar

# A persistência precisa de banco. Sem SGDF_JDBC os testes se auto-pulam e
# dizem isso — silêncio seria pior, porque esconderia que a rede não rodou.
echo "→ persistência"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDePersistencia | filtrar

echo "→ conciliação (fase 1b)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.conciliacao.TestesDeConciliacao | filtrar

echo "→ classificação (F1-03)"
java -Dfile.encoding=UTF-8 -Dsgdf.raiz="$RAIZ" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.classificacao.TestesDeClassificacao | filtrar
