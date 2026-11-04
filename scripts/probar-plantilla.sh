#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D45: a plantilla de módulo é reproducible. Xera as dúas variantes con scripts/novo-modulo.sh
# (`probe`, sen campos personalizados, e `probecf`, con --custom-fields PROBECF, un destino novo) e pásalles as mesmas comprobacións que a calquera cambio (formato, ArchUnit, Modulith, permisos,
# catálogo de erros, os seus tests e IT, multiconcello, seguridade, contrato, lint, textos,
# tests (lista, alta e edición) e build da web, e que o E2E con axe compila e se lista). Ao saír (tamén con erro) desfaino e esixe que a árbore quede idéntica.
# Imprime o tempo que tarda.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
# shellcheck source=scripts/entorno.sh
source scripts/entorno.sh
# shellcheck source=scripts/lib/arbore.sh
source scripts/lib/arbore.sh

MODULO=probe
MODULO_CF=probecf
antes=$(estado_arbore)
t0=$SECONDS
ao_sair() {
  local rc=$?
  # Pode saír con erro desde web/ ou api/: os --remove usan rutas desde a raíz.
  cd "$(git rev-parse --show-toplevel)"
  # En orde inversa: cada --remove restaura os ficheiros tal como estaban antes de xeralo.
  [[ -d .novo-modulo/$MODULO_CF ]] && scripts/novo-modulo.sh --remove "$MODULO_CF"
  [[ -d .novo-modulo/$MODULO ]] && scripts/novo-modulo.sh --remove "$MODULO"
  if [[ "$(estado_arbore)" != "$antes" ]]; then
    echo "probar-plantilla: FALLO — a árbore non quedou como estaba" >&2
    rc=1
  fi
  ((rc == 0)) && echo "probar-plantilla: OK en $((SECONDS - t0)) s (xerar, comprobar e desfacer)"
  exit "$rc"
}
trap ao_sair EXIT

paso() { printf '\n» %s\n' "$1"; shift; "$@"; }

paso "Xerar (sen campos personalizados)" scripts/novo-modulo.sh "$MODULO"
t_xerar=$((SECONDS - t0))
paso "Xerar (con campos personalizados)" scripts/novo-modulo.sh --custom-fields PROBECF "$MODULO_CF"
cd api
paso "Formato Java (D10)" ./mvnw -B -q spotless:check
paso "Contrato OpenAPI rexenerado" ./mvnw -B -q verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=OpenApiContractIT -Dfailsafe.failIfNoSpecifiedTests=true -Dfurar.openapi.write=true
paso "Gardas e tests do módulo (D12, D14, D15, D16, D17)" ./mvnw -B verify \
  -Dtest='ArchitectureTest,ModularityTest,RolePermissionsTest,ErrorCatalogTest,MigrationLockTest,Probe*Test' \
  -Dsurefire.failIfNoSpecifiedTests=true \
  -Dit.test='ProbeIT,ProbecfIT,ProbecfCustomFieldsIT,SchemaTenancyIT,EndpointSecurityIT,OpenApiContractIT' \
  -Dfailsafe.failIfNoSpecifiedTests=true
cd ../web
paso "Lint (D24)" npm run lint -- --max-warnings 0
paso "Textos (D25)" npm run i18n:check
paso "Formato web" npm run format:check
paso "Tests da feature" npx ng test --watch=false --include="src/app/features/$MODULO/**/*.spec.ts" \
  --include="src/app/features/$MODULO_CF/**/*.spec.ts"
paso "Build (D22)" npx ng build --configuration development
# E2E xerado (ARQ2-3, T8): compila e Playwright o recoñece nos 3 proxectos (gl, es, gl-mobile).
# Executalo pide o perfil full co módulo despregado; iso faino a CI (D34), non esta proba.
paso "E2E con axe: tipos" npx tsc -p tsconfig.e2e.json --noEmit
lista_e2e() {
  local out
  out=$(npx playwright test --list "e2e/$MODULO.e2e.ts" "e2e/$MODULO_CF.e2e.ts")
  echo "$out" | tail -1
  grep -q '^Total: 12 tests in 2 files$' <<<"$out" || { echo "esperaba 12 probas (2 × 2 ficheiros × 3 proxectos)" >&2; return 1; }
}
paso "E2E con axe: lista (gl, es, gl-mobile)" lista_e2e
cd ..
echo "probar-plantilla: esqueleto xerado en $t_xerar s; comprobado en $((SECONDS - t0)) s"
