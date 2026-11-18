// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * API pública síncrona das reservas para outros módulos (ADR-0005): lecturas dentro do concello
 * actual (salvo {@link #hasResidentBookings}), con records propios da raíz e nunca o dominio (A19).
 * Non comproba permisos: xa os comprobou o caso de uso que chama; nunca se expón por HTTP. Mínima
 * na fase 1: crece co que pidan os avisos e o impacto dos cambios da instalación.
 */
public interface BookingQueries {

  /** A reserva do concello actual, se existe. */
  Optional<BookingSummary> find(UUID id);

  /**
   * A reserva do concello actual para contala nun correo, se existe e ten a quen escribir (nin
   * anonimizada nin do persoal sen correo).
   */
  Optional<BookingNotice> notice(UUID id);

  /**
   * O recordatorio desa reserva do concello actual (ADR-0035), se aínda toca enviarlo: segue
   * confirmada, ten correo, o prazo para cancelar de balde non pasou e o seu correo non deu de
   * baixa os recordatorios neste concello.
   */
  Optional<BookingReminder> reminder(UUID id);

  /**
   * A quen avisar de que a franxa dese uso que empeza en {@code start} quedou libre (ADR-0033,
   * punto 7), no concello actual e en orde aleatoria (non hai quenda). Baleira se a hora xa non
   * está libre. Fóra quedan as esperas vencidas, as creadas despois de {@code releasedAt}, os
   * correos que xa teñen reserva confirmada dese uso nesa franxa e os bloqueados por faltas.
   */
  List<WaitNotice> waitNotices(UUID useId, Instant start, Instant releasedAt);

  /**
   * A quen escribir polas esperas canceladas en {@code cancellationId} (ADR-0033, revisión do
   * 2026-11-11), no concello actual: unha entrada por correo e idioma con todas as súas franxas.
   * Baleira se xa caducaron (7 días).
   */
  List<CancelledWaitsNotice> cancelledWaitNotices(UUID cancellationId);

  /**
   * O correo {@code kind} da partida aberta {@code matchId} do concello actual (ADR-0040, punto
   * 12), co estado de agora: baleiro se xa non toca (reaberta, sen persoas dentro, a solicitude
   * retirada ou xa decidida doutro xeito).
   *
   * @param playerId a solicitude, en {@code REQUESTED}, {@code ACCEPTED} e {@code REJECTED}
   */
  Optional<OpenMatchNotice> openMatchNotice(
      UUID matchId, OpenMatchNotice.Kind kind, @Nullable UUID playerId);

  /**
   * A tempada do concello actual para o correo á entidade (ADR-0039, punto 12), co estado de agora:
   * baleiro se xa non existe ou a entidade xa non ten correo (arquivada).
   */
  Optional<SeriesNotice> seriesNotice(UUID seriesId);

  /**
   * A solicitude rexeitada do concello actual para o correo á entidade (ADR-0039, punto 12):
   * baleiro se xa non existe, non está rexeitada ou a entidade xa non ten correo.
   */
  Optional<SeriesRequestNotice> seriesRequestNotice(UUID requestId);

  /**
   * Se a persoa reservou algunha vez como veciña, en <b>calquera</b> concello e en calquera estado
   * (a única lectura fóra do concello actual; ADR-0027, revisión do 2026-10-30): unha conta persoal
   * non acepta unha invitación ao persoal. Só di si ou non.
   *
   * @param subject {@code sub} do token
   */
  boolean hasResidentBookings(String subject);
}
