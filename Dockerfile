# =============================================================================
# SGDF — imagem de produção
#
# SEC-07 exige "imagem base fixada e escaneada". Fixada não é a tag `latest` nem
# `21`: é a versão exata, para que o build de hoje e o de daqui a três meses
# produzam a mesma base — e para que o resultado do escaneamento continue valendo
# depois de aprovado. Uma tag móvel invalida o escaneamento sem avisar ninguém.
# =============================================================================

# --- etapa de build ----------------------------------------------------------
# maven-eclipse-temurin e NÃO temurin-jdk puro: a imagem do JDK não traz Maven, e
# o `RUN mvn` falharia na primeira construção. Verificado construindo.
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

# As dependências primeiro, e sozinhas. Sem isso, trocar uma linha de código
# rebaixaria o cache e o Maven baixaria a internet inteira a cada commit.
COPY app/pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY app/src ./src
# Os testes NÃO rodam aqui. Eles rodam no CI, contra um PostgreSQL de verdade —
# e 129 dos 1305 precisam de banco. Um `mvn package` que os pula em silêncio
# daria a impressão de ter testado; este comando é explícito sobre o que faz.
# O NOME FIXO NÃO É CAPRICHO: o jar sai com a versão no nome
# (sgdf-0.1.0-SNAPSHOT.jar) e o ENTRYPOINT é uma lista literal, sem shell para
# expandir curinga. Renomear aqui é o que permite `-jar sgdf.jar` lá embaixo
# sem que subir a versão quebre a imagem em silêncio.
RUN mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/sgdf-*.jar extract --layers --destination /camadas \
 && mv /camadas/application/sgdf-*.jar /camadas/application/sgdf.jar

# --- etapa de execução -------------------------------------------------------
FROM eclipse-temurin:21.0.12_8-jre-alpine-3.24

# FIXAR NÃO BASTA, E A EXECUÇÃO 18 PROVOU ISSO.
#
# A base anterior (21.0.5_11, Alpine 3.21.2) reprovou no gate com 2 CRITICAL e
# 11 HIGH — TODAS em pacotes do sistema, TODAS com correção publicada:
# openssl (CVE-2026-31789), sqlite-libs (CVE-2025-3277), musl, p11-kit, zlib.
# Nenhuma vinha do Java: o escaneamento do `[jar]` não achou nada.
#
# Subir a tag sozinho NÃO resolve. Escaneei aqui a mais nova
# (21.0.12_8-jre-alpine-3.24, Alpine 3.24.1) e ela ainda traz 5 HIGH com
# correção disponível — libcrypto3/libssl3/openssl (CVE-2026-14456) e libexpat
# (CVE-2026-76956, CVE-2026-76957). É estrutural, não azar de versão: a imagem
# base é construída num dia e os patches do Alpine saem depois dele. Qualquer
# tag fixada começa a envelhecer no instante em que é publicada.
#
# Então a fixação governa o QUE (o JRE 21.0.12_8 e o ramo Alpine 3.24, iguais
# hoje e daqui a três meses) e o `apk upgrade` governa o NÍVEL DE PATCH desse
# mesmo ramo. O build deixa de ser bit a bit reprodutível — e essa é a troca
# certa: "reprodutível e vulnerável" não é controle, é uma foto antiga com
# carimbo. O que certifica o resultado é o escaneamento do CI, que roda depois
# desta linha e sobre a imagem que de fato vai para produção.
#
# `--no-cache` para não deixar o índice do apk na camada final.
RUN apk upgrade --no-cache

# NÃO RODA COMO ROOT. Um processo que não precisa de root e roda como root é
# privilégio concedido por omissão — o mesmo defeito que o Autorizador recusa no
# domínio (cap. 15.1: negar é o padrão).
RUN addgroup -S sgdf && adduser -S -G sgdf -h /app sgdf
WORKDIR /app

# Na ordem em que mudam: o que muda menos vem primeiro, e a camada se reaproveita.
COPY --from=build --chown=sgdf:sgdf /camadas/dependencies/ ./
COPY --from=build --chown=sgdf:sgdf /camadas/spring-boot-loader/ ./
COPY --from=build --chown=sgdf:sgdf /camadas/snapshot-dependencies/ ./
COPY --from=build --chown=sgdf:sgdf /camadas/application/ ./

USER sgdf
EXPOSE 8080

# MaxRAMPercentage e não Xmx: o limite do contêiner pode mudar sem que ninguém
# lembre de mudar o Xmx, e a JVM que não vê o limite é morta pelo OOM killer sem
# deixar heap dump nem mensagem.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom -Duser.timezone=America/Sao_Paulo"

# O healthcheck bate no endpoint que o application.yaml expõe sem detalhe — ver
# `management.endpoint.health.show-details: never`. Um health que conta qual
# dependência caiu conta topologia a quem não está autenticado.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/saude/health | grep -q '"status":"UP"' || exit 1

# `-jar sgdf.jar` E NÃO A CLASSE DO LOADER — A IMAGEM NUNCA SUBIU COM A OUTRA.
#
# O ENTRYPOINT anterior era `java org.springframework.boot.loader.launch.JarLauncher`,
# sem `-jar` e sem `-cp`. Isso só funciona com o layout EXPLODIDO que o antigo
# `-Djarmode=layertools` produzia, em que as classes do loader ficam soltas na
# raiz de /app. Este Dockerfile usa `-Djarmode=tools`, que produz outra coisa:
#
#   application/sgdf.jar        o jar fino, com Class-Path apontando para lib/
#   dependencies/lib/*.jar      as 56 dependências
#   spring-boot-loader/         VAZIO
#
# Sem as classes do loader no classpath, o contêiner morria na primeira linha
# com "Could not find or load main class ...JarLauncher". Medido subindo a
# pilha do compose.
#
# E NÃO É REGRESSÃO DO SPRING BOOT 4. A tabela do Trivy da execução 19, ainda
# com o Boot 3.5.16, já listava `app/lib/HikariCP-6.3.3.jar` — o mesmo layout
# `lib/`. A imagem nunca pôde iniciar, desde o primeiro build.
#
# O que escondeu isso foi o meu próprio passo de fumaça da imagem: ele roda
# `docker run --entrypoint java ... -version`, justamente para NÃO subir a
# aplicação. Um teste que contorna o ENTRYPOINT não testa o ENTRYPOINT. Quem
# pega isto agora é a fumaça da PILHA, no job da imagem.
ENTRYPOINT ["java", "-jar", "sgdf.jar"]
