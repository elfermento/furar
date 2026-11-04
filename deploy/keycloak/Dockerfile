# syntax=docker/dockerfile:1
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Keycloak de Furar (ADR-0007, ADR-0015): build optimizado, baixo /auth, temas de acceso, correo e conta en galego e castelán,
# a lista de contrasinais comúns e a extensión do rexistro (a invitación verifica o correo, ADR-0027).
# Contexto: a RAÍZ do repo (para copiar LICENSE; .dockerignore da raíz só deixa pasar os temas, a
# extensión e o wrapper de Maven da API).
# O realm non vai na imaxe: móntase en /opt/keycloak/data/import (demo ou produción).

# Extensión de Furar (deploy/keycloak/extension, EUPL-1.2): o paso do rexistro que dá o correo por
# verificado coa proba da invitación do persoal (ADR-0027) e a ligazón curta dos correos (ADR-0007). Compílase e próbase aquí co wrapper de
# Maven da API (mesma versión de Maven) e o JDK da API; o jar só leva código de Furar.
FROM eclipse-temurin:25.0.4.1_1-jdk-alpine@sha256:1e20ead9eec7234db293f1913438090d533ed47c0b9e12dc8c7627a802a44ed6 AS extension
WORKDIR /build
COPY api/.mvn/wrapper/ .mvn/wrapper/
COPY api/.mvn/maven.config .mvn/maven.config
COPY api/mvnw ./
COPY deploy/keycloak/extension/pom.xml ./
RUN for i in 1 2 3 4 5; do ./mvnw -B -q --version >/dev/null && break; [ "$i" = 5 ] && exit 1; sleep $((i * 5)); done \
 && ./mvnw -B -q dependency:go-offline -DexcludeArtifactIds=maven-site-plugin,maven-install-plugin,maven-deploy-plugin
COPY deploy/keycloak/extension/src/ src/
# Online: os tests baixan o provedor de JUnit de surefire, que go-offline non trae.
RUN ./mvnw -B -q package && test -s target/furar-keycloak-extension.jar

# Digest de 26.8.1-2 (reconstrución do 2026-10-31): o -1 (2026-10-23) trae OpenJDK 21.0.12 con
# CVE-2026-91415 (HIGH, corrixida en 21.0.13) e antes o -0 traía ca-certificates con
# CVE-2026-114406 (CRITICAL); Trivy (D37) fallaba. Entra antes dos 14 días por ser un parche de
# seguridade do produto (ADR-0004); Renovate segue as seguintes.
FROM quay.io/keycloak/keycloak:26.8.1@sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332 AS build
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=false \
    KC_HTTP_RELATIVE_PATH=/auth \
    KC_FEATURES_DISABLED=impersonation
COPY --chown=1000:0 deploy/keycloak/themes/furar /opt/keycloak/themes/furar
COPY --from=extension --chown=1000:0 /build/target/furar-keycloak-extension.jar /opt/keycloak/providers/furar-keycloak-extension.jar
# Lista de contrasinais comúns da política do realm (passwordBlacklist): Keycloak búscaa en
# data/password-blacklists (o realm vai montado en data/import, que non a tapa).
COPY --chown=1000:0 deploy/keycloak/password-blacklists /opt/keycloak/data/password-blacklists
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.1@sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332
COPY --from=build /opt/keycloak/ /opt/keycloak/
# Etiquetas OCI e licenzas (EUPL art. 5, LIC-9): os temas son de Furar (EUPL-1.2; as traducións
# levan tamén Apache-2.0, ADR-0002); Keycloak é Apache-2.0 e trae os seus avisos en /opt/keycloak.
# Criterio común ás tres imaxes (LIC-12, ADR-0002): org.opencontainers.image.licenses é a licenza
# de Furar; a de cada paquete da imaxe (base UBI, Keycloak, JDK) está no SBOM da release.
ARG FURAR_REVISION=unknown
ARG FURAR_VERSION=dev
ARG FURAR_SOURCE=https://github.com/elfermento/furar
LABEL org.opencontainers.image.title="furar-keycloak" \
      org.opencontainers.image.description="Furar: Keycloak cos temas de Furar en galego e castelán e a extensión do rexistro" \
      org.opencontainers.image.vendor="Santiago Herrero Padín e colaboradores de Furar" \
      org.opencontainers.image.url="${FURAR_SOURCE}" \
      org.opencontainers.image.documentation="${FURAR_SOURCE}" \
      org.opencontainers.image.version="${FURAR_VERSION}" \
      org.opencontainers.image.licenses="EUPL-1.2" \
      org.opencontainers.image.source="${FURAR_SOURCE}" \
      org.opencontainers.image.revision="${FURAR_REVISION}"
COPY LICENSE /licenses/EUPL-1.2.txt
# A letra do tema de acceso (Atkinson Hyperlegible Next) é OFL-1.1.
COPY LICENSES/OFL-1.1.txt /licenses/OFL-1.1.txt
# Un só nodo: caché local. En Keycloak 26 `cache` é opción de execución (kc.sh build non a garda),
# así que vai aquí e non na etapa build; alí quedaba sen efecto e arrancaba un clúster JGroups
# (portos 7800 e 57800, este sen TLS) na rede interna. D31 compróbao.
ENV KC_CACHE=local \
    KC_HTTP_ENABLED=true \
    KC_HTTP_PORT=8082 \
    KC_HTTP_MANAGEMENT_PORT=9000 \
    KC_PROXY_HEADERS=xforwarded \
    KC_LOG_LEVEL=info \
    JAVA_OPTS_KC_HEAP="-XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -Xms128m -Xmx512m"
USER 1000
EXPOSE 8082 9000
# Sen curl na imaxe: petición HTTP crúa por /dev/tcp ao porto de xestión. Mírase o código HTTP
# (200 = UP, 503 = DOWN), non o texto "UP": a resposta DOWN do arranque leva "Graceful Shutdown":
# "UP", e daquela Docker daba healthy antes de importar o realm (QA4, D33/D41 intermitentes).
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/9000 && printf 'GET /auth/health/ready HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '^HTTP/[0-9.]* 200 ' <&3"]
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
