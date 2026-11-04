#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# X09 · O galego de Keycloak está completo (AP-05). Keycloak non trae galego: o tema furar
# ten que traducir TODAS as claves en inglés da versión fixada no Dockerfile:
#   login   ← theme/base/login           (org.keycloak.keycloak-themes)
#   email   ← theme/base/email           (org.keycloak.keycloak-themes)
#   account ← theme/keycloak.v3/account  (org.keycloak.keycloak-account-ui)
# Falla se falta unha clave ou se a tradución non ten os mesmos parámetros ({0}, {{nome}}).
# Ao subir Keycloak, as claves novas aparecen aquí. Os modelos que o tema substitúe (login/*.ftl e
# email/{html,text}/*.ftl con «Base: sha256 …») teñen que partir da base da versión fixada, sen
# textos fixos en inglés e coas claves furar* que usan en gl e es.
set -euo pipefail
cd "$(dirname "$0")/.."
base=$(awk '/^FROM quay\.io\/keycloak\/keycloak/{print $2; exit}' deploy/keycloak/Dockerfile)
version=$(sed -E 's/.*:([0-9.]+)@.*/\1/' <<<"$base")
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"; [[ -n ${id:-} ]] && docker rm "$id" >/dev/null 2>&1 || true' EXIT
id=$(docker create "$base")
for j in themes account-ui; do
  docker cp -q "$id:/opt/keycloak/lib/lib/main/org.keycloak.keycloak-$j-$version.jar" "$tmp/$j.jar"
done
python3 -I - "$tmp" <<'FIN'
import re, sys, zipfile
tmp = sys.argv[1]
def claves(texto):
    d = {}
    for l in texto.splitlines():
        if not l.strip() or l.lstrip().startswith(('#', '!')):
            continue
        m = re.match(r'^([^=:\s]+)\s*[=:](.*)$', l)
        if m:
            d[m.group(1)] = m.group(2)
    return d
param = re.compile(r'\{\{\w+\}\}|\{\d+(?:,[^}]*)?\}|\$\{[^}]+\}')
def params(v):
    # Só o índice ou o nome: {0,choice,…} conta como {0}.
    return sorted({re.sub(r'^\{(\d+),.*\}$', r'{\1}', p) for p in param.findall(v)})
casos = [
    ('themes', 'theme/base/login/messages/messages_en.properties', 'login'),
    ('themes', 'theme/base/email/messages/messages_en.properties', 'email'),
    ('account-ui', 'theme/keycloak.v3/account/messages/messages_en.properties', 'account'),
]
mal = 0
for jar, ruta, tema in casos:
    en = claves(zipfile.ZipFile(f'{tmp}/{jar}.jar').read(ruta).decode('utf-8'))
    gl = claves(open(f'deploy/keycloak/themes/furar/{tema}/messages/messages_gl.properties', encoding='utf-8').read())
    faltan = [k for k in en if k not in gl]
    distintos = [k for k in en if k in gl and params(en[k]) != params(gl[k])]
    print(f'{tema}: {len(en)} claves en inglés, {len(gl)} en galego, faltan {len(faltan)}, parámetros distintos {len(distintos)}')
    for k in faltan:
        print(f'  falta {tema}/{k}={en[k]}')
    for k in distintos:
        print(f'  parámetros {tema}/{k}: en {params(en[k])} gl {params(gl[k])}')
    mal += len(faltan) + len(distintos)
# Modelos substituídos no tema (login/*.ftl e email/{html,text}/*.ftl con «Base: sha256 …»): a
# base ten que ser a da versión fixada (se Keycloak a cambia, hai que volver aplicar os cambios),
# sen textos en inglés fóra das mensaxes, e cada clave furar* que usan, en gl e es.
import glob, hashlib
# Textos do realm (localizationTexts): ningún desde o 2026-11-03 (os de privacidade son de cada
# concello); cada clave furar* vai nas mensaxes do tema.
do_realm = set()
ingles = re.compile(r'Warning alert|en-US')
orixes = [('login', 'deploy/keycloak/themes/furar/login/*.ftl', 'theme/keycloak.v2/login/'),
          ('email', 'deploy/keycloak/themes/furar/email/html/*.ftl', 'theme/base/email/html/'),
          ('email', 'deploy/keycloak/themes/furar/email/text/*.ftl', 'theme/base/email/text/')]
for tema, patron, orixe_dir in orixes:
    for ftl in sorted(glob.glob(patron)):
        texto = open(ftl, encoding='utf-8').read()
        m = re.search(r'Base: sha256 ([0-9a-f]{64})', texto)
        if not m:
            continue
        nome = ftl.split('/themes/furar/', 1)[1]
        orixe = zipfile.ZipFile(f'{tmp}/themes.jar').read(orixe_dir + ftl.rsplit('/', 1)[1])
        corpo = re.sub(r'<#--.*?-->', '', texto, flags=re.S)
        erros = []
        if hashlib.sha256(orixe).hexdigest() != m.group(1):
            erros.append(f'a base de {orixe_dir} cambiou: volve aplicar os cambios e actualiza o sha256')
        erros += [f'texto en inglés: {t}' for t in sorted(set(ingles.findall(corpo)))]
        for k in sorted(set(re.findall(r'msg\("(furar\w+)"', corpo)) - do_realm):
            for lingua in ('gl', 'es'):
                if k not in claves(open(f'deploy/keycloak/themes/furar/{tema}/messages/messages_{lingua}.properties', encoding='utf-8').read()):
                    erros.append(f'falta {k} en {lingua}')
        print(f'{nome}: base sha256 {m.group(1)[:12]}…, {len(erros)} erros')
        for e in erros:
            print(f'  {e}')
        mal += len(erros)
sys.exit(1 if mal else 0)
FIN
echo "X09 OK"
