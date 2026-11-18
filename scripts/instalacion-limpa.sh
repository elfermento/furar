#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D40 · Instalación limpa: o que vería quen clona o repositorio.
# Copia os ficheiros versionables (git ls-files) a un directorio temporal, `cp .env.example .env`
# e levanta o perfil full co proxecto furar-limpo (volumes novos) → /naron responde 200.
#   --git        clona o último commit en vez de copiar a árbore de traballo
#   --construir  constrúe as imaxes en serie no directorio limpo (se non, usa as locais)
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"

modo=arbore construir=0
for a in "$@"; do
  case $a in
    --git) modo=git ;;
    --construir) construir=1 ;;
    *) falla "opción descoñecida: $a" ;;
  esac
done

titulo "D40 · Instalación limpa ($modo)"
# Baixa o principal (comparte portos); os seus volumes quedan.
dc down >/dev/null 2>&1 || true

# Dentro do repo (ignorado por git): Colima só monta $HOME.
tmp=$(mktemp -d "$FURAR_RAIZ/.verificar/limpo.XXXXXX")
limpar() {
  (cd "$tmp" && docker compose -p furar-limpo --profile full down -v >/dev/null 2>&1) || true
  rm -rf "$tmp"
}
trap limpar EXIT

if [[ $modo == git ]]; then
  git clone -q "$FURAR_RAIZ" "$tmp/furar"
else
  mkdir -p "$tmp/furar"
  (cd "$FURAR_RAIZ" && git ls-files -z -co --exclude-standard | while IFS= read -r -d '' f; do [[ -e "$f" ]] && printf '%s\0' "$f"; done \
    | tar --null -T - -cf -) | tar -xf - -C "$tmp/furar"
fi
cd "$tmp/furar"
cp .env.example .env
ok "copia en $tmp/furar ($(git -C "$FURAR_RAIZ" ls-files -co --exclude-standard | wc -l | tr -d ' ') ficheiros)"

if (( construir )); then
  for s in api web keycloak; do docker compose -p furar-limpo --profile full build "$s" >/dev/null; ok "imaxe $s"; done
fi
docker compose -p furar-limpo --profile full up -d --wait --wait-timeout 300 >/dev/null \
  || { docker compose -p furar-limpo --profile full ps; falla "furar-limpo non quedou san"; }
ok "furar-limpo levantado con volumes novos"

esperar "GET /naron" "$(http_code "$FURAR_URL/naron")" 200
esperar "GET $FURAR_API_CREAR_CONCELLO/naron" "$(http_code "$FURAR_URL$FURAR_API_CREAR_CONCELLO/naron")" 200
esperar "OIDC do realm furar" "$(http_code "$FURAR_URL/auth/realms/furar/.well-known/openid-configuration")" 200
echo "D40 OK"
