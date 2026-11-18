#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# X09 · O galego e o castelán de Keycloak están completos. Keycloak non trae galego e o seu
# castelán vai de usted e con textos en inglés: o tema furar ten que traducir, nos dous idiomas,
# TODAS as claves en inglés da versión fixada no Dockerfile:
#   login   ← theme/base/login           (org.keycloak.keycloak-themes)
#   email   ← theme/base/email           (org.keycloak.keycloak-themes)
#   account ← theme/keycloak.v3/account  (org.keycloak.keycloak-account-ui)
# Falla se falta unha clave ou se a tradución non ten os mesmos parámetros ({0}, {{nome}}).
# Ao subir Keycloak, as claves novas aparecen aquí. Os modelos que o tema substitúe (login/*.ftl e
# email/{html,text}/*.ftl con «Base: sha256 …») teñen que partir da base da versión fixada, sen
# textos fixos en inglés e coas claves furar* que usan en gl e es. As follas da consola
# (account/resources/css) só pisan clases e variables de PatternFly que a consola fixada trae, e as
# súas url() relativas chegan a ficheiros do tema (a letra é a do tema de acceso, sen copia).
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
# Textos que deixan fóra un parámetro a propósito: o botón «Configurar» da consola (sen o nome do
# método, que xa vai no título da fila; decisión do mantedor do 2026-11-18).
sen_nome = {'account': {'setUpNew'}}
mal = 0
for jar, ruta, tema in casos:
    en = claves(zipfile.ZipFile(f'{tmp}/{jar}.jar').read(ruta).decode('utf-8'))
    for lingua in ('gl', 'es'):
        tr = claves(open(f'deploy/keycloak/themes/furar/{tema}/messages/messages_{lingua}.properties', encoding='utf-8').read())
        faltan = [k for k in en if k not in tr]
        distintos = [k for k in en if k in tr and k not in sen_nome.get(tema, set()) and params(en[k]) != params(tr[k])]
        print(f'{tema}/{lingua}: {len(en)} claves en inglés, {len(tr)} traducidas, faltan {len(faltan)}, parámetros distintos {len(distintos)}')
        for k in faltan:
            print(f'  falta {tema}/{lingua}/{k}={en[k]}')
        for k in distintos:
            print(f'  parámetros {tema}/{lingua}/{k}: en {params(en[k])} {lingua} {params(tr[k])}')
        mal += len(faltan) + len(distintos)
# Modelos substituídos no tema (login/*.ftl e email/{html,text}/*.ftl con «Base: sha256 …»): a
# base ten que ser a da versión fixada (se Keycloak a cambia, hai que volver aplicar os cambios),
# sen textos en inglés fóra das mensaxes, e cada clave furar* que usan, en gl e es.
import glob, hashlib, os
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
# Estilo da consola (account/resources/css/*.css): cada clase e variable de PatternFly que pisa
# segue na consola da versión fixada (se Keycloak as cambia, o estilo deixaría de aplicarse sen
# erro), e cada url() relativa chega a un ficheiro do tema (a letra é a do tema de acceso, sen copia).
import posixpath
ui = zipfile.ZipFile(f'{tmp}/account-ui.jar')
consola = ''.join(ui.read(n).decode('utf-8', 'replace') for n in ui.namelist()
                  if n.startswith('theme/keycloak.v3/account/resources/assets/') and n.endswith(('.css', '.js')))
for folla in sorted(glob.glob('deploy/keycloak/themes/furar/account/resources/css/*.css')):
    css = re.sub(r'/\*.*?\*/', '', open(folla, encoding='utf-8').read(), flags=re.S)
    pf = sorted(set(re.findall(r'\.(pf-(?:v5-[\w-]+|m-[\w-]+))', css)) | set(re.findall(r'(--pf-v5-[\w-]+)\s*:', css)))
    erros = [f'{t} xa non está na consola de Keycloak' for t in pf if t not in consola]
    for u in re.findall(r'url\(([^)]+)\)', css):
        u = u.strip('\'"')
        if u.startswith(('data:', 'http:', 'https:', '/')):
            erros.append(f'url({u}): só rutas relativas ao tema')
            continue
        servida = posixpath.normpath(posixpath.join('account/furar/css', u)).split('/')
        if len(servida) < 3 or servida[1] != 'furar' or not os.path.isfile(f'deploy/keycloak/themes/furar/{servida[0]}/resources/' + '/'.join(servida[2:])):
            erros.append(f'url({u}) non chega a ningún ficheiro do tema furar')
    print(f'{folla.split("/themes/furar/", 1)[1]}: {len(pf)} selectores e variables de PatternFly, {len(erros)} erros')
    for e in erros:
        print(f'  {e}')
    mal += len(erros)
sys.exit(1 if mal else 0)
FIN
echo "X09 OK"
