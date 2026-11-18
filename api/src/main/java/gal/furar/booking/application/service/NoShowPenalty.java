// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingNoShow;
import gal.furar.booking.application.port.in.NoShowStanding;
import gal.furar.booking.application.port.out.NoShowLedger;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.NoShowRule;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * A penalización das faltas do concello actual (ADR-0032, punto 7), para os casos de uso que marcan
 * unha falta, a desfán ou reservan: cantas faltas leva un correo, cando se bloquea e cando se
 * levanta. Todo por correo e dentro do concello; sempre na transacción de quen chama.
 */
@Component
class NoShowPenalty {

  private final MunicipalityQueries municipalities;
  private final NoShowLedger ledger;

  NoShowPenalty(MunicipalityQueries municipalities, NoShowLedger ledger) {
    this.municipalities = municipalities;
    this.ledger = ledger;
  }

  /** A regra do concello actual; sen concello gardado, apagada. */
  NoShowRule rule() {
    return municipalities
        .noShowPolicy(TenantContext.require().id())
        .map(p -> new NoShowRule(p.since(), p.threshold(), p.windowDays(), p.blockDays()))
        .orElseGet(() -> new NoShowRule(null, 3, 30, 15));
  }

  /**
   * Despois de marcar unha falta dese correo: se chega ao límite, créase o bloqueo, desde agora ata
   * o final do día de hoxe máis os días de bloqueo, coas faltas que o causaron. Os números
   * conxélanse no bloqueo: cambialos despois non o move.
   *
   * @return como queda a persoa e se esta falta creou un bloqueo; sen nada coa penalización apagada
   */
  Fault afterFault(String emailKey, Instant now) {
    NoShowRule rule = rule();
    if (!rule.enabled()) {
      return new Fault(Optional.empty(), false);
    }
    List<BookingId> faults = ledger.countingFaults(emailKey, rule.countFrom(now));
    if (rule.reached(faults.size())) {
      Instant endsAt = rule.blockEnd(now);
      ledger.block(emailKey, now, endsAt, faults);
      return new Fault(
          Optional.of(standing(rule, faults.size(), NoShowRule.lastBlockedDay(endsAt))), true);
    }
    // Xa bloqueada por faltas anteriores: esta conta para a seguinte vez, e o aviso segue dicindo
    // ata cando non pode reservar, coas faltas do bloqueo e as de despois.
    Optional<NoShowLedger.ActiveBlock> active = ledger.activeBlock(emailKey, now);
    return new Fault(
        Optional.of(
            active
                .map(
                    b ->
                        standing(
                            rule,
                            b.faults() + faults.size(),
                            NoShowRule.lastBlockedDay(b.endsAt())))
                .orElseGet(() -> standing(rule, faults.size(), null))),
        false);
  }

  /**
   * O que deixa unha falta.
   *
   * @param standing como queda a persoa; baleiro coa penalización apagada
   * @param blockedNow se esta falta creou un bloqueo
   */
  record Fault(Optional<NoShowStanding> standing, boolean blockedNow) {}

  /**
   * Como está agora ese correo, sen escribir nada: o aviso da ficha dunha reserva con falta, tamén
   * ao reabrila. Baleiro coa penalización apagada.
   */
  Optional<NoShowStanding> standing(String emailKey, Instant now) {
    NoShowRule rule = rule();
    if (!rule.enabled()) {
      return Optional.empty();
    }
    int counting = ledger.countingFaults(emailKey, rule.countFrom(now)).size();
    return Optional.of(
        ledger
            .activeBlock(emailKey, now)
            .map(b -> standing(rule, b.faults() + counting, NoShowRule.lastBlockedDay(b.endsAt())))
            .orElseGet(() -> standing(rule, counting, null)));
  }

  /** O evento da falta para o correo: sen números coa penalización apagada. */
  BookingNoShow event(BookingId booking, UUID eventId, Optional<NoShowStanding> standing) {
    UUID municipality = TenantContext.require().id();
    return standing
        .map(
            s ->
                new BookingNoShow(
                    municipality,
                    booking.value(),
                    eventId,
                    s.faults(),
                    s.threshold(),
                    s.windowDays(),
                    s.blockDays(),
                    s.blockedUntil()))
        .orElseGet(
            () -> {
              NoShowRule rule = rule();
              return new BookingNoShow(
                  municipality,
                  booking.value(),
                  eventId,
                  0,
                  rule.threshold(),
                  rule.windowDays(),
                  rule.blockDays(),
                  null);
            });
  }

  /**
   * O bloqueo vixente dese correo, só coa penalización acesa: quen reserva por internet non pode.
   */
  Optional<NoShowLedger.ActiveBlock> blocked(String emailKey, Instant now) {
    return rule().enabled() ? ledger.activeBlock(emailKey, now) : Optional.empty();
  }

  /**
   * A falta desa reserva deixou de selo (desfíxose, validouse, cancelouna ou cambiouna o persoal,
   * ou corrixiuse o correo): levanta o bloqueo vixente que causase. Non pon a conta a cero.
   *
   * @return se levantou algún
   */
  boolean lift(BookingId cause, Instant now, String actorSubject) {
    return ledger.lift(cause, now, actorSubject) > 0;
  }

  private static NoShowStanding standing(NoShowRule rule, int faults, @Nullable LocalDate until) {
    return new NoShowStanding(faults, rule.windowDays(), rule.threshold(), rule.blockDays(), until);
  }
}
