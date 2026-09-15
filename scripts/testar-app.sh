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

# =============================================================================
# "FALHOU" E "NÃO RODOU" TÊM A MESMA COR, E ISSO JÁ CUSTOU TEMPO DUAS VEZES.
#
# O cluster de teste caiu no meio da execução em duas ocasiões. O `set -e`
# abortou corretamente — e a saída ficou parecendo uma suíte que falhou, porque
# o resumo some junto. Quem lê "0 asserções" conclui que nada passou; a verdade
# era que nada tinha rodado.
#
# É o mesmo defeito que o passo Veredito do CI já corrigiu num nível acima:
# ausência de resultado não é resultado. Aqui embaixo, a mesma distinção.
# =============================================================================
SAIDA="$(mktemp -t sgdf-suite.XXXXXX)"
exec > >(tee "$SAIDA") 2>&1

NAO_RODOU=0

ao_terminar() {
    local codigo=$?
    [[ $codigo -eq 0 ]] && { totalizar; rm -f "$SAIDA"; return 0; }

    # O pré-voo já disse o que houve, com a instrução do que fazer. Repetir o
    # diagnóstico aqui produziria DOIS veredictos para o mesmo fato — e o
    # segundo, por não ter contexto, chamaria de "asserção falsa" o que era
    # "não rodou". Foi o que aconteceu na primeira versão deste trap.
    [[ $NAO_RODOU -eq 1 ]] && { rm -f "$SAIDA"; return $codigo; }

    echo
    echo "======================================================================"
    if grep -qE "Connection (refused|to .* refused)|could not connect" "$SAIDA"; then
        echo "A SUÍTE NÃO RODOU — o PostgreSQL de teste não respondeu."
        echo
        echo "ISTO NÃO É UMA FALHA DE TESTE. Nenhuma asserção foi avaliada a"
        echo "partir do ponto em que a conexão caiu; o que veio antes não diz"
        echo "nada sobre o que veio depois."
        echo
        echo "    ./scripts/banco-de-teste.sh     # sobe e aplica o esquema"
    elif grep -q "Exception in thread" "$SAIDA"; then
        echo "A SUÍTE INTERROMPEU com exceção — não chegou ao fim."
        echo
        grep -m3 -A2 "Exception in thread" "$SAIDA" | sed 's/^/    /'
    else
        echo "A SUÍTE REPROVOU: há asserção falsa acima."
        grep -m10 -E "^\s+FALHA" "$SAIDA" | sed 's/^/    /'
    fi
    echo "======================================================================"
    rm -f "$SAIDA"
    return $codigo
}

# O TOTAL, PORQUE CONTAR À MÃO É CONVITE A CONTAR ERRADO.
#
# Cada executor imprime o seu "N/N". Sem a soma, saber se a suíte cresceu ou
# encolheu entre dois commits exige somar vinte linhas na cabeça — e um número
# que ninguém confere é um número que ninguém usa.
totalizar() {
    # `match(...)` com array é extensão do GNU awk e este ambiente não a tem —
    # o erro foi "syntax error at or near ,". A forma abaixo é POSIX e roda em
    # qualquer awk.
    awk '
        /testes passaram/ {
            for (i = 1; i <= NF; i++) {
                if ($i ~ /^[0-9]+\/[0-9]+$/) {
                    split($i, a, "/"); passaram += a[1]; total += a[2]
                }
            }
        }
        END {
            printf "\n=== TOTAL: %d/%d asserções ===\n", passaram, total
            if (passaram != total) { exit 1 }
        }
    ' "$SAIDA"
}

trap ao_terminar EXIT

