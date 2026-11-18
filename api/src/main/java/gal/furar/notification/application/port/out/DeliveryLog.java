// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.out;

import gal.furar.notification.domain.Delivery;
import gal.furar.notification.domain.DeliveryStatus;
import gal.furar.notification.domain.IncidentMailDelivery;
import gal.furar.notification.domain.InvitationMailDelivery;
import gal.furar.notification.domain.MailKind;
import gal.furar.notification.domain.OpenMatchDelivery;
import gal.furar.notification.domain.SeriesDelivery;
import gal.furar.notification.domain.WaitDelivery;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  /**
   * Os correos dese tipo e evento (unha liberación ou unha cancelación) a esas esperas que xa se
   * rexistraron, por espera.
   */
  Map<UUID, WaitDelivery> waitDeliveries(MailKind kind, UUID releaseId, Collection<UUID> waitIds);

  /**
   * As esperas de {@code waitIds} que recibiron un aviso (SENT) desde {@code since}, por calquera
   * liberación: o mínimo entre avisos. Unha consulta para todas.
   */
  Set<UUID> waitsNoticedSince(Collection<UUID> waitIds, Instant since);

  /** Garda o resultado dun aviso a unha espera nunha transacción propia e curta. */
  void record(WaitDelivery delivery);

  /**
   * Conservación dos correos ás esperas («Quedou libre» e «Lista de espera cancelada»): borra os
   * rexistrados antes de {@code cutoff}.
   *
   * @return cantos se borraron
   */
  int purgeWaitNoticesBefore(Instant cutoff);

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

  /** As claves dos correos dese tipo desa incidencia que xa se rexistraron (ADR-0036). */
  Set<String> incidentKeys(UUID incidentId, IncidentMailDelivery.Kind kind);

  /** Garda o resultado do correo dunha incidencia nunha transacción propia e curta. */
  void record(IncidentMailDelivery delivery);

  /**
   * Borra os envíos destas incidencias, na transacción de quen chama (ou nunha propia).
   *
   * @return cantos se borraron
   */
  int forgetIncidents(Collection<UUID> incidentIds);

  /**
   * Os correos dese tipo e publicación dunha partida aberta xa rexistrados (ADR-0040), polo seu
   * destino (a fila da persoa ou a reserva de quen organiza).
   */
  Map<UUID, OpenMatchDelivery> openMatchDeliveries(MailKind kind, String eventId);

  /** Garda o resultado dun correo dunha partida aberta nunha transacción propia e curta. */
  void record(OpenMatchDelivery delivery);

  /**
   * O correo dese tipo e publicación dunha tempada ou solicitude, se xa se rexistrou (ADR-0039).
   */
  Optional<SeriesDelivery> seriesDelivery(MailKind kind, UUID target, String eventId);

  /** Garda o resultado dun correo dunha tempada, nunha transacción propia. */
  void record(SeriesDelivery delivery);

  /**
   * Conservación: borra os envíos rexistrados antes de {@code cutoff}, de reservas e de
   * invitacións.
   *
   * @return cantos se borraron
   */
  int purgeCreatedBefore(Instant cutoff);
}
