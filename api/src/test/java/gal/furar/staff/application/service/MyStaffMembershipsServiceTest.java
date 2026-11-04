// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.Role;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.application.port.in.MyStaffMembership;
import gal.furar.staff.domain.StaffRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Os concellos da persoa da sesión: só os seus, co rol de cada un, polo nome. */
class MyStaffMembershipsServiceTest {

  static final String ME = "sub-me";

  final TenantRef naron = new TenantRef(UUID.randomUUID(), "naron");
  final TenantRef ferrol = new TenantRef(UUID.randomUUID(), "ferrol");
  final TenantRef ares = new TenantRef(UUID.randomUUID(), "ares");
  final TenantRef noSlug = TenantRef.of(UUID.randomUUID());
  final Map<UUID, String> names =
      Map.of(naron.id(), "Narón", ferrol.id(), "Ferrol", ares.id(), "Ares", noSlug.id(), "X");

  /** Rol por (sub, concello). */
  final Map<String, Role> roles = new HashMap<>();

  final List<String> askedSubjects = new ArrayList<>();
  final List<TenantRef> visited = new ArrayList<>();
  Optional<Actor> actor = Optional.of(new Actor(ME, Set.of(), true));
  boolean activeAnywhere = true;

  final MunicipalityQueries municipalities =
      new MunicipalityQueries() {
        @Override
        public Optional<MunicipalityMailContext> mailContext(UUID municipalityId) {
          return Optional.empty();
        }

        @Override
        public Optional<String> name(UUID municipalityId) {
          return Optional.ofNullable(names.get(municipalityId));
        }
      };

  final MyStaffMembershipsService service =
      new MyStaffMembershipsService(
          () -> actor,
          subject -> {
            askedSubjects.add(subject);
            return activeAnywhere;
          },
          () -> List.of(naron, ferrol, ares, noSlug),
          (subject, municipality, authTime) -> {
            assertThat(authTime).as("never records a sign-in").isNull();
            visited.add(municipality);
            return Optional.ofNullable(roles.get(subject + "@" + municipality.id()));
          },
          municipalities);

  @Test
  void listsOnlyTheCallersActiveMembershipsSortedByName() {
    roles.put(ME + "@" + naron.id(), Role.MANAGER);
    roles.put(ME + "@" + ferrol.id(), Role.STAFF);
    roles.put("sub-other@" + ares.id(), Role.MANAGER);
    roles.put(ME + "@" + noSlug.id(), Role.MANAGER);

    assertThat(service.mine())
        .containsExactly(
            new MyStaffMembership("ferrol", "Ferrol", StaffRole.STAFF),
            new MyStaffMembership("naron", "Narón", StaffRole.MANAGER));
    assertThat(askedSubjects).containsExactly(ME);
  }

  @Test
  void aPersonWhoIsNotStaffAnywhereGetsNothingWithoutVisitingEachMunicipality() {
    activeAnywhere = false;
    roles.put(ME + "@" + naron.id(), Role.MANAGER);

    assertThat(service.mine()).isEmpty();
    assertThat(visited).isEmpty();
  }

  @Test
  void aGlobalRoleIsNotAMembership() {
    roles.put(ME + "@" + naron.id(), Role.PLATFORM_ADMIN);

    assertThat(service.mine()).isEmpty();
  }

  @Test
  void withoutSessionItFails() {
    actor = Optional.empty();

    assertThatThrownBy(service::mine).isInstanceOf(DomainException.class);
  }
}
