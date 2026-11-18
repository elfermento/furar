// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import gal.furar.shared.domain.PageResult;
import java.util.List;
import java.util.UUID;

/**
 * O que fai unha persoa responsable dunha entidade coa súa conta de sempre (ADR-0039, punto 10):
 * ver e cancelar as reservas da entidade, reservar no seu nome e solicitar tempadas. Sen ser
 * responsable dunha entidade dada de alta, 404 {@code booking.organization-not-found}; unha conta
 * de traballo, 403 {@code booking.staff-account}.
 */
public interface MyOrganizationsUseCase {

  /** As entidades das que é responsable, por nome. */
  List<MyOrganization> mine();

  /** Unha entidade da persoa responsable. */
  record MyOrganization(UUID id, String name) {}

  /** As próximas ou o historial da entidade. */
  PageResult<MyBooking> bookings(UUID organizationId, boolean upcoming, int page, int size);

  MyBooking booking(UUID organizationId, BookingId id);

  /**
   * Cancela unha sesión (ata que empeza, sen custo) ou unha reserva extra (coas súas normas). A
   * persoa responsable queda como quen cancelou.
   */
  MyBooking cancel(UUID organizationId, BookingId id, long expectedVersion);

  /**
   * Reserva no nome da entidade: o fluxo de sempre, sen persoas nin usos por prazas; o límite do
   * uso conta para a entidade.
   */
  MyBooking reserve(UUID organizationId, ReserveCommand command);

  /** Solicita unha tempada; como moito cinco pendentes. */
  SeriesRequestView request(UUID organizationId, SeriesRequestCommand command);

  /** As solicitudes da entidade, da máis nova. */
  List<SeriesRequestView> requests(UUID organizationId);

  /** Retira unha pendente; xa decidida, 409 {@code booking.series-request-decided}. */
  void withdraw(UUID organizationId, UUID requestId);
}
