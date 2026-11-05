// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Occupied;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.PageResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Reservas gardadas, sempre no concello actual e dentro dunha transacción (ADR-0006). Quen escribe
 * xa ten o bloqueo da instalación (ADR-0025, punto 5).
 */
public interface BookingRepository {

  Optional<Booking> findById(BookingId id);

  /** A reserva se é da persoa co {@code sub} dado; a doutra persoa non existe para ela. */
  Optional<Booking> findOfHolder(BookingId id, String subject);

  /**
   * As reservas da persoa: as próximas (confirmadas que aínda non remataron, da máis próxima) ou o
   * historial (realizadas e canceladas, da máis recente).
   */
  PageResult<Booking> ofHolder(String subject, boolean upcoming, Instant now, int page, int size);

  /** Unha páxina da lista de xestión (plan do dominio 2, §2.2), co total. */
  PageResult<Booking> search(BookingFilter filter, int page, int size);

  /**
   * As confirmadas que empezan en {@code [from, to)}, dunha instalación ou de todas ({@code null}),
   * por día e hora: os bloques do calendario.
   */
  List<Booking> confirmedStarting(Instant from, Instant to, @Nullable UUID facilityId);

  /**
   * Cantas confirmadas empezan cada día (ou cada día e hora, con {@code byHour}) en {@code [from,
   * to)}, dunha instalación ou de todas, nunha consulta. Os días sen reservas non aparecen.
   */
  List<StartCount> countConfirmedStarting(
      Instant from, Instant to, @Nullable UUID facilityId, boolean byHour);

  /**
   * As ocupacións activas destas instalacións que pisan {@code [from, to)}, por instalación, nunha
   * soa consulta (§1.2.18). As instalacións sen ocupación non aparecen.
   */
  Map<UUID, List<Occupied>> activeOccupancy(Collection<UUID> facilityIds, Instant from, Instant to);

  /**
   * As confirmadas da persoa nese uso que empezan en {@code [from, to)} (límite por persoa), sen
   * contar {@code except}: ao cambiar, a reserva que se cambia (§1.2.8).
   */
  int countConfirmed(
      UUID useId, String subject, Instant from, Instant to, @Nullable BookingId except);

  /**
   * Se a persoa xa ten unha confirmada nese uso que pisa {@code [from, to)} (unha praza), sen
   * contar {@code except}.
   */
  boolean holds(UUID useId, String subject, Instant from, Instant to, @Nullable BookingId except);

  /**
   * Garda unha reserva nova na orde de ADR-0025 punto 5: {@code booking}, {@code
   * booking_occupancy}, {@code use_slot} (só nas prazas, unha praza por franxa) e a fila {@code
   * CREATED} da historia.
   *
   * @param capacity as prazas do uso, só nos usos por prazas
   * @return falso, sen escribir nada, se o código xa existe no concello: quen chama xera outro
   * @throws DomainException {@code booking.slot-taken}, {@code booking.part-taken} ou {@code
   *     booking.seat-taken-by-you} se o para a base (as exclusións e o cupo)
   */
  boolean insert(Booking booking, @Nullable Integer capacity, String actorSubject);

  /**
   * Primeiro paso de cambiar (§1.2.8): a ocupación da reserva queda inactiva e, nas prazas, cada
   * franxa devolve a súa praza. A fila {@code booking} non cambia ata {@link #reschedule}; se algo
   * falla despois, a transacción desfai tamén isto.
   */
  void release(Booking booking);

  /**
   * Garda o cambio despois de {@link #release}: a fila {@code booking} (mesmo id e código, versión
   * + 1), a ocupación nova, as prazas e a fila {@code CHANGED} co período de antes e o de despois.
   *
   * @param capacity as prazas do uso novo, só nos usos por prazas
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes; {@code
   *     booking.slot-taken}, {@code booking.part-taken} ou {@code booking.seat-taken-by-you} se o
   *     para a base
   */
  void reschedule(Booking before, Booking after, @Nullable Integer capacity, String actorSubject);

  /**
   * Garda unha cancelación: a fila {@code booking} (versión + 1), a ocupación inactiva, as prazas
   * devoltas e a fila da historia.
   *
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes
   */
  void cancel(Booking before, Booking after, String actorSubject);

  /**
   * Garda unha cancelación do concello coas canles do aviso (P12) na fila da historia.
   *
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes
   */
  void cancel(
      Booking before, Booking after, String actorSubject, Set<BookingCancelled.Channel> channels);

  /**
   * As confirmadas da instalación que aínda non remataron en {@code now} (o modal de reservas
   * afectadas, ADR-0025 punto 16), dun uso ou de todos ({@code null}) e, se se dá, só as que pisan
   * {@code [from, to)}; da primeira á última.
   */
  List<Booking> confirmedNotEnded(
      UUID facilityId,
      @Nullable UUID useId,
      Instant now,
      @Nullable Instant from,
      @Nullable Instant to);

  /**
   * Leva as prazas novas do uso aos seus contadores {@code use_slot} das franxas que empezan desde
   * {@code from} (ADR-0012); os das pasadas quedan coa capacidade que tiñan.
   */
  void resizeSeats(UUID useId, int capacity, Instant from);

  /** Se a instalación tivo algunha vez unha reserva, tamén cancelada ou xa pasada. */
  boolean anyOf(UUID facilityId);

  /**
   * Garda que o concello mantivo a reserva ante un cambio da instalación: só a fila {@code KEPT} da
   * historia; a reserva e a súa ocupación quedan como estaban.
   */
  void keep(Booking booking, Instant at, String actorSubject);

  /**
   * Garda un cambio de cobro: a fila {@code booking} (versión + 1) e a fila da historia ({@link
   * gal.furar.booking.domain.BookingEventKind#ofPayment}).
   *
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes
   */
  void changePayment(Booking before, Booking after, String actorSubject);

  /**
   * Garda unha corrección dos datos da reserva sen cambiar o oco nin o cobro (para quen é, nas que
   * fixo o persoal): a fila {@code booking} (versión + 1). Sen fila na historia: queda no rexistro
   * de actividade.
   *
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes
   */
  void correct(Booking before, Booking after);
}
