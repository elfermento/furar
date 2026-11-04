// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.AddressMatch;
import gal.furar.facility.domain.AddressSuggestion;
import gal.furar.facility.domain.SearchArea;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Buscador de enderezos substituíble (ADR-0023): CartoCiudad ou os enderezos gardados ({@code
 * FURAR_GEOCODER}). Se o buscador non responde, lanza {@code facility.geocoder-unavailable} (503) e
 * a web deixa encher o enderezo a man.
 */
public interface AddressSearch {

  /** Máximo de suxestións por consulta. */
  int LIMIT = 10;

  /** Suxestións para un texto xa normalizado de 3 a 100 caracteres, dentro da área. */
  List<AddressSuggestion> suggest(String query, SearchArea area);

  /** Enderezo dunha suxestión; baleiro se o id non é deste buscador ou xa non existe. */
  Optional<AddressMatch> resolve(String id);

  /** Enderezo máis próximo a un punto; baleiro se non hai. */
  Optional<AddressMatch> reverse(BigDecimal latitude, BigDecimal longitude);
}
