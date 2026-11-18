// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.out.persistence;

import gal.furar.incident.application.port.out.IncidentRepository;
import gal.furar.incident.domain.Incident;
import gal.furar.incident.domain.IncidentId;
import gal.furar.incident.domain.IncidentPhoto;
import gal.furar.incident.domain.IncidentStatus;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.tenancy.TenantScopedPersistenceAdapter;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** As incidencias do concello actual: falla sen concello ou sen transacción (ADR-0006). */
@Component
class IncidentPersistenceAdapter extends TenantScopedPersistenceAdapter
    implements IncidentRepository {

  private final IncidentSql sql;
  private final Clock clock;

  IncidentPersistenceAdapter(IncidentSql sql, Clock clock) {
    this.sql = sql;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Incident insert(Incident incident, List<PhotoContent> photos) {
    UUID municipality = tenant().id();
    sql.insert(municipality, incident);
    Instant now = clock.instant();
    for (PhotoContent content : photos) {
      IncidentPhoto photo = content.photo();
      sql.insertPhoto(municipality, incident.id().value(), photo, content.contentType(), now);
      sql.insertContent(municipality, photo.id(), IncidentSql.LARGE, content.large());
      sql.insertContent(municipality, photo.id(), IncidentSql.THUMB, content.thumb());
    }
    return incident.withVersion(0);
  }

  @Override
  @Transactional
  public Incident update(Incident incident) {
    if (incident.version() == null || sql.update(tenant().id(), incident) == 0) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return incident.withVersion(incident.version() + 1);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Incident> findById(IncidentId id) {
    UUID municipality = tenant().id();
    return sql.find(municipality, id.value()).map(i -> withPhotos(municipality, List.of(i)).get(0));
  }

  @Override
  @Transactional(readOnly = true)
  public List<Incident> recent(int limit) {
    UUID municipality = tenant().id();
    return withPhotos(municipality, sql.recent(municipality, limit));
  }

  @Override
  @Transactional(readOnly = true)
  public long countByStatus(IncidentStatus status) {
    return sql.countByStatus(tenant().id(), status.name());
  }

  @Override
  @Transactional(readOnly = true)
  public long countReportedSince(String subject, Instant since) {
    return sql.countReportedSince(tenant().id(), subject, since);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PhotoData> photo(IncidentId id, UUID photoId, boolean large) {
    return sql.photoContent(
            tenant().id(), id.value(), photoId, large ? IncidentSql.LARGE : IncidentSql.THUMB)
        .map(p -> new PhotoData(p.bytes(), p.contentType()));
  }

  @Override
  @Transactional
  public boolean deletePhoto(IncidentId id, UUID photoId) {
    UUID municipality = tenant().id();
    if (sql.deletePhoto(municipality, id.value(), photoId) == 0) {
      return false;
    }
    sql.touch(municipality, id.value(), clock.instant());
    return true;
  }

  @Override
  @Transactional
  public boolean delete(IncidentId id) {
    return sql.delete(tenant().id(), id.value()) > 0;
  }

  /** Engade as fotos a cada incidencia cunha soa lectura. */
  private List<Incident> withPhotos(UUID municipality, List<Incident> incidents) {
    if (incidents.isEmpty()) {
      return incidents;
    }
    Map<UUID, List<IncidentPhoto>> byIncident = new LinkedHashMap<>();
    for (IncidentSql.PhotoRow row :
        sql.photos(municipality, incidents.stream().map(i -> i.id().value()).toList())) {
      byIncident.computeIfAbsent(row.incidentId(), k -> new ArrayList<>()).add(row.photo());
    }
    return incidents.stream()
        .map(i -> withPhotos(i, byIncident.getOrDefault(i.id().value(), List.of())))
        .toList();
  }

  private static Incident withPhotos(Incident i, List<IncidentPhoto> photos) {
    return new Incident(
        i.id(),
        i.facilityId(),
        i.bookingId(),
        i.type(),
        i.description(),
        i.status(),
        i.origin(),
        i.reporter(),
        i.internalNote(),
        i.resolutionMessage(),
        photos,
        i.createdAt(),
        i.updatedAt(),
        i.resolvedAt(),
        i.photosRemovedAt(),
        i.anonymizedAt(),
        i.version());
  }
}
