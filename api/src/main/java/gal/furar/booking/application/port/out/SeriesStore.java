// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.season.Series;
import gal.furar.booking.domain.season.SeriesPlanner;
import gal.furar.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * As tempadas das entidades e as súas sesións (ADR-0039), sempre no concello actual e dentro dunha
 * transacción (ADR-0006). Quen escribe sesións xa ten o bloqueo da instalación; as sesións
 * escríbense e cancélanse en lote (tres viaxes á base), para non pasar do tempo de espera dos
 * bloqueos.
 */
public interface SeriesStore {

  Optional<Series> find(UUID id);

  /** Coa fila bloqueada ({@code for update}), despois do bloqueo da instalación. */
  Optional<Series> findForUpdate(UUID id);

  /** A serie creada con esa clave de reintento. */
  Optional<Series> findByKey(String idempotencyKey);

  /**
   * As activas (sen rematar e coa última data desde hoxe) ou as rematadas, da primeira data á
   * última.
   */
  List<Series> list(boolean ended, LocalDate today);

  /** As tempadas dunha entidade, da máis recente. */
  List<Series> ofOrganization(UUID organizationId);

  /** Cantas sesións confirmadas e canceladas ten cada serie. */
  Map<UUID, Counts> counts(Collection<UUID> seriesIds);

  /** Sesións dunha serie por estado. */
  record Counts(int confirmed, int cancelled) {}

  /** As datas de todas as sesións da serie (tamén canceladas). */
  List<LocalDate> sessionDates(UUID seriesId);

  /** As ocupacións activas da instalación que pisan {@code [from, to)}, co nome de quen é. */
  List<SeriesPlanner.Occupant> occupants(UUID facilityId, Instant from, Instant to);

  /** Dos códigos dados, os que xa ten algunha reserva do concello. Unha consulta. */
  Set<String> takenCodes(Collection<String> codes);

  void insert(Series series);

  /**
   * Garda os cambios da serie (versión + 1).
   *
   * @throws DomainException {@code common.stale-version}
   */
  void update(Series before, Series after);

  /**
   * As sesións novas en lote: {@code booking}, {@code booking_occupancy} e a fila {@code CREATED}
   * da historia.
   *
   * @return as sesións cuxo código xa existía (non se escribiron): quen chama xera outros
   * @throws DomainException {@code booking.slot-taken} ou {@code booking.part-taken} se o para a
   *     base
   */
  List<Booking> insertSessions(List<Booking> sessions, String actorSubject);

  /**
   * As sesións confirmadas e sen marca dunha actividade do concello (ADR-0041), destas reservas,
   * que empezan despois de {@code after}, en orde.
   */
  List<Booking> activitySessions(UUID activityId, Collection<UUID> bookingIds, Instant after);

  /** Pon o nome da actividade nas súas sesións sen anonimizar (o titular é só ese nome). */
  void renameActivity(UUID activityId, String name);

  /** As sesións confirmadas da serie que empezan despois de {@code after}, en orde. */
  List<Booking> confirmedSessions(UUID seriesId, Instant after);

  /**
   * Cancela en lote: cada fila {@code booking} (versión + 1), as ocupacións inactivas e a fila
   * {@code CANCELLED_BY_MUNICIPALITY} da historia, co aviso por correo.
   *
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes
   */
  void cancelSessions(List<Booking> before, List<Booking> after, String actorSubject);
}
