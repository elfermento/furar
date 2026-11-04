#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# T7: falla se algún informe de Surefire ou Failsafe ten tests omitidos (skipped > 0) ou fallidos.
# Maven non falla cun assumeTrue(false); isto si. Úsano D11, api_ut e api_it (scripts/entorno.sh).
#   scripts/sen-omitidos.sh                 todos os informes de api/target (D11, tras clean)
#   scripts/sen-omitidos.sh 'ClaseA,ClaseB'  só os desas clases (api_ut/api_it, sen clean)
set -euo pipefail
target="$(git rev-parse --show-toplevel)/api/target"
shopt -s nullglob
informes=()
if [[ -n ${1:-} ]]; then
  IFS=',' read -ra clases <<<"$1"
  for c in "${clases[@]}"; do
    informes+=("$target"/surefire-reports/TEST-*."$c".xml "$target"/failsafe-reports/TEST-*."$c".xml)
  done
else
  informes=("$target"/surefire-reports/TEST-*.xml "$target"/failsafe-reports/TEST-*.xml)
fi
if ((${#informes[@]} == 0)); then echo "sen-omitidos: non hai informes en $target" >&2; exit 1; fi
malos=$(perl -ne 'if (/<testsuite\b/) { ($n)=/\bname="([^"]+)"/; ($s)=/\bskipped="(\d+)"/; ($f)=/\bfailures="(\d+)"/; ($e)=/\berrors="(\d+)"/;
  print "$n (skipped=$s failures=$f errors=$e)\n" if $s>0 || $f>0 || $e>0; close ARGV }' "${informes[@]}")
if [[ -n $malos ]]; then
  printf 'Tests omitidos ou fallidos (T7: non se admite ningún skipped):\n%s\n' "$malos" >&2
  exit 1
fi
# A8b (QA Q3): no modo completo, cada clase @NativeSql MUNICIPALITY de src/main ten o informe de
# failsafe do seu <Clase>TenantIT con tests > 0. A8b só pode ver que o IT existe; isto, que correu.
if [[ -z ${1:-} ]]; then
  root="$(git rev-parse --show-toplevel)"
  sen_it=()
  while IFS= read -r f; do
    [[ -n $f ]] || continue
    clase=$(basename "$f" .java)
    informe=("$target"/failsafe-reports/TEST-*."${clase}TenantIT".xml)
    n=0
    if ((${#informe[@]} > 0)); then
      n=$(perl -ne 'if (/<testsuite\b/) { ($t)=/\btests="(\d+)"/; print $t; exit }' "${informe[0]}")
    fi
    ((n > 0)) || sen_it+=("$clase (${clase}TenantIT: tests=${n})")
  done < <(grep -rlE 'scope *= *(NativeSql\.Scope\.)?MUNICIPALITY' "$root/api/src/main/java" || true)
  if ((${#sen_it[@]} > 0)); then
    printf 'SQL nativo MUNICIPALITY sen TenantIT executado (A8b, ADR-0006):\n%s\n' "${sen_it[@]}" >&2
    exit 1
  fi
fi
echo "Informes sen omitidos nin fallos: ${#informes[@]} ficheiros"
