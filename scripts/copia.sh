#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Copia de seguridade das bases "furar" e "keycloak" (pg_dump, formato custom). ADR-0015.
#   scripts/copia.sh [directorio] [--conservar N]
#     directorio     por defecto $FURAR_COPIAS_DIR/AAAAMMDD-HHMMSS (FURAR_COPIAS_DIR=copias/)
#     --conservar N  despois de copiar, borra as copias de copias/ máis antigas que as N
#                    últimas (tamén FURAR_COPIAS_CONSERVAR=N). Por defecto non borra nada.
# Cifrado (recomendado; as copias teñen datos persoais): con FURAR_COPIAS_AGE_DESTINATARIO (no
# contorno ou no .env)
# (chave pública age1…, ou un ficheiro de destinatarios) cífranse con age (BSD-3-Clause) e
# non queda ningunha copia en claro. A chave privada gárdase FÓRA do servidor. Sen age no
# sistema úsase o da imaxe local furar-age (scripts/lib/age.sh).
# Produción: o .env con COMPOSE_FILE=compose.yaml:compose.prod.yaml xa basta.
# Envío fóra do servidor: ver docs/instalacion.md §6 (rclone ou rsync).
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"
source "$(dirname "$0")/lib/age.sh"

# Valores do .env do servidor se non veñen no contorno (desde_env, scripts/lib/comun.sh).
FURAR_COPIAS_AGE_DESTINATARIO=${FURAR_COPIAS_AGE_DESTINATARIO:-$(desde_env FURAR_COPIAS_AGE_DESTINATARIO)}
FURAR_COPIAS_CONSERVAR=${FURAR_COPIAS_CONSERVAR:-$(desde_env FURAR_COPIAS_CONSERVAR)}
dir="" conservar=${FURAR_COPIAS_CONSERVAR:-0}
while (($#)); do
  case $1 in
    --conservar) conservar=${2:?falta N}; shift ;;
    -*) falla "opción descoñecida: $1" ;;
    *) dir=$1 ;;
  esac
  shift
done
[[ $conservar =~ ^[0-9]+$ ]] || falla "--conservar precisa un número"
destinatario=${FURAR_COPIAS_AGE_DESTINATARIO:-}
if [[ -n $destinatario ]]; then
  age_preparar
fi
base_copias=${FURAR_COPIAS_DIR:-$FURAR_RAIZ/copias}
dir=${dir:-$base_copias/$(date -u +%Y%m%d-%H%M%S)}
mkdir -p "$dir"
chmod 700 "$dir"

titulo "Copia en $dir"
for base in furar keycloak; do
  if [[ -n $destinatario ]]; then
    # Cífrase no fluxo: o volcado en claro nunca toca o disco.
    dc exec -T postgres pg_dump -U postgres --format=custom --no-password "$base" | age_cifrar "$destinatario" >"$dir/$base.dump.age"
    [[ -s "$dir/$base.dump.age" ]] || falla "copia baleira de $base"
    ok "$base cifrada con age ($(du -h "$dir/$base.dump.age" | cut -f1))"
  else
    dc exec -T postgres pg_dump -U postgres --format=custom --no-password "$base" >"$dir/$base.dump"
    [[ -s "$dir/$base.dump" ]] || falla "copia baleira de $base"
    ok "$base ($(du -h "$dir/$base.dump" | cut -f1))"
  fi
done
dc exec -T postgres psql -U postgres -d furar -Atc 'select version()' >"$dir/version.txt"
# De que versión de Furar e de que esquema é a copia (OPS-R2-2): restaurar.sh négase a cargala
# con outra FURAR_VERSION no .env, porque a API desa versión migraría a copia restaurada.
migracion=$(dc exec -T postgres psql -U postgres -d furar -Atc \
  "select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1" 2>/dev/null || true)
printf 'FURAR_VERSION=%s\nFLYWAY_ULTIMA=%s\n' "$(furar_version_actual)" "$migracion" >"$dir/furar.txt"
(cd "$dir" && { shasum -a 256 ./*.dump* >SHA256SUMS 2>/dev/null || sha256sum ./*.dump* >SHA256SUMS; })
chmod 600 "$dir"/*
ok "suma de comprobación en $dir/SHA256SUMS"

# Rotación: só directorios con forma AAAAMMDD-HHMMSS dentro de copias/ (nunca outros).
if ((conservar > 0)) && [[ -d $base_copias ]]; then
  antigas=$(find "$base_copias" -mindepth 1 -maxdepth 1 -type d -name '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]' \
    | sort -r | tail -n +$((conservar + 1)))
  for d in $antigas; do rm -rf "$d"; ok "borrada a copia antiga $(basename "$d")"; done
fi
echo "$dir"
