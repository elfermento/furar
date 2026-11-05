// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.InvitationMailDelivery;
import gal.furar.notification.domain.MailKind;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Rexistro dos correos enviados no concello actual. */
public interface DeliveryLog {

  Optional<Delivery> find(UUID bookingId, MailKind kind, String eventId);

  /** O último correo da reserva que saíu, se saíu algún. */
  Optional<Sent> lastSent(UUID bookingId);

  /**
   * Un correo dunha reserva que saíu.
   *
   * @param kind que correo
   * @param at cando saíu
   */
  record Sent(MailKind kind, Instant at) {}

  /**
   * Garda o resultado dun intento nunha transacción propia: queda aínda que quen chama desfaga a
   * súa (o fallo que se lanza para reintentar).
   */
  void record(Delivery delivery);

  /** O resultado do correo desa invitación, se xa se rexistrou. */
  Optional<DeliveryStatus> invitationStatus(UUID invitationId);

  /** Garda o resultado do correo dunha invitación nunha transacción propia e curta. */
  void record(InvitationMailDelivery delivery);

  /**
   * Borra os envíos destas invitacións, na transacción de quen chama (ou nunha propia).
   *
   * @return cantos se borraron
   */
  int forgetInvitations(Collection<UUID> invitationIds);

  /**
   * Conservación: borra os envíos rexistrados antes de {@code cutoff}, de reservas e de
   * invitacións.
   *
   * @return cantos se borraron
   */
  int purgeCreatedBefore(Instant cutoff);
}
