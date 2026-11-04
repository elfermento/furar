#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# EUPL art. 5 nas imaxes distribuídas (LIC-9): cada imaxe de Furar leva as etiquetas OCI de
# licenza, orixe e commit, e o texto da EUPL dentro; a API e a web, tamén os avisos de
# terceiros (LIC-17). Parte de D37.
set -euo pipefail
fallo=0
for s in api web keycloak; do
  i="furar-$s:local"
  lic=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.licenses"}}' "$i")
  src=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.source"}}' "$i")
  rev=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$i")
  tit=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.title"}}' "$i")
  case $s in web) f=/srv/LICENSE.txt ;; *) f=/licenses/EUPL-1.2.txt ;; esac
  printf '%s: licenses=%s source=%s revision=%s\n' "$i" "$lic" "$src" "$rev"
  [[ $tit == "furar-$s" ]] || { echo "  ✗ title=$tit (esperado furar-$s)"; fallo=1; }
  # Mesmo criterio nas tres (LIC-12): a licenza de Furar; o resto, no SBOM.
  [[ $lic == EUPL-1.2 ]] || { echo "  ✗ org.opencontainers.image.licenses=$lic (esperado EUPL-1.2, ADR-0002)"; fallo=1; }
  [[ -n $src && -n $rev ]] || { echo "  ✗ faltan source ou revision"; fallo=1; }
  docker run --rm --entrypoint sh "$i" -c "grep -q 'EUROPEAN UNION PUBLIC LICENCE v. 1.2' $f" \
    || { echo "  ✗ falta $f coa EUPL-1.2"; fallo=1; }
  # Avisos de terceiros dentro da imaxe (LIC-17): quen só recibe a imaxe tamén os ve.
  case $s in
    api) docker run --rm --entrypoint sh "$i" -c \
           'test -s /licenses/THIRD-PARTY.txt && grep -q "(EPL-2.0)" /licenses/THIRD-PARTY.txt && grep -q "\"bomFormat\" *: *\"CycloneDX\"" /licenses/bom.json' \
           || { echo "  ✗ faltan /licenses/THIRD-PARTY.txt ou /licenses/bom.json (CycloneDX)"; fallo=1; } ;;
    web) docker run --rm --entrypoint sh "$i" -c 'test -s /srv/3rdpartylicenses.txt' \
           || { echo "  ✗ falta /srv/3rdpartylicenses.txt"; fallo=1; } ;;
  esac
done
exit $fallo
