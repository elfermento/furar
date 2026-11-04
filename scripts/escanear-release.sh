#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Escanea coa política de D37 as imaxes da última GitHub Release (escaneo.yml, SEC-06):
# descarga furar-VERSIÓN.env da release e pasa Trivy a cada imaxe por digest.
#   scripts/escanear-release.sh [VERSIÓN]     (precisa gh con GH_TOKEN ou sesión)
set -euo pipefail
cd "$(dirname "$0")/.."
repo=${GITHUB_REPOSITORY:?define GITHUB_REPOSITORY=propietario/repo (na CI de GitHub xa vén)}
version=${1:-$(gh release view --repo "$repo" --json tagName --jq .tagName 2>/dev/null || true)}
if [[ -z $version ]]; then echo "aínda non hai ningunha release en $repo: nada que escanear"; exit 0; fi
trivy=$(awk '/^aquasec\/trivy:/{print $1}' scripts/imaxes-ferramentas.txt)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
gh release download "$version" --repo "$repo" --pattern "furar-$version.env" --dir "$tmp"
rexistro=$(awk -F= '/^FURAR_REGISTRY=/{print $2}' "$tmp/furar-$version.env")
mal=0
for s in api web keycloak; do
  d=$(awk -F= -v k="FURAR_$(tr '[:lower:]' '[:upper:]' <<<"$s")_DIGEST" '$1 == k {print $2}' "$tmp/furar-$version.env")
  ref="$rexistro/furar-$s@sha256:$d"
  echo "» $ref ($version)"
  docker run --rm -v furar-trivy-cache:/root/.cache/ -v "$PWD/.trivyignore.yaml:/furar/.trivyignore.yaml:ro" "$trivy" \
    image --scanners vuln --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed \
    --ignorefile /furar/.trivyignore.yaml "$ref" || mal=1
done
exit "$mal"
