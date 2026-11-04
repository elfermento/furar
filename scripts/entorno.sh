# shellcheck shell=sh
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Contorno común para desenvolver e verificar Furar (bash ou zsh):
#   source scripts/entorno.sh
# - Node 24 diante no PATH (Homebrew node@24 se existe).
# - Docker de Colima e variables de Testcontainers (Colima non expón Ryuk).
# - Funcións api_ut 'Clases' e api_it 'Clases' (docs/definicion-de-feito.md).
# Non cambia nada fóra desta shell.

if [ -d /opt/homebrew/opt/node@24/bin ]; then
  case ":$PATH:" in
    *":/opt/homebrew/opt/node@24/bin:"*) ;;
    *) export PATH="/opt/homebrew/opt/node@24/bin:$PATH" ;;
  esac
fi

if [ -z "${DOCKER_HOST:-}" ] && [ -S "$HOME/.colima/default/docker.sock" ]; then
  export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
fi
case "${DOCKER_HOST:-}" in
  *colima*)
    export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
    export TESTCONTAINERS_RYUK_DISABLED=true
    ;;
esac

export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1g}"

# As IT de illamento multiconcello (D14). Única lista: úsana definicion-de-feito.md (D14) e rapido.sh --tenancy.
export TENANCY_ITS='SchemaTenancyIT,TenantIsolationIT,MissingTenantIT,AsyncListenerTenantIT,CustomFieldsTenantIT,NativeSqlRowLevelSecurityIT'

api_ut() {
  (cd "$(git rev-parse --show-toplevel)/api" && ./mvnw -B test -Dtest="$1" -Dsurefire.failIfNoSpecifiedTests=true \
    && "$(git rev-parse --show-toplevel)/scripts/sen-omitidos.sh" "$1")
}

api_it() {
  (cd "$(git rev-parse --show-toplevel)/api" && ./mvnw -B verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test="$1" -Dfailsafe.failIfNoSpecifiedTests=true \
    && "$(git rev-parse --show-toplevel)/scripts/sen-omitidos.sh" "$1")
}
