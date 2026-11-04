// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Os ocos libres que ve a veciñanza, sen sesión (plan do dominio 2, §2.2 e §1.2.18). Consultar non
 * garda nada (ADR-0025, punto 9). Sen data, hoxe en Europe/Madrid.
 */
public interface ReadAvailabilityUseCase {

  /**
   * Os ocos dun uso nun día, para a ficha e a páxina de reservar.
   *
   * @throws gal.furar.shared.domain.DomainException {@code common.not-found} se a instalación non
   *     se ve (borrador, de baixa ou doutro concello) ou o uso non é dela
   */
  UseAvailability ofUse(UUID facilityId, UUID useId, @Nullable LocalDate date);

  /**
   * Para a portada e o listado: unha páxina das instalacións que se reservan por internet, co
   * número de ocos libres dese día e as tres primeiras horas, cunha consulta de ocupación.
   */
  FacilitiesAvailability ofPage(@Nullable LocalDate date, int page, int size);
}
