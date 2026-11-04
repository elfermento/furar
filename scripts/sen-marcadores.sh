#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# GOV-3/LIC-8: nunha etiqueta vX.Y.Z (ou con --release) falla se quedan marcadores sen decidir
# nos ficheiros que le o xurado ou un concello. Fóra dunha release só avisa.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
release=0
[[ ${1:-} == --release ]] && release=1
[[ ${CI_COMMIT_TAG:-${GITHUB_REF_NAME:-}} =~ ^v[0-9]+\.[0-9]+\.[0-9]+ ]] && release=1
atopados=$(grep -nE 'pendente>|forxa-pendente\.invalid' -- *.md *.yml *.yaml docs/instalacion.md 2>/dev/null || true)
if [[ -z $atopados ]]; then echo "Sen marcadores pendentes"; exit 0; fi
if ((release)); then
  printf 'Marcadores sen decidir nunha release:\n%s\n' "$atopados" >&2
  exit 1
fi
printf 'Aviso (non é release): marcadores pendentes do mantedor:\n%s\n' "$atopados"
