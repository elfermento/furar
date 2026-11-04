#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Copia diaria para cron (OPS-R2-4, docs/instalacion.md §6): copia.sh, envío fóra con rclone e
# conservación tamén no destino. Rexistra as dúas fases co seu resultado e sae con erro se
# algunha falla; opcionalmente avisa a un comprobador (ping) do éxito ou do fallo.
#   scripts/copia-diaria.sh >> /var/log/furar-copia.log 2>&1
# Valores (no contorno ou no .env do servidor):
#   FURAR_COPIAS_CONSERVAR      copias que quedan no servidor (por defecto 14)
#   FURAR_COPIAS_DESTINO        destino de rclone, p. ex. copias-concello:furar (baleiro: sen envío)
#   FURAR_COPIAS_DIAS_DESTINO   días que se conservan no destino (por defecto 30; 0 = non borrar)
#   FURAR_COPIAS_PING_URL       URL que recibe GET ao rematar ben e GET a <URL>/fail se falla
#                               (calquera comprobador de tarefas con ese formato, tamén autoaloxado); opcional
set -uo pipefail
source "$(dirname "$0")/lib/comun.sh"

valor() { local v=${!1:-}; [[ -n $v ]] || v=$(desde_env "$1"); echo "${v:-$2}"; }
conservar=$(valor FURAR_COPIAS_CONSERVAR 14)
destino=$(valor FURAR_COPIAS_DESTINO "")
dias=$(valor FURAR_COPIAS_DIAS_DESTINO 30)
ping=$(valor FURAR_COPIAS_PING_URL "")
rexistro() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }
avisar() { [[ -z $ping ]] || curl -fsS -m 10 --retry 3 -o /dev/null "$ping$1" || rexistro "aviso: non se puido avisar a $ping$1"; }
fallar() { rexistro "ERRO: $*"; avisar /fail; exit 1; }

base=${FURAR_COPIAS_DIR:-$FURAR_RAIZ/copias}
mkdir -p "$base"
# Dúas execucións á vez (cron lento e unha manual) competirían na rotación e en rclone (OPS-R3-6).
# Sen ping /fail: a outra execución é a que informa.
cerrollo "$base/.lock" || { rexistro "ERRO: xa hai unha copia en curso ($base/.lock)"; exit 1; }
rexistro "inicio da copia (conservar $conservar no servidor)"
avisar /start
saida=$("$FURAR_RAIZ/scripts/copia.sh" --conservar "$conservar" 2>&1) || { printf '%s\n' "$saida"; fallar "copia.sh fallou"; }
printf '%s\n' "$saida"
dir=$(tail -1 <<<"$saida")
[[ -d $dir ]] || fallar "copia.sh non devolveu o directorio da copia"
rexistro "copia feita: $dir"

if [[ -n $destino ]]; then
  command -v rclone >/dev/null || fallar "FURAR_COPIAS_DESTINO definido e non está rclone"
  rclone copy --exclude '/.lock*' "$base/" "$destino/" || fallar "rclone copy a $destino fallou"
  rexistro "enviada a $destino"
  if ((dias > 0)); then
    # A conservación que fixa o concello aplícase tamén fóra do servidor (RGPD: limitación do prazo).
    rclone delete --min-age "${dias}d" "$destino/" && rclone rmdirs --leave-root "$destino/" \
      || fallar "non se puideron borrar en $destino as copias de máis de $dias días"
    rexistro "en $destino só quedan copias de ata $dias días"
  fi
else
  rexistro "aviso: sen FURAR_COPIAS_DESTINO, a copia só está neste servidor"
fi
avisar ""
rexistro "fin: correcto"
