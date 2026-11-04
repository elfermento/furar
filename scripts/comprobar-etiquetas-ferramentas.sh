#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Cada ferramenta de scripts/imaxes-ferramentas.txt que aparece con etiqueta literal na
# definición de feito, nos workflows ou nos scripts leva a mesma etiqueta que no .txt. Se
# Renovate sobe unha e non a outra, o comando usaría unha imaxe sen digest fixado (OPS-R3-4).
set -euo pipefail
cd "$(dirname "$0")/.."
erros=0
while read -r imaxe _; do
  [[ -z $imaxe || $imaxe == \#* ]] && continue
  nome=${imaxe%:*}
  while read -r uso; do
    [[ -z $uso ]] && continue
    if [[ ${uso#*:} != "$imaxe" ]]; then
      echo "  ✗ $uso (esperado $imaxe)" >&2
      erros=1
    fi
  done < <(grep -rnoE "(^|[^a-z0-9./-])${nome//./\\.}:[A-Za-z0-9._-]+" docs/definicion-de-feito.md .github/workflows scripts \
    --include='*.md' --include='*.yml' --include='*.yaml' --include='*.sh' \
    | sed -E "s#:[0-9]+:[^a-z0-9./-]?#:#" | grep -v '^scripts/imaxes-ferramentas.txt' || true)
done <scripts/imaxes-ferramentas.txt
((erros == 0)) && echo "  ✓ etiquetas das ferramentas iguais a scripts/imaxes-ferramentas.txt"
exit "$erros"
