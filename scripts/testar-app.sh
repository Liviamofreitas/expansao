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

echo "→ matriz: prazo e materialização (F0-04, F0-06, F0-07)"
java -Dfile.encoding=UTF-8 -Dsgdf.raiz="$RAIZ" -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.matriz.TestesDeMatriz | filtrar

echo "→ rascunho e publicação da matriz (F0-05)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.matriz.TestesDeRascunho | filtrar

echo "→ cadastro (F0-02, F0-03)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDeCadastro | filtrar

echo "→ exceção com SoD (F0-09)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDeExcecao | filtrar

echo "→ coleta (F1-01)"
java -cp "$ALVO:$CP" br.com.engesoftware.sgdf.coleta.TestesDeColeta | filtrar

echo "→ organização e conflitos (F1-10)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDeOrganizacao | filtrar

echo "→ extração (F1-02)"
java -cp "$ALVO:$CP" br.com.engesoftware.sgdf.extracao.TestesDeExtracao | filtrar

echo "→ leitura de tabela"
java -cp "$ALVO:$CP" br.com.engesoftware.sgdf.extracao.TestesDeTabela | filtrar

echo "→ leitores de documento (A18)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" br.com.engesoftware.sgdf.documento.TestesDeDocumento | filtrar

echo "→ validações unitárias (V8)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" br.com.engesoftware.sgdf.validacao.TestesDeValidacao | filtrar

echo "→ privacidade (F2-06, F2-07)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.privacidade.TestesDePrivacidade | filtrar

echo "→ book (F1-08)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" br.com.engesoftware.sgdf.book.TestesDeBook | filtrar

# A persistência precisa de banco. Sem SGDF_JDBC os testes se auto-pulam e
# dizem isso — silêncio seria pior, porque esconderia que a rede não rodou.
echo "→ persistência"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDePersistencia | filtrar

echo "→ derivação de eventos (F2-02)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.folha.TestesDeEventos | filtrar

echo "→ conciliação (fase 1b)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.conciliacao.TestesDeConciliacao | filtrar

echo "→ autorização (F0-01)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.seguranca.TestesDeAutorizacao | filtrar

echo "→ triagem (F1-06)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.triagem.TestesDeTriagem | filtrar

echo "→ notificação (F1-09)"
java -Dfile.encoding=UTF-8 -Dsgdf.raiz="$RAIZ" -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.notificacao.TestesDeNotificacao | filtrar

echo "→ fronteira HTTP (F0-01, F1-07, F1-10)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" br.com.engesoftware.sgdf.web.TestesDeWeb | filtrar

echo "→ classificação (F1-03)"
java -Dfile.encoding=UTF-8 -Dsgdf.raiz="$RAIZ" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.classificacao.TestesDeClassificacao | filtrar
