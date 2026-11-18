// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.municipality.MunicipalityQueries;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.StaffRoles;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.application.port.in.MyStaffMembership;
import gal.furar.staff.application.port.in.MyStaffMembershipsUseCase;
import gal.furar.staff.application.port.out.StaffAccountLookup;
import gal.furar.staff.domain.StaffRole;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Os concellos onde a persoa da sesión é persoal activo (ADR-0027, revisión do 2026-11-03). Sen
 * lectura nova entre concellos: primeiro {@code is_active_staff_account} (só si ou non) corta a
 * quen non é persoal; despois, o rol en cada concello lese con {@link StaffRoles}, que abre a súa
 * transacción curta nese concello e coa súa RLS, como fai cada petición da xestión. Non rexistra
 * acceso (sen {@code auth_time}). Sen transacción propia: cada concello leva a súa.
 */
@Service
public class MyStaffMembershipsService implements MyStaffMembershipsUseCase {

  private static final Comparator<MyStaffMembership> BY_NAME =
      Comparator.comparing(
          MyStaffMembership::name, Collator.getInstance(Locale.forLanguageTag("gl")));

  private final CurrentActor currentActor;
  private final StaffAccountLookup accounts;
  private final MunicipalityDirectory directory;
  private final StaffRoles roles;
  private final MunicipalityQueries municipalities;

  public MyStaffMembershipsService(
      CurrentActor currentActor,
      StaffAccountLookup accounts,
      MunicipalityDirectory directory,
      StaffRoles roles,
      MunicipalityQueries municipalities) {
    this.currentActor = currentActor;
    this.accounts = accounts;
    this.directory = directory;
    this.roles = roles;
    this.municipalities = municipalities;
  }

  @Override
  @PersonUseCase
  public List<MyStaffMembership> mine() {
    Actor actor =
        currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
    String subject = actor.subject();
    if (!accounts.isActiveAnywhere(subject)) {
      return List.of();
    }
    return directory.all().stream()
        .flatMap(municipality -> membership(subject, municipality).stream())
        .sorted(BY_NAME)
        .toList();
  }

  private Optional<MyStaffMembership> membership(String subject, TenantRef municipality) {
    String slug = municipality.slug();
    if (slug == null) {
      return Optional.empty();
    }
    return roles
        .activeRole(subject, municipality, null)
        .flatMap(
            role -> Arrays.stream(StaffRole.values()).filter(r -> r.role() == role).findFirst())
        .flatMap(
            role ->
                municipalities
                    .name(municipality.id())
                    .map(name -> new MyStaffMembership(slug, name, role)));
  }
}
