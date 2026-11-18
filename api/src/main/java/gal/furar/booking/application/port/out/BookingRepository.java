// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.BookingCancelled;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingEventKind;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Occupied;
import gal.furar.booking.domain.Participant;
import gal.furar.booking.domain.Participants;
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

  /**
   * A reserva co bloqueo da súa fila ata o fin da transacción (ADR-0032, punto 4): validar e marcar
   * a asistencia non tocan a ocupación, así que non precisan o bloqueo da instalación.
   */
  Optional<Booking> findLocked(BookingId id);

  /** A reserva dese código no concello, co bloqueo da súa fila. */
  Optional<Booking> findByCodeLocked(String code);

  /**
   * Garda un cambio da marca de asistencia (validada, falta ou desfeita): a fila {@code booking}
   * (versión + 1) e a fila {@code kind} da historia.
   *
   * @return o id da fila da historia
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes
   */
  UUID markAttendance(Booking before, Booking after, BookingEventKind kind, String actorSubject);

  /** A reserva se é da persoa co {@code sub} dado; a doutra persoa non existe para ela. */
  Optional<Booking> findOfHolder(BookingId id, String subject);

  /**
   * As reservas da persoa: as próximas (confirmadas que aínda non remataron, da máis próxima) ou o
   * historial (realizadas e canceladas, da máis recente).
   */
  PageResult<Booking> ofHolder(String subject, boolean upcoming, Instant now, int page, int size);

  /** A reserva se é desa entidade (ADR-0039); a doutra non existe para ela. */
  Optional<Booking> findOfOrganization(BookingId id, UUID organizationId);

  /**
   * As reservas da entidade (sesións e reservas extra): as próximas ou o historial, coma {@link
   * #ofHolder}.
   */
  PageResult<Booking> ofOrganization(
      UUID organizationId, boolean upcoming, Instant now, int page, int size);

  /**
   * As reservas extra confirmadas da entidade nese uso que empezan en {@code [from, to)}: o límite
   * do uso conta para a entidade coma unha persoa; as sesións non contan (ADR-0039, punto 10).
   */
  int countConfirmedOfOrganization(UUID useId, UUID organizationId, Instant from, Instant to);

  /** Unha páxina da lista de xestión, co total. */
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
   * soa consulta. As instalacións sen ocupación non aparecen.
   */
  Map<UUID, List<Occupied>> activeOccupancy(Collection<UUID> facilityIds, Instant from, Instant to);

  /**
   * As confirmadas dese correo nese uso que empezan en {@code [from, to)} (límite por persoa;
   * ADR-0031: cóntase polo correo, teña conta ou non), sen contar {@code except}: ao cambiar, a
   * reserva que se cambia. Unha reserva de N prazas conta como unha.
   */
  int countConfirmed(
      UUID useId, String emailKey, Instant from, Instant to, @Nullable BookingId except);

  /**
   * Os correos de {@code emailKeys} con reserva confirmada dese uso que pisa {@code [from, to)}
   * (ADR-0033): quen xa ten a hora non espera por ela nin recibe o aviso. Unha consulta para todos.
   */
  Set<String> bookedAmong(UUID useId, Collection<String> emailKeys, Instant from, Instant to);

  /**
   * As reservas confirmadas dese correo que empezan en {@code [from, to)}, en calquera uso: as
   * normas da persoa ao propoñer ocos parecidos (ADR-0033, punto 9), nunha consulta.
   */
  List<ConfirmedSpan> confirmedSpans(String emailKey, Instant from, Instant to);

  /**
   * De {@code personKeys} ({@link gal.furar.booking.domain.Participant#personKey}), as persoas que
   * xa teñen praza nese uso nun período que pisa {@code [from, to)} (unha praza por persoa), sen
   * contar a reserva {@code except}.
   */
  Set<String> seated(
      UUID useId,
      Collection<String> personKeys,
      Instant from,
      Instant to,
      @Nullable BookingId except);

  /**
   * As persoas de cada reserva por prazas (ADR-0031, punto 5): nunha confirmada, as que ocupan
   * praza; nunha cancelada, as que levaba ao cancelar. As reservas sen persoas non aparecen.
   */
  Map<UUID, List<Participant>> participants(Collection<BookingId> ids);

  /**
   * Pasa á conta as reservas dese correo que aínda non teñen ningunha (ADR-0031, punto 7), cunha
   * fila {@code LINKED} na historia de cada unha. Unha conta de traballo non se liga. Repetilo non
   * cambia nada.
   *
   * @return cantas ligou
   */
  int link(String emailKey, String subject, Instant now);

  /**
   * Garda unha reserva nova na orde de ADR-0025 punto 5: {@code booking}, {@code
   * booking_occupancy}, {@code use_slot} (só nas prazas, as da reserva en cada franxa), {@code
   * booking_participant} e a fila {@code CREATED} da historia.
   *
   * @param capacity as prazas do uso, só nos usos por prazas
   * @param participants as persoas da reserva; {@link Participants#none} fóra dos usos por prazas
   * @return falso, sen escribir nada, se o código xa existe no concello: quen chama xera outro
   * @throws DomainException {@code booking.slot-taken}, {@code booking.part-taken} ou {@code
   *     booking.seat-taken-by-you} se o para a base (as exclusións e o cupo); {@code
   *     booking.dependent-unknown} se unha ficha se borrou mentres se reservaba
   */
  boolean insert(
      Booking booking, @Nullable Integer capacity, String actorSubject, Participants participants);

  /**
   * Primeiro paso de cambiar: a ocupación da reserva queda inactiva e, nas prazas, cada franxa
   * devolve as súas prazas e as persoas de antes quedan substituídas. A fila {@code booking} non
   * cambia ata {@link #reschedule}; se algo falla despois, a transacción desfai tamén isto.
   */
  void release(Booking booking);

  /**
   * Garda o cambio despois de {@link #release}: a fila {@code booking} (mesmo id e código, versión
   * + 1), a ocupación nova, as prazas, as persoas de despois do cambio e a fila {@code CHANGED} co
   * período de antes e o de despois.
   *
   * @param capacity as prazas do uso novo, só nos usos por prazas
   * @param participants as persoas de despois do cambio
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes; {@code
   *     booking.slot-taken}, {@code booking.part-taken} ou {@code booking.seat-taken-by-you} se o
   *     para a base
   */
  void reschedule(
      Booking before,
      Booking after,
      @Nullable Integer capacity,
      String actorSubject,
      Participants participants);

  /**
   * Garda unha cancelación: a fila {@code booking} (versión + 1), a ocupación inactiva, as prazas
   * devoltas e a fila da historia.
   *
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes
   */
  void cancel(Booking before, Booking after, String actorSubject);

  /**
   * Garda unha cancelación do concello coas canles do aviso na fila da historia.
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
   * fixo o persoal): a fila {@code booking} (versión + 1). Se cambia o correo (ADR-0031, punto 7),
   * cambian con el a clave do titular e a conta ligada, e queda unha fila {@code HOLDER_CHANGED} na
   * historia, sen correos; o resto queda no rexistro de actividade.
   *
   * @throws DomainException {@code common.stale-version} se outra escritura chegou antes; {@code
   *     booking.seat-taken-by-you} se o correo novo xa ten praza nesa franxa
   */
  void correct(Booking before, Booking after, String actorSubject);
}
