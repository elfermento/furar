// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.in.MyBookingBlockUseCase;
import gal.furar.booking.domain.NoShowRule;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Se a persoa da sesión está bloqueada por faltas neste concello (ADR-0032, punto 7): polo correo
 * verificado do seu token, así que quen crea a conta despois de faltar no mostrador tamén o está.
 */
@Service
public class MyBookingBlockService implements MyBookingBlockUseCase {

  private final NoShowPenalty penalty;
  private final CurrentActor currentActor;
  private final Clock clock;

  public MyBookingBlockService(NoShowPenalty penalty, CurrentActor currentActor, Clock clock) {
    this.penalty = penalty;
    this.currentActor = currentActor;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public Block block() {
    String email =
        currentActor
            .current()
            .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
            .profile()
            .email();
    int windowDays = penalty.rule().windowDays();
    if (email == null) {
      return Block.none(windowDays);
    }
    return penalty
        .blocked(EmailKey.of(email), clock.instant())
        .map(b -> new Block(true, NoShowRule.lastBlockedDay(b.endsAt()), b.faults(), windowDays))
        .orElseGet(() -> Block.none(windowDays));
  }
}
