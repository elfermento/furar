#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Notas da GitHub Release (traballo `release` da CI, OPS-2): que copiar ao .env do servidor
# e como comprobar a sinatura das imaxes. Saída en Markdown.
#   scripts/notas-release.sh VERSIÓN DIRECTORIO_DAS_IMAXES
set -euo pipefail
version=${1:?uso: notas-release.sh VERSIÓN DIRECTORIO}
dir=${2:?uso: notas-release.sh VERSIÓN DIRECTORIO}
env_file="$dir/furar-$version.env"
[[ -s $env_file ]] || { echo "falta $env_file" >&2; exit 1; }
rexistro=$(awk -F= '/^FURAR_REGISTRY=/{print $2}' "$env_file")
# publicar-imaxes.sh escribe as plataformas no comentario «# Arquitecturas: …» (antes só amd64).
arquitecturas=$(sed -n 's/^# Arquitecturas: //p' "$env_file")
arquitecturas=${arquitecturas:-linux/amd64}
repo=${GITHUB_REPOSITORY:?define GITHUB_REPOSITORY=propietario/repo (na CI de GitHub xa vén)}

# Realm (OPS-R2-3, ADR-0015): Keycloak só o importa na primeira instalación. Se cambiou desde a
# release anterior, as notas teñen que dicir que facer a man: docs/notas-realm/VERSIÓN.md é
# obrigatorio e sen el non hai release.
anterior=$(git describe --tags --abbrev=0 --match 'v*' "$version^" 2>/dev/null || true)
realm_notas=""
if [[ -n $anterior ]] && ! git diff --quiet "$anterior" "$version" -- deploy/keycloak/import/; then
  nota=docs/notas-realm/$version.md
  [[ -s $nota ]] || { echo "o realm cambiou desde $anterior e falta $nota (pasos a man para as instalacións existentes)" >&2; exit 1; }
  realm_notas=$(printf '\n## Cambios no realm de Keycloak (acción manual)\n\nKeycloak non os aplica ao actualizar: o realm só se importa na primeira instalación. Desde %s:\n\n%s\n' "$anterior" "$(sed '/^<!--/,/-->$/d' "$nota")")
