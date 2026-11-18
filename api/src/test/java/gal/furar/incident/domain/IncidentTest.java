// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentTest {

  static final Instant NOW = Instant.parse("2026-11-11T10:42:00Z");
  static final Reporter RESIDENT =
      new Reporter("sub-1", "Persoa Proba", "persoa@example.org", Language.GL);

  static Incident report(IncidentOrigin origin, String description, int photos) {
    List<IncidentPhoto> list =
        java.util.stream.IntStream.range(0, photos)
            .mapToObj(i -> new IncidentPhoto(UUID.randomUUID(), Math.min(i, 2), 10, 10))
            .toList();
    return Incident.report(
        IncidentId.newId(),
        UUID.randomUUID(),
        null,
        IncidentType.BREAKDOWN,
        description,
        origin,
        RESIDENT,
        list,
        NOW);
  }

  @Test
  void aNewReportStartsAsNewWithTheTextTrimmed() {
    Incident incident = report(IncidentOrigin.RESIDENT, "  A rede está rota.  ", 2);
    assertThat(incident.status()).isEqualTo(IncidentStatus.NEW);
    assertThat(incident.description()).isEqualTo("A rede está rota.");
    assertThat(incident.photos()).hasSize(2);
    assertThat(incident.resolvedAt()).isNull();
    assertThat(incident.canNotifyReporter()).isTrue();
  }

  @Test
  void theTextIsRequiredAndShort() {
    assertThatThrownBy(() -> report(IncidentOrigin.RESIDENT, "   ", 0))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.DESCRIPTION_INVALID);
    assertThatThrownBy(() -> report(IncidentOrigin.RESIDENT, "x".repeat(501), 0))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.DESCRIPTION_INVALID);
    assertThat(report(IncidentOrigin.RESIDENT, "x".repeat(500), 0).description()).hasSize(500);
  }

  @Test
  void atMostThreePhotos() {
    assertThatThrownBy(() -> report(IncidentOrigin.RESIDENT, "Rota", 4))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.TOO_MANY_PHOTOS);
  }

  @Test
  void resolvingKeepsTheMessageAndTheDateAndReopeningClearsThem() {
    Incident incident = report(IncidentOrigin.RESIDENT, "Rota", 0).withVersion(0);
    Instant later = NOW.plusSeconds(3600);
    Incident resolved = incident.review(IncidentStatus.RESOLVED, " Avisado ", " Cambiada ", later);
    assertThat(resolved.resolvedAt()).isEqualTo(later);
    assertThat(resolved.internalNote()).isEqualTo("Avisado");
    assertThat(resolved.resolutionMessage()).isEqualTo("Cambiada");
    assertThat(resolved.resolvedSince(incident)).isTrue();

    Incident again =
        resolved.review(IncidentStatus.RESOLVED, null, "Cambiada", later.plusSeconds(60));
    assertThat(again.resolvedAt()).isEqualTo(later);
    assertThat(again.resolvedSince(resolved)).isFalse();

    Incident reopened = resolved.review(IncidentStatus.IN_PROGRESS, "Volveu", "Ignorada", later);
    assertThat(reopened.resolvedAt()).isNull();
    assertThat(reopened.resolutionMessage()).isNull();
  }

  @Test
  void theMessageOnlyGoesToResidentsAndIsShort() {
    Incident staff = report(IncidentOrigin.STAFF, "Rota", 0);
    assertThat(staff.review(IncidentStatus.RESOLVED, null, "Para ninguén", NOW).resolutionMessage())
        .isNull();
    assertThat(staff.canNotifyReporter()).isFalse();
    Incident resident = report(IncidentOrigin.RESIDENT, "Rota", 0);
    assertThatThrownBy(() -> resident.review(IncidentStatus.RESOLVED, null, "x".repeat(501), NOW))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.MESSAGE_INVALID);
    assertThatThrownBy(() -> resident.review(IncidentStatus.NEW, "x".repeat(1001), null, NOW))
        .extracting(e -> ((DomainException) e).errorCode())
        .isEqualTo(IncidentError.NOTE_INVALID);
  }

  @Test
  void anAnonymousReporterHasNobodyToWriteTo() {
    Reporter nobody = Reporter.anonymous(Language.ES);
    assertThat(nobody.subject()).isNull();
    assertThat(nobody.email()).isNull();
    assertThat(nobody.toString()).doesNotContain("@");
    assertThat(RESIDENT.toString()).doesNotContain("persoa@example.org");
  }
}
