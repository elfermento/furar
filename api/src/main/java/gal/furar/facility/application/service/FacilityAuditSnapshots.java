// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.domain.Address;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityDetails;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.UsePart;
import gal.furar.facility.domain.UsePeriod;
import gal.furar.facility.domain.UseRules;
import gal.furar.facility.domain.Weekdays;
import gal.furar.facility.domain.schedule.Block;
import gal.furar.facility.domain.schedule.ScheduleDay;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.TimeRange;
import gal.furar.shared.audit.AuditChanges;
import gal.furar.shared.audit.AuditChanges.Snapshot;
import java.time.DayOfWeek;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * As fotos da configuración dunha instalación para o rexistro de actividade (ADR-0020, revisión do
 * 2026-11-10): o que se compara antes e despois de cada escritura. Valores curtos e sen datos
 * persoais; o teléfono, o correo, os textos longos (descrición, normas, pago) e a situación no mapa
 * van ocultos (só «cambiou»). Os motivos dos bloqueos e das excepcións son texto libre: ocultos.
 */
public final class FacilityAuditSnapshots {

  private FacilityAuditSnapshots() {}

  /** A ficha: nome, estado, datos, enderezo, servizos e os textos (estes, ocultos). */
  public static Snapshot of(Facility f) {
    FacilityDetails d = f.details();
    return AuditChanges.snapshot()
        .value("facility.name", f.name())
        .value("facility.status", f.status().name())
        .value("facility.published", f.published())
        .value("facility.type", d.type() == null ? null : d.type().name())
        .value("facility.ridegaCode", d.ridegaCode())
        .value("facility.sports", d.sports().size())
        .hidden("facility.phone", d.phone())
        .hidden("facility.email", d.email())
        .value("facility.onlineBooking", d.onlineBooking())
        .value("facility.showFreeSlots", d.showFreeSlots())
        .value("facility.covered", d.covered())
        .value("facility.entryControl", d.entryControl())
        .value("facility.address", address(f.address()))
        .hidden(
            "facility.location",
            f.address() == null ? null : f.address().latitude() + "," + f.address().longitude())
        .value(
            "facility.amenities",
            f.amenities().stream().map(Enum::name).sorted().collect(Collectors.joining(",")))
        .hidden("facility.description", f.description())
        .hidden("facility.houseRules", f.houseRules())
        .hidden("facility.paymentNotes", f.paymentNotes());
  }

  /** Un uso: nome, modo, prazas, duración, prezo, datas, días e regras. */
  public static Snapshot of(FacilityUse u) {
    UseRules r = u.rules();
    return AuditChanges.snapshot()
        .value("use.name", u.name())
        .value("use.rentalMode", u.rentalMode().name())
        .value("use.capacity", u.capacity())
        .value("use.parts", u.parts().stream().map(UsePart::name).collect(Collectors.joining(", ")))
        .value("use.slotMinutes", u.slotMinutes())
        .value("use.availability", u.availability().name())
        .value("use.periods", periods(u.periods()))
        .value("use.weekdays", weekdays(u.weekdays()))
        .value("use.blocksOthers", u.blocksOthers())
        .value("use.priceKind", u.price().kind().name())
        .value("use.price", AuditChanges.money(u.price().amountCents()))
        .value("use.minAdvanceHours", r.minAdvanceHours())
        .value("use.maxAdvanceDays", r.maxAdvanceDays())
        .value("use.maxSlots", r.maxSlots())
        .value("use.capCount", r.capCount())
        .value("use.capPeriod", r.capPeriod() == null ? null : r.capPeriod().name())
        .value("use.cancelHours", r.cancelHours())
        .value("use.archived", u.isArchived());
  }

  /** Unha tempada: nome, datas e os tramos de cada día. */
  public static Snapshot of(Season season) {
    Snapshot s =
        AuditChanges.snapshot()
            .value("season.name", season.name())
            .value("season.startDate", season.startDate())
            .value("season.endDate", season.endDate());
    for (ScheduleDay day : ScheduleDay.values()) {
      s.value("season.day." + day.name(), hours(season.week().hours(day)));
    }
    return s;
  }

  /** Unha excepción do horario: o día, aberta ou pechada e as horas; o motivo, oculto. */
  public static Snapshot of(ScheduleException e) {
    return AuditChanges.snapshot()
        .value("exception.date", e.date())
        .value("exception.hours", e.isOpen() ? hours(e.hours()) : "CLOSED")
        .hidden("exception.reason", e.reason());
  }

  /** Un bloqueo: o día e as horas; o motivo, oculto. */
  public static Snapshot of(Block b) {
    return AuditChanges.snapshot()
        .value("block.date", b.date())
        .value("block.hours", hours(List.of(b.interval())))
        .hidden("block.reason", b.reason());
  }

  private static @Nullable String address(@Nullable Address a) {
    if (a == null) {
      return null;
    }
    String street = a.streetNumber() == null ? a.street() : a.street() + ", " + a.streetNumber();
    return street + " · " + a.postalCode() + " " + a.locality();
  }

  /** Os tramos como «09:00–14:00, 16:00–21:00»; sen ningún, «CLOSED». */
  private static String hours(List<TimeRange> ranges) {
    if (ranges.isEmpty()) {
      return "CLOSED";
    }
    return ranges.stream()
        .map(r -> AuditChanges.range(r.startMinute(), r.endMinute()))
        .collect(Collectors.joining(", "));
  }

  private static @Nullable String periods(List<UsePeriod> periods) {
    if (periods.isEmpty()) {
      return null;
    }
    return periods.stream().map(p -> p.from() + " – " + p.to()).collect(Collectors.joining(", "));
  }

  /** Os días da semana do uso como «MONDAY,TUESDAY» (todos: «ALL»). */
  private static String weekdays(int mask) {
    if ((mask & Weekdays.ALL) == Weekdays.ALL) {
      return "ALL";
    }
    StringBuilder out = new StringBuilder();
    for (DayOfWeek day : DayOfWeek.values()) {
      if ((mask & Weekdays.bit(day)) != 0) {
        out.append(out.isEmpty() ? "" : ",").append(day.name());
      }
    }
    return out.toString();
  }
}