# PRÉ-VOO: o banco responde ANTES de compilar?
#
# Sem isto, a primeira suíte que precisa de banco morre com um stack trace no
# meio de vinte outras — e a causa fica enterrada. Falhar aqui custa dois
# segundos e aponta para o lugar certo.
if [[ -n "${SGDF_JDBC:-}" ]]; then
    hp="$(sed -E 's|^jdbc:postgresql://([^/?]+).*|\1|' <<< "$SGDF_JDBC")"
    hospedeiro="${hp%%:*}"
    porta="${hp##*:}"
    [[ "$porta" == "$hospedeiro" ]] && porta=5432
    if ! (exec 3<>"/dev/tcp/$hospedeiro/$porta") 2>/dev/null; then
        echo "O BANCO DE TESTE NÃO RESPONDE em $hospedeiro:$porta."
        echo "A suíte NÃO vai rodar — 129 das asserções precisam de banco, e"
        echo "rodar só o resto daria um verde que não significa nada."
        echo
        echo "    ./scripts/banco-de-teste.sh"
        NAO_RODOU=1
        exit 1
    fi
fi

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
filtrar() { grep -viE 'picked up|^(WARNING|INFO|SEVERE):|^[A-Z][a-z]{2} [0-9]{2}, [0-9]{4}|PDSimpleFont|commons-logging|\] WARN '; }

# A FIAÇÃO ANTES DE TUDO: custa milissegundos, não precisa de banco, e pega o
# defeito que já derrubou a aplicação três vezes nesta base. Se ela quebrou,
# nada do que vier depois importa — a aplicação não sobe.
echo "→ fiação do Spring (bean com proxy não pode ser de classe final)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.web.TestesDeFiacao | filtrar

echo "→ matriz: prazo e materialização (F0-04, F0-06, F0-07)"
java -Dfile.encoding=UTF-8 -Dsgdf.raiz="$RAIZ" -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.matriz.TestesDeMatriz | filtrar

echo "→ rascunho e publicação da matriz (F0-05)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.matriz.TestesDeRascunho | filtrar

echo "→ cadastro (F0-02, F0-03)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDeCadastro | filtrar

echo "→ ciclo e indicador D+3 (F3-03, F3-04)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.ciclo.TestesDeCiclo | filtrar

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

echo "→ completude por equipe (F2-03)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDeCompletude | filtrar

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

echo "→ abertura de ciclos (F0-07)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDeAbertura | filtrar

echo "→ limite de uso e bloqueio (SEC-06)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.limite.TestesDeLimite | filtrar

echo "→ ingestão ponta a ponta até o banco (RA-07)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDeIngestao | filtrar

echo "→ orquestração de jobs (RA-07)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.persistencia.TestesDeAgendador | filtrar

echo "→ retenção por temporalidade e expurgo (A08, LGPD-02)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.retencao.TestesDeExpurgo | filtrar

echo "→ recertificação de acessos (F3-06, SEC-10)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.seguranca.TestesDeRecertificacao | filtrar

echo "→ ativação por contrato (F3-02)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.notificacao.TestesDeAtivacao | filtrar

echo "→ indicadores e exportações (F3-05, cap. 21)"
java -Dfile.encoding=UTF-8 -Dsgdf.jdbc="${SGDF_JDBC:-}" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.indicadores.TestesDeIndicadores | filtrar

echo "→ pipeline ponta a ponta (F3-01, metade)"
java -Dfile.encoding=UTF-8 -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.pipeline.TestesDePipeline | filtrar

# A MASSA REAL NÃO VIVE NO REPOSITÓRIO (cap. 19). Sem SGDF_MASSA a medição se
# pula e diz isso — silêncio esconderia que a precisão não foi medida.
echo "→ precisão de classificação sobre a massa real (cap. 19)"
java -Dfile.encoding=UTF-8 -Dsgdf.raiz="$RAIZ" -Dsgdf.massa="${SGDF_MASSA:-}" \
    -cp "$ALVO:$CP" br.com.engesoftware.sgdf.pipeline.MedirPrecisao | filtrar

echo "→ classificação (F1-03)"
java -Dfile.encoding=UTF-8 -Dsgdf.raiz="$RAIZ" -cp "$ALVO:$CP" \
    br.com.engesoftware.sgdf.classificacao.TestesDeClassificacao | filtrar
