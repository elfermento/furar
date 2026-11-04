#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Carril rápido (ADR-0016, ARQ3-3): obrigatorio en cada cambio, sen Docker, obxectivo < 3 min.
# Formato, regras de arquitectura e modularidade (A1–A22, M1–M3, coas súas autoprobas), tests
# unitarios da API e da extensión de Keycloak, lint (TS e modelos), i18n, formato, tests unitarios e tipos da web. Non substitúe a
# scripts/verificar.sh (carril completo: Testcontainers, compose full, e2e, imaxes, copias), que
# corre na CI e antes de cada etiqueta.
#
#   scripts/rapido.sh          API e web
#   scripts/rapido.sh api      só a API
#   scripts/rapido.sh web      só a web
#   scripts/rapido.sh --tenancy [api]  ademais, as 6 IT de illamento entre concellos (D14; Docker,
#                                       ~1 min co contedor reutilizado). Pídese antes do PR dun módulo con táboas.
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=scripts/entorno.sh
source scripts/entorno.sh

tenancy=0
if [[ ${1:-} == --tenancy ]]; then tenancy=1; shift; fi
que=${1:-todo}
case $que in todo | api | web) ;; *) echo "uso: scripts/rapido.sh [--tenancy] [api|web]" >&2; exit 64 ;; esac
inicio=$SECONDS

paso() { # descrición comando...
  local desc=$1
  shift
  printf '  %-44s ' "$desc"
  local t=$SECONDS log
  log=$(mktemp)
  if "$@" >"$log" 2>&1; then
    printf '✓ %ss\n' $((SECONDS - t))
    rm -f "$log"
  else
    printf '✗\n'
    tail -40 "$log" >&2
    rm -f "$log"
    exit 1
  fi
}

if [[ $que != web ]]; then
  paso "API: formato (spotless:check)" bash -c 'cd api && ./mvnw -B -q spotless:check'
  paso "API: arquitectura, modularidade e unitarios" bash -c 'cd api && ./mvnw -B test'
  paso "Keycloak: extensión (unitarios)" bash -c 'api/mvnw -B -q -f deploy/keycloak/extension/pom.xml verify'
  if ((tenancy)); then
    paso "API: illamento entre concellos (D14)" bash -c "source scripts/entorno.sh && api_it '$TENANCY_ITS'"
  fi
fi
if [[ $que != api ]]; then
  paso "Web: lint (TS e modelos)" bash -c 'cd web && npm run -s lint -- --max-warnings 0'
  paso "Web: i18n gl/es" bash -c 'cd web && npm run -s i18n:check'
  paso "Web: formato" bash -c 'cd web && npm run -s format:check'
  paso "Web: tests unitarios" bash -c 'cd web && npm run -s test -- --watch=false'
  paso "Web: tipos" bash -c 'cd web && npx tsc --noEmit -p tsconfig.app.json && npx tsc --noEmit -p tsconfig.spec.json'
fi
echo "rapido OK en $((SECONDS - inicio)) s (carril completo: scripts/verificar.sh)"
