#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# D28 (ADR-0005, ADR-0013): cada garda FALLA ante unha violación real e o fallo nomea a regra.
# Introduce cada violación, executa a comprobación da definición de feito, esixe código ≠ 0 E as
# mensaxes esperadas (se falla por outro motivo, non conta), e restaura. Ao saír (tamén con erro
# ou Ctrl-C) restaura todo, borra as saídas compiladas das violacións en api/target (Maven non
# borra clases nin migracións de fontes que xa non existen) e comproba que a árbore queda igual.
#
#   scripts/probar-violacion.sh           todos os casos (a–z)
#   scripts/probar-violacion.sh a c       só eses casos
# Rexistros: .verificar/violacions/<caso>-<n>.log
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
# shellcheck source=scripts/entorno.sh
source scripts/entorno.sh
# shellcheck source=scripts/lib/arbore.sh
source scripts/lib/arbore.sh

LOGS=.verificar/violacions
mkdir -p "$LOGS"
copia=$(mktemp -d)
novos=()
antes=$(estado_arbore)

gardar() { # ficheiros que se van modificar (copia previa)
  local f
  for f; do
    [[ -e $copia/$f ]] && continue
    mkdir -p "$copia/$(dirname "$f")"
    cp -p "$f" "$copia/$f"
  done
}

novo() { # ruta < contido
  [[ -e $1 ]] && { echo "probar-violacion: $1 xa existe" >&2; exit 1; }
  novos+=("$1")
  mkdir -p "$(dirname "$1")"
  cat >"$1"
}

limpar_compilado() {
  find api/target \( -name 'Violacion*' -o -name '*__violacion_*' \) -exec rm -rf {} + 2>/dev/null || true
}

