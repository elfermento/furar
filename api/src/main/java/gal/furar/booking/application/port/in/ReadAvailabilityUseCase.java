// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Os ocos libres que ve a veciñanza, sen sesión. Consultar non garda nada (ADR-0025, punto 9). Sen
 * data, hoxe en Europe/Madrid.
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
   * número de ocos libres dese día e as tres primeiras horas, cunha consulta de ocupación. Con
   * {@code sports} (claves de deporte), só contan os ocos dos usos que teñen algún deles.
   */
  FacilitiesAvailability ofPage(@Nullable LocalDate date, int page, int size, Set<String> sports);

  /** {@link #ofPage} sen filtro de deportes. */
  default FacilitiesAvailability ofPage(@Nullable LocalDate date, int page, int size) {
    return ofPage(date, page, size, Set.of());
  }

  /**
   * Para a portada común (ADR-0030): todas as instalacións do concello actual que se reservan por
   * internet, co mesmo ca {@link #ofPage} e un número fixo de lecturas, sexan cantas sexan ({@code
   * page} 0 e {@code size} as que saen).
   */
  FacilitiesAvailability ofAllFacilities(@Nullable LocalDate date, Set<String> sports);

  /** {@link #ofAllFacilities} sen filtro de deportes. */
  default FacilitiesAvailability ofAllFacilities(@Nullable LocalDate date) {
    return ofAllFacilities(date, Set.of());
  }
}
