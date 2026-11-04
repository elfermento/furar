# shellcheck shell=bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# age (BSD-3-Clause) para as copias cifradas (OPS-7): o do sistema se está; se non, o da imaxe
# local furar-age (deploy/ferramentas/age/Dockerfile: alpine por digest, binario oficial de age por SHA-256).
# Así copia.sh, restaurar.sh e D41 funcionan igual en calquera máquina con Docker, sen instalar
# nada fóra do repo. As chaves entran no contedor pola entrada estándar, nunca por variables
# (veríanse en docker inspect) nin por volumes (Colima non comparte /var/folders).
# Uso: source "$FURAR_RAIZ/scripts/lib/age.sh"; age_cifrar … (precisa comun.sh antes)
FURAR_AGE_IMAXE=furar-age:1.3.2
FURAR_AGE_FIN='--furar-fin-chave--'
FURAR_AGE_MODO=${FURAR_AGE_MODO:-}

age_preparar() { # decide sistema ou docker; constrúe a imaxe a primeira vez
  [[ -n $FURAR_AGE_MODO ]] && return 0
  if command -v age >/dev/null && command -v age-keygen >/dev/null; then FURAR_AGE_MODO=sistema; return 0; fi
  command -v docker >/dev/null || falla "sen age nin docker: apt install age"
  if ! docker image inspect "$FURAR_AGE_IMAXE" >/dev/null 2>&1; then
    local saida
    # --force-rm: un RUN fallido non deixa contedor intermedio (builder clásico).
    saida=$(docker build -q --force-rm -t "$FURAR_AGE_IMAXE" - <"$FURAR_RAIZ/deploy/ferramentas/age/Dockerfile" 2>&1) \
      || falla "non se puido construír $FURAR_AGE_IMAXE (alternativa: apt install age): $saida"
  fi
  FURAR_AGE_MODO=docker
}

age_modo() { age_preparar; printf '%s' "$FURAR_AGE_MODO"; }

_age_docker() { # comando… (stdin e stdout pasan)
  docker run --rm -i --network none --read-only --tmpfs /tmp:mode=1777 --cap-drop ALL \
    --security-opt no-new-privileges "$FURAR_AGE_IMAXE" "$@"
}

# No contedor: le a chave ata a liña de fin (o read de sh le byte a byte dun tubo, así que o
# resto da entrada chega intacto a age), gárdaa en /tmp (tmpfs, 0600) e executa age.
_age_con_chave() { # ficheiro_chave orde_age  (stdin: o fluxo de datos)
  local chave=$1 orde=$2
  { cat "$chave"; printf '\n%s\n' "$FURAR_AGE_FIN"; cat; } | _age_docker sh -c '
    umask 077
    while IFS= read -r l && [ "$l" != "$0" ]; do printf "%s\n" "$l"; done >/tmp/k
    '"$orde" "$FURAR_AGE_FIN"
}

age_cifrar() { # destinatario (age1… ou ficheiro de destinatarios); stdin en claro → stdout cifrado
  age_preparar
  if [[ $FURAR_AGE_MODO == sistema ]]; then
    if [[ -f $1 ]]; then age -R "$1"; else age -r "$1"; fi
  elif [[ -f $1 ]]; then
    _age_con_chave "$1" 'exec age -R /tmp/k'
  else
    _age_docker age -r "$1"
  fi
}

age_descifrar() { # identidade ficheiro.age → stdout en claro
  age_preparar
  if [[ $FURAR_AGE_MODO == sistema ]]; then age -d -i "$1" "$2"
  else _age_con_chave "$1" 'exec age -d -i /tmp/k' <"$2"; fi
}

age_xerar_identidade() { # ficheiro (0600) coa chave privada nova
  age_preparar
  (umask 077
    if [[ $FURAR_AGE_MODO == sistema ]]; then age-keygen -o "$1" 2>/dev/null
    else _age_docker age-keygen 2>/dev/null >"$1"; fi)
}

age_publica() { # identidade → chave pública age1…
  age_preparar
  if [[ $FURAR_AGE_MODO == sistema ]]; then age-keygen -y "$1"; else _age_docker age-keygen -y <"$1"; fi
}
