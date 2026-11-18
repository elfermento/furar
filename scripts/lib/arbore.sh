# shellcheck shell=bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Pegada da árbore de traballo: hash dos nomes e contidos de todo o que git seguiría (versionado
# ou novo, sen os ignorados). Funciona tamén antes do primeiro commit. D28 e D45 compárana antes
# e despois para demostrar que deixan a árbore exactamente como estaba.

estado_arbore() {
  (cd "$(git rev-parse --show-toplevel)" &&
    git ls-files -z --cached --others --exclude-standard | sort -zu |
    while IFS= read -r -d '' f; do
      if [[ -f $f ]]; then shasum -a 256 "$f"; else echo "borrado $f"; fi
    done | shasum -a 256 | cut -d' ' -f1)
}
