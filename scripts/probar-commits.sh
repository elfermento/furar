#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D46: cada commit propio cumpre Conventional Commits (commitlint.config.mjs) e leva un
# `Signed-off-by:` do seu autor (DCO). Commits revisados: os que non están en origin/main; se
# non hai origin/main, TODOS, incluído o primeiro (`base..HEAD` deixaríao fóra).
# D47: --autoproba demostra que a garda falla de verdade, nun repo temporal (non toca este).
# D46a: --mensaxe aplica as mesmas regras a unha mensaxe AÍNDA SEN COMMIT, coa identidade git
# actual como autor. É o hook commit-msg (.githooks/) e a proba previa ao primeiro commit.
#   scripts/probar-commits.sh [rango]     p. ex. origin/main..HEAD (a CI pasa o do PR)
#   scripts/probar-commits.sh --autoproba
#   scripts/probar-commits.sh --mensaxe <ficheiro>
#   scripts/probar-commits.sh --primeiro  (D46a: a mensaxe de PRIMEIRO_COMMIT coa identidade actual)
set -euo pipefail
RAIZ=$(cd "$(dirname "$0")/.." && pwd)
cd "$RAIZ"
# shellcheck source=scripts/entorno.sh
source scripts/entorno.sh
COMMITLINT=21.2.4
PRIMEIRO_COMMIT='chore: import furar technical base'
CONFIG=$RAIZ/commitlint.config.mjs

# Unha mensaxe (ficheiro) cumpre commitlint e leva `Signed-off-by: <autor>`. Se non, imprime o
# motivo (": falta «…»" ou a saída de commitlint sangrada) e devolve 1. Única regra de D46/D46a.
mensaxe_valida() { # ficheiro autor
  local saida
  if ! saida=$(npx --yes --package "@commitlint/cli@$COMMITLINT" commitlint --config "$CONFIG" <"$1" 2>&1); then
    printf '\n%s\n' "$(sed 's/^/    /' <<<"$saida")"; return 1
  fi
  git interpret-trailers --parse <"$1" | sed -n 's/^Signed-off-by: //p' | grep -qxF "$2" ||
    { echo ": falta «Signed-off-by: $2» (git commit -s)"; return 1; }
}

# Revisa os commits de `rango` no repo `repo`; imprime ✓/✗ por commit e devolve 1 se algún falla.
revisar() { # repo rango
  local repo=$1 rango=$2 commits c curto autor motivo tmp fallos=0 n=0
  commits=$(git -C "$repo" rev-list --no-merges --reverse "$rango")
  [[ -n $commits ]] || { echo "probar-commits: ningún commit en $rango"; return 0; }
  tmp=$(mktemp)
  for c in $commits; do
    n=$((n + 1))
    curto=$(git -C "$repo" log -1 --format='%h %s' "$c")
    autor=$(git -C "$repo" log -1 --format='%an <%ae>' "$c")
    git -C "$repo" log -1 --format=%B "$c" >"$tmp"
    if ! motivo=$(mensaxe_valida "$tmp" "$autor"); then
      echo "✗ $curto$motivo"; fallos=$((fallos + 1)); continue
    fi
    echo "✓ $curto"
  done
  rm -f "$tmp"
  echo "probar-commits: $n commits en $rango, $fallos con problemas"
  ((fallos == 0))
}

