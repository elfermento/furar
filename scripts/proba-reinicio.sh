#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D38 · Os datos sobreviven a un reinicio: crea un concello, `down` (sen -v), `up --wait`, segue.
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"

titulo "D38 · Reinicio sen perder datos"
slug="reinicio$(date +%s | tail -c 6)"
crear_concello "$slug"
existe_concello "$slug" || falla "o concello $slug non se ve tras crealo"
ok "concello $slug creado"

dc down >/dev/null 2>&1
ok "compose baixado (volumes intactos)"
dc up -d --wait --wait-timeout 300 >/dev/null
ok "compose levantado de novo"

existe_concello "$slug" || falla "o concello $slug desapareceu tras o reinicio"
ok "o concello $slug segue"
[[ -n "$(token xestor-naron)" ]] || falla "o login de xestor-naron non funciona tras o reinicio"
ok "o login segue funcionando"
echo "D38 OK"
