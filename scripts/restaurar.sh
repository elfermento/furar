#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Restaura unha copia de scripts/copia.sh: BORRA as bases "furar" e "keycloak" e cárgaas da copia.
#   scripts/restaurar.sh DIRECTORIO [--si] [--outra-version]
# Para api, web e keycloak mentres restaura e volve arrincalos coa versión do .env actual.
# A copia ten que ser desa mesma versión (furar.txt, OPS-R2-2): se non, a API migraría a base
# restaurada ao esquema novo. Para volver atrás, pon antes a versión vella no .env
# (docs/instalacion.md §7). --outra-version sáltao (só se sabes que o esquema non cambiou).
# Copia cifrada (*.dump.age): FURAR_COPIAS_AGE_IDENTIDADE=ficheiro coa chave privada de age
# (age do sistema ou, se non está, o da imaxe local furar-age: scripts/lib/age.sh).
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"
source "$(dirname "$0")/lib/age.sh"

dir=${1:?uso: restaurar.sh DIRECTORIO [--si] [--outra-version]}
shift
si=0 outra=0
for a in "$@"; do
  case $a in
    --si) si=1 ;;
    --outra-version) outra=1 ;;
    *) falla "opción descoñecida: $a" ;;
  esac
done
cifrada=0
[[ -s "$dir/furar.dump.age" ]] && cifrada=1
for f in furar keycloak; do
  if ((cifrada)); then [[ -s "$dir/$f.dump.age" ]] || falla "falta $dir/$f.dump.age"
  else [[ -s "$dir/$f.dump" ]] || falla "falta $dir/$f.dump"; fi
done
if ((cifrada)); then
  age_preparar
  [[ -r ${FURAR_COPIAS_AGE_IDENTIDADE:-} ]] || falla "copia cifrada: define FURAR_COPIAS_AGE_IDENTIDADE (ficheiro da chave privada)"
fi
volcado() { # base → volcado en claro pola saída estándar
  if ((cifrada)); then age_descifrar "$FURAR_COPIAS_AGE_IDENTIDADE" "$dir/$1.dump.age"; else cat "$dir/$1.dump"; fi
}
if [[ -f "$dir/SHA256SUMS" ]]; then
  (cd "$dir" && { shasum -a 256 -c SHA256SUMS >/dev/null 2>&1 || sha256sum -c SHA256SUMS >/dev/null; }) \
    || falla "a suma de comprobación non coincide"
  ok "suma de comprobación correcta"
fi
actual=$(furar_version_actual)
if [[ -f "$dir/furar.txt" ]]; then
  da_copia=$(sed -nE 's/^FURAR_VERSION=(.*)$/\1/p' "$dir/furar.txt")
  migracion=$(sed -nE 's/^FLYWAY_ULTIMA=(.*)$/\1/p' "$dir/furar.txt")
  if [[ $da_copia != "$actual" ]]; then
    ((outra)) || falla "a copia é de Furar $da_copia (última migración ${migracion:-ningunha}) e o .env ten $actual.
    Pon FURAR_VERSION=$da_copia e os seus *_DIGEST no .env e fai git checkout $da_copia antes de restaurar
    (docs/instalacion.md §7); se non, a API $actual migraría a copia. --outra-version sáltao."
    aviso "copia de Furar $da_copia restaurada con $actual (--outra-version)"
  else
    ok "copia de Furar $da_copia, como o .env (última migración ${migracion:-ningunha})"
  fi
else
  aviso "a copia non di de que versión de Furar é (furar.txt): comproba ti que é $actual"
fi
# Antes de borrar nada: a chave ten que descifrar as dúas copias (cunha chave errada, o DROP
# deixaría o concello sen datos e sen copia que cargar).
if ((cifrada)); then
  for f in furar keycloak; do
    age_descifrar "$FURAR_COPIAS_AGE_IDENTIDADE" "$dir/$f.dump.age" >/dev/null 2>&1 \
      || falla "non se pode descifrar $f.dump.age con $FURAR_COPIAS_AGE_IDENTIDADE (outra chave privada?): non se tocou nada"
  done
  ok "a chave privada descifra a copia"
fi
if ((si == 0)); then
  read -r -p "Isto borra os datos actuais de Furar e Keycloak. Escribe SI para seguir: " r
  [[ "$r" == SI ]] || falla "cancelado"
fi

titulo "Restaurar desde $dir"
servizos=$(dc config --services | grep -vxE 'postgres|mailpit' | tr '\n' ' ')
# shellcheck disable=SC2086
dc stop $servizos >/dev/null 2>&1 || true
dc up -d --wait postgres >/dev/null

psql_admin() { dc exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d postgres -qc "$1"; }
for base in furar keycloak; do
  dono=$([[ $base == keycloak ]] && echo keycloak || echo furar_owner)
  psql_admin "DROP DATABASE IF EXISTS $base WITH (FORCE)"
  psql_admin "CREATE DATABASE $base OWNER $dono"
  volcado "$base" | dc exec -T postgres pg_restore -U postgres -d "$base" --exit-on-error --no-password
  ok "$base restaurada"
done
psql_admin "REVOKE ALL ON DATABASE keycloak FROM PUBLIC; REVOKE ALL ON DATABASE furar FROM PUBLIC; GRANT CONNECT ON DATABASE furar TO furar_app"

# shellcheck disable=SC2086
dc up -d --wait --wait-timeout 300 $servizos >/dev/null
ok "servizos arrincados: $servizos"
