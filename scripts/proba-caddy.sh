#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# X08 · Regras do Caddyfile sen levantar Furar (SEC-01, SEC-05): Caddy real (imaxe fixada)
# co Caddyfile do repo e dous contedores «api» e «keycloak» que só responden 200.
#   - FURAR_ADMIN_IPS fóra da IP de quen pide → /auth/admin e /auth/realms/master/* = 403;
#     /auth/realms/furar/* segue chegando a Keycloak.
#   - Sen FURAR_ADMIN_IPS: pechada tamén desde unha rede privada; co valor local de compose.yaml
#     (redes privadas), ábrese.
#   - /api/*: corpo de 300 KB → 413 e non chega á API; 10 KB → chega. A importación dun concello
#     (/api/v1/municipality-imports) admite ata 50 MB (SEC3-03) e a subida de fotos dunha
#     biblioteca do concello (POST …/municipalities/*/photos), 9 MB: 8 MiB pasan alí e dan 413 noutra ruta.
#   - /tiles (ADR-0023): un servidor de teselas falso devolve o que lle chega. Só z 0–19 con GET e
#     HEAD; ao provedor non chegan X-Forwarded-*, Cookie, Authorization, Referer, Accept-Language
#     nin a query, si o User-Agent de Furar; de volta, sen Set-Cookie, con caché longa e CSP
#     «sandbox», e só PNG (HTML ou SVG do provedor → 502). Plantilla
#     FURAR_MAP_TILES_TEMPLATE con camiño ou con query (KVP, como o WMTS do IGN); sen ela, a do
#     Mapa base do IGN. FURAR_MAP=off: todo /tiles 404 sen tocar o provedor. Con HTTPS_PROXY, a
#     petición ao provedor (por defecto www.ign.es) vai polo proxy.
#   - Sitio HTTPS: «Host: 127.0.0.1» desde fóra → conexión cortada; o healthcheck interno, ok.
set -euo pipefail
cd "$(dirname "$0")/.."
caddy=$(awk '/^caddy:/{print $1}' scripts/imaxes-ferramentas.txt)
rede=furar-proba-caddy-$$
porto=$((20000 + RANDOM % 20000))
contedores=()
limpar() {
  ((${#contedores[@]})) && docker rm -f "${contedores[@]}" >/dev/null 2>&1 || true
  docker network rm "$rede" >/dev/null 2>&1 || true
}
trap limpar EXIT
docker network create "$rede" >/dev/null

# Upstreams falsos cun Caddyfile pasado por variable (sen montar ficheiros: Colima só comparte
# $HOME). A configuración vai despois do bloque global.
falso() { # alias configuración
  # shellcheck disable=SC2016 # $CONF expándeo o sh do contedor
  contedores+=("$(docker run -d --rm --network "$rede" --network-alias "$1" -e CONF="$2" "$caddy" \
    sh -c 'printf "{\n\tadmin off\n}\n%s\n" "$CONF" >/tmp/Caddyfile && exec caddy run --config /tmp/Caddyfile --adapter caddyfile')")
}
# api: le o corpo enteiro antes de responder 200 (se respondese sen lelo, Caddy podería non
# chegar ao límite e un corpo grande pasaría por «200»). keycloak: só 200.
falso api ':8081 {
	respond "api {http.request.body}" 200
}'
falso keycloak ':8082 {
	respond "keycloak" 200
}'
# Servidor de teselas: responde un «PNG» co que lle chegou e intenta deixar unha cookie.
falso teselas ':8090 {
	header Content-Type image/png
	header Set-Cookie "rastro=1"
	respond "metodo={method} host={host} uri={uri} ua={header.User-Agent} xff={header.X-Forwarded-For} xfh={header.X-Forwarded-Host} xfp={header.X-Forwarded-Proto} cookie={header.Cookie} auth={header.Authorization} referer={header.Referer} lang={header.Accept-Language} rid={header.X-Request-Id} fin" 200
}'
# Provedor que responde o que non é unha tesela: HTML (páxina de cota) e SVG con script.
falso teselasmal ':8091 {
	@svg path /svg/*
	header @svg Content-Type image/svg+xml
	respond @svg "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>" 200
	header Content-Type "text/html; charset=utf-8"
	header Content-Disposition inline
	respond "<script>alert(document.cookie)</script>" 429
}'

extra_env=() # variables de máis para o seguinte arrincar
arrincar() { # IPs-admin|"" [enderezo do sitio] → id do contedor
  local env=()
  [[ -n $1 ]] && env=(-e "FURAR_ADMIN_IPS=$1")
  [[ -n ${2:-} ]] && env+=(-e "FURAR_SITE_ADDRESS=$2")
  ((${#extra_env[@]})) && env+=("${extra_env[@]}")
  docker run -d --rm --network "$rede" -p "127.0.0.1:$porto:8080" ${env[@]+"${env[@]}"} \
    -v "$PWD/deploy/caddy/Caddyfile:/etc/caddy/Caddyfile:ro" "$caddy" \
    caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
}
# Host: localhost (o sitio principal): «Host: 127.0.0.1» é o sitio do healthcheck, que só atende a
# loopback e a FURAR_ADMIN_IPS (último bloque).
agardar() { for _ in $(seq 50); do curl -s -o /dev/null -H 'Host: localhost' "http://127.0.0.1:$porto/healthz" && return; sleep 0.2; done; echo "Caddy non arrincou" >&2; exit 1; }
codigo() { curl -s -o /dev/null -w '%{http_code}' -H 'Host: localhost' "$@"; }
esperar() { # descrición obtido esperado
  if [[ $2 == "$3" ]]; then echo "  ✓ $1 ($2)"; else echo "  ✗ $1: obtido $2, esperado $3" >&2; exit 1; fi
}
u="http://127.0.0.1:$porto"

echo "» FURAR_ADMIN_IPS=192.0.2.1/32 (produción pechada)"
c=$(arrincar 192.0.2.1/32); contedores+=("$c"); agardar
for r in /auth/admin /auth/admin/ /auth/admin/master/console/ /auth/realms/master /auth/realms/master/protocol/openid-connect/token; do
  esperar "$r" "$(codigo "$u$r")" 403
done
esperar "/auth/realms/furar/.well-known/openid-configuration" "$(codigo "$u/auth/realms/furar/.well-known/openid-configuration")" 200
esperar "/auth/realms/masterx (non é o realm master)" "$(codigo "$u/auth/realms/masterx/")" 200

echo "» corpo en /api/*"
corpo=$(mktemp)
trap 'rm -f "$corpo" "$corpo.p" "$corpo.g" "$corpo.f"; limpar' EXIT
head -c 307200 /dev/zero | tr '\0' 'a' >"$corpo"
esperar "POST /api/v1/x con 300 KB" "$(codigo -X POST -H 'Content-Type: application/json' --data-binary "@$corpo" "$u/api/v1/x")" 413
esperar "PUT /api/v1/x con 300 KB sen Content-Length (chunked)" \
  "$(codigo -X PUT -H 'Content-Type: application/json' -H 'Transfer-Encoding: chunked' --data-binary "@$corpo" "$u/api/v1/x")" 413
head -c 10240 "$corpo" >"$corpo.p"
esperar "POST /api/v1/x con 10 KB" "$(codigo -X POST -H 'Content-Type: application/json' --data-binary "@$corpo.p" "$u/api/v1/x")" 200
# SEC3-03: a importación dun concello admite ficheiros grandes; o resto da API, non.
head -c 1048576 /dev/zero | tr '\0' 'a' >"$corpo.g"
esperar "POST /api/v1/municipality-imports con 1 MB" \
  "$(codigo -X POST -H 'Content-Type: application/json' --data-binary "@$corpo.g" "$u/api/v1/municipality-imports")" 200
esperar "POST /api/v1/municipalities con 1 MB" \
  "$(codigo -X POST -H 'Content-Type: application/json' --data-binary "@$corpo.g" "$u/api/v1/municipalities")" 413
esperar "POST /api/v1/municipality-imports/x con 1 MB (só a ruta exacta)" \
  "$(codigo -X POST -H 'Content-Type: application/json' --data-binary "@$corpo.g" "$u/api/v1/municipality-imports/x")" 413
# Fotos: 8 MiB (o máximo de Spring por ficheiro) en multipart pasan Caddy só na subida.
head -c 8388608 /dev/zero >"$corpo.f"
fotos=/api/v1/municipalities/naron/photos
esperar "POST …/municipalities/{slug}/photos con 8 MiB (multipart)" "$(codigo -X POST -F "file=@$corpo.f;type=image/jpeg" "$u$fotos")" 200
esperar "POST /api/v1/x con 8 MiB (multipart)" "$(codigo -X POST -F "file=@$corpo.f;type=image/jpeg" "$u/api/v1/x")" 413
esperar "PUT …/photos con 8 MiB (só a subida)" "$(codigo -X PUT -H 'Content-Type: application/json' --data-binary "@$corpo.f" "$u$fotos")" 413
esperar "POST …/photos/x con 8 MiB (só a ruta exacta)" "$(codigo -X POST -F "file=@$corpo.f;type=image/jpeg" "$u$fotos/x")" 413
head -c 9437184 /dev/zero >"$corpo.f"
esperar "POST …/photos con 9 MiB" "$(codigo -X POST -F "file=@$corpo.f;type=image/jpeg" "$u$fotos")" 413
docker rm -f "$c" >/dev/null

# Teselas (ADR-0023) contra o servidor falso; a petición leva todo o que non debe saír.
echo "» /tiles"
tesela() { # ruta [opcións de curl] → corpo do servidor de teselas
  curl -s -H 'Host: localhost' -H 'Cookie: SESSION=segredo' -H 'Authorization: Bearer segredo' \
    -H 'Referer: http://localhost/naron/instalacions' -H 'X-Forwarded-For: 198.51.100.7' \
    -H 'X-Forwarded-Host: veciña.example' -H 'Accept-Language: gl' "${@:2}" "$u$1"
}
extra_env=(-e FURAR_MAP_TILES_UPSTREAM=http://teselas:8090 -e 'FURAR_MAP_TILES_TEMPLATE=/osm/{z}/{x}/{y}.png')
c=$(arrincar ""); contedores+=("$c"); agardar
extra_env=()
eco=$(tesela '/tiles/14/7905/5878.png?k=v')
esperado="metodo=GET host=teselas uri=/osm/14/7905/5878.png ua=Furar (+https://github.com/elfermento/furar) xff= xfh= xfp= cookie= auth= referer= lang= rid= fin"
esperar "o que chega ao provedor" "$eco" "$esperado"
cab=$(tesela /tiles/14/7905/5878.png -I | tr -d '\r')
esperar "HEAD /tiles/14/7905/5878.png" "$(head -1 <<<"$cab" | cut -d' ' -f2)" 200
esperar "Content-Type" "$(grep -i '^content-type:' <<<"$cab" | cut -d' ' -f2-)" image/png
esperar "Cache-Control" "$(grep -i '^cache-control:' <<<"$cab" | cut -d' ' -f2-)" "public, max-age=604800"
esperar "Set-Cookie do provedor" "$(grep -ci '^set-cookie:' <<<"$cab" || true)" 0
esperar "CSP da tesela" "$(grep -i '^content-security-policy:' <<<"$cab" | cut -d' ' -f2-)" "default-src 'none'; sandbox"
esperar "/tiles/0/0/0.png" "$(codigo "$u/tiles/0/0/0.png")" 200
esperar "/tiles/19/1/1.png" "$(codigo "$u/tiles/19/1/1.png")" 200
for r in /tiles/20/1/1.png /tiles/14/1/1.jpg /tiles/14/a/1.png /tiles/14/1/1.png/x /tiles/ /tiles/../index.html; do
  esperar "$r" "$(codigo --path-as-is "$u$r")" 404
done
esperar "POST /tiles/14/1/1.png" "$(codigo -X POST "$u/tiles/14/1/1.png")" 405
esperar "DELETE /tiles/14/1/1.png" "$(codigo -X DELETE "$u/tiles/14/1/1.png")" 405
docker rm -f "$c" >/dev/null
# O provedor responde HTML ou SVG: non chega ao navegador (só PNG).
extra_env=(-e FURAR_MAP_TILES_UPSTREAM=http://teselasmal:8091)
c=$(arrincar ""); contedores+=("$c"); agardar
extra_env=()
mal=$(curl -s -D - -H 'Host: localhost' "$u/tiles/1/0/0.png" | tr -d '\r')
esperar "provedor en text/html" "$(head -1 <<<"$mal" | cut -d' ' -f2)" 502
esperar "sen HTML do provedor" "$(grep -c 'alert' <<<"$mal" || true)" 0
esperar "sen Content-Disposition do provedor" "$(grep -ci '^content-disposition:' <<<"$mal" || true)" 0
docker rm -f "$c" >/dev/null
extra_env=(-e FURAR_MAP_TILES_UPSTREAM=http://teselasmal:8091 -e 'FURAR_MAP_TILES_TEMPLATE=/svg/{z}/{x}/{y}.png')
c=$(arrincar ""); contedores+=("$c"); agardar
extra_env=()
mal=$(curl -s -D - -H 'Host: localhost' "$u/tiles/1/0/0.png" | tr -d '\r')
esperar "provedor en image/svg+xml" "$(head -1 <<<"$mal" | cut -d' ' -f2)" 502
esperar "sen SVG do provedor" "$(grep -c 'alert' <<<"$mal" || true)" 0
docker rm -f "$c" >/dev/null
# Sen FURAR_MAP_TILES_TEMPLATE: o Mapa base do IGN (WMTS KVP); a query do navegador non pasa.
extra_env=(-e FURAR_MAP_TILES_UPSTREAM=http://teselas:8090)
c=$(arrincar ""); contedores+=("$c"); agardar
extra_env=()
ign='/wmts/ign-base?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=IGNBaseTodo&STYLE=default&TILEMATRIXSET=GoogleMapsCompatible&FORMAT=image/png'
esperar "plantilla por defecto (IGN)" "$(tesela '/tiles/3/2/1.png?TILEROW=9&k=v' | grep -oE 'uri=[^ ]+')" \
  "uri=$ign&TILEMATRIX=3&TILECOL=2&TILEROW=1"
docker rm -f "$c" >/dev/null
# Plantilla propia con query (KVP): a query é a da plantilla, nunca a do navegador.
extra_env=(-e FURAR_MAP_TILES_UPSTREAM=http://teselas:8090
  -e 'FURAR_MAP_TILES_TEMPLATE=/wmts?LAYER=base&TILEMATRIX={z}&TILECOL={x}&TILEROW={y}&FORMAT=image/png')
c=$(arrincar ""); contedores+=("$c"); agardar
extra_env=()
esperar "plantilla KVP" "$(tesela '/tiles/14/7905/5878.png?LAYER=outra' | grep -oE 'uri=[^ ]+')" \
  "uri=/wmts?LAYER=base&TILEMATRIX=14&TILECOL=7905&TILEROW=5878&FORMAT=image/png"
docker rm -f "$c" >/dev/null
# FURAR_MAP=off: sen mapa, aínda que o provedor responda; nada chega a el (o 404 é o de Caddy).
extra_env=(-e FURAR_MAP=off -e FURAR_MAP_TILES_UPSTREAM=http://teselas:8090)
c=$(arrincar ""); contedores+=("$c"); agardar
extra_env=()
for r in /tiles/14/7905/5878.png /tiles/0/0/0.png /tiles/; do
  esperar "sen mapa: $r" "$(codigo "$u$r")" 404
done
esperar "sen mapa: POST /tiles/14/1/1.png" "$(codigo -X POST "$u/tiles/14/1/1.png")" 404
esperar "sen mapa: corpo do 404" "$(tesela /tiles/14/7905/5878.png | grep -c 'metodo=' || true)" 0
docker rm -f "$c" >/dev/null
# Proxy saínte (FURAR_HTTP_PROXY → HTTPS_PROXY en compose.yaml): co provedor por defecto (HTTPS),
# Caddy abre o túnel polo proxy. O proxy falso rexeita o CONNECT: nada sae da máquina.
falso proxy ':3128 {
	log
	respond "proxy" 403
}'
proxy=${contedores[${#contedores[@]}-1]}
extra_env=(-e HTTPS_PROXY=http://proxy:3128)
c=$(arrincar ""); contedores+=("$c"); agardar
extra_env=()
esperar "tesela co proxy que rexeita" "$(codigo "$u/tiles/1/0/0.png")" 502
esperar "CONNECT ao provedor polo proxy" \
  "$(docker logs "$proxy" 2>&1 | grep -c '"method":"CONNECT".*"host":"www.ign.es:443"' || true)" 1
docker rm -f "$c" >/dev/null

# Desde outro contedor da rede de Docker (IP privada 172.x, como chegaría nun servidor Linux).
desde_rede() { # contedor ruta → código HTTP ou «sen resposta»
  local ip r
  ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$1")
  r=$(docker run --rm --network "$rede" "$caddy" wget -q -S -O /dev/null --header "Host: localhost" "http://$ip:8080$2" 2>&1 \
    | grep -oE 'HTTP/[0-9.]+ [0-9]{3}' | head -1 | cut -d' ' -f2 || true)
  echo "${r:-sen resposta}"
}

# Curso (2026-10-23): sen a variable, a administración queda pechada (antes abría as redes privadas).
echo "» sen FURAR_ADMIN_IPS (pechada por defecto)"
c=$(arrincar ""); contedores+=("$c"); agardar
esperar "/auth/admin/ desde a rede de Docker" "$(desde_rede "$c" /auth/admin/)" 403
esperar "/auth/realms/master/ desde a rede de Docker" "$(desde_rede "$c" /auth/realms/master/)" 403
esperar "/auth/realms/furar/ desde a rede de Docker" "$(desde_rede "$c" /auth/realms/furar/)" 200
docker rm -f "$c" >/dev/null

echo "» FURAR_ADMIN_IPS de compose.yaml (local: redes privadas e loopback)"
c=$(arrincar "127.0.0.0/8 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 ::1/128 fc00::/7"); contedores+=("$c"); agardar
esperar "/auth/admin/ desde a rede de Docker" "$(desde_rede "$c" /auth/admin/)" 200
esperar "/auth/realms/master/ desde a rede de Docker" "$(desde_rede "$c" /auth/realms/master/)" 200
docker rm -f "$c" >/dev/null

# SEC2-02: en produción (sitio HTTPS) o sitio http://127.0.0.1:8080 só é para o healthcheck.
echo "» FURAR_SITE_ADDRESS=https://reservas.example.gal, FURAR_ADMIN_IPS=127.0.0.1/32"
c=$(arrincar 127.0.0.1/32 https://reservas.example.gal); contedores+=("$c")
for _ in $(seq 50); do docker exec "$c" wget -qO- http://127.0.0.1:8080/healthz >/dev/null 2>&1 && break; sleep 0.2; done
esperar "healthcheck dentro do contedor (Host: 127.0.0.1)" "$(docker exec "$c" wget -qO- http://127.0.0.1:8080/healthz)" ok
# «Desde fóra» = outro contedor da rede: co porto publicado, Colima e Docker Desktop fan ver
# loopback a Caddy; nun servidor Linux, o DNAT de Docker conserva a IP de quen pide.
ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$c")
fora() { # Host ruta → código HTTP ou «sen resposta»
  local r
  r=$(docker run --rm --network "$rede" "$caddy" wget -q -S -O /dev/null --header "Host: $1" "http://$ip:8080$2" 2>&1 \
    | grep -oE 'HTTP/[0-9.]+ [0-9]{3}' | head -1 | cut -d' ' -f2 || true)
  echo "${r:-sen resposta}"
}
esperar "/api/v1/x desde fóra con «Host: 127.0.0.1»" "$(fora 127.0.0.1 /api/v1/x)" "sen resposta"
esperar "/ desde fóra con «Host: 127.0.0.1»" "$(fora 127.0.0.1 /)" "sen resposta"
esperar "HTTP do sitio público desde fóra → HTTPS" "$(fora reservas.example.gal /api/v1/x)" 308
echo "X08 OK"
