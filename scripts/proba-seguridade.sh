#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D31 · Seguridade en execución contra o compose full levantado (ADR-0007, ADR-0015).
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"

titulo "D31 · Seguridade en execución ($FURAR_URL)"

esperar "POST $FURAR_API_CREAR_CONCELLO sen token" \
  "$(http_code -X POST "$FURAR_URL$FURAR_API_CREAR_CONCELLO" -H 'Content-Type: application/json' -d '{}')" 401
ct=$(cabeceira content-type "$FURAR_URL$FURAR_API_CREAR_CONCELLO" -X POST -H 'Content-Type: application/json' -d '{}')
esperar "  …en problem+json" "${ct%%;*}" application/problem+json

esperar "/actuator/env por Caddy" "$(http_code "$FURAR_URL/actuator/env")" 404
ct=$(cabeceira content-type "$FURAR_URL/actuator/env")
esperar "  …en problem+json" "${ct%%;*}" application/problem+json
esperar "/api/actuator/env por Caddy" "$(http_code "$FURAR_URL/api/actuator/env")" 404

# Dentro do contedor: o porto da app non serve actuator e o de xestión só health.
interno() { # url → código HTTP visto por wget de busybox
  # -S escribe as cabeceiras sangradas («  HTTP/1.1 404»); a liña «wget: server returned error» non conta.
  dc exec -T api sh -c "wget -S -qO /dev/null '$1' 2>&1 | awk '/^ +HTTP\\//{c=\$2} END{print c}'" || true
}
esperar "Porto 8081 da API (dentro do contedor), /actuator/env" "$(interno http://127.0.0.1:8081/actuator/env)" 404
esperar "Porto 9081 da API (dentro do contedor), /actuator/env" "$(interno http://127.0.0.1:9081/actuator/env)" 404
esperar "Porto 9081 da API (dentro do contedor), readiness" "$(interno http://127.0.0.1:9081/actuator/health/readiness)" 200

for ruta in / /naron/instalacions; do
  csp=$(cabeceira content-security-policy "$FURAR_URL$ruta")
  [[ "$csp" == *"default-src 'self'"* && "$csp" == *"frame-ancestors 'none'"* ]] \
    || falla "CSP da web ausente ou incompleta en $ruta: «$csp»"
  script_src=$(tr ';' '\n' <<<"$csp" | grep -E '^[[:space:]]*script-src' || true)
  [[ -n "$script_src" && "$script_src" != *"'unsafe-inline'"* ]] || falla "script-src ausente ou con 'unsafe-inline' en $ruta"
  ok "CSP da web en $ruta"
done

# Primeiro o 200: un 503 de Keycloak a medio arrancar tampouco leva CSP e pasaría (QA4).
esperar "Discovery do realm furar por Caddy" \
  "$(http_code "$FURAR_URL/auth/realms/furar/.well-known/openid-configuration")" 200
csp=$(cabeceira content-security-policy "$FURAR_URL/auth/realms/furar/.well-known/openid-configuration")
esperar "Sen CSP en /auth/realms/furar/.well-known/openid-configuration" "${csp:-ausente}" ausente
csp=$(cabeceira content-security-policy "$FURAR_URL/auth/realms/furar/account")
[[ "$csp" != *"default-src 'self'"* ]] || falla "a CSP da web chega a /auth: «$csp»"
ok "/auth/* non leva a CSP da web"

for h in x-content-type-options referrer-policy strict-transport-security permissions-policy; do
  [[ -n "$(cabeceira "$h" "$FURAR_URL/")" ]] || falla "falta a cabeceira $h"
done
ok "cabeceiras de seguridade en /"
esperar "Cabeceira Server oculta" "$(cabeceira server "$FURAR_URL/" | tr -d ' ')" ""

# SEC3-01: no perfil full (contrasinais de demo públicos) nada se publica fóra deste equipo.
abertos=$(dc config --format json | jq -r '.services | to_entries[] | .key as $s
  | .value.ports[]? | select((.host_ip // "") != "127.0.0.1") | "\($s):\(.published)"')
[[ -z "$abertos" ]] || falla "portos publicados fóra de 127.0.0.1: $abertos (FURAR_HTTP_BIND no .env?)"
ok "todos os portos publicados en 127.0.0.1"

# Keycloak nun só nodo (KC_CACHE=local en execución): sen clúster JGroups nin os seus portos
# 7800 e 57800 (57800 aceptaba conexións sen TLS desde a rede interna) (CVE-2026-102554, D37).
dc logs --no-log-prefix keycloak 2>&1 | grep -qE 'ISPN000078|JGroups' \
  && falla "Keycloak arrancou JGroups (KC_CACHE=local non aplica en execución)"
escoita=$(dc exec -T keycloak cat /proc/net/tcp /proc/net/tcp6 \
  | awk '$4 == "0A" { split($2, a, ":"); print a[2] }' | sort -u)
[[ -n "$escoita" ]] || falla "non se puideron ler os portos de Keycloak"
if grep -qxE '1E78|E1C8' <<<"$escoita"; then falla "Keycloak escoita en 7800 ou 57800 (JGroups)"; fi
ok "Keycloak sen JGroups nin portos 7800/57800"

echo "D31 OK"
