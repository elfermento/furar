#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Publica unha versión no repositorio PÚBLICO sen o historial do privado (ADR-0016, OPS-R3-1):
# exporta a árbore dunha etiqueta (git archive: só o versionado) sen os ficheiros de traballo
# internos, crea un repositorio novo cun único commit asinado (DCO) e, só con --publicar, fai push
# da rama main e da etiqueta. Se o público xa ten versións, o commit vai enriba do último (un
# commit por versión, fast-forward). O repositorio privado nunca se fai público.
#
# Que non se publica: a lista privada .publicar-excluir da propia etiqueta (liñas «ruta <ruta>» e
# «termo <ERE>»; a lista exclúese a si mesma). Sen ela, ou se algunha ruta ou termo chega á árbore
# exportada, o script falla antes de crear o commit.
#
#   scripts/publicar-v1.sh v0.1.0 https://github.com/elfermento/furar             en seco (por defecto)
#   scripts/publicar-v1.sh v0.1.0 https://github.com/elfermento/furar --publicar  push de verdade
#
# En seco crea o repositorio en .verificar/publicar-v1 e comproba REUSE, que non quedan marcadores,
# que a etiqueta saíu de scripts/preparar-release.sh e que a árbore é a da etiqueta menos as rutas excluídas.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
etiqueta=${1:?uso: scripts/publicar-v1.sh vX.Y.Z <URL> [--publicar]}
remoto=${2:?falta a URL do repositorio público}
publicar=0
[[ ${3:-} == --publicar ]] && publicar=1
[[ $etiqueta =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "etiqueta non válida: $etiqueta" >&2; exit 64; }
git rev-parse -q --verify "refs/tags/$etiqueta" >/dev/null || { echo "non existe a etiqueta $etiqueta" >&2; exit 1; }

# Este commit é o único público e non se pode reescribir (LIC-16): só vale a identidade elixida
# NESTE repo (--local), nunca a global (alcume e correo persoal), nin un correo persoal de webmail.
nome=$(git config --local user.name || true) correo=$(git config --local user.email || true)
[[ -n $nome && -n $correo ]] ||
  { echo "falta a identidade neste repo: git config --local user.name '<nome legal>' e user.email '<correo do proxecto>'" >&2; exit 1; }
[[ $correo != "$(git config --global user.email || true)" ]] ||
  { echo "o correo local é o mesmo ca o global ($correo): usa o correo do proxecto" >&2; exit 1; }
if tr '[:upper:]' '[:lower:]' <<<"$correo" | grep -Eq '@(gmail|googlemail|hotmail|outlook|live|yahoo|icloud|me)\.'; then
  echo "correo persoal ($correo): usa o correo do proxecto ou o no-reply da forxa" >&2; exit 1
fi

# A lista do que non se publica lese da etiqueta, non da copia de traballo.
lista=.publicar-excluir
git cat-file -e "$etiqueta:$lista" 2>/dev/null || { echo "publicar-v1: falta $lista na etiqueta $etiqueta" >&2; exit 1; }
rutas=("$lista") termos=()
while read -r clave valor; do
  case $clave in
    '' | '#'*) ;;
    ruta) rutas+=("$valor") ;;
    termo) termos+=("$valor") ;;
    *) echo "publicar-v1: liña descoñecida en $lista: $clave $valor" >&2; exit 1 ;;
  esac
