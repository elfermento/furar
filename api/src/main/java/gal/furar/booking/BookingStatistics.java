// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * API pública dos agregados das reservas para os indicadores (ADR-0026). Sempre no concello actual
 * e sempre agregados: contas, minutos e céntimos por uso, día, hora ou período; ningún nome, correo
 * nin {@code sub} sae de aquí. Cada reserva conta no día do seu inicio en Europe/Madrid, tamén no
 * cobrado e nas cancelacións. As «reservas» e os «minutos» son só das confirmadas (as realizadas
 * inclúense). Non comproba permisos: faino o caso de uso que chama.
 */
public interface BookingStatistics {

  /** Reservas confirmadas e os seus minutos por uso e día. */
  List<UseDay> perUseAndDay(StatsQuery query);

  /**
   * Minutos reservados (confirmadas) por uso, día ISO da semana e hora, cortando cada reserva polas
   * horas que pisa: unha de 90 minutos ás 10:00 dá 60 ás 10 e 30 ás 11.
   */
  List<UseWeekdayHour> perUseWeekdayHour(StatsQuery query);

  /** Reservas e minutos (confirmadas) e céntimos pagados por período e uso. */
  List<UseBucket> perBucket(StatsQuery query, StatsUnit unit);

  /** Persoas distintas con reservas confirmadas en cada período. */
  List<PeopleBucket> peoplePerBucket(StatsQuery query, StatsUnit unit);

  /** Persoas distintas con reservas confirmadas en todo o filtro (non a suma dos períodos). */
  long people(StatsQuery query);

  /** Cancelacións por período: da persoa en prazo e fóra de prazo, e do concello. */
  List<CancellationBucket> cancellations(StatsQuery query, StatsUnit unit);

  /** Os cobros por instalación: pagado, pendente, a devolver e devolto. */
  List<FacilityMoney> money(StatsQuery query);

  /**
   * Valoracións por período (ADR-0037): cantas e a suma das notas, polo día en que se fixeron
   * (Europe/Madrid), coa instalación e o uso da súa reserva. Tamén as de comentario oculto.
   */
  List<ReviewBucket> reviews(StatsQuery query, StatsUnit unit);

  /** Os usos con algunha reserva (calquera estado) no filtro. */
  Set<UUID> useIds(StatsQuery query);

  /**
   * As reservas confirmadas que pisan os días {@code from}–{@code to} (Europe/Madrid, incluídos)
   * nesas instalacións, sen persoa: o uso, a parte, o inicio, a fin e as prazas; e o aforo de cada
   * franxa dos usos por prazas con algunha reserva nese tempo (a ocupación publicada, ADR-0034).
   */
  ConfirmedIntervals confirmedIntervals(LocalDate from, LocalDate to, Collection<UUID> facilityIds);

  /**
   * @param partId a parte reservada, só nos usos por partes
   * @param seats as prazas reservadas (1 fóra dos usos por prazas)
   */
  record ConfirmedInterval(
      UUID facilityId, UUID useId, @Nullable UUID partId, Instant start, Instant end, int seats) {}

  /**
   * O aforo dunha franxa dun uso por prazas tal como quedou conxelado ao reservar.
   *
   * @param slotStart o inicio da franxa
   */
  record SeatSlot(UUID useId, Instant slotStart, int capacity) {}

  /** As reservas e os aforos das franxas. */
  record ConfirmedIntervals(List<ConfirmedInterval> bookings, List<SeatSlot> seatSlots) {

    public ConfirmedIntervals {
      bookings = List.copyOf(bookings);
      seatSlots = List.copyOf(seatSlots);
    }
  }

  /**
   * @param day o día do inicio, en Europe/Madrid
   * @param bookings reservas confirmadas
   * @param minutes a súa duración sumada
   */
  record UseDay(UUID useId, UUID facilityId, LocalDate day, long bookings, long minutes) {}

  /**
   * @param isoWeekday de 1 (luns) a 7 (domingo), en Europe/Madrid
   * @param hour de 0 a 23, en Europe/Madrid
   * @param minutes minutos reservados dentro desa hora
   */
  record UseWeekdayHour(UUID useId, int isoWeekday, int hour, long minutes) {}

  /**
   * @param start primeiro día do período (o luns na semana)
   * @param bookings reservas confirmadas
   * @param minutes a súa duración sumada
   * @param paidCents importe das reservas pagadas, calquera que sexa o seu estado
   */
  record UseBucket(LocalDate start, UUID useId, long bookings, long minutes, long paidCents) {}

  /**
   * @param start primeiro día do período
   * @param people persoas distintas con reservas confirmadas nese período
   */
  record PeopleBucket(LocalDate start, long people) {}

  /**
   * @param start primeiro día do período
   * @param inTime canceladas pola persoa antes do límite para cancelar
   * @param late canceladas pola persoa desde o límite
   * @param byMunicipality canceladas polo concello
   */
  record CancellationBucket(LocalDate start, long inTime, long late, long byMunicipality) {}

  /**
   * @param start primeiro día do período
   * @param reviews cantas valoracións
   * @param starsSum a suma das súas notas
   */
  record ReviewBucket(LocalDate start, long reviews, long starsSum) {}

  /**
   * @param paidCents pagado
   * @param pendingCents pendente, só das confirmadas
   * @param toRefundCents a devolver
   * @param refundedCents devolto
   */
  record FacilityMoney(
      UUID facilityId, long paidCents, long pendingCents, long toRefundCents, long refundedCents) {}
}
