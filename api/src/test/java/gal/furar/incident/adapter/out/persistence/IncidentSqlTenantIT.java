// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.incident.domain.Incident;
import gal.furar.incident.domain.IncidentId;
import gal.furar.incident.domain.IncidentOrigin;
import gal.furar.incident.domain.IncidentPhoto;
import gal.furar.incident.domain.IncidentStatus;
import gal.furar.incident.domain.IncidentType;
import gal.furar.incident.domain.Reporter;
import gal.furar.shared.domain.Language;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.AbstractTenantIT;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A8b (ADR-0006): o SQL nativo de {@link IncidentSql} só le, cambia e borra as incidencias, as
 * fotos e os bytes do seu concello.
 */
class IncidentSqlTenantIT extends AbstractTenantIT {

  static final Instant NOW = Instant.parse("2026-11-11T10:42:00Z");

  @Autowired IncidentSql subject;
  @Autowired TransactionTemplate tx;

  <T> T as(UUID municipality, Supplier<T> work) {
    return TenantContext.runAs(TenantRef.of(municipality), () -> tx.execute(s -> work.get()));
  }

  /** Unha incidencia cunha foto, resolta hai un ano se {@code resolved}. */
  UUID incident(UUID municipality, boolean resolved) {
    IncidentPhoto photo = new IncidentPhoto(UUID.randomUUID(), 0, 40, 30);
    Incident incident =
        Incident.report(
            IncidentId.newId(),
            UUID.randomUUID(),
            null,
            IncidentType.CLEANING,
            "Papeleiras cheas",
            IncidentOrigin.RESIDENT,
            new Reporter("sub-" + municipality, "Persoa", "persoa@example.org", Language.GL),
            List.of(photo),
            NOW.minusSeconds(400 * 86400L));
    Incident stored =
        resolved
            ? incident.review(IncidentStatus.RESOLVED, null, null, NOW.minusSeconds(365 * 86400L))
            : incident;
    as(
        municipality,
        () -> {
          subject.insert(municipality, stored);
          subject.insertPhoto(
              municipality, stored.id().value(), photo, "image/jpeg", NOW.minusSeconds(86400));
          subject.insertContent(municipality, photo.id(), IncidentSql.LARGE, new byte[] {1, 2});
          subject.insertContent(municipality, photo.id(), IncidentSql.THUMB, new byte[] {3});
          return null;
        });
    return stored.id().value();
  }

  @Override
  protected Object seed(UUID municipality) {
    return incident(municipality, false);
  }

  @Override
  protected Collection<Object> visibleTo(UUID municipality) {
    return as(
        municipality,
        () ->
            subject.recent(municipality, 100).stream().map(i -> (Object) i.id().value()).toList());
  }

  long rows(String table, UUID municipality) {
    return jdbc.sql("select count(*) from " + table + " where municipality_id = :m")
        .param("m", municipality)
        .query(Long.class)
        .single();
  }

  @Test
  void readsCountsAndChangesOnlyItsOwn() {
    UUID a = id(createMunicipality(uniqueSlug("inc-a")));
    UUID b = id(createMunicipality(uniqueSlug("inc-b")));
    UUID mine = incident(a, false);
    UUID theirs = incident(b, false);

    assertThat(as(a, () -> subject.find(a, theirs))).isEmpty();
    Incident found = as(a, () -> subject.find(a, mine)).orElseThrow();
    assertThat(found.reporter().email()).isEqualTo("persoa@example.org");
    assertThat(found.version()).isZero();
    assertThat(as(a, () -> subject.countByStatus(a, "NEW"))).isEqualTo(1);
    assertThat(
            as(a, () -> subject.countReportedSince(a, "sub-" + a, NOW.minusSeconds(500 * 86400L))))
        .isEqualTo(1);
    assertThat(
            as(a, () -> subject.countReportedSince(a, "sub-" + b, NOW.minusSeconds(500 * 86400L))))
        .isZero();
    assertThat(as(a, () -> subject.photos(a, List.of(mine, theirs))))
        .extracting(IncidentSql.PhotoRow::incidentId)
        .containsExactly(mine);
    UUID theirPhoto =
        jdbc.sql("select id from incident_photo where incident_id = :i")
            .param("i", theirs)
            .query(UUID.class)
            .single();
    assertThat(as(a, () -> subject.photoContent(a, theirs, theirPhoto, IncidentSql.LARGE)))
        .isEmpty();
    assertThat(as(b, () -> subject.photoContent(b, theirs, theirPhoto, IncidentSql.LARGE)))
        .get()
        .extracting(IncidentSql.PhotoData::contentType)
        .isEqualTo("image/jpeg");

    // A cambia a súa e non a de B (mesma versión).
    Incident theirsAsMine =
        new Incident(
            new IncidentId(theirs),
            found.facilityId(),
            null,
            found.type(),
            found.description(),
            IncidentStatus.IN_PROGRESS,
            found.origin(),
            found.reporter(),
            null,
            null,
            List.of(),
            found.createdAt(),
            NOW,
            null,
            null,
            null,
            0L);
    assertThat(as(a, () -> subject.update(a, theirsAsMine))).isZero();
    assertThat(
            as(
                a,
                () -> subject.update(a, found.review(IncidentStatus.IN_PROGRESS, "Ok", null, NOW))))
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select status from incident where id = :i")
                .param("i", theirs)
                .query(String.class)
                .single())
        .isEqualTo("NEW");
  }

  @Test
  void deletesOnlyItsOwn() {
    UUID a = id(createMunicipality(uniqueSlug("inc-del-a")));
    UUID b = id(createMunicipality(uniqueSlug("inc-del-b")));
    UUID mine = incident(a, true);
    UUID theirs = incident(b, true);
    UUID theirPhoto =
        jdbc.sql("select id from incident_photo where incident_id = :i")
            .param("i", theirs)
            .query(UUID.class)
            .single();
    UUID myPhoto =
        jdbc.sql("select id from incident_photo where incident_id = :i")
            .param("i", mine)
            .query(UUID.class)
            .single();

    assertThat(as(a, () -> subject.deletePhoto(a, theirs, theirPhoto))).isZero();
    assertThat(as(a, () -> subject.touch(a, theirs, NOW))).isZero();
    assertThat(as(a, () -> subject.delete(a, theirs))).isZero();
    assertThat(rows("incident_photo", b)).isEqualTo(1);
    assertThat(rows("incident_photo_content", b)).isEqualTo(2);
    assertThat(rows("incident", b)).isEqualTo(1);

    assertThat(as(a, () -> subject.deletePhoto(a, mine, myPhoto))).isEqualTo(1);
    assertThat(rows("incident_photo_content", a)).isZero();
    assertThat(as(a, () -> subject.touch(a, mine, NOW))).isEqualTo(1);
    assertThat(as(a, () -> subject.delete(a, mine))).isEqualTo(1);
    assertThat(rows("incident", a)).isZero();
  }
}
