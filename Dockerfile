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
RUN mvn -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/sgdf-*.jar extract --layers --destination /camadas

# --- etapa de execução -------------------------------------------------------
FROM eclipse-temurin:21.0.5_11-jre-alpine

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
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
