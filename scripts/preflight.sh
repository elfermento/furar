#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Comprobacións antes de instalar ou actualizar nun servidor (docs/instalacion.md §2):
# Docker en marcha, Compose ≥ 2.24.4 (compose.prod.yaml usa `!override`, que chegou na
# 2.24.4: compose-go v2.0.0-rc.3), memoria para Docker, .env con permisos 600 e a
# configuración de produción completa (`docker compose config`) e, en produción, o correo da
# primeira administración (FURAR_ADMIN_EMAIL: falla nunha instalación nova, avisa nas outras).
# Código ≠ 0 se algo falla.
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"
min_compose=2.24.4

titulo "Comprobacións do servidor"
docker info >/dev/null 2>&1 || falla "Docker non está en marcha (ou non tes permiso: grupo docker)"
ok "Docker $(docker version --format '{{.Server.Version}}')"
v=$(docker compose version --short 2>/dev/null | sed 's/^v//') || falla "falta o complemento docker compose"
[[ "$(printf '%s\n' "$min_compose" "$v" | sort -V | head -1)" == "$min_compose" ]] \
  || falla "Docker Compose $v: precísase $min_compose ou posterior (!override en compose.prod.yaml)"
ok "Docker Compose $v (≥ $min_compose)"
mem=$(docker info --format '{{.MemTotal}}')
((mem >= 3500 * 1024 * 1024)) || falla "Docker ten $((mem / 1048576)) MiB; Furar precisa uns 3,5 GB"
ok "memoria para Docker: $((mem / 1048576)) MiB"
env_file=$FURAR_RAIZ/.env
[[ -f $env_file ]] || falla "falta $env_file (cp .env.example .env)"
perm=$(stat -c %a "$env_file" 2>/dev/null || stat -f %Lp "$env_file")
[[ $perm == 600 ]] || aviso ".env ten permisos $perm: recomendado chmod 600 .env"
grep -qE '^COMPOSE_FILE=.*compose\.prod\.yaml' "$env_file" || aviso ".env sen COMPOSE_FILE=compose.yaml:compose.prod.yaml: estás en modo local"
# Copias cifradas (OPS-R3-3): mellor age do sistema; a imaxe furar-age precisa descargar age de
# GitHub a primeira vez, xusto cando se restaura nun servidor novo.
if grep -qE '^FURAR_COPIAS_AGE_DESTINATARIO=.+' "$env_file"; then
  if command -v age >/dev/null && command -v age-keygen >/dev/null; then ok "age $(age --version)"
  else aviso "copias cifradas sen age no sistema: instálao (apt install age) para restaurar sen depender da rede"; fi
fi
erro=$(dc config -q 2>&1) || falla "configuración incompleta: $erro"
ok "configuración de Compose completa"
# Correo da primeira administración da plataforma (ADR-0028). Sen el, ninguén pode entrar como
# administración: nunha instalación nova (sen o volume da base) é un erro; nunha que xa ten base
# só se avisa, porque cando xa hai administración non fai falta (docs/instalacion.md §3).
if grep -qE '^COMPOSE_FILE=.*compose\.prod\.yaml' "$env_file" \
  && [[ -z $(desde_env FURAR_ADMIN_EMAIL | sed -E "s/^'(.*)'$/\1/") ]]; then
  proxecto=$(dc config 2>/dev/null | sed -n 's/^name: //p' | head -1)
  if [[ -z $(docker volume ls -q --filter "label=com.docker.compose.project=$proxecto" \
    --filter label=com.docker.compose.volume=pgdata) ]]; then
    falla "FURAR_ADMIN_EMAIL baleiro nunha instalación nova: ponlle o correo de quen administrará a plataforma (scripts/instalar.sh --correo-admin)"
  fi
  aviso "FURAR_ADMIN_EMAIL baleiro: se aínda non hai administración da plataforma, ponlle un correo e reinicia a API (docker compose up -d api), ou engádea como dono da base (insert into platform_admin, docs/instalacion.md)"
fi
echo "preflight OK"
