// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import gal.furar.shared.domain.Audience;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * D15 (ADR-0007): os casos xéranse desde a matriz {@link RolePermissions}. Para cada rol e permiso:
 * vale no seu concello segundo a matriz, nunca noutro concello, e os roles globais valen en todos.
 */
class RolePermissionsTest {

  @TestFactory
  Stream<DynamicTest> matrixDecidesEveryCase() {
    List<DynamicTest> tests = new ArrayList<>();
    for (Role role : Role.values()) {
      for (PermissionKey permission : RolePermissions.all()) {
        boolean allowed = RolePermissions.allows(role, permission);
        String scope = RolePermissions.isGlobal(role) ? RoleGrant.ANY : "naron";
        Actor actor = new Actor("u", Set.of(new RoleGrant(scope, role)));
        tests.add(
            DynamicTest.dynamicTest(
                role + " " + permission + " → " + allowed,
                () -> {
                  assertThat(actor.can(permission, "naron")).isEqualTo(allowed);
                  assertThat(actor.can(permission, "ferrol"))
                      .isEqualTo(allowed && RolePermissions.isGlobal(role));
                  assertThat(actor.can(permission, null))
                      .isEqualTo(allowed && RolePermissions.isGlobal(role));
                  assertThat(RolePermissions.of(role).contains(permission)).isEqualTo(allowed);
                }));
      }
    }
    return tests.stream();
  }

