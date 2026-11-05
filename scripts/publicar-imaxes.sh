#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Escanea, publica e asina as imaxes de Furar (traballo `images` da CI, ADR-0016).
#   scripts/publicar-imaxes.sh VERSIÓN [--publicar] [--sen-construir] [--asinar] [--arquitecturas=A,B]
#   --sen-construir  usa furar-*:local tal como están (na CI: as que pasaron e2e, OPS-5)
#   --publicar       etiqueta e sube a $FURAR_REGISTRY (obrigatorio: sen valor por defecto)
#   --asinar         cosign sen chaves: sinatura e SBOM como atestación (só na CI, con OIDC)
#   --arquitecturas=amd64,arm64  (con --sen-construir) usa furar-*:local-<arq>, unha imaxe por
#                    arquitectura (na CI: as que pasaron o e2e en cada runner), e publica
#                    VERSIÓN como índice multiarquitectura (VERSIÓN-<arq> é cada imaxe)
# Sen opcións: constrúe en serie, escanea e xera os SBOM (proba en local).
# Tamén serve a quen precise imaxes propias (un rexistro do concello), da arquitectura do equipo:
#   FURAR_REGISTRY=rexistro.concello.gal/furar scripts/publicar-imaxes.sh v0.1.0 --publicar
# Saída en .verificar/imaxes/: SBOM CycloneDX por imaxe (e arquitectura) e furar-VERSIÓN.env
# cos digests que usa compose.prod.yaml (FURAR_VERSION e FURAR_*_DIGEST; con --arquitecturas,
# o do índice: o mesmo `imaxe@sha256:…` vale en amd64 e en arm64).
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:?uso: publicar-imaxes.sh VERSIÓN [--publicar] [--sen-construir] [--asinar] [--arquitecturas=A,B]}
shift
publicar=0 construir=1 asinar=0 arqs=""
for a in "$@"; do
  case $a in
    --publicar) publicar=1 ;;
    --sen-construir) construir=0 ;;
    --asinar) asinar=1 ;;
    --arquitecturas=*) arqs=${a#*=} ;;
    *) echo "opción descoñecida: $a" >&2; exit 64 ;;
  esac
done
((asinar == 0 || publicar == 1)) || { echo "--asinar precisa --publicar" >&2; exit 64; }
# Construír outra arquitectura sería emulala (QEMU): cada unha constrúese e próbase no seu runner.
[[ -z $arqs ]] || ((construir == 0)) || { echo "--arquitecturas precisa --sen-construir" >&2; exit 64; }
[[ -z $arqs || $arqs =~ ^[a-z0-9]+(,[a-z0-9]+)+$ ]] || { echo "--arquitecturas: lista separada por comas, p. ex. amd64,arm64" >&2; exit 64; }
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
if [[ -n $arqs ]]; then plataformas="linux/${arqs//,/, linux/}"; else plataformas="linux/$(docker version -f '{{.Server.Arch}}')"; fi
printf '# Furar %s: copia estas liñas ao .env do servidor (docs/instalacion.md)\n# Arquitecturas: %s\nFURAR_REGISTRY=%s\nFURAR_VERSION=%s\n' \
  "$version" "$plataformas" "$rexistro" "$version" >"$env_file"

trivy_run() { docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v furar-trivy-cache:/root/.cache/ \
  -v "$PWD/.trivyignore.yaml:/furar/.trivyignore.yaml:ro" -v "$PWD/$saida:/out" "$trivy" "$@"; }

# Sube unha imaxe local co nome destino e imprime o digest co que quedou no rexistro.
subir() { # imaxe_local destino
  local digest
  docker tag "$1" "$2"
  docker push "$2" >&2
  digest=$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$2" | grep -F "${2%:*}@" | head -1 | cut -d@ -f2)
  [[ $digest == sha256:* ]] || { echo "sen digest para $2" >&2; exit 1; }
  echo "$digest"
}

for s in api web keycloak; do
  echo "» $s"
  if ((construir)); then docker compose --profile full build "$s"; fi
  # Unha entrada por arquitectura: imaxe local e sufixo (do SBOM e da etiqueta no rexistro).
  if [[ -z $arqs ]]; then orixes=("furar-$s:local"); sufixos=(""); else orixes=(); sufixos=(); fi
  for q in ${arqs//,/ }; do orixes+=("furar-$s:local-$q"); sufixos+=("-$q"); done
  dixests=()
  for i in "${!orixes[@]}"; do
    orixe=${orixes[$i]} suf=${sufixos[$i]}
    docker image inspect "$orixe" >/dev/null || { echo "falta $orixe" >&2; exit 1; }
    if [[ -n $suf ]]; then
      arq=$(docker image inspect --format '{{.Architecture}}' "$orixe")
      [[ $arq == "${suf#-}" ]] || { echo "$orixe é $arq, non ${suf#-}" >&2; exit 1; }
    fi
    # Mesma política que D37: HIGH e CRITICAL con corrección fallan; excepcións xustificadas e
    # con caducidade en .trivyignore.yaml (SEC-06).
    trivy_run image --scanners vuln --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed \
      --ignorefile /furar/.trivyignore.yaml "$orixe"
    trivy_run image --scanners vuln --format cyclonedx --output "/out/furar-$s-$version$suf.cdx.json" "$orixe"
    if ((publicar)); then dixests+=("$(subir "$orixe" "$rexistro/furar-$s:$version$suf")"); fi
  done
  ((publicar)) || continue
  destino="$rexistro/furar-$s:$version"
  if [[ -z $arqs ]]; then
    digest=${dixests[0]}
  else
    # Índice (manifest list) coas imaxes de cada arquitectura, xa no rexistro: o seu digest é o
    # do .env, e Docker escolle en cada servidor a imaxe da súa arquitectura.
    docker buildx imagetools create -t "$destino" "${dixests[@]/#/$rexistro/furar-$s@}"
    digest=$(docker buildx imagetools inspect --format '{{json .Manifest}}' "$destino" | jq -r .digest)
    [[ $digest == sha256:* ]] || { echo "sen digest do índice $destino" >&2; exit 1; }
    # Relido polo digest: o que vai ao .env é un índice con todas as arquitecturas.
    indice=$(docker buildx imagetools inspect --format '{{json .Manifest}}' "$rexistro/furar-$s@$digest")
    for q in ${arqs//,/ }; do
      jq -e --arg p "linux/$q" '[.manifests[].platform | "\(.os)/\(.architecture)"] | index($p)' <<<"$indice" >/dev/null \
        || { echo "o índice $destino non ten linux/$q" >&2; exit 1; }
    done
  fi
  printf 'FURAR_%s_DIGEST=%s\n' "$(tr '[:lower:]' '[:upper:]' <<<"$s")" "${digest#sha256:}" >>"$env_file"
  if ((asinar)); then
    # --recursive: asina o índice e cada imaxe; o SBOM de cada arquitectura, sobre a súa imaxe.
    cosign sign --yes --recursive "$rexistro/furar-$s@$digest"
    for i in "${!sufixos[@]}"; do
      if [[ -z $arqs ]]; then obxecto=$digest; else obxecto=${dixests[$i]}; fi
      cosign attest --yes --type cyclonedx --predicate "$saida/furar-$s-$version${sufixos[$i]}.cdx.json" "$rexistro/furar-$s@$obxecto"
    done
  fi
done
echo "» $env_file"
cat "$env_file"
