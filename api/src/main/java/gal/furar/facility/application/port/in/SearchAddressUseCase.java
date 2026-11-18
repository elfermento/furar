// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.AddressMatch;
import gal.furar.facility.domain.AddressSuggestion;
import java.math.BigDecimal;
import java.util.List;

/**
 * Busca de enderezos para a alta e a sección Localización (especificación, bloque 1, «Enderezos»):
 * xestor e persoal. O enderezo gárdase despois coa instalación.
 */
public interface SearchAddressUseCase {

  /**
   * Suxestións do concello actual. Con menos de 3 ou máis de 100 caracteres devolve unha lista
   * baleira sen consultar o buscador.
   */
  List<AddressSuggestion> suggest(String query);

  /** Enderezo dunha suxestión ou {@code facility.address-not-found}. */
  AddressMatch resolve(String id);

  /** Punto do mapa → enderezo, ou {@code facility.address-not-found}. */
  AddressMatch reverse(BigDecimal latitude, BigDecimal longitude);
}
