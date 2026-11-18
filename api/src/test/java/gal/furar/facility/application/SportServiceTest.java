// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.facility.application.port.in.CreateSportCommand;
import gal.furar.facility.application.service.SportService;
import gal.furar.facility.domain.FacilityError;
import gal.furar.facility.domain.Sport;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Catálogo e «+ crear», con repositorio en memoria. */
class SportServiceTest {

  final InMemorySportRepository repository = new InMemorySportRepository();
  final List<AuditEvent> audit = new ArrayList<>();
  final SportService service =
      new SportService(
          repository,
          audit::add,
          Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC));
  TenantContext.Scope scope;

  @BeforeEach
  void enter() {
    scope = TenantContext.open(new TenantRef(UUID.randomUUID(), "naron"));
  }

  @AfterEach
  void leave() {
    scope.close();
  }

  @Test
  void listsCatalogAndOwnSportsAlphabeticallyInTheLanguage() {
    service.create(new CreateSportCommand("Bolos celtas", "Bolos celtas"));
    assertThat(service.list(Language.GL))
        .extracting(s -> s.label().gl())
        .containsExactly("Balonmán", "Bolos celtas", "Fútbol", "Pádel");
    assertThat(service.list(Language.ES))
        .extracting(s -> s.label().es())
        .containsExactly("Balonmano", "Bolos celtas", "Fútbol", "Pádel");
  }

  @Test
  void createdSportIsCustomAndAudited() {
    Sport created = service.create(new CreateSportCommand(" Pickleball ", "Pickleball"));
    assertThat(created.ref().isCustom()).isTrue();
    assertThat(created.label().gl()).isEqualTo("Pickleball");
    assertThatThrownBy(() -> service.create(new CreateSportCommand("PICKLEBALL", "Pickleball")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.errorCode()).isEqualTo(FacilityError.SPORT_NAME_TAKEN));
    assertThat(audit)
        .extracting(e -> e.action() + " " + e.outcome())
        .containsExactly("facility.sport-create SUCCESS", "facility.sport-create FAILURE");
  }
}
