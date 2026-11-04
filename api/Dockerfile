# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Imaxe da API (ADR-0015): JDK só para compilar; en execución JRE, usuario 10001, healthcheck.
# Contexto: a RAÍZ do repo (para copiar LICENSE; .dockerignore da raíz). Imaxes de terceiros fixadas por digest (Renovate actualízaas).

FROM eclipse-temurin:25.0.4.1_1-jdk-alpine@sha256:1e20ead9eec7234db293f1913438090d533ed47c0b9e12dc8c7627a802a44ed6 AS build
WORKDIR /build
COPY api/.mvn/ .mvn/
COPY api/mvnw api/pom.xml ./
# Lista de licenzas permitidas: o perfil licenses (D03) le ../scripts/allowed-licenses.txt
COPY scripts/allowed-licenses.txt /scripts/allowed-licenses.txt
# Capa de dependencias: só se volve baixar cando cambia o pom.xml. Sen BuildKit (Colima) non hai
# --mount=type=cache, así que con --no-cache todo vén de Maven Central: .mvn/maven.config
# reintenta os 5xx (o resolver por defecto só reintenta 429 e 503) e o wrapper reinténtase aquí.
# go-offline non baixa os plugins que package non usa (site e doxia, install, deploy).
# O SBOM (LIC-17) xérase aquí: cyclonedx exixe modo online e só depende do pom.xml.
RUN for i in 1 2 3 4 5; do ./mvnw -B -q --version >/dev/null && break; [ "$i" = 5 ] && exit 1; sleep $((i * 5)); done \
 && ./mvnw -B -q -Plicenses dependency:go-offline -DexcludeArtifactIds=maven-site-plugin,maven-install-plugin,maven-deploy-plugin \
 && ./mvnw -B -q cyclonedx:makeBom
COPY api/src/ src/
# Avisos de terceiros dentro da imaxe (LIC-17): THIRD-PARTY.txt (só execución; falla se aparece
# unha licenza fóra da lista) e o SBOM CycloneDX da API, como /srv/3rdpartylicenses.txt na web.
RUN ./mvnw -B -q -o -DskipTests -Plicenses -Dfurar.license.excludedScopes=system,test,provided \
      package license:add-third-party \
 && test -s target/generated-sources/license/THIRD-PARTY.txt && test -s target/bom.json \
 && java -Djarmode=tools -jar target/furar-api.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:25.0.4.1_1-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048
# Etiquetas OCI e texto da EUPL na imaxe distribuída (EUPL art. 5, LIC-9). Mesmo patrón ca web.
ARG FURAR_REVISION=unknown
ARG FURAR_VERSION=dev
ARG FURAR_SOURCE=https://github.com/elfermento/furar
LABEL org.opencontainers.image.title="furar-api" \
      org.opencontainers.image.description="Furar: API de reserva de instalacións deportivas municipais" \
      org.opencontainers.image.vendor="Santiago Herrero Padín e colaboradores de Furar" \
      org.opencontainers.image.url="${FURAR_SOURCE}" \
      org.opencontainers.image.documentation="${FURAR_SOURCE}" \
      org.opencontainers.image.version="${FURAR_VERSION}" \
      org.opencontainers.image.licenses="EUPL-1.2" \
      org.opencontainers.image.source="${FURAR_SOURCE}" \
      org.opencontainers.image.revision="${FURAR_REVISION}"
COPY LICENSE /licenses/EUPL-1.2.txt
COPY --from=build /build/target/generated-sources/license/THIRD-PARTY.txt /licenses/THIRD-PARTY.txt
COPY --from=build /build/target/bom.json /licenses/bom.json
# Parches fixados mentres a imaxe base non os trae (D37, CVE-2026-111497 en brotli-libs e
# CVE-2026-117610 e CVE-2026-118370 en libexpat): retirar cando eclipse-temurin publique un digest
# con brotli-libs >= 1.2.1-r0 e libexpat >= 2.10.0-r0 (Renovate sobe o digest).
RUN apk add --no-cache 'brotli-libs=1.2.1-r0' 'libexpat=2.10.0-r0' \
 && addgroup -S -g 10001 furar && adduser -S -D -H -u 10001 -G furar furar
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /build/target/extracted/application/ ./
USER 10001:10001
EXPOSE 8081 9081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:+UseSerialGC -Xss512k" \
    TZ=UTC
# wget de busybox: a imaxe JRE non trae curl
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- http://127.0.0.1:9081/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
