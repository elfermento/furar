// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha incidencia (ADR-0036): un problema nunha instalación do que avisou a veciñanza (ou que
 * anotou o persoal), co seu tipo, o texto, ata tres fotos e o estado. O concello cambia o estado,
 * escribe unha nota interna e, ao resolver, unha mensaxe opcional para a persoa. Inmutable: cada
 * cambio devolve outra.
 *
 * @param bookingId a reserva da que vén o aviso, se vén de «As miñas reservas»
 * @param resolvedAt cando se resolveu, só mentres está resolta
 * @param photosRemovedAt cando a baixa da conta de quen avisou borrou as fotos
 * @param anonymizedAt cando se quitou a persoa (baixa da conta)
 * @param version nula antes de gardala
 */
public record Incident(
    IncidentId id,
    UUID facilityId,
    @Nullable UUID bookingId,
    IncidentType type,
    String description,
    IncidentStatus status,
    IncidentOrigin origin,
    Reporter reporter,
    @Nullable String internalNote,
    @Nullable String resolutionMessage,
    List<IncidentPhoto> photos,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant resolvedAt,
    @Nullable Instant photosRemovedAt,
    @Nullable Instant anonymizedAt,
    @Nullable Long version) {

  public static final int DESCRIPTION_MAX = 500;
  public static final int NOTE_MAX = 1000;
  public static final int MESSAGE_MAX = 500;
  public static final int MAX_PHOTOS = 3;

  public Incident {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(reporter, "reporter");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    description = text(description, DESCRIPTION_MAX, IncidentError.DESCRIPTION_INVALID, true);
    internalNote = text(internalNote, NOTE_MAX, IncidentError.NOTE_INVALID, false);
    resolutionMessage = text(resolutionMessage, MESSAGE_MAX, IncidentError.MESSAGE_INVALID, false);
    photos = List.copyOf(photos);
    if (photos.size() > MAX_PHOTOS) {
      throw new DomainException(IncidentError.TOO_MANY_PHOTOS, Map.of("max", MAX_PHOTOS));
    }
    if ((status == IncidentStatus.RESOLVED) != (resolvedAt != null)) {
      throw new IllegalArgumentException("Only a resolved incident has resolvedAt");
    }
  }

  /**
   * Un aviso novo, en estado Nova.
   *
   * @param photos as fotos xa tratadas, na orde en que se subiron
   */
  public static Incident report(
      IncidentId id,
      UUID facilityId,
      @Nullable UUID bookingId,
      IncidentType type,
      String description,
      IncidentOrigin origin,
      Reporter reporter,
      List<IncidentPhoto> photos,
      Instant now) {
    return new Incident(
        id,
        facilityId,
        bookingId,
        type,
        description,
        IncidentStatus.NEW,
        origin,
        reporter,
        null,
        null,
        photos,
        now,
        now,
        null,
        null,
        null,
        null);
  }

  /**
   * O concello cambia o estado e a nota. A mensaxe para a persoa só se garda se queda resolta (e só
   * se avisou a veciñanza); en calquera outro estado bórrase. Volver a abrir unha resolta quita a
   * data de resolución.
   */
  public Incident review(
      IncidentStatus newStatus,
      @Nullable String newNote,
      @Nullable String newMessage,
      Instant now) {
    Objects.requireNonNull(newStatus, "newStatus");
    boolean resolved = newStatus == IncidentStatus.RESOLVED;
    Instant resolvedOn = resolved ? (resolvedAt != null ? resolvedAt : now) : null;
    String message = resolved && origin == IncidentOrigin.RESIDENT ? newMessage : null;
    return new Incident(
        id,
        facilityId,
        bookingId,
        type,
        description,
        newStatus,
        origin,
        reporter,
        newNote,
        message,
        photos,
        createdAt,
        now,
        resolvedOn,
        photosRemovedAt,
        anonymizedAt,
        version);
  }

  /** Se acaba de pasar a resolta respecto de {@code before}: toca o correo á persoa. */
  public boolean resolvedSince(Incident before) {
    return status == IncidentStatus.RESOLVED && before.status != IncidentStatus.RESOLVED;
  }

  /** A mesma cunha versión nova (despois de gardala). */
  public Incident withVersion(long newVersion) {
    return new Incident(
        id,
        facilityId,
        bookingId,
        type,
        description,
        status,
        origin,
        reporter,
        internalNote,
        resolutionMessage,
        photos,
        createdAt,
        updatedAt,
        resolvedAt,
        photosRemovedAt,
        anonymizedAt,
        newVersion);
  }

  /** Se ten a quen escribir ao resolver: un aviso da veciñanza cun correo e sen anonimizar. */
  public boolean canNotifyReporter() {
    return origin == IncidentOrigin.RESIDENT && reporter.email() != null && anonymizedAt == null;
  }

  private static @Nullable String text(
      @Nullable String value, int max, IncidentError error, boolean required) {
    String stripped = value == null ? null : value.strip();
    if (stripped == null || stripped.isEmpty()) {
      if (required) {
        throw new DomainException(error, Map.of("max", max));
      }
      return null;
    }
    if (stripped.length() > max) {
      throw new DomainException(error, Map.of("max", max));
    }
    return stripped;
  }

  @Override
  public String toString() {
    return "Incident[id=" + id.value() + ", status=" + status + ", type=" + type + "]";
  }
}
