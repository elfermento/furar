// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.security.PersonArrival;
import gal.furar.shared.tenancy.TenantContext;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A reserva do mostrador pasa á conta do seu correo (ADR-0031, punto 7). Cando a persoa chega a un
 * concello coa súa conta e o correo verificado, as reservas que o persoal fixo a nome dese correo e
 * aínda non teñen conta pasan a ser súas: saen en «as miñas reservas» e pode cambialas e cancelalas
 * coas normas de sempre.
 *
 * <ul>
 *   <li><b>Transacción curta propia</b> ({@code REQUIRES_NEW}), antes do controlador e coa RLS do
 *       concello da petición: ningunha escritura cruza concellos. As doutro concello líganse cando
 *       a persoa o visita.
 *   <li><b>Unha lectura por petición</b> sobre un índice parcial; só se hai algo, a escritura.
 *       Repetilo non cambia nada.
 *   <li>Unha conta de traballo non se liga nunca (compróbao a propia sentenza).
 *   <li>Cada reserva ligada deixa unha fila {@code LINKED} na súa historia, coa propia persoa como
 *       quen actúa. Non vai ao rexistro de actividade, que é do persoal.
 * </ul>
 */
@Service
class BookingLinkService implements PersonArrival {

  private static final Logger LOG = LoggerFactory.getLogger(BookingLinkService.class);

  private final BookingRepository bookings;
  private final TransactionTemplate tx;
  private final Clock clock;

  BookingLinkService(
      BookingRepository bookings, PlatformTransactionManager transactions, Clock clock) {
    this.bookings = bookings;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Override
  public void arrived(String subject, String verifiedEmail) {
    if (TenantContext.current().isEmpty()) {
      return;
    }
    String emailKey = EmailKey.of(verifiedEmail);
    Integer linked = tx.execute(status -> bookings.link(emailKey, subject, clock.instant()));
    if (linked != null && linked > 0) {
      // Sen a conta nin o correo: só o concello e cantas.
      LOG.info(
          "bookings linked to their account municipality={} count={}",
          TenantContext.require().id(),
          linked);
    }
  }
}