fi
# Cambios desta versión: a súa sección de CHANGELOG.md (scripts/preparar-release.sh), sen o título.
cambios=$(awk -v t="## [${version#v}] - " 'index($0, t) == 1 {s=1; next} /^## \[/{s=0} s' CHANGELOG.md)
grep -q '[^[:space:]]' <<<"$cambios" || { echo "CHANGELOG.md non ten a sección [${version#v}] (scripts/preparar-release.sh $version)" >&2; exit 1; }
# Código fonte das imaxes base (LIC-12). Publicar as imaxes é redistribuír os binarios GPL/LGPL
# que traen as bases (busybox e musl en Alpine; glibc, bash… en UBI; o JDK, GPL-2.0 con
# Classpath Exception): por cada base, o digest exacto e onde está o seu código, máis a oferta
# escrita (GPL-2.0 §3.b). O SBOM de cada imaxe lista todos os paquetes coa súa licenza.
base_de() { # Dockerfile → imaxe do último FROM (a de execución)
  awk '/^FROM /{b=$2} END{print b}' "$1"
}
so_de() { # servizo → «nome versión» do sistema operativo segundo o SBOM (o de amd64, se hai un por arquitectura)
  local f="$dir/furar-$1-$version.cdx.json"
  [[ -s $f ]] || f="$dir/furar-$1-$version-amd64.cdx.json"
  [[ -s $f ]] || { echo "? ?"; return; }
  jq -r '[.components[]? | select(.type == "operating-system")][0] | "\(.name // "?") \(.version // "?")"' "$f"
}
fonte_alpine() { # servizo → liña de Markdown
  local so v
  so=$(so_de "$1"); v=${so#* }
  if [[ $so == alpine\ * && $v != "?" ]]; then
    printf 'Alpine %s: paquetes e parches en [aports v%s](https://gitlab.alpinelinux.org/alpine/aports/-/tree/v%s); cada `APKBUILD` di de onde sae o código orixinal' "$v" "$v" "$v"
  else
    printf 'Alpine (versión en `/etc/alpine-release` da imaxe): [aports](https://gitlab.alpinelinux.org/alpine/aports)'
  fi
}
fonte_bases() {
  local api web kc jdk caddy_v kc_v
  api=$(base_de api/Dockerfile); web=$(base_de web/Dockerfile); kc=$(base_de deploy/keycloak/Dockerfile)
  # eclipse-temurin:25.0.4.1_1-jre-alpine@… → etiqueta jdk-25.0.4.1+1 do código de Adoptium
  jdk=$(sed -E 's/^[^:]+:([0-9.]+)_([0-9]+).*/jdk-\1+\2/' <<<"$api")
  caddy_v=$(sed -E 's/^[^:]+:([0-9.]+).*/v\1/' <<<"$web")
  kc_v=$(sed -E 's/^[^:]+:([0-9.]+).*/\1/' <<<"$kc")
  cat <<BASES

## Código fonte das imaxes base

As imaxes levan software libre de terceiros con licenzas copyleft (GPL e LGPL: busybox e musl en Alpine, glibc e bash en Red Hat UBI 9, o JDK con *Classpath Exception*). Cada imaxe vén dunha base fixada por digest; o código correspondente está en:

| Imaxe | Base exacta | Código fonte |
|---|---|---|
| \`furar-api\` | \`$api\` | $(fonte_alpine api). JDK: [adoptium/jdk25u $jdk](https://github.com/adoptium/jdk25u/tree/$jdk) e [temurin-build](https://github.com/adoptium/temurin-build). |
| \`furar-web\` | \`$web\` | $(fonte_alpine web). Caddy: [caddyserver/caddy $caddy_v](https://github.com/caddyserver/caddy/tree/$caddy_v). |
| \`furar-keycloak\` | \`$kc\` | Keycloak: [keycloak/keycloak $kc_v](https://github.com/keycloak/keycloak/tree/$kc_v). Paquetes de UBI 9: SRPM en [BaseOS](https://cdn-ubi.redhat.com/content/public/ubi/dist/ubi9/9/x86_64/baseos/source/SRPMS/) e [AppStream](https://cdn-ubi.redhat.com/content/public/ubi/dist/ubi9/9/x86_64/appstream/source/SRPMS/); a capa base rexese pola UBI EULA ([ADR-0002](https://github.com/$repo/blob/$version/docs/adr/0002-monorepo-licenzas-reuse.md)). |

O SBOM CycloneDX adxunto (\`furar-*-$version*.cdx.json\`, un por imaxe e arquitectura) lista cada paquete coa súa versión e licenza. A etiqueta \`org.opencontainers.image.licenses\` das tres imaxes é a de Furar (EUPL-1.2); o resto da imaxe, segundo o SBOM.

**Oferta escrita (GPL-2.0 §3.b):** durante tres anos desde esta release, calquera persoa pode pedir ao mantedor de Furar (contacto en \`publiccode.yml\` ou unha *issue* na forxa) unha copia en soporte electrónico do código fonte correspondente aos binarios GPL e LGPL destas imaxes, sen máis custo ca o da entrega.
BASES
}

cat <<FIN
# Furar $version

Instalación e actualización: [docs/instalacion.md](https://github.com/$repo/blob/$version/docs/instalacion.md).

## Cambios
$cambios

## Valores para o \`.env\` do servidor

Tamén no ficheiro adxunto \`furar-$version.env\`:

\`\`\`sh
$(grep -v '^#' "$env_file")
\`\`\`

As imaxes son exactamente as que pasaron a CI desta etiqueta (tests, e2e, Trivy HIGH/CRITICAL), cada arquitectura no seu propio runner. Arquitecturas: $arquitecturas (o mesmo digest vale en todas: Docker descarga a do servidor).
$realm_notas
$(fonte_bases)

## Comprobar a sinatura (opcional)

Imaxes asinadas con [cosign](https://docs.sigstore.dev/) sen chaves desde a CI de GitHub (o índice e a imaxe de cada arquitectura); o SBOM CycloneDX vai como atestación da imaxe de cada arquitectura e como adxunto.

\`\`\`sh
$(for s in api web keycloak; do
  d=$(awk -F= -v k="FURAR_$(tr '[:lower:]' '[:upper:]' <<<"$s")_DIGEST" '$1 == k {print $2}' "$env_file")
  printf 'cosign verify %s/furar-%s@sha256:%s \\\n  --certificate-identity "https://github.com/%s/.github/workflows/ci.yml@refs/tags/%s" \\\n  --certificate-oidc-issuer https://token.actions.githubusercontent.com\n' \
    "$rexistro" "$s" "$d" "$repo" "$version"
done)
\`\`\`
FIN
