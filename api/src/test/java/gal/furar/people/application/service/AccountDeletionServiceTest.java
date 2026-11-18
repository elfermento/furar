// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.people.application.port.in.AccountDeletionView;
import gal.furar.people.application.port.out.AccountClosures;
import gal.furar.people.domain.AccountDeletionBlocker;
import gal.furar.people.domain.AccountDeletionBlocker.Kind;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.MunicipalityPlaces;
import gal.furar.shared.tenancy.TenantRef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ADR-0043: o que impide eliminar a conta sae por tipo (reservas, partidas, eventos, persoal,
 * plataforma) e, dentro de cada un, polo nome do concello, co slug para ligalo. Sen nada, pódese.
 */
class AccountDeletionServiceTest {

  static final UUID NARON = UUID.fromString("00000000-0000-7000-8000-000000000001");
  static final UUID ARES = UUID.fromString("00000000-0000-7000-8000-000000000002");
  static final UUID EVENT = UUID.fromString("00000000-0000-7000-8000-0000000000e1");

  final Map<String, List<AccountDeletionBlocker>> blockers = new HashMap<>();
  Optional<Actor> actor = Optional.of(new Actor("vecina", Set.of(), true));

  final AccountClosures closures =
      new AccountClosures() {
        @Override
        public List<AccountDeletionBlocker> blockers(String subject) {
          return blockers.getOrDefault(subject, List.of());
        }

        @Override
        public Map<UUID, Long> close(String subject) {
          throw new AssertionError("checking never closes");
        }
      };

  AccountDeletionService service() {
    CurrentActor current = () -> actor;
    MunicipalityPlaces places =
        id -> Optional.of(new MunicipalityPlaces.Place(id.equals(NARON) ? "Narón" : "Ares"));
    return new AccountDeletionService(
        closures,
        current,
        () -> List.of(new TenantRef(NARON, "naron"), new TenantRef(ARES, "ares")),
        places);
  }

  @Test
  void withNothingPendingTheAccountCanGo() {
    assertThat(service().mine()).isEmpty();
    assertThat(service().allowed("vecina")).isTrue();
  }

  @Test
  void listsWhatToCancelByKindAndMunicipalityWithTheLinks() {
    blockers.put(
        "vecina",
        List.of(
            new AccountDeletionBlocker(Kind.STAFF, NARON, null, null, null, 1),
            new AccountDeletionBlocker(Kind.BOOKING, NARON, null, null, null, 2),
            new AccountDeletionBlocker(Kind.EVENT, ARES, EVENT, "Carreira", "Carrera", 1),
            new AccountDeletionBlocker(Kind.BOOKING, ARES, null, null, null, 1),
            new AccountDeletionBlocker(Kind.PLATFORM_ADMIN, null, null, null, null, 1)));

    List<AccountDeletionView> views = service().mine();

    assertThat(views)
        .extracting(
            AccountDeletionView::kind, AccountDeletionView::slug, AccountDeletionView::count)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(Kind.BOOKING, "ares", 1L),
            org.assertj.core.groups.Tuple.tuple(Kind.BOOKING, "naron", 2L),
            org.assertj.core.groups.Tuple.tuple(Kind.EVENT, "ares", 1L),
            org.assertj.core.groups.Tuple.tuple(Kind.STAFF, "naron", 1L),
            org.assertj.core.groups.Tuple.tuple(Kind.PLATFORM_ADMIN, null, 1L));
    AccountDeletionView event = views.get(2);
    assertThat(event.municipalityName()).isEqualTo("Ares");
    assertThat(event.eventId()).isEqualTo(EVENT);
    assertThat(event.eventNameGl()).isEqualTo("Carreira");
    assertThat(event.eventNameEs()).isEqualTo("Carrera");
    assertThat(views.get(4).municipalityName()).isNull();
    assertThat(service().allowed("vecina")).isFalse();
  }

  @Test
  void onlyTheAccountOfTheSession() {
    blockers.put(
        "outra", List.of(new AccountDeletionBlocker(Kind.BOOKING, NARON, null, null, null, 1)));
    assertThat(service().mine()).isEmpty();
    actor = Optional.empty();
    assertThatThrownBy(() -> service().mine()).isInstanceOf(DomainException.class);
  }
}
