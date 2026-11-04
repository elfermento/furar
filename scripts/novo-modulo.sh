#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Xera o esqueleto dun módulo desde templates/module (docs/como-engadir-un-modulo.md, D45):
# API hexagonal completa con tests (dominio, caso de uso, IT), migración con municipality_id,
# na web lista, alta e edición (If-Match; con --custom-fields, <furar-custom-fields-form>), cos
# seus specs, e E2E con axe (web/e2e/<nome>.e2e.ts); permiso, ruta e textos gl/es. Pasa as gardas
# tal como sae.
#
#   scripts/novo-modulo.sh [--custom-fields TARGET] <módulo> [Nome] [plural] [ruta]
#       --custom-fields  con campos personalizados (ADR-0011): TARGET de CustomFieldTarget
#                        libre e sen rexistrar (SPACE, BOOKING, RESIDENT_PROFILE, ORGANIZATION
#                        ou un novo; MUNICIPALITY e FACILITY xa teñen dono). Engade
#                        validación e proxección no caso de uso, valores no almacén tipado de
#                        customfields (CustomFieldValueStore, desde a persistencia), customFields
#                        nos DTO e <Nome>CustomFieldsIT.
#       módulo  paquete Java, en inglés e minúsculas ............ facility
#       Nome    agregado, PascalCase (por defecto: Módulo) ....... Facility
#       plural  URL e etiqueta OpenAPI, kebab-case (Nome+s) ...... facilities
#       ruta    ruta visible da web, en galego (o plural) ........ instalacions
#   scripts/novo-modulo.sh --remove <módulo>
#       borra o xerado, restaura os ficheiros que se tocaron e as saídas compiladas.
#
# Sempre: alta, lista, detalle e edición (PUT con If-Match), API pública síncrona <Nome>Queries
# (ARQ-3) e evento <Nome>Created. Marcadores da plantilla: liñas con «// @cf» ou «-- @cf» (só con
# campos), «// @nocf» (só sen eles), bloques «// @cf{ … // @cf}» (en HTML «<!-- @cf{ -->»)
# e ficheiros «*.cf».
#
# Copia en .novo-modulo/<módulo>/ (ignorado) os ficheiros existentes que modifica: gl.json,
# es.json, app.routes.ts, api/openapi.json e migrations.lock. Permisos (<Nome>Permission), destino
# de campos (<Nome>CustomFieldTarget) e mensaxes da API (messages/<módulo>_*.properties) van no
# propio módulo (ADR-0005 ARQ3-2).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
# shellcheck source=scripts/entorno.sh
source scripts/entorno.sh

PLANTILLA=templates/module
ESTADO=.novo-modulo
ROUTES=web/src/app/app.routes.ts
MARCA_RUTAS='// novo-modulo:'
TOCADOS=(web/src/i18n/gl.json web/src/i18n/es.json "$ROUTES" api/openapi.json api/src/main/resources/db/migrations.lock)

die() { echo "novo-modulo: $*" >&2; exit 1; }
uso() { sed -n '5,21p' "$0"; exit 64; }