  /**
   * A matriz enteira por código, lida dos {@code enum} de cada módulo (ADR-0005 ARQ3-2). Un módulo
   * novo que dea un permiso a alguén máis ca ao xestor engade aquí a súa liña: é a revisión
   * explícita de quen pode facer que. O xestor «fai todo» no seu concello (especificación, bloque
   * 7), así que un permiso só seu (como o xera a plantilla) non precisa liña.
   */
  @Test
  void expectedMatrix() {
    Map<String, Set<Role>> expected =
        Map.ofEntries(
            // ADR-0025, punto 4: cobrar é traballo do mostrador; cancelar, da xestión.
            Map.entry("booking.cancel", Set.of(Role.MANAGER)),
            Map.entry("booking.payment", Set.of(Role.MANAGER, Role.STAFF)),
            Map.entry("booking.view", Set.of(Role.MANAGER, Role.STAFF)),
            // ADR-0028 (revisión do 2026-11-03): a plataforma configura o concello enteiro
            // (datos, aparencia, servizo, datos legais, instalacións e campos), pero non os datos
            // da veciñanza: nin reservas, nin indicadores, nin exportación.
            Map.entry("customfields.manage", Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
            Map.entry("facility.manage", Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
            Map.entry("facility.view", Set.of(Role.MANAGER, Role.STAFF, Role.PLATFORM_ADMIN)),
            // Plan do dominio 3, P1: os indicadores só os ve o xestor.
            Map.entry("indicators.view", Set.of(Role.MANAGER)),
            // ADR-0007: a plataforma só dá de alta concellos (tamén desde un ficheiro
            // furar-export).
            Map.entry("municipality.create", Set.of(Role.PLATFORM_ADMIN)),
            Map.entry("municipality.export", Set.of(Role.MANAGER)),
            Map.entry("municipality.import", Set.of(Role.PLATFORM_ADMIN)),
            Map.entry("municipality.update", Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
            Map.entry("staff-data.view", Set.of(Role.MANAGER, Role.STAFF)),
            // ADR-0027 (revisión do 2026-11-02): a plataforma fai co persoal o mesmo ca o xestor
            // (invitar con calquera rol, cambiar o rol, desactivar e reactivar).
            Map.entry("staff.view", Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
            Map.entry("staff.invite", Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
            Map.entry("staff.manage", Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
            Map.entry("staff.deactivate", Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
            // O rexistro do concello: o xestor e a plataforma (ADR-0028, revisión do 2026-11-03).
            Map.entry("audit.view", Set.of(Role.MANAGER, Role.PLATFORM_ADMIN)),
            // LOPDGDD art. 32: os bloqueados só os exporta o xestor, nunca a plataforma.
            Map.entry("audit.export-blocked", Set.of(Role.MANAGER)),
            Map.entry("audit.platform-view", Set.of(Role.PLATFORM_ADMIN)));
    assertThat(codes(RolePermissions.all())).containsAll(expected.keySet());
    // Un permiso que dá algo a alguén máis ca ao xestor ten que estar aquí.
    for (PermissionKey p : RolePermissions.all()) {
      if (expected.containsKey(p.code()) || !p.roles().equals(Set.of(Role.MANAGER))) {
        assertThat(p.roles())
            .as("%s: its row in expectedMatrix (who can do what is reviewed here)", p.code())
            .isEqualTo(expected.get(p.code()));
      }
    }
    assertThat(RolePermissions.of(Role.RESIDENT)).isEmpty();
  }

  /**
   * Mínimo privilexio da plataforma (ADR-0007; especificación, bloque 7: «Non ve reservas nin datos
   * da veciñanza»). A súa fila é exactamente esta (ADR-0027 e ADR-0028, revisión do 2026-11-03:
   * alta de concellos, a configuración enteira de cada concello, o seu persoal e os rexistros): un
   * permiso novo para ela é unha decisión que se revisa aquí.
   */
  @Test
  void platformRowIsMinimal() {
    List<String> platform = codes(RolePermissions.of(Role.PLATFORM_ADMIN));
    assertThat(platform)
        .containsExactly(
            "audit.platform-view",
            "audit.view",
            "customfields.manage",
            "facility.manage",
            "facility.view",
            "municipality.create",
            "municipality.import",
            "municipality.update",
            "staff.deactivate",
            "staff.invite",
            "staff.manage",
            "staff.view");
    // Os datos da veciñanza quedan fóra: reservas, indicadores, exportación (leva datos persoais),
    // os bloqueados do rexistro e os datos internos das reservas.
    assertThat(platform)
        .noneMatch(c -> c.startsWith("booking."))
        .doesNotContain(
            "staff-data.view", "indicators.view", "municipality.export", "audit.export-blocked");
    Optional<Actor> admin =
        Optional.of(new Actor("a", Set.of(new RoleGrant("*", Role.PLATFORM_ADMIN))));
    assertThat(Audiences.forMunicipality(admin, "naron")).isEqualTo(Audience.PUBLIC);
    // A configuración (campos do concello e das instalacións), coma o persoal.
    assertThat(Audiences.forConfiguration(admin, "naron")).isEqualTo(Audience.STAFF);
    assertThat(Audiences.forFacilities(admin, "naron")).isEqualTo(Audience.STAFF);
    Optional<Actor> resident = Optional.of(new Actor("r", Set.of()));
    assertThat(Audiences.forConfiguration(resident, "naron")).isEqualTo(Audience.PUBLIC);
    assertThat(Audiences.forFacilities(resident, "naron")).isEqualTo(Audience.PUBLIC);
  }

  /**
   * O xestor fai todo no seu concello (especificación, bloque 7): ten cada permiso que non sexa só
   * da plataforma.
   */
  @Test
  void managerHasEveryMunicipalityPermission() {
    for (PermissionKey p : RolePermissions.all()) {
      if (!p.roles().equals(Set.of(Role.PLATFORM_ADMIN))) {
        assertThat(p.roles()).as(p.code()).contains(Role.MANAGER);
      }
    }
  }

  /** Todo {@code @RequiresPermission} do código de produción nomea un permiso rexistrado. */
  @Test
  void everyRequiredPermissionIsRegistered() {
    List<String> unknown = new ArrayList<>();
    int checked = 0;
    for (JavaClass c :
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("gal.furar")) {
      for (JavaMethod m : c.getMethods()) {
        if (m.isAnnotatedWith(RequiresPermission.class)) {
          checked++;
          String code = m.reflect().getAnnotation(RequiresPermission.class).value();
          if (RolePermissions.byCode(code).isEmpty()) {
            unknown.add(c.getSimpleName() + "." + m.getName() + " → " + code);
          }
        }
      }
    }
    assertThat(checked).isPositive();
    assertThat(unknown).as("@RequiresPermission with an unregistered code").isEmpty();
  }

  private static List<String> codes(java.util.Collection<PermissionKey> permissions) {
    return permissions.stream().map(PermissionKey::code).sorted().toList();
  }

  @Test
  void parsesGrants() {
    assertThat(RoleGrant.parse("naron:MANAGER")).contains(new RoleGrant("naron", Role.MANAGER));
    assertThat(RoleGrant.parse("*:PLATFORM_ADMIN"))
        .contains(new RoleGrant("*", Role.PLATFORM_ADMIN));
    assertThat(RoleGrant.parse("naron:PLATFORM_ADMIN")).isEmpty();
    // SEC-04: un rol de concello co ámbito global non vale en ningún concello.
    for (Role role : Role.values()) {
      if (!RolePermissions.isGlobal(role)) {
        assertThat(RoleGrant.parse("*:" + role)).as("*:" + role).isEmpty();
        assertThatThrownBy(() -> new RoleGrant(RoleGrant.ANY, role))
            .isInstanceOf(IllegalArgumentException.class);
      }
    }
    assertThat(RoleGrant.parse("naron:KING")).isEmpty();
    assertThat(RoleGrant.parse("naron")).isEmpty();
    assertThat(RoleGrant.parse(":MANAGER")).isEmpty();
    assertThat(RoleGrant.parse("naron:")).isEmpty();
    assertThat(RoleGrant.parse(null)).isEmpty();
    assertThat(new RoleGrant("naron", Role.STAFF).appliesTo("naron")).isTrue();
    assertThat(new RoleGrant("naron", Role.STAFF).appliesTo("ferrol")).isFalse();
  }

  @Test
  void audiences() {
    Optional<Actor> staff = Optional.of(new Actor("s", Set.of(new RoleGrant("naron", Role.STAFF))));
    Optional<Actor> resident =
        Optional.of(new Actor("r", Set.of(new RoleGrant("naron", Role.RESIDENT))));
    assertThat(Audiences.of(Optional.empty(), "naron", false)).isEqualTo(Audience.PUBLIC);
    assertThat(Audiences.of(staff, "naron", false)).isEqualTo(Audience.STAFF);
    assertThat(Audiences.of(staff, "ferrol", true)).isEqualTo(Audience.OWNER);
    assertThat(Audiences.of(resident, "naron", true)).isEqualTo(Audience.OWNER);
    assertThat(Audiences.of(resident, "naron", false)).isEqualTo(Audience.PUBLIC);
    Optional<Actor> noRoles = Optional.of(new Actor("n", Set.of()));
    Optional<Actor> admin =
        Optional.of(new Actor("a", Set.of(new RoleGrant("*", Role.PLATFORM_ADMIN))));
    assertThat(Audiences.forMunicipality(resident, "naron")).isEqualTo(Audience.OWNER);
    assertThat(Audiences.forMunicipality(resident, "ferrol")).isEqualTo(Audience.PUBLIC);
    assertThat(Audiences.forMunicipality(noRoles, "naron")).isEqualTo(Audience.PUBLIC);
    assertThat(Audiences.forMunicipality(Optional.empty(), "naron")).isEqualTo(Audience.PUBLIC);
    assertThat(Audiences.forMunicipality(staff, "naron")).isEqualTo(Audience.STAFF);
    assertThat(Audiences.forMunicipality(admin, "naron")).isEqualTo(Audience.PUBLIC);
  }
}
