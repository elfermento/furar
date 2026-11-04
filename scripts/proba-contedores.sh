#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D36 · Contedores do perfil full sen privilexios (ADR-0015, SEC3-04): PID 1 sen root, sen
# capacidades (cap_drop ALL; postgres só as cinco que precisa para baixar a postgres),
# no-new-privileges e raíz só lectura en api, web e mailpit.
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"

titulo "D36 · Contedores sen privilexios"
ids=$(dc ps -q)
n=$(wc -l <<<"$ids" | tr -d ' ')
((n >= 5)) || falla "esperaba polo menos 5 contedores e hai $n"
for c in $ids; do
  read -r nome cap_drop cap_add sec ro < <(docker inspect -f \
    '{{index .Config.Labels "com.docker.compose.service"}} {{json .HostConfig.CapDrop}} {{json .HostConfig.CapAdd}} {{json .HostConfig.SecurityOpt}} {{.HostConfig.ReadonlyRootfs}}' "$c")
  # Docker novo devolve «CAP_CHOWN»; o vello, «CHOWN».
  cap_drop=${cap_drop//CAP_/} cap_add=${cap_add//CAP_/}
  uid=$(docker top "$c" -o pid,uid | awk 'NR==2{print $2}')
  [[ $uid != 0 ]] || falla "$nome: o PID 1 corre como root"
  [[ $cap_drop == '["ALL"]' ]] || falla "$nome: cap_drop $cap_drop (esperado [\"ALL\"])"
  case $nome in
    postgres*) esperado='["CHOWN","DAC_OVERRIDE","FOWNER","SETGID","SETUID"]' ;;
    *) esperado=null ;;
  esac
  [[ $cap_add == "$esperado" ]] || falla "$nome: cap_add $cap_add (esperado $esperado)"
  [[ $sec == *no-new-privileges:true* ]] || falla "$nome: sen no-new-privileges ($sec)"
  case $nome in
    api | web | mailpit) [[ $ro == true ]] || falla "$nome: raíz escribible (read_only)" ;;
  esac
  ok "$nome: uid $uid, cap_drop ALL, cap_add $cap_add, no-new-privileges, read_only $ro"
done
echo "D36 OK"
