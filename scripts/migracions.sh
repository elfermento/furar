#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Rexistro das migracións aplicadas (ADR-0010): api/src/main/resources/db/migrations.lock garda
# o SHA-256 de cada V*.sql. Unha migración rexistrada nunca se edita nin se borra: un concello
# instalado xa a aplicou e `flyway validate` pararía o seu despregue. MigrationLockTest compróbao
# en cada build; isto só engade liñas e compara co historial.
#
#   scripts/migracions.sh fixar         engade ao rexistro as migracións novas (nunca cambia liñas)
#   scripts/migracions.sh base <ref>    código 1 se algunha liña do rexistro en <ref> cambiou ou
#                                       desapareceu (CI: rama base do PR ou última etiqueta)
set -euo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || dirname "$0"/..)"
DIR=api/src/main/resources/db/migration
LOCK=api/src/main/resources/db/migrations.lock
die() { echo "migracions: $*" >&2; exit 1; }
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }

case ${1:-} in
  fixar)
    [[ -f $LOCK ]] || die "falta $LOCK"
    ultima=$( (grep -vE '^\s*(#|$)' "$LOCK" || true) | awk '{print $2}' | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | tail -1 || true)
    while IFS= read -r f; do
      nome=$(basename "$f")
      if rexistro=$(grep -E "  ${nome}\$" "$LOCK"); then
        [[ ${rexistro%% *} == "$(sha "$f")" ]] || die "$nome xa está rexistrada e cambiou: unha migración aplicada non se edita, crea outra"
        continue
      fi
      v=$(sed -E 's/^V([0-9]+)__.*/\1/' <<<"$nome")
      [[ -z $ultima ]] || ((v > ultima)) || die "$nome é anterior á última rexistrada (V$ultima): cámbialle a versión"
      printf '%s  %s\n' "$(sha "$f")" "$nome" >>"$LOCK"
      echo "migracions: rexistrada $nome"
    done < <(find "$DIR" -name 'V*__*.sql' | sort)
    ;;
  base)
    ref=${2:?falta a referencia}
    antes=$(git show "$ref:$LOCK" 2>/dev/null) || { echo "migracions: $ref non ten $LOCK; nada que comparar"; exit 0; }
    mal=$(grep -vE '^\s*(#|$)' <<<"$antes" | grep -vxFf "$LOCK" || true)
    [[ -z $mal ]] || die "liñas de $LOCK en $ref que cambiaron ou desapareceron (migracións aplicadas editadas):
$mal"
    echo "migracions: rexistro de $ref intacto"
    ;;
  *) sed -n '9,12p' "$0"; exit 64 ;;
esac
