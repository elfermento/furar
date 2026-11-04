#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D41 · Copia e restauración de ida e volta (ADR-0015):
# marca (concello + usuario de Keycloak) → copia.sh → borra o volume → up (baleiro) →
# a marca non está → restaurar.sh → a marca e o login volven.
set -euo pipefail
source "$(dirname "$0")/lib/comun.sh"

titulo "D41 · Copia e restauración"
proxecto=$(dc config --format json | jq -r .name)
# pgdata (full) ou pgdata-prod (compose.prod.yaml): o volume que monta postgres.
volume=$(dc config --format json | jq -r '.services.postgres.volumes[] | select(.target == "/var/lib/postgresql") | .source')
[[ -n $volume && $volume != null ]] || falla "non atopo o volume de postgres"
dc up -d --wait --wait-timeout 300 >/dev/null

marca="copia$(date +%s | tail -c 6)"
contrasinal="Proba-copia-$(date +%s)-ok" # cumpre a política do realm
crear_concello "$marca"
crear_usuario_keycloak "$marca" "$contrasinal"
[[ -n "$(token "$marca@example.org" "$contrasinal")" ]] || falla "o usuario $marca non pode entrar antes da copia"
ok "marca creada: concello e usuario $marca"

dir=$FURAR_RAIZ/.verificar/copia-$marca
"$FURAR_RAIZ/scripts/copia.sh" "$dir" >/dev/null
ok "copia feita en $dir"
grep -qE '^FLYWAY_ULTIMA=[0-9]+$' "$dir/furar.txt" && grep -qx "FURAR_VERSION=$(furar_version_actual)" "$dir/furar.txt" \
  || falla "a copia non garda a versión de Furar e a última migración: $(tr '\n' ' ' <"$dir/furar.txt")"
ok "a copia garda $(tr '\n' ' ' <"$dir/furar.txt")"
# OPS-R2-2: cunha FURAR_VERSION distinta no .env, restaurar.sh négase antes de parar nada.
if FURAR_VERSION=v0.0.0-outra "$FURAR_RAIZ/scripts/restaurar.sh" "$dir" --si >/dev/null 2>&1; then
  falla "restaurar.sh aceptou unha copia doutra versión de Furar"
fi
existe_concello "$marca" || falla "restaurar.sh rexeitou a copia pero tocou os datos"
ok "restaurar.sh rexeita a copia con outra FURAR_VERSION no .env, sen tocar nada"

dc down >/dev/null 2>&1
docker volume rm "${proxecto}_$volume" >/dev/null
ok "volume ${proxecto}_$volume borrado"
dc up -d --wait --wait-timeout 300 >/dev/null
! existe_concello "$marca" || falla "o concello $marca segue tras borrar o volume: a proba non vale"
ok "base nova sen a marca"

"$FURAR_RAIZ/scripts/restaurar.sh" "$dir" --si >/dev/null
existe_concello "$marca" || falla "o concello $marca non volveu coa restauración"
ok "concello $marca restaurado"
agardar_http "$FURAR_URL/auth/realms/furar/.well-known/openid-configuration" 200
[[ -n "$(token "$marca@example.org" "$contrasinal")" ]] || falla "o usuario $marca non pode entrar tras restaurar"
ok "login de $marca tras restaurar"
rm -rf "$dir"

# Copia cifrada con age (OPS-7): sen nada en claro e restaurable coa chave privada. Sempre se
# proba: age do sistema ou, se non está, o da imaxe local furar-age (scripts/lib/age.sh).
source "$FURAR_RAIZ/scripts/lib/age.sh"
ok "age: $(age_modo) ($([[ $FURAR_AGE_MODO == sistema ]] && age --version || echo "$FURAR_AGE_IMAXE"))"
chave=$(mktemp -d "$FURAR_RAIZ/.verificar/chave-age.XXXXXX")
age_xerar_identidade "$chave/identidade.txt"
publica=$(age_publica "$chave/identidade.txt")
[[ $publica == age1* ]] || falla "age-keygen non deu unha chave pública age1…: $publica"
dir=$FURAR_RAIZ/.verificar/copia-cifrada-$marca
FURAR_COPIAS_AGE_DESTINATARIO=$publica "$FURAR_RAIZ/scripts/copia.sh" "$dir" >/dev/null
[[ -s $dir/furar.dump.age && -s $dir/keycloak.dump.age && ! -e $dir/furar.dump && ! -e $dir/keycloak.dump ]] \
  || falla "a copia cifrada deixou volcados en claro ou non cifrou"
