#!/bin/sh
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Xera o fragmento de Caddy coa Content-Security-Policy da web (ADR-0007).
# Le os hashes que `security.autoCsp` de Angular deixa en index.html e engádeos a script-src.
# style-src sen 'unsafe-inline': o tema e os [style.*] aplícanse por CSSOM (setProperty), que a
# CSP non bloquea, e ningún compoñente ten estilos propios (Angular inxectaría <style>).
# Uso (no build da imaxe web): csp.sh dist/.../browser/index.html > /etc/caddy/csp.caddy
set -eu
index=${1:?uso: csp.sh index.html}
[ -f "$index" ] || { echo "csp.sh: non existe $index" >&2; exit 1; }

hashes=$(grep -oE "'sha(256|384|512)-[A-Za-z0-9+/=]+'" "$index" | sort -u | tr '\n' ' ' | sed 's/ $//')
script_src="'self'${hashes:+ $hashes}"

policy="default-src 'self'; script-src $script_src; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-src 'self'; worker-src 'self'; manifest-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"

printf '# Xerado por deploy/caddy/csp.sh desde %s. Non editar.\nheader Content-Security-Policy "%s"\n' "$(basename "$index")" "$policy"
