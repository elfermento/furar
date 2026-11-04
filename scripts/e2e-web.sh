#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D34 (T14): Playwright + axe nos proxectos gl, es e gl-mobile. Playwright sae con 0 se hai tests
# omitidos; isto non: le o informe json e esixe 0 omitidos, 0 fallidos, 0 intermitentes e tantos
# tests correctos como lista `playwright test --list`, e nunca menos que o mínimo (argumento 1),
# para que borrar tests tampouco poña a CI en verde.
#   scripts/e2e-web.sh 171
set -euo pipefail
minimo=${1:?uso: scripts/e2e-web.sh <mínimo de tests>}
cd "$(git rev-parse --show-toplevel)/web"
proxectos=(--project=gl --project=es --project=gl-mobile)
informe=test-results-json/resultados.json

npx playwright install chromium
listados=$(npx playwright test --list "${proxectos[@]}" | sed -nE 's/^Total: ([0-9]+) tests? in .*/\1/p')
[[ -n $listados ]] || { echo "e2e-web: non se puido contar os tests con --list" >&2; exit 1; }
if ((listados < minimo)); then
  echo "e2e-web: FALLO — $listados tests listados, menos que o mínimo da DoD ($minimo)" >&2
  exit 1
fi

rm -f "$informe"
rc=0
npx playwright test "${proxectos[@]}" || rc=$?
[[ -s $informe ]] || { echo "e2e-web: non hai informe json en web/$informe" >&2; exit 1; }
read -r esperados omitidos fallidos intermitentes < <(jq -r '.stats | "\(.expected) \(.skipped) \(.unexpected) \(.flaky)"' "$informe")
echo "e2e-web: listados=$listados correctos=$esperados omitidos=$omitidos fallidos=$fallidos intermitentes=$intermitentes (mínimo $minimo)"
if ((rc != 0 || fallidos > 0 || intermitentes > 0)); then
  echo "e2e-web: FALLO — Playwright con erros (código $rc)" >&2
  exit 1
fi
if ((omitidos > 0)); then
  echo "e2e-web: FALLO — $omitidos tests omitidos (T14: non se admite ningún skipped)" >&2
  exit 1
fi
if ((esperados != listados)); then
  echo "e2e-web: FALLO — correron $esperados tests correctos de $listados listados" >&2
  exit 1
fi
echo "e2e-web OK"