[[ $(head -c 21 "$dir/furar.dump.age") == age-encryption.org/v1 ]] || falla "furar.dump.age non ten a cabeceira de age"
ok "copia cifrada con age (sen volcados en claro)"
# Cunha chave privada que non é a do destinatario, restaurar.sh falla sen tocar os datos.
age_xerar_identidade "$chave/outra.txt"
if FURAR_COPIAS_AGE_IDENTIDADE=$chave/outra.txt "$FURAR_RAIZ/scripts/restaurar.sh" "$dir" --si >/dev/null 2>&1; then
  falla "restaurar.sh aceptou a copia cifrada cunha chave privada que non é a súa"
fi
existe_concello "$marca" || falla "a restauración coa chave errada tocou os datos"
ok "coa chave privada errada, restaurar.sh négase sen tocar nada"
despois="${marca}x"
crear_concello "$despois"
FURAR_COPIAS_AGE_IDENTIDADE=$chave/identidade.txt "$FURAR_RAIZ/scripts/restaurar.sh" "$dir" --si >/dev/null
! existe_concello "$despois" || falla "a restauración cifrada non volveu ao estado da copia"
existe_concello "$marca" || falla "o concello $marca non está tras a restauración cifrada"
ok "restauración da copia cifrada"
rm -rf "$dir" "$chave"

# Rotación (--conservar N): só borra directorios AAAAMMDD-HHMMSS e deixa os N máis novos.
rot=$(mktemp -d "$FURAR_RAIZ/.verificar/rotacion.XXXXXX")
mkdir -p "$rot/20200101-000000" "$rot/20200102-000000" "$rot/20200103-000000" "$rot/outra-cousa"
FURAR_COPIAS_DIR=$rot "$FURAR_RAIZ/scripts/copia.sh" --conservar 2 >/dev/null
restan=$(find "$rot" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort | tr '\n' ' ')
[[ $restan == "20200103-000000 2"*" outra-cousa " && $(wc -w <<<"$restan") -eq 3 ]] \
  || falla "rotación incorrecta: quedan $restan"
ok "rotación --conservar 2: quedan a nova e 20200103-000000; outra-cousa non se toca"
rm -rf "$rot"

# copia-diaria.sh (OPS-R2-4): cun rclone falso que apunta as chamadas; se unha fase falla, sae con
# erro e o rexistro dio; se vai ben, borra no destino as copias vellas.
rot=$(mktemp -d "$FURAR_RAIZ/.verificar/diaria.XXXXXX")
mkdir -p "$rot/bin"
printf '#!/bin/sh\necho "$*" >>"%s/rclone.log"\n[ "$1" != copy ] || [ -z "$RCLONE_FALLA" ] || exit 1\n' "$rot" >"$rot/bin/rclone"
chmod +x "$rot/bin/rclone"
diaria() { PATH="$rot/bin:$PATH" FURAR_COPIAS_DIR="$rot/copias" FURAR_COPIAS_DESTINO=remoto:furar FURAR_COPIAS_DIAS_DESTINO=30 \
  "$FURAR_RAIZ/scripts/copia-diaria.sh"; }
diaria >"$rot/ok.log" 2>&1 || { cat "$rot/ok.log"; falla "copia-diaria.sh fallou"; }
grep -q 'fin: correcto' "$rot/ok.log" && grep -q '^delete --min-age 30d remoto:furar/' "$rot/rclone.log" \
  || falla "copia-diaria.sh non aplicou a conservación no destino"
ok "copia-diaria.sh: copia, envío e conservación de 30 días no destino"
if RCLONE_FALLA=1 diaria >"$rot/mal.log" 2>&1; then falla "copia-diaria.sh saíu ben con rclone fallando"; fi
grep -q 'ERRO: rclone copy' "$rot/mal.log" || falla "o rexistro de copia-diaria.sh non di que fase fallou"
ok "copia-diaria.sh: se o envío falla, sae con erro e o rexistro dio"
# OPS-R3-6: con outra execución en curso (cerrollo collido), non fai nada e sae con erro.
( cerrollo "$rot/copias/.lock" && touch "$rot/collido"; exec sleep 60 ) &
suxeita=$!
for _ in $(seq 50); do [[ -e $rot/collido ]] && break; sleep 0.1; done
[[ -e $rot/collido ]] || falla "a proba non puido coller o cerrollo"
: >"$rot/rclone.log"
if diaria >"$rot/dobre.log" 2>&1; then kill "$suxeita"; falla "copia-diaria.sh correu con outra copia en curso"; fi
kill "$suxeita" 2>/dev/null; wait "$suxeita" 2>/dev/null || true
grep -q 'xa hai unha copia en curso' "$rot/dobre.log" && [[ ! -s $rot/rclone.log ]] \
  || falla "copia-diaria.sh non respectou o cerrollo"
ok "copia-diaria.sh: con outra copia en curso, sae con erro sen copiar nin enviar"
rm -rf "$rot"
echo "D41 OK"