restaurar() {
  local f
  for f in ${novos[@]+"${novos[@]}"}; do rm -f "$f"; done
  novos=()
  # Sen -p: a fonte restaurada ten que ser máis nova ca a .class con violación, ou Maven non
  # a recompila e api/target queda coa versión rota.
  (cd "$copia" && find . -type f) | while IFS= read -r f; do cp "$copia/$f" "${f#./}"; done
  rm -rf "${copia:?}"/* 2>/dev/null || true
  limpar_compilado
}

ao_sair() {
  local rc=$?
  restaurar
  rm -rf "$copia"
  # T2/ARQ-7: nada compilado durante as violacións sobrevive (o seguinte paso, D45 ou D11,
  # recompila desde as fontes restauradas, sen depender das datas).
  rm -rf api/target/classes api/target/test-classes api/target/generated-sources \
    api/target/generated-test-sources api/target/maven-status
  if [[ "$(estado_arbore)" != "$antes" ]]; then
    echo "probar-violacion: FALLO — a árbore non quedou como estaba" >&2
    rc=1
  fi
  exit "$rc"
}
trap ao_sair EXIT
trap 'exit 130' INT TERM

fallos=0
# Executa a comprobación: debe fallar e a saída debe conter todas as mensaxes esperadas.
debe_fallar() { # caso descrición comando mensaxe...
  local caso=$1 desc=$2 cmd=$3 log m falta=()
  shift 3
  log=$LOGS/$caso-$(date +%H%M%S).log
  ultimo_log=$log
  printf '  %s %-58s ' "$caso" "$desc"
  if bash -c "source scripts/entorno.sh; $cmd" >"$log" 2>&1; then
    printf '✗ NON detectada (a comprobación pasou)\n'
    fallos=$((fallos + 1))
    return
  fi
  for m; do grep -qF -- "$m" "$log" || falta+=("$m"); done
  if ((${#falta[@]})); then
    printf '✗ fallou, pero sen «%s» (outro motivo?) → %s\n' "${falta[*]}" "$log"
    fallos=$((fallos + 1))
  else
    printf '✓ detectada (%s)\n' "$*"
  fi
}

API=api/src/main/java/gal/furar
# Cabeceira dos ficheiros de violación (non é a deste script).
# REUSE-IgnoreStart
cabeceira='// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2'
# REUSE-IgnoreEnd

caso_a() {
  novo "$API/customfields/application/service/ViolacionUploadService.java" <<JAVA
$cabeceira
package gal.furar.customfields.application.service;

import gal.furar.municipality.adapter.out.persistence.MunicipalityJpaRepository;
import org.springframework.web.multipart.MultipartFile;

public class ViolacionUploadService {
  private final MunicipalityJpaRepository municipalities;

  public ViolacionUploadService(MunicipalityJpaRepository municipalities) {
    this.municipalities = municipalities;
  }

  public long upload(MultipartFile file) {
    return municipalities.count() + file.getSize();
  }
}
JAVA
  debe_fallar a "MultipartFile e repositorio doutro módulo → D12" \
    "api_ut 'ArchitectureTest,ModularityTest'" "A2 " "M2 " "ViolacionUploadService"
}

caso_b() {
  novo "$API/municipality/application/port/in/ViolacionPingUseCase.java" <<JAVA
$cabeceira
package gal.furar.municipality.application.port.in;

public interface ViolacionPingUseCase {
  String ping();
}
JAVA
  novo "$API/municipality/application/service/ViolacionPingService.java" <<JAVA
$cabeceira
package gal.furar.municipality.application.service;

import gal.furar.municipality.application.port.in.ViolacionPingUseCase;
import org.springframework.stereotype.Service;

@Service
public class ViolacionPingService implements ViolacionPingUseCase {
  @Override
  public String ping() {
    return "pong";
  }
}
JAVA
  novo "$API/municipality/adapter/in/web/ViolacionPingController.java" <<JAVA
$cabeceira
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.application.port.in.ViolacionPingUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/municipalities/{slug}/violacion-ping")
class ViolacionPingController {
  private final ViolacionPingUseCase ping;

  ViolacionPingController(ViolacionPingUseCase ping) {
    this.ping = ping;
  }

  @GetMapping
  String ping(@PathVariable String slug) {
    return ping.ping();
  }
}
JAVA
  debe_fallar b1 "caso de uso sen @RequiresPermission → D12" \
    "api_ut 'ArchitectureTest'" "A15" "ViolacionPingService"
  debe_fallar b2 "endpoint novo sen contrato nin @PublicEndpoint → D15" \
    "api_it 'EndpointSecurityIT'" "EndpointSecurityIT" "violacion-ping"
}

caso_c() {
  novo api/src/main/resources/db/migration/V209912312359__violacion_note.sql <<'SQL'
-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
create table violacion_note (id uuid primary key, body text not null);
SQL
  debe_fallar c "táboa sen municipality_id → D14" \
    "api_it 'SchemaTenancyIT'" "violacion_note: municipality_id uuid not null missing"
}

WEB_HTML=web/src/app/features/customfields/custom-fields-admin.page.html
WEB_STORE=web/src/app/features/customfields/customfields.store.ts

caso_d() {
  gardar "$WEB_HTML"
  printf '%s\n' '<img src="/violacion.png" />' '<div (click)="problem.set(null)">x</div>' >>"$WEB_HTML"
  debe_fallar d "<img> sen alt e (click) nun div → D24" \
    "cd web && npm run lint -- --max-warnings 0" "alt-text" "click-events-have-key-events" "custom-fields-admin.page.html"
}

caso_e() {
  gardar "$WEB_STORE"
  printf '%s\n' "import '../municipality/municipality.store';" >>"$WEB_STORE"
  debe_fallar e "feature que importa outra feature → D24" \
    "cd web && npm run lint -- --max-warnings 0" "furar/zones" "customfields.store.ts"
}

caso_f() {
  gardar web/src/i18n/gl.json
  jq '.common.violacionSoGl = "Só en galego"' web/src/i18n/gl.json >"$copia/gl.tmp" && mv "$copia/gl.tmp" web/src/i18n/gl.json
  debe_fallar f "clave en gl.json que falta en es.json → D25" \
    "cd web && npm run i18n:check" "es.json: falta a clave common.violacionSoGl"
}

caso_g() {
  gardar web/angular.json
  jq '(.. | objects | select(has("coverageThresholds")) | .coverageThresholds) |= map_values(99)' \
    web/angular.json >"$copia/angular.tmp" && mv "$copia/angular.tmp" web/angular.json
  debe_fallar g "umbral de cobertura ao 99 % → D26" \
    "cd web && npm test -- --watch=false --coverage" "does not meet global threshold (99%)"
}

caso_h() {
  gardar "$WEB_HTML"
  printf '%s\n' "<span [attr.title]=\"'Lista de concellos'\">x</span>" >>"$WEB_HTML"
  debe_fallar h "literal de texto nun binding ([attr.title]) → D25" \
    "cd web && npm run i18n:check" "Lista de concellos" "sen traducir"
}

# T10: texto escrito en TypeScript e pintado con {{ }} (i18n:check só mira os modelos) → D24.
WEB_TS=web/src/app/features/customfields/custom-fields-admin.page.ts
caso_l() {
  gardar "$WEB_TS"
  perl -0pi -e "s/(^export class \w+[^\n]*\{\n)/\$1  protected readonly qaText = 'Benvido ao concello';\n/m" "$WEB_TS"
  grep -q "qaText = 'Benvido ao concello'" "$WEB_TS" || { echo "probar-violacion: non se puido introducir l" >&2; exit 1; }
  debe_fallar l "texto nunha propiedade TS dun compoñente → D24 (T10)" \
    "cd web && npm run lint -- --max-warnings 0" "Texto visible en TypeScript (D25)" "custom-fields-admin.page.ts"
}

# T14: Playwright e Vitest saen con 0 cun test omitido; o lint ten que paralo antes → D24.
E2E_PUBLICO=web/e2e/publico.e2e.ts
SPEC_FIELD=web/src/app/shared/ui/field.component.spec.ts
caso_p() {
  gardar "$E2E_PUBLICO"
  gardar "$SPEC_FIELD"
  printf '%s\n' "test.skip('violacion omitida', () => undefined);" >>"$E2E_PUBLICO"
  perl -pi -e "s/^  it\('liga a etiqueta/  it.only('liga a etiqueta/" "$SPEC_FIELD"
  grep -q "it.only('liga a etiqueta" "$SPEC_FIELD" || { echo "probar-violacion: non se puido introducir p" >&2; exit 1; }
  debe_fallar p "test.skip nun e2e e it.only nun spec → D24 (T14)" \
    "cd web && npm run lint -- --max-warnings 0" "Sen tests omitidos nin enfocados" "publico.e2e.ts" "field.component.spec.ts"
}

# Fuga real: sen @TenantId, Hibernate deixa de filtrar. TenantIsolationIT ten que fallar nas
# ASERCIÓNS (datos de B creados por JDBC no setUp), non antes por un 409 ou un 500 no setUp.
# A API conecta como furar_app_bypass (BYPASSRLS, só no contedor de tests, ADR-0006): así a RLS
# non tapa a fuga e próbase que @TenantId, por si só, illa. A RLS próbase en u e TenantRlsSpringIT.
SEN_RLS='export FURAR_TEST_DB_USER=furar_app_bypass;'

ENTIDADE=$API/customfields/adapter/out/persistence/CustomFieldEntity.java
caso_i() {
  gardar "$ENTIDADE"
  sed -i.bak '/^  @TenantId$/d' "$ENTIDADE" && rm -f "$ENTIDADE.bak"
  debe_fallar i "entidade sen @TenantId → asercións de TenantIsolationIT" \
    "$SEN_RLS api_it 'TenantIsolationIT'" "Expecting an empty Optional" "to contain exactly in any order"
  sen_erros i
}

# Fuga real (Q1/ARQ-2): o resolver nunca filtra. As tres clases de illamento teñen que fallar en
# ASERCIÓNS que nomeen a fuga, sen ningún erro (409/500) no setUp.
RESOLVER=$API/shared/tenancy/TenantIdentifierResolver.java
caso_j() {
  gardar "$RESOLVER"
  sed -i.bak 's/^    if (TenantContext.isPlatform()) {$/    if (true) { \/\/ violacion/' "$RESOLVER" && rm -f "$RESOLVER.bak"
  grep -q '// violacion' "$RESOLVER" || { echo "probar-violacion: non se puido introducir j" >&2; exit 1; }
  debe_fallar j "resolver sempre PLATFORM → asercións das 3 clases, 0 erros" \
    "$SEN_RLS api_it 'TenantIsolationIT,MissingTenantIT,CustomFieldsTenantIT'" \
    "Expecting an empty Optional" \
    "MissingTenantIT.sessionWithoutTenantReadsNothingAndCannotWrite" \
    "CustomFieldsTenantIT.definitionsOfOneMunicipalityDoNotExistInAnother" \
    "CustomFieldsTenantIT.sameCodeCanExistInTwoMunicipalities"
  sen_erros j
}

# Esixe que o último rexistro só teña fallos de aserción, ningún erro (excepción fóra dunha aserción).
sen_erros() { # caso
  if grep -Eq 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [1-9]' "$ultimo_log"; then
    printf '  %s ✗ hai erros, non só fallos de aserción → %s\n' "$1" "$ultimo_log"
    fallos=$((fallos + 1))
  fi
}

# Composta (ARQ-5): FK de negocio que non leva municipality_id → SchemaTenancyIT.
caso_k() {
  novo api/src/main/resources/db/migration/V209912312359__violacion_note.sql <<'SQL'
-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
create table violacion_note (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    definition_id   uuid not null references custom_field (id),
    constraint violacion_note_tenant_uk unique (municipality_id, id)
);
create index violacion_note_municipality_idx on violacion_note (municipality_id);
SQL
  debe_fallar k "FK sen municipality_id cara a outra táboa do concello → D14" \
    "api_it 'SchemaTenancyIT'" "violacion_note: foreign key (definition_id) to custom_field without municipality_id"
}

# T7/Q13: un test de illamento desactivado en silencio cun assumeTrue(false). Maven dá
# BUILD SUCCESS con «Skipped: 1»; sen-omitidos.sh (dentro de api_it e de D11) ten que fallar.
ILLAMENTO=api/src/test/java/gal/furar/customfields/adapter/out/persistence/TenantIsolationIT.java
caso_m() {
  gardar "$ILLAMENTO"
  perl -0pi -e 's/(void findByIdAndReferenceDoNotCrossTenants\(\) \{\n)/$1    org.junit.jupiter.api.Assumptions.assumeTrue(false); \/\/ violacion\n/' "$ILLAMENTO"
  grep -q '// violacion' "$ILLAMENTO" || { echo "probar-violacion: non se puido introducir m" >&2; exit 1; }
  debe_fallar m "assumeTrue(false) nun test de illamento → sen-omitidos (T7)" \
    "api_it 'TenantIsolationIT'" "non se admite ningún skipped" "TenantIsolationIT (skipped=1"
}

# QA Q3: SQL MUNICIPALITY co predicado só dentro de «( … or 1 = 1)» e un TenantIT baleiro → A8b.
CF_MAIN=$API/customfields/adapter/out/persistence
CF_TEST=api/src/test/java/gal/furar/customfields/adapter/out/persistence
caso_n() {
  novo "$CF_MAIN/ViolacionParenOrSql.java" <<JAVA
$cabeceira
package gal.furar.customfields.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class ViolacionParenOrSql {
  private final JdbcClient jdbc;

  ViolacionParenOrSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  int count(UUID m) {
    return jdbc.sql(
            "select count(*) from custom_field where (municipality_id = :m or 1 = 1)")
        .param("m", m)
        .query(Integer.class)
        .single();
  }
}
JAVA
  novo "$CF_TEST/ViolacionParenOrSqlTenantIT.java" <<JAVA
$cabeceira
package gal.furar.customfields.adapter.out.persistence;

class ViolacionParenOrSqlTenantIT {
  ViolacionParenOrSql subject;
}
JAVA
  debe_fallar n "«(municipality_id = :m or 1 = 1)» e TenantIT baleiro → A8b (Q3)" \
    "api_ut 'ArchitectureTest'" "A8b " "ViolacionParenOrSql" "only inside parentheses" \
    "does not extend AbstractTenantIT"
}

# SQL MUNICIPALITY correcto e TenantIT de verdade, pero sobre unha táboa sen RLS → D14.
caso_o() {
  novo api/src/main/resources/db/migration/V209912312359__violacion_norls.sql <<'SQL'
-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
create table violacion_norls (
    id              uuid primary key,
    municipality_id uuid not null references municipality (id),
    constraint violacion_norls_tenant_uk unique (municipality_id, id)
);
SQL
  novo "$CF_MAIN/ViolacionNoRlsSql.java" <<JAVA
$cabeceira
package gal.furar.customfields.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@NativeSql(scope = NativeSql.Scope.MUNICIPALITY)
class ViolacionNoRlsSql {
  private final JdbcClient jdbc;

  ViolacionNoRlsSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  int count(UUID m) {
    return jdbc.sql("select count(*) from violacion_norls where municipality_id = :m")
        .param("m", m)
        .query(Integer.class)
        .single();
  }
}
JAVA
  novo "$CF_TEST/ViolacionNoRlsSqlTenantIT.java" <<JAVA
$cabeceira
package gal.furar.customfields.adapter.out.persistence;

import gal.furar.testsupport.AbstractTenantIT;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

class ViolacionNoRlsSqlTenantIT extends AbstractTenantIT {
  @Autowired ViolacionNoRlsSql subject;

  @Override
  protected Object seed(UUID municipality) {
    jdbc.sql("insert into violacion_norls (id, municipality_id) values (gen_random_uuid(), :m)")
        .param("m", municipality)
        .update();
    return municipality;
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return subject.count(municipality) > 0 ? List.of(municipality) : List.of();
  }
}
JAVA
  debe_fallar o "SQL MUNICIPALITY sobre táboa sen RLS → NativeSqlRowLevelSecurityIT" \
    "api_ut 'ArchitectureTest' && api_it 'NativeSqlRowLevelSecurityIT'" \
    "everyTableOfMunicipalityNativeSqlHasRowLevelSecurity" "violacion_norls"
}

# QA Q12 (T13): GLOBAL cunha táboa do concello despois dunha coma no FROM → A8b.
caso_q() {
  novo "$CF_MAIN/ViolacionCommaSql.java" <<JAVA
$cabeceira
package gal.furar.customfields.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;

@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "violacion")
class ViolacionCommaSql {
  private final JdbcClient jdbc;

  ViolacionCommaSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  int count() {
    return jdbc.sql("select count(*) from municipality m, custom_field d where true")
        .query(Integer.class)
        .single();
  }
}
JAVA
  debe_fallar q "GLOBAL con táboa do concello tras unha coma no FROM → A8b (Q12)" \
    "api_ut 'ArchitectureTest'" "A8b " "ViolacionCommaSql" "comma in FROM/USING"
}

# QA Q13 (T13): @Formula cunha subconsulta que @TenantId non filtra → A22.
caso_r() {
  gardar "$ENTIDADE"
  perl -0pi -e 's/(\n  \@Id private UUID id;\n)/$1\n  \@org.hibernate.annotations.Formula("(select count(*) from custom_field o where o.code = code)")\n  private long violacionSameCode;\n/' "$ENTIDADE"
  grep -q 'violacionSameCode' "$ENTIDADE" || { echo "probar-violacion: non se puido introducir r" >&2; exit 1; }
  debe_fallar r "@Formula cunha subconsulta nunha entidade → A22 (Q13)" \
    "api_ut 'ArchitectureTest'" "A22 " "org.hibernate.annotations.Formula"
}

# QN1: GLOBAL con «table t» e «from (table t)», que a lista de palabras clave non vía → A8b.
caso_s() {
  novo "$CF_MAIN/ViolacionTableSql.java" <<JAVA
$cabeceira
package gal.furar.customfields.adapter.out.persistence;

import gal.furar.shared.persistence.NativeSql;
import org.springframework.jdbc.core.simple.JdbcClient;

@NativeSql(scope = NativeSql.Scope.GLOBAL, reason = "violacion")
class ViolacionTableSql {
  private final JdbcClient jdbc;

  ViolacionTableSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Object all() {
    return jdbc.sql("table custom_field").query().listOfRows();
  }

  Object nested() {
    return jdbc.sql("select count(*) from (table custom_field) t").query().listOfRows();
  }
}
JAVA
  debe_fallar s "GLOBAL con «table custom_field» → A8b (QN1)" \
    "api_ut 'ArchitectureTest'" "A8b " "ViolacionTableSql" \
    "outside TenancyAllowlist: [custom_field]"
}

# QN2: @NativeQuery de Spring Data JPA 4 nun repositorio, SQL que A8b non le → A22.
caso_t() {
  local repo=$CF_MAIN/CustomFieldJpaRepository.java
  gardar "$repo"
  perl -0pi -e 's/(interface CustomFieldJpaRepository[^{]*\{\n)/$1  \@org.springframework.data.jpa.repository.NativeQuery("select code from custom_field")\n  java.util.List<String> violacionAllCodes();\n\n/' "$repo"
  grep -q 'violacionAllCodes' "$repo" || { echo "probar-violacion: non se puido introducir t" >&2; exit 1; }
  debe_fallar t "@NativeQuery nun repositorio JPA → A22 (QN2)" \
    "api_ut 'ArchitectureTest'" "A22 " "org.springframework.data.jpa.repository.NativeQuery"
}

# ARQ4-1/T18 (D14 l): unha migración que borra a política ou desactiva a RLS → SchemaTenancyIT.
caso_u() {
  novo api/src/main/resources/db/migration/V209912312359__violacion_sen_rls.sql <<'SQL'
-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
drop policy custom_field_tenant on custom_field;
create policy violacion_open on custom_field using (true);
SQL
  debe_fallar u "política RLS borrada e unha «using (true)» → SchemaTenancyIT (T18)" \
    "api_it 'SchemaTenancyIT'" "custom_field: permissive policy violacion_open does not filter by municipality" \
    "custom_field: no policy on municipality_id = furar.municipality_id for ALL"
}

# N11/D14: unha política que só mira «municipality_id is not null» deixa ver todos os concellos.
# As dúas probas de RLS (mesma función de testsupport) teñen que fallar. Vai sobre facility: é a
# táboa que le o SQL nativo do concello (FacilitySql); custom_field xa non a le ningún.
caso_w() {
  novo api/src/main/resources/db/migration/V209912312359__violacion_rls_not_null.sql <<'SQL'
-- SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
-- SPDX-License-Identifier: EUPL-1.2
drop policy facility_tenant on facility;
create policy violacion_not_null on facility using (municipality_id is not null);
SQL
  debe_fallar w "política «municipality_id is not null» → SchemaTenancyIT e NativeSql…IT" \
    "api_it 'SchemaTenancyIT,NativeSqlRowLevelSecurityIT'" \
    "SchemaTenancyIT.everyBusinessTableIsScopedByMunicipality" \
    "NativeSqlRowLevelSecurityIT.everyTableOfMunicipalityNativeSqlHasRowLevelSecurity" \
    "facility: permissive policy violacion_not_null does not filter by municipality"
  sen_erros w
}

# N8/D16: o problema sae sen traceId. ProblemDetailsIT ten que fallar en ASERCIÓNS que nomeen o
# campo, sen ningún erro (NullPointerException).
PROBLEM_WRITER=$API/shared/web/ProblemWriter.java
caso_x() {
  gardar "$PROBLEM_WRITER"
  sed -i.bak 's/^    problem.setProperty("traceId", traceId(request));$/    \/\/ violacion: sen traceId/' "$PROBLEM_WRITER" && rm -f "$PROBLEM_WRITER.bak"
  grep -q '// violacion' "$PROBLEM_WRITER" || { echo "probar-violacion: non se puido introducir x" >&2; exit 1; }
  debe_fallar x "problema sen traceId → ProblemDetailsIT nomea o campo, 0 erros" \
    "api_it 'ProblemDetailsIT'" "problem field 'traceId' missing"
  sen_erros x
}

# QA P7 (D26): o <input type="number"> do formulario dinámico perde aria-describedby e
# aria-invalid. O axe non o ve; o test por FieldType ten que fallar.
CAMPO_HTML=web/src/app/shared/custom-fields/custom-field.component.html
caso_y() {
  gardar "$CAMPO_HTML"
  perl -0pi -e 's/(type="number"(?:(?!\/>).)*?)\n\s*\[attr\.aria-describedby\]="describedBy\(\)"\n\s*\[attr\.aria-invalid\]="error\(\) \? \x27true\x27 : null"/$1 data-violacion/s' "$CAMPO_HTML"
  grep -q 'data-violacion' "$CAMPO_HTML" || { echo "probar-violacion: non se puido introducir y" >&2; exit 1; }
  debe_fallar y "número sen aria-describedby nin aria-invalid → test por FieldType" \
    "cd web && npm test -- --watch=false" "tipo NUMBER" "liga axuda e erro con aria-describedby"
}

# QA 6.ª rolda (D53): cambiar o resultado esperado dunha fila sen rexistrar a súa proba → o
# contrato de verificar.sh ten que fallar (antes daba verde só polo código de saída).
caso_z() {
  gardar docs/definicion-de-feito.md
  sed -i.bak -E 's/^(\| D16 .*)\| backend \|$/\1(cambio sen proba) | backend |/' docs/definicion-de-feito.md \
    && rm -f docs/definicion-de-feito.md.bak
  grep -q '(cambio sen proba)' docs/definicion-de-feito.md || { echo "probar-violacion: non se puido introducir z" >&2; exit 1; }
  debe_fallar z "fila D16 cambiada sen proba rexistrada → contrato (D53)" \
    "scripts/verificar.sh --contrato" "D16: a fila cambiou desde que se rexistrou a súa proba"
}

# QA N13 (ADR-0009): unha feature que chama á API á man con HttpClient (tipo inventado) ou
# fetch (tamén rxjs/ajax e rxjs/fetch, QA P1) compila igual cando cambia a API. O lint ten que
# paralo → D24 (furar/zones).
caso_v() {
  novo web/src/app/features/municipality/violacion-raw-http.ts <<EOF
$cabeceira
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ajax } from 'rxjs/ajax';
import { fromFetch } from 'rxjs/fetch';

@Injectable({ providedIn: 'root' })
export class ViolacionRawHttp {
  private readonly http = inject(HttpClient);
  load() {
    return this.http.get<{ nombre: string }>('/api/v1/municipalities/naron');
  }
  loadFetch() {
    return fetch('/api/v1/municipalities/naron');
  }
  loadAjax() {
    return ajax.getJSON<{ nombre: string }>('/api/v1/municipalities/naron');
  }
  loadFromFetch() {
    return fromFetch('/api/v1/municipalities/naron');
  }
}
EOF
  debe_fallar v "feature con HttpClient e fetch á man (sen cliente xerado) → D24" \
    "cd web && npm run lint -- --max-warnings 0" "HTTP á man en features/ (@angular/common/http)" \
    "HTTP á man (fetch)" "HTTP á man en features/ (rxjs/ajax)" "HTTP á man en features/ (rxjs/fetch)" \
    "violacion-raw-http.ts"
}

casos=("$@")
((${#casos[@]})) || casos=(a b c d e f g h i j k l m n o p q r s t u v w x y z)
limpar_compilado
echo "probar-violacion: casos ${casos[*]}"
for c in "${casos[@]}"; do
  declare -F "caso_$c" >/dev/null || { echo "caso descoñecido: $c (a–z)" >&2; exit 64; }
  "caso_$c"
  restaurar
done

if ((fallos)); then
  echo "probar-violacion: FALLO — $fallos garda(s) non detectaron a súa violación"
  exit 1
fi
echo "probar-violacion: OK — as ${#casos[@]} violacións fallan coa regra esperada e a árbore queda igual"
