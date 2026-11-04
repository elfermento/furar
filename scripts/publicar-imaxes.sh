#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Escanea, publica e asina as imaxes de Furar (traballo `images` da CI, ADR-0016).
#   scripts/publicar-imaxes.sh VERSIÓN [--publicar] [--sen-construir] [--asinar]
#   --sen-construir  usa furar-*:local tal como están (na CI: as que pasaron e2e, OPS-5)
#   --publicar       etiqueta e sube a $FURAR_REGISTRY (obrigatorio: sen valor por defecto)
#   --asinar         cosign sen chaves: sinatura e SBOM como atestación (só na CI, con OIDC)
# Sen opcións: constrúe en serie, escanea e xera os SBOM (proba en local).
# Tamén serve a quen precise imaxes propias (p. ex. ARM64 ou un rexistro do concello):
#   FURAR_REGISTRY=rexistro.concello.gal/furar scripts/publicar-imaxes.sh v0.1.0 --publicar
# Saída en .verificar/imaxes/: SBOM CycloneDX por imaxe e furar-VERSIÓN.env cos digests
# que usa compose.prod.yaml (FURAR_VERSION e FURAR_*_DIGEST).
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:?uso: publicar-imaxes.sh VERSIÓN [--publicar] [--sen-construir] [--asinar]}
shift
publicar=0 construir=1 asinar=0
for a in "$@"; do
  case $a in
    --publicar) publicar=1 ;;
    --sen-construir) construir=0 ;;
    --asinar) asinar=1 ;;
    *) echo "opción descoñecida: $a" >&2; exit 64 ;;
  esac
done
((asinar == 0 || publicar == 1)) || { echo "--asinar precisa --publicar" >&2; exit 64; }
if ((publicar == 1)); then
  rexistro=${FURAR_REGISTRY:?FURAR_REGISTRY é obrigatorio con --publicar (p. ex. o rexistro da forxa)}
else
  rexistro=${FURAR_REGISTRY:-furar.invalid}
fi
trivy=$(awk '/^aquasec\/trivy:/{print $1}' scripts/imaxes-ferramentas.txt)
saida=.verificar/imaxes
mkdir -p "$saida"
# Commit na etiqueta OCI org.opencontainers.image.revision (compose pásao como build arg).
FURAR_REVISION=${FURAR_REVISION:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}
export FURAR_REVISION
export FURAR_VERSION="$version"
env_file="$saida/furar-$version.env"
printf '# Furar %s: copia estas liñas ao .env do servidor (docs/instalacion.md)\nFURAR_REGISTRY=%s\nFURAR_VERSION=%s\n' \
  "$version" "$rexistro" "$version" >"$env_file"

trivy_run() { docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v furar-trivy-cache:/root/.cache/ \
  -v "$PWD/.trivyignore.yaml:/furar/.trivyignore.yaml:ro" -v "$PWD/$saida:/out" "$trivy" "$@"; }

for s in api web keycloak; do
  echo "» $s"
  if ((construir)); then docker compose --profile full build "$s"; fi
  docker image inspect "furar-$s:local" >/dev/null || { echo "falta furar-$s:local" >&2; exit 1; }
  # Mesma política que D37: HIGH e CRITICAL con corrección fallan; excepcións xustificadas e
  # con caducidade en .trivyignore.yaml (SEC-06).
  trivy_run image --scanners vuln --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed \
    --ignorefile /furar/.trivyignore.yaml "furar-$s:local"
  trivy_run image --scanners vuln --format cyclonedx --output "/out/furar-$s-$version.cdx.json" "furar-$s:local"
  if ((publicar)); then
    destino="$rexistro/furar-$s:$version"
    docker tag "furar-$s:local" "$destino"
    docker push "$destino"
    digest=$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$destino" | grep -F "$rexistro/furar-$s@" | head -1 | cut -d@ -f2)
    [[ $digest == sha256:* ]] || { echo "sen digest para $destino" >&2; exit 1; }
    printf 'FURAR_%s_DIGEST=%s\n' "$(tr '[:lower:]' '[:upper:]' <<<"$s")" "${digest#sha256:}" >>"$env_file"
    if ((asinar)); then
      cosign sign --yes "$rexistro/furar-$s@$digest"
      cosign attest --yes --type cyclonedx --predicate "$saida/furar-$s-$version.cdx.json" "$rexistro/furar-$s@$digest"
    fi
  fi
done
echo "» $env_file"
cat "$env_file"
