#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D23 (ADR-0009): demostra que un cambio no contrato rompe a web, modelos HTML incluídos.
# Aplica tres mutacións (campo renomeado na resposta, obrigatorio novo na petición e cambio de
# tipo, T15), cada unha nunha COPIA de api/openapi.json; xera o cliente desde ela e esixe que
# `ng build` FALLE co erro TS agardado. Ao saír (tamén con erro ou Ctrl-C) volve xerar o cliente
# desde o contrato real. Non toca api/openapi.json.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
# shellcheck source=scripts/entorno.sh
source scripts/entorno.sh

tmp=$(mktemp -d)
restaurar() {
  (cd web && npx ng-openapi-gen --config ng-openapi-gen.json >/dev/null 2>&1) || echo "probar-contrato: non se puido rexenerar o cliente; executa: (cd web && npm run api:generate)" >&2
  rm -rf "$tmp"
}
trap restaurar EXIT
keep_log=${PROBAR_CONTRATO_LOG:-}

# Cada mutación: descrición, filtro jq sobre openapi.json e erro TS que ten que aparecer no build.
mutacions=(
  "MunicipalityResponse.name → displayName (renomeado)"
  '.components.schemas.MunicipalityResponse
    |= (.properties |= with_entries(if .key == "name" then .key = "displayName" else . end)
        | .required |= map(if . == "name" then "displayName" else . end))'
  "Property 'name' does not exist on type 'MunicipalityResponse'"

  "CreateMunicipalityRequest.region obrigatorio novo (T15)"
  '.components.schemas.CreateMunicipalityRequest
    |= (.properties.region = {"type": "string"} | .required += ["region"])'
  "Property 'region' is missing in type"

  "CreateMunicipalityRequest.name string → integer (T15)"
  '.components.schemas.CreateMunicipalityRequest.properties.name = {"type": "integer", "format": "int32"}'
  "is not assignable to type 'number'"
)

mutar() { # descrición filtro erro
  local desc=$1 filtro=$2 erro=$3
  jq "$filtro" api/openapi.json >"$tmp/openapi.json"
  cmp -s api/openapi.json "$tmp/openapi.json" && { echo "probar-contrato: a mutación «$desc» non cambiou nada" >&2; exit 1; }
  (cd web && npx ng-openapi-gen --config ng-openapi-gen.json --input "$tmp/openapi.json" >/dev/null)
  if (cd web && NO_COLOR=1 npx ng build --configuration development --output-path "$tmp/dist") >"$tmp/build.log" 2>&1; then
    echo "probar-contrato: FALLO — a web compilou cun contrato roto ($desc)." >&2
    exit 1
  fi
  if ! grep -qF "$erro" "$tmp/build.log"; then
    echo "probar-contrato: FALLO — o build fallou, pero non por «$desc» (agardábase «$erro»):" >&2
    cat "$tmp/build.log" >&2
    exit 1
  fi
  echo "probar-contrato: OK — $desc rompe o build. Erros e onde:"
  sed 's/\x1b\[[0-9;]*m//g' "$tmp/build.log" | grep -oE "TS[0-9]+: [^[]+|src/app/[^ ]+:[0-9]+:[0-9]+" | sort -u | head -12 || true
  if [[ -n $keep_log ]]; then cat "$tmp/build.log" >>"$keep_log"; fi
}

[[ -z $keep_log ]] || : >"$keep_log"
for ((i = 0; i < ${#mutacions[@]}; i += 3)); do
  mutar "${mutacions[i]}" "${mutacions[i + 1]}" "${mutacions[i + 2]}"
done
echo "probar-contrato: OK — as $((${#mutacions[@]} / 3)) mutacións do contrato rompen o build da web"