done < <(git show "$etiqueta:$lista")
((${#termos[@]})) || { echo "publicar-v1: $lista non ten ningún termo" >&2; exit 1; }

# Árbore pública: a da etiqueta sen as rutas excluídas (índice temporal; o do repo non se toca).
indice=$(mktemp)
trap 'rm -f "$indice"' EXIT
GIT_INDEX_FILE=$indice git read-tree "$etiqueta"
GIT_INDEX_FILE=$indice git rm -r -q --cached --ignore-unmatch -- "${rutas[@]/#/:(literal)}" >/dev/null
arbore=$(GIT_INDEX_FILE=$indice git write-tree)

destino=.verificar/publicar-v1
rm -rf "$destino" && mkdir -p "$destino"
git archive --format=tar "$arbore" | tar -x -C "$destino"

# Garda: nada da lista chega á árbore exportada (nin rutas nin termos).
for ruta in "${rutas[@]}"; do
  [[ ! -e $destino/$ruta && ! -L $destino/$ruta ]] || { echo "publicar-v1: exportaríase $ruta" >&2; exit 1; }
done
if fugas=$(grep -r -n -I -i -E -f <(printf '%s\n' "${termos[@]}") -- "$destino"); then
  printf 'publicar-v1: termos da lista privada na árbore exportada:\n%s\n' "$fugas" >&2
  exit 1
fi

(
  cd "$destino"
  git init -q -b main # antes de sen-marcadores.sh, que se sitúa na raíz do repositorio git
  scripts/sen-marcadores.sh --release
  # A etiqueta saíu de scripts/preparar-release.sh: CHANGELOG e publiccode.yml din esta versión.
  grep -q "^## \[${etiqueta#v}\] - [0-9]\{4\}-[0-9][0-9]-[0-9][0-9]$" CHANGELOG.md &&
    grep -qx "softwareVersion: \"${etiqueta#v}\"" publiccode.yml && grep -q '^releaseDate: "[0-9-]*"$' publiccode.yml ||
    { echo "publicar-v1: CHANGELOG.md ou publiccode.yml non din ${etiqueta#v} (scripts/preparar-release.sh $etiqueta)" >&2; exit 1; }
  git config user.name "$nome"
  git config user.email "$correo"
  git add -A
  # Versións seguintes (v0.x de ensaio, v1.0.1…): un commit por versión enriba do último público,
  # sen o historial do privado. A primeira vez o repositorio público está baleiro (commit raíz).
  if previo=$(git ls-remote --heads "$remoto" main); then
    if [[ -n $previo ]]; then
      git fetch -q "$remoto" main
      # O público só ten commits deste script. Se non, a URL é doutro repositorio (p. ex. o privado
      # antes de renomealo) e un push levaría alí esta versión.
      alleos=$(git log --format=%s FETCH_HEAD | grep -cv '^chore(release): furar v[0-9]' || true)
      if ((alleos > 0)); then
        echo "publicar-v1: $remoto ten commits que non son de publicar-v1 (é o repositorio privado?)" >&2; exit 1
      fi
      git reset -q --soft FETCH_HEAD
    fi
  elif ((publicar)); then
    echo "publicar-v1: non se pode ler $remoto" >&2; exit 1
  else
    echo "publicar-v1: aviso: non se pode ler $remoto; en seco como primeira publicación" >&2
  fi
  git commit -q -s -m "chore(release): furar $etiqueta"
  git tag -a "$etiqueta" -m "Furar $etiqueta"
  scripts/reuse.sh lint
  # O Signed-off-by do commit é exactamente a identidade elixida (DCO).
  [[ $(git log -1 --format=%B | sed -n 's/^Signed-off-by: //p') == "$nome <$correo>" ]] ||
    { echo "publicar-v1: o Signed-off-by non coincide con «$nome <$correo>»" >&2; exit 1; }
)

# A árbore publicada é exactamente a da etiqueta menos as rutas excluídas.
test "$(git -C "$destino" rev-parse "HEAD^{tree}")" = "$arbore" ||
  { echo "publicar-v1: a árbore non coincide coa da etiqueta sen as rutas excluídas" >&2; exit 1; }

if ((publicar)); then
  git -C "$destino" push "$remoto" main "refs/tags/$etiqueta"
  echo "publicar-v1: publicado $etiqueta en $remoto (un commit, sen historial)"
else
  echo "publicar-v1: en seco OK ($destino). Para publicar: engade --publicar"
fi
