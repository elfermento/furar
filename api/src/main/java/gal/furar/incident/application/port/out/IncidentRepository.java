// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.application.port.out;

import gal.furar.incident.domain.Incident;
import gal.furar.incident.domain.IncidentId;
import gal.furar.incident.domain.IncidentPhoto;
import gal.furar.incident.domain.IncidentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** As incidencias do concello actual: falla sen concello ou sen transacción (ADR-0006). */
public interface IncidentRepository {

  /** Garda unha incidencia nova coas súas fotos (os bytes, á parte) e devólvea coa versión 0. */
  Incident insert(Incident incident, List<PhotoContent> photos);

  /**
   * Garda os cambios se a versión gardada é a de {@code incident}; devólvea coa versión nova.
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.stale-version} se cambiou
   */
  Incident update(Incident incident);

  Optional<Incident> findById(IncidentId id);

  /** As máis recentes, da máis nova á máis vella, coas súas fotos, nun número fixo de lecturas. */
  List<Incident> recent(int limit);

  long countByStatus(IncidentStatus status);

  /** Cantas avisou esta conta desde {@code since}. */
  long countReportedSince(String subject, Instant since);

  /**
   * Os bytes dunha foto desa incidencia.
   *
   * @param large a grande ou a miniatura
   */
  Optional<PhotoData> photo(IncidentId id, UUID photoId, boolean large);

  /**
   * Quita unha foto desa incidencia (cos bytes) e sobe a versión da incidencia.
   *
   * @return falso se a foto non era desa incidencia
   */
  boolean deletePhoto(IncidentId id, UUID photoId);

  /**
   * Borra a incidencia coas súas fotos.
   *
   * @return falso se non existía
   */
  boolean delete(IncidentId id);

  /** Unha foto xa tratada para gardar: o seu lugar e os bytes das dúas variantes. */
  record PhotoContent(IncidentPhoto photo, byte[] large, byte[] thumb, String contentType) {

    public PhotoContent {
      Objects.requireNonNull(photo, "photo");
      Objects.requireNonNull(large, "large");
      Objects.requireNonNull(thumb, "thumb");
      Objects.requireNonNull(contentType, "contentType");
    }
  }

  /** Os bytes dunha variante e o seu tipo. */
  record PhotoData(byte[] bytes, String contentType) {}
}
