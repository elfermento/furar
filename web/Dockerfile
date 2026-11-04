# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Imaxe web (ADR-0014, ADR-0015): Node só para compilar; en execución Caddy (proxy de /api e
# /auth + estáticos), usuario 10001 e healthcheck. Contexto: a RAÍZ do repo (precisa
# api/openapi.json para xerar o cliente, deploy/caddy/ e LICENSE); o .dockerignore da raíz só deixa
# pasar iso. Sen RUN --mount: o builder clásico de Colima non ten BuildKit.

FROM node:24.22.1-alpine@sha256:06d7adf3e0ef61a13f1d28682726366f97a15aa1888c934d3ecec86c33918b53 AS build
WORKDIR /src/web
# Capa de dependencias: só se volve instalar cando cambia o package-lock.json.
COPY web/package.json web/package-lock.json web/.npmrc ./
RUN npm ci --no-audit --no-fund
COPY api/openapi.json /src/api/openapi.json
COPY deploy/caddy/csp.sh /src/deploy/caddy/csp.sh
COPY web/ ./
# prebuild xera o cliente TS desde o contrato: se a API cambiou, o build falla aquí.
RUN npm run build \
 && sh /src/deploy/caddy/csp.sh dist/furar-web/browser/index.html > /src/csp.caddy

FROM caddy:2.11.7-alpine@sha256:24ec7b05bc532c7bb57f727030c5056e257ff7138b4b50460306819ff79362bd
# Etiquetas OCI: licenza, orixe e commit (compose pasa FURAR_REVISION; publicar-imaxes.sh expórtao).
ARG FURAR_REVISION=unknown
ARG FURAR_VERSION=dev
ARG FURAR_SOURCE=https://github.com/elfermento/furar
# description, url, documentation, vendor e version sobrescriben as que herda da imaxe de Caddy.
LABEL org.opencontainers.image.title="furar-web" \
      org.opencontainers.image.description="Furar: web de reserva de instalacións deportivas municipais" \
      org.opencontainers.image.vendor="Santiago Herrero Padín e colaboradores de Furar" \
      org.opencontainers.image.url="${FURAR_SOURCE}" \
      org.opencontainers.image.documentation="${FURAR_SOURCE}" \
      org.opencontainers.image.version="${FURAR_VERSION}" \
      org.opencontainers.image.licenses="EUPL-1.2" \
      org.opencontainers.image.source="${FURAR_SOURCE}" \
      org.opencontainers.image.revision="${FURAR_REVISION}"
COPY deploy/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/csp.caddy /etc/caddy/csp.caddy
COPY --from=build /src/web/dist/furar-web/browser/ /srv/
# Avisos que esixe redistribuír o bundle: licenzas de terceiros que extrae Angular (MIT, BSD,
# Apache, OFL-1.1 da tipografía) e a EUPL de Furar. Sérvense en /3rdpartylicenses.txt e
# /LICENSE.txt, ligados desde o pé da web.
COPY --from=build /src/web/dist/furar-web/3rdpartylicenses.txt /srv/3rdpartylicenses.txt
COPY LICENSE /srv/LICENSE.txt
# A imaxe oficial dálle a /usr/bin/caddy a capacidade de ficheiro cap_net_bind_service; con
# cap_drop: [ALL] o exec falla («operation not permitted»). Furar escoita en 8080/8443, así que
# se quita (cp non copia os atributos estendidos) (SEC3-04).
# brotli-libs e curl fixados mentres a imaxe de Caddy non os trae (D37, CVE-2026-111497 e
# CVE-2026-92392): retirar cando Renovate suba a un digest con brotli-libs >= 1.2.1-r0 e
# curl/libcurl >= 8.23.0-r0.
RUN apk add --no-cache 'brotli-libs=1.2.1-r0' 'curl=8.23.0-r0' 'libcurl=8.23.0-r0' \
 && cp -p /usr/bin/caddy /usr/bin/caddy.sen-cap && mv /usr/bin/caddy.sen-cap /usr/bin/caddy \
 && mkdir -p /data /config \
 && chown -R 10001:10001 /data /config
ENV XDG_DATA_HOME=/data \
    XDG_CONFIG_HOME=/config
USER 10001:10001
EXPOSE 8080 8443
# wget de busybox (a imaxe alpine non trae curl)
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
  CMD wget -qO- http://127.0.0.1:8080/healthz >/dev/null || exit 1
CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
