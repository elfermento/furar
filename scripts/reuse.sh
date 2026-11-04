#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Executa reuse (imaxe fixada en scripts/imaxes-ferramentas.txt) só sobre os ficheiros
# versionables: os que git seguiría (`git ls-files -co --exclude-standard`).
#   scripts/reuse.sh lint
#   scripts/reuse.sh spdx
#   scripts/reuse.sh titulares   (D02: falla se reuse falla, se non hai ficheiros ou se hai
#                                 un titular distinto de Furar e dos de third-party-holders.txt)
#
# Por que non `reuse lint` directamente no repo: reuse 6.2 pregunta a git polos ignorados con
# `git ls-files --ignored --directory --no-empty-directory`, que non devolve os directorios
# ignorados que están dentro dun directorio sen seguimento (api/target, web/node_modules
# mentres o índice está baleiro ou nun módulo novo aínda sen engadir). Entón reuse analiza
# builds e dependencias. Aquí a lista de ficheiros sae de git, dentro do contedor (sen a
# configuración global do host), e reuse corre sobre esa copia. Non toca o repo nin o índice.
set -euo pipefail

raiz=$(git rev-parse --show-toplevel)
imaxe=$(awk '/^fsfe\/reuse:/{print $1}' "$raiz/scripts/imaxes-ferramentas.txt")
[[ -n $imaxe ]] || { echo "fsfe/reuse non está en scripts/imaxes-ferramentas.txt" >&2; exit 2; }
if [[ ${1:-} == titulares ]]; then
  # Terceiros admitidos: «titular | motivo» en scripts/third-party-holders.txt (LIC-5).
  # Atribuír ben un ficheiro alleo non debe facer fallar D02; un titular sen motivo, si.
  filtro=(-e 'Santiago Herrero Padín e colaboradores de Furar')
  while IFS= read -r entrada; do
    [[ -z $entrada || $entrada == \#* ]] && continue
    [[ $entrada == *' | '?* ]] || { echo "third-party-holders.txt: falta «titular | motivo»: $entrada" >&2; exit 1; }
    filtro+=(-e "${entrada%% | *}")
  done <"$raiz/scripts/third-party-holders.txt"
  spdx=$("$0" spdx)
  total=$(grep -c '^FileCopyrightText' <<<"$spdx" || true)
  alleos=$(grep '^FileCopyrightText' <<<"$spdx" | grep -vF "${filtro[@]}" || true)
  echo "titulares: $total; terceiros admitidos: $((${#filtro[@]} / 2 - 1)); sen motivo: $(grep -c . <<<"$alleos" || true)"
  [[ $total -gt 0 && -z $alleos ]] || { printf '%s\n' "$alleos" >&2; exit 1; }
  exit 0
fi
(($# > 0)) || { echo "uso: $0 lint|spdx [argumentos de reuse]" >&2; exit 2; }

# Nun worktree, .git é un ficheiro que apunta ao repositorio principal por ruta absoluta:
# móntase tamén ese directorio, na mesma ruta, para que git o atope dentro do contedor.
comun=$(git -C "$raiz" rev-parse --path-format=absolute --git-common-dir)
montaxes=(-v "$raiz:/src:ro")
[[ $comun == "$raiz/.git" ]] || montaxes+=(-v "$comun:$comun:ro")
# shellcheck disable=SC2016 # o script interno expándese no contedor
exec docker run --rm "${montaxes[@]}" --entrypoint sh "$imaxe" -euc '
  git -c safe.directory="*" -C /src ls-files -z -co --exclude-standard >/tmp/ficheiros
  python3 - <<FIN
import os, shutil, sys
nomes = [n for n in open("/tmp/ficheiros", "rb").read().decode().split("\\0") if n]
copiados = 0
for n in nomes:
    orixe = os.path.join("/src", n)
    if not os.path.lexists(orixe):  # en seguimento pero borrado na árbore
        continue
    destino = os.path.join("/tmp/arbore", n)
    os.makedirs(os.path.dirname(destino), exist_ok=True)
    shutil.copy2(orixe, destino, follow_symlinks=False)
    copiados += 1
# Garda: un repo que o contedor non ve (p. ex. fóra dos directorios que monta Colima)
# daría «0 / 0» e pasaría sen comprobar nada.
if copiados == 0:
    sys.exit("reuse.sh: ningún ficheiro versionable en /src")
print(f"reuse.sh: {copiados} ficheiros versionables", file=sys.stderr)
FIN
  cd /tmp/arbore && exec reuse "$@"
' reuse "$@"