# Repo temporal con un caso por regra; o primeiro commit é malo para probar que entra na revisión.
autoproba() {
  local dir saida esperado obtido
  dir=$(mktemp -d)
  trap 'rm -rf "$dir"' RETURN
  git -C "$dir" init -q -b main
  ci() { # autor mensaxe
    GIT_AUTHOR_DATE=2026-01-01T00:00:00Z GIT_COMMITTER_DATE=2026-01-01T00:00:00Z \
      git -C "$dir" -c user.name="${1% <*}" -c user.email="$(sed -E 's/.*<(.*)>/\1/' <<<"$1")" \
      -c commit.gpgsign=false -c core.hooksPath=/dev/null commit -q --allow-empty -m "$2"
  }
  local ana='Ana Proba <ana@example.org>' bea='Bea Proba <bea@example.org>'
  ci "$ana" $'feat(api): first commit without sign-off'
  ci "$ana" $'feat(api): add municipality list\n\nSigned-off-by: '"$ana"
  ci "$ana" $'Added stuff\n\nSigned-off-by: '"$ana"
  ci "$ana" $'fix(web): sign-off of another person\n\nSigned-off-by: '"$bea"
  esperado=$'✗ feat(api): first commit without sign-off\n✓ feat(api): add municipality list\n✗ Added stuff\n✗ fix(web): sign-off of another person'
  if saida=$(revisar "$dir" HEAD); then
    echo "$saida"; echo "autoproba: ✗ a garda aceptou commits malos"; return 1
  fi
  obtido=$(grep -E '^[✓✗] ' <<<"$saida" | sed -E 's/^([✓✗]) [0-9a-f]+ /\1 /; s/: falta «.*$//')
  echo "$saida"
  if [[ $obtido != "$esperado" ]]; then
    printf 'autoproba: ✗ veredictos inesperados\n--- esperado\n%s\n--- obtido\n%s\n' "$esperado" "$obtido"
    return 1
  fi
  grep -q 'falta «Signed-off-by: Ana Proba <ana@example.org>»' <<<"$saida" ||
    { echo "autoproba: ✗ a sinatura allea non se nomeou"; return 1; }
  echo "autoproba: ✓ 4 casos (primeiro commit incluído, sen DCO, tipo inválido, DCO alleo)"
  # --mensaxe (hook e proba previa ao primeiro commit), coa identidade de Ana por entorno.
  local m=$dir/MSG r
  pre() { GIT_AUTHOR_NAME='Ana Proba' GIT_AUTHOR_EMAIL=ana@example.org "$RAIZ/scripts/probar-commits.sh" --mensaxe "$m" >/dev/null 2>&1; }
  printf 'chore: initial import\n\n# comentario do editor\nSigned-off-by: %s\n' "$ana" >"$m"
  pre || { echo "autoproba: ✗ --mensaxe rexeitou unha mensaxe boa"; return 1; }
  for r in 'chore: initial import' $'chore: initial import\n\nSigned-off-by: '"$bea" $'Initial import\n\nSigned-off-by: '"$ana"; do
    printf '%s\n' "$r" >"$m"
    if pre; then echo "autoproba: ✗ --mensaxe aceptou: ${r%%$'\n'*}"; return 1; fi
  done
  echo "autoproba: ✓ --mensaxe acepta a boa e rexeita sen DCO, DCO alleo e tipo inválido"
}

if [[ ${1:-} == --autoproba ]]; then autoproba; exit; fi
if [[ ${1:-} == --primeiro ]]; then
  # O que vai facer o mantedor: git commit -s -m "$PRIMEIRO_COMMIT". Códigos: 0 listo; 1 a mensaxe
  # incumpre as regras (erro do repo); 2 pendente dunha persoa: sen identidade git ELIXIDA neste
  # repo (a global, p. ex. un alcume e o correo persoal, quedaría para sempre no historial e no
  # Signed-off-by) ou sen o hook activado.
  ident=$(git var GIT_AUTHOR_IDENT 2>/dev/null) || { echo "pendente: sen identidade git (git config user.name/user.email neste repo)"; exit 2; }
  autor=$(sed -E 's/ [0-9]+ [+-][0-9]{4}$//' <<<"$ident")
  msg=$(mktemp); trap 'rm -f "$msg"' EXIT
  printf '%s\n\nSigned-off-by: %s\n' "$PRIMEIRO_COMMIT" "$autor" >"$msg"
  "$0" --mensaxe "$msg" || exit 1
  pendente=()
  { git config --local user.name >/dev/null && git config --local user.email >/dev/null; } ||
    pendente+=("identidade só neste repo: git config user.name '<nome legal>' && git config user.email '<correo do proxecto>' (hoxe usaríase a global: $autor)")
  [[ $(git config core.hooksPath || true) == .githooks ]] || pendente+=("hook commit-msg: git config core.hooksPath .githooks")
  if ((${#pendente[@]})); then
    printf 'pendente do mantedor (D46a):\n'; printf '  - %s\n' "${pendente[@]}"; exit 2
  fi
  echo "primeiro commit listo para $autor: git commit -s -m \"$PRIMEIRO_COMMIT\""
  exit 0
fi
if [[ ${1:-} == --mensaxe ]]; then
  [[ -r ${2:-} ]] || { echo "uso: $0 --mensaxe <ficheiro>" >&2; exit 64; }
  autor=$(git var GIT_AUTHOR_IDENT | sed -E 's/ [0-9]+ [+-][0-9]{4}$//')
  # Sen as liñas de comentario que git engade ao editor (o hook recibe a mensaxe en bruto).
  limpa=$(mktemp); trap 'rm -f "$limpa"' EXIT
  git stripspace --strip-comments <"$2" >"$limpa"
  if motivo=$(mensaxe_valida "$limpa" "$autor"); then
    echo "✓ mensaxe válida para $autor"; exit 0
  fi
  echo "✗ mensaxe rexeitada$motivo" >&2; exit 1
fi

git rev-parse --verify -q HEAD >/dev/null || { echo "probar-commits: aínda non hai commits (D46 pendente do primeiro commit asinado)" >&2; exit 2; }
if (($#)); then
  rango=$1
elif git rev-parse --verify -q origin/main >/dev/null; then
  rango=origin/main..HEAD
else
  rango=HEAD
fi
revisar "$RAIZ" "$rango"