# --------------------------------------------------------------------------------------------
eliminar() {
  local m=$1 d=$ESTADO/$1 f
  [[ -f $d/creados.txt ]] || die "non hai rexistro de «${m}» en $d (non o xerou este script)"
  while IFS= read -r f; do rm -f "$f"; done <"$d/creados.txt"
  while IFS= read -r f; do dirname "$f"; done <"$d/creados.txt" | sort -ru |
    while IFS= read -r f; do rmdir -p "$f" 2>/dev/null || true; done
  # Sen -p: a fonte restaurada ten que ser máis nova ca a .class con violación, ou Maven non
  # a recompila e api/target queda coa versión rota.
  (cd "$d/copia" && find . -type f) | while IFS= read -r f; do cp "$d/copia/$f" "${f#./}"; done
  # Maven non borra clases nin migracións de fontes que xa non existen (D28/D45).
  rm -rf "api/target/classes/gal/furar/$m" "api/target/test-classes/gal/furar/$m" api/target/modulith-docs/*"-$m".*
  rm -f "api/target/classes/messages/${m}_gl.properties" "api/target/classes/messages/${m}_es.properties"
  find api/target/surefire-reports api/target/failsafe-reports -name "*gal.furar.$m.*" -delete 2>/dev/null || true
  find api/target -name "V*__$(cat "$d/taboa")_create.sql" -delete 2>/dev/null || true
  rm -rf "$d"
  rmdir "$ESTADO" 2>/dev/null || true
  if [[ -d web/node_modules ]]; then (cd web && npx ng-openapi-gen --config ng-openapi-gen.json >/dev/null); fi
  echo "novo-modulo: «${m}» eliminado; ficheiros restaurados"
}

# --------------------------------------------------------------------------------------------
[[ ${1:-} == --remove ]] && { [[ -n ${2:-} ]] || uso; eliminar "$2"; exit; }
target=
if [[ ${1:-} == --custom-fields ]]; then
  target=${2:-}
  shift 2 || uso
  [[ $target =~ ^[A-Z][A-Z0-9_]{1,39}$ ]] ||
    die "--custom-fields «${target}»: código de destino en maiúsculas (^[A-Z][A-Z0-9_]{1,39}$)"
  # Cada destino ten un só dono (CustomFieldTargets rexeita duplicados ao arrancar).
  if grep -rqF "new CustomFieldTarget(\"$target\")" api/src/main/java; then
    die "--custom-fields «${target}»: ese destino xa o rexistra outro módulo; o novo precisa o seu"
  fi
fi
[[ $# -ge 1 && $# -le 4 && ${1:-} != -* ]] || uso

modulo=$1
[[ $modulo =~ ^[a-z][a-z0-9]{1,29}$ ]] || die "módulo «${modulo}»: minúsculas e díxitos, 2–30 (paquete Java)"
Nome=${2:-$(perl -e 'print ucfirst shift' "$modulo")}
[[ $Nome =~ ^[A-Z][a-z0-9]+([A-Z][a-z0-9]+)*$ ]] || die "Nome «${Nome}»: PascalCase (Facility, SportsCentre)"
kebab=$(perl -e '$_ = shift; s/(?<!^)([A-Z])/-$1/g; print lc' "$Nome")
plural=${3:-${kebab}s}
ruta=${4:-$plural}
for v in "$plural" "$ruta"; do
  [[ $v =~ ^[a-z][a-z0-9]*(-[a-z0-9]+)*$ ]] || die "«${v}»: kebab-case en minúsculas"
done
name=$(perl -e 'print lcfirst shift' "$Nome")
taboa=${kebab//-/_}
NAME=$(tr '[:lower:]' '[:upper:]' <<<"$taboa")
Names=$(perl -e 'print join "", map { ucfirst } split /-/, shift' "$plural")

# Colisións: código, táboa, ruta, permiso e textos.
[[ -e api/src/main/java/gal/furar/$modulo ]] && die "xa existe o paquete gal.furar.$modulo"
[[ -e $ESTADO/$modulo ]] && die "xa hai un $modulo xerado: scripts/novo-modulo.sh --remove $modulo"
grep -rqiE "create table( if not exists)? $taboa( |\()" api/src/main/resources/db/migration && die "xa existe a táboa $taboa"
grep -rqF "\"$modulo.manage\"" api/src/main/java && die "xa existe o permiso $modulo.manage"
[[ -e api/src/main/resources/messages/${modulo}_gl.properties ]] && die "xa hai mensaxes de $modulo"
grep -qF "path: '$ruta'" "$ROUTES" && die "xa existe a ruta /:concello/$ruta"
case $ruta in xestion | plataforma | erro) die "a ruta «${ruta}» está reservada" ;; esac
jq -e --arg n "$name" --arg m "$modulo" '.[$n] or .titles[$n] or .errors[$m]' web/src/i18n/gl.json >/dev/null &&
  die "xa hai textos «${name}» ou errors.$modulo en gl.json"
grep -qF "$MARCA_RUTAS" "$ROUTES" || die "falta a marca «${MARCA_RUTAS}» en $ROUTES"

# Versión Flyway: agora (UTC), e sempre despois da última migración.
ts=$(date -u +%Y%m%d%H%M)
ultima=$(find api/src/main/resources/db/migration -name 'V*__*.sql' | sed -E 's|.*/V([0-9]+)__.*|\1|' | sort -n | tail -1)
[[ -n $ultima ]] && ((ts <= ultima)) && ts=$((ultima + 1))

# Sen $(...) dentro de export: agocharía o código de saída (SC2155).
if [[ -n $target ]]; then M_CF=1; else M_CF=0; fi
export M_MODULE=$modulo M_NAME_P=$Nome M_NAME=$name M_KEBAB=$kebab M_SNAKE=$taboa M_UPPER=$NAME \
  M_NAMES_P=$Names M_NAMES=$plural M_ROUTE=$ruta M_TS=$ts M_TARGET=${target:-NONE} M_CF
# Marcadores e variante (con ou sen campos personalizados); despois, os nomes.
subst() {
  perl -ne 'BEGIN { $cf = $ENV{M_CF}; $skip = 0 }
    if (/^\s*(?:\/\/|--|<!--)\s*\@(no)?cf([{}])\s*(?:-->)?\s*$/) {
      my $wanted = $1 ? !$cf : $cf;
      $skip = ($2 eq "{" && !$wanted) ? 1 : 0;
      next;
    }
    next if $skip;
    if (s/\s*(?:\/\/|--)\s*\@(no)?cf\s*$/\n/) { next if ($1 ? $cf : !$cf); }
    s/\@module\@/$ENV{M_MODULE}/g; s/\@Names\@/$ENV{M_NAMES_P}/g; s/\@names\@/$ENV{M_NAMES}/g;
    s/\@Name\@/$ENV{M_NAME_P}/g; s/\@name-kebab\@/$ENV{M_KEBAB}/g; s/\@name_snake\@/$ENV{M_SNAKE}/g;
    s/\@name\@/$ENV{M_NAME}/g; s/\@NAME\@/$ENV{M_UPPER}/g; s/\@route\@/$ENV{M_ROUTE}/g; s/\@ts\@/$ENV{M_TS}/g;
    s/\@TARGET\@/$ENV{M_TARGET}/g;
    print;'
}

d=$ESTADO/$modulo
mkdir -p "$d/copia"
printf '%s' "$taboa" >"$d/taboa"
: >"$d/creados.txt"
for f in "${TOCADOS[@]}"; do
  mkdir -p "$d/copia/$(dirname "$f")"
  cp -p "$f" "$d/copia/$f"
done
# Se algo falla a medias, desfaise.
trap 'rc=$?; if ((rc != 0)); then echo "novo-modulo: erro; desfago o xerado" >&2; eliminar "$modulo" >/dev/null 2>&1 || true; fi' EXIT

# 1. Ficheiros novos desde a plantilla
while IFS= read -r orixe; do
  if [[ $orixe == *.cf ]]; then
    [[ -n $target ]] || continue
    destino=$(subst <<<"${orixe#"$PLANTILLA"/}")
    destino=${destino%.cf}
  else
    destino=$(subst <<<"${orixe#"$PLANTILLA"/}")
  fi
  [[ -e $destino ]] && die "xa existe $destino"
  mkdir -p "$(dirname "$destino")"
  subst <"$orixe" >"$destino"
  echo "$destino" >>"$d/creados.txt"
done < <(find "$PLANTILLA/api" "$PLANTILLA/web" -type f | sort)

# A migración nova entra no rexistro de aplicadas (ADR-0010, MigrationLockTest).
scripts/migracions.sh fixar >/dev/null

# 2. Anacos en ficheiros existentes
frag=$PLANTILLA/fragments
ruta_ts=$(subst <"$frag/route.ts")
RUTA=$ruta_ts MARCA=$MARCA_RUTAS perl -0pi -e 's/^(\s*\Q$ENV{MARCA}\E)/$ENV{RUTA}\n$1/m' "$ROUTES"
for l in gl es; do
  subst <"$frag/$l.json" >"$d/$l.json"
  jq --indent 2 --slurpfile f "$d/$l.json" '. * $f[0]' "web/src/i18n/$l.json" >"$d/$l.out"
  mv "$d/$l.out" "web/src/i18n/$l.json"
done

# 3. Formato (google-java-format e prettier) só do xerado e do tocado
(cd api && ./mvnw -B -q spotless:apply "-DspotlessFiles=.*/gal/furar/$modulo/.*\.java")
grep '^web/' "$d/creados.txt" | sed 's|^web/||' |
  (cd web && xargs npx prettier --log-level warn --write src/i18n/gl.json src/i18n/es.json src/app/app.routes.ts)

trap - EXIT
n=$(wc -l <"$d/creados.txt" | tr -d ' ')
cat <<TXT
novo-modulo: «${modulo}» xerado ($n ficheiros; permiso ${modulo}.manage; táboa $taboa; /api/v1/municipalities/{slug}/$plural; web /:concello/$ruta; campos personalizados: ${target:-non}).
Seguintes pasos (docs/como-engadir-un-modulo.md):
  1. Contrato:  (cd api && ./mvnw -B verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OpenApiContractIT -Dfurar.openapi.write=true)
  2. Permiso:   ${modulo}.manage é só do xestor; se o dás a outro rol, engádeo a RolePermissionsTest.expectedMatrix.
  3. Glosario, textos reais en gl/es e, se hai decisión nova, ADR.
  Desfacer:     scripts/novo-modulo.sh --remove $modulo
TXT
