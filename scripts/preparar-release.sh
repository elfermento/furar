#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Prepara o commit dunha versión, xusto antes de etiquetala (ADR-0016):
#   - CHANGELOG.md: o contido de «[Sen publicar]» pasa a «[X.Y.Z] - DATA» e «[Sen publicar]» queda baleiro;
#   - publiccode.yml: softwareVersion e releaseDate.
# A data é a do día en que se etiqueta (UTC); non se escribe a man.
#
#   scripts/preparar-release.sh v0.1.0 [AAAA-MM-DD]
#   git commit -s -am "chore(release): v0.1.0" && git tag -a v0.1.0 -m "Furar v0.1.0"
#
# publicar-v1.sh comproba na etiqueta que os dous ficheiros din esta versión.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
etiqueta=${1:?uso: scripts/preparar-release.sh vX.Y.Z [AAAA-MM-DD]}
data=${2:-$(date -u +%F)}
[[ $etiqueta =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]] || { echo "etiqueta non válida: $etiqueta" >&2; exit 64; }
version=${BASH_REMATCH[1]}
[[ $data =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || { echo "data non válida: $data" >&2; exit 64; }
! git rev-parse -q --verify "refs/tags/$etiqueta" >/dev/null || { echo "xa existe a etiqueta $etiqueta" >&2; exit 1; }
! grep -qF "## [$version]" CHANGELOG.md || { echo "CHANGELOG.md xa ten a sección [$version]" >&2; exit 1; }
grep -qx '## \[Sen publicar\]' CHANGELOG.md || { echo "CHANGELOG.md sen a sección «## [Sen publicar]»" >&2; exit 1; }
# Algo que publicar: unha liña «- …» entre «[Sen publicar]» e a seguinte versión.
awk '/^## \[Sen publicar\]$/{s=1; next} /^## \[/{s=0} s && /^- /{n++} END{exit !n}' CHANGELOG.md ||
  { echo "«[Sen publicar]» está baleira en CHANGELOG.md" >&2; exit 1; }

tmp=$(mktemp)
awk -v v="$version" -v d="$data" '{print} /^## \[Sen publicar\]$/{print ""; print "## [" v "] - " d}' CHANGELOG.md >"$tmp"
cat "$tmp" >CHANGELOG.md
# publiccode.yml: substitúe as dúas claves ou, se non están, engádeas despois de url.
grep -v -E '^(softwareVersion|releaseDate):' publiccode.yml |
  awk -v v="$version" -v d="$data" '{print} /^url:/{print "softwareVersion: \"" v "\""; print "releaseDate: \"" d "\""}' >"$tmp"
cat "$tmp" >publiccode.yml
rm -f "$tmp"
grep -qx "softwareVersion: \"$version\"" publiccode.yml || { echo "publiccode.yml sen url: non se puido escribir a versión" >&2; exit 1; }
echo "preparar-release: $etiqueta con data $data en CHANGELOG.md e publiccode.yml"
echo "Agora: git commit -s -am \"chore(release): $etiqueta\" && git tag -a $etiqueta -m \"Furar $etiqueta\""
