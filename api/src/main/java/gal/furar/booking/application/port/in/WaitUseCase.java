// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.Wait;
import gal.furar.facility.FacilityCover;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A lista de espera da persoa da sesión no concello (ADR-0033, punto 5): pedir que se lle avise se
 * queda libre unha hora ocupada, deixar de esperar e ver as súas esperas. Sen quenda e sen reserva
 * automática: o aviso vai a todas á vez e reserva a primeira que entre.
 */
public interface WaitUseCase {

  /**
   * Esperar pola franxa dese uso que empeza en {@code start}. Repetir non cambia nada.
   *
   * @throws gal.furar.shared.domain.DomainException {@code booking.staff-account}, {@code
   *     booking.blocked-no-show}, {@code common.not-found}, {@code booking.facility-not-bookable},
   *     {@code booking.use-not-available}, {@code booking.slot-invalid}, {@code booking.too-soon},
   *     {@code booking.too-far}, {@code booking.wait-slot-free}, {@code booking.wait-own-booking}
   *     ou {@code booking.wait-limit}
   */
  Joined join(UUID facilityId, UUID useId, Instant start);

  /**
   * Deixar de esperar.
   *
   * @throws gal.furar.shared.domain.DomainException {@code booking.wait-not-found} se non existe ou
   *     é doutra conta
   */
  void leave(UUID id);

  /** As esperas activas da persoa, por inicio. */
  List<MyWait> mine();

  /**
   * O resultado de esperar.
   *
   * @param created falso se xa esperaba por esa hora
   */
  record Joined(MyWait entry, boolean created) {}

  /**
   * Unha espera cos nomes de agora.
   *
   * @param cover portada da instalación (a miniatura da tarxeta); baleira se xa non existe
   */
  record MyWait(Wait entry, String facilityName, @Nullable FacilityCover cover, String useName) {}
}
