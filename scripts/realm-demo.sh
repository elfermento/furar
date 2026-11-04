#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Xera o realm de demo (dev/full) = realm de produción + deploy/keycloak/demo/overlay.json
# (cliente furar-e2e, usuarios de demo e o IdP SAML «clave-simulada», ADR-0019); o fluxo de
# acceso é o mesmo ca en produción. ADR-0015. O rol do persoal non vai no realm: sae da táboa de
# pertenzas da API (ADR-0027).
# O realm do IdP simulado (import-demo/clave-simulada-realm.json) edítase á man.
#   scripts/realm-demo.sh          reescribe deploy/keycloak/import-demo/furar-realm.json
#   scripts/realm-demo.sh --check  falla se o ficheiro versionado non está ao día
set -euo pipefail
cd "$(dirname "$0")/.."

prod=deploy/keycloak/import/furar-realm.json
overlay=deploy/keycloak/demo/overlay.json
out=deploy/keycloak/import-demo/furar-realm.json

xerar() {
  jq --sort-keys --indent 2 --slurpfile o "$overlay" '
    ($o[0]) as $o
    | .clients += $o.clients
    | .users = $o.users
    | .clients |= map(if .clientId == "furar-web" then . * $o.webClient else . end)
    # Cl@ve simulada (AP2-03): IdP SAML 2.0 de demo, o realm clave-simulada do mesmo Keycloak.
    | .identityProviders = $o.identityProviders
    | .identityProviderMappers = $o.identityProviderMappers
  ' "$prod"
}

if [[ "${1:-}" == "--check" ]]; then
  if ! diff -u "$out" <(xerar); then
    echo "realm-demo: $out non está ao día; executa scripts/realm-demo.sh" >&2
    exit 1
  fi
  echo "realm-demo: ao día"
else
  mkdir -p "$(dirname "$out")"
  xerar > "$out"
  echo "realm-demo: escrito $out"
fi
