// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.AddressMatchResponse;
import gal.furar.facility.adapter.in.web.dto.AddressSuggestionResponse;
import gal.furar.facility.application.port.in.SearchAddressUseCase;
import gal.furar.facility.domain.AddressMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busca de enderezos polo servidor (ADR-0023): suxestións, resolución e punto → enderezo. Xestor e
 * persoal (o permiso vive no caso de uso); nunca pública. Se o buscador non responde, 503 {@code
 * facility.geocoder-unavailable} e a web deixa encher o enderezo a man.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/address-search")
@Tag(name = "facilities", description = "Instalacións")
class AddressSearchController {

  private final SearchAddressUseCase search;

  AddressSearchController(SearchAddressUseCase search) {
    this.search = search;
  }

  @GetMapping
  @Operation(
      operationId = "suggestAddresses",
      summary = "Ata 10 suxestións do concello; con menos de 3 ou máis de 100 caracteres, ningunha")
  List<AddressSuggestionResponse> suggest(
      @PathVariable String slug, @RequestParam(defaultValue = "") String q) {
    return search.suggest(q).stream()
        .map(s -> new AddressSuggestionResponse(s.id(), s.label()))
        .toList();
  }

  @GetMapping("/reverse")
  @Operation(operationId = "reverseAddress", summary = "Enderezo máis próximo a un punto do mapa")
  AddressMatchResponse reverse(
      @PathVariable String slug, @RequestParam BigDecimal lat, @RequestParam BigDecimal lon) {
    return response(search.reverse(lat, lon));
  }

  @GetMapping("/{id}")
  @Operation(operationId = "resolveAddress", summary = "Enderezo dunha suxestión")
  AddressMatchResponse resolve(@PathVariable String slug, @PathVariable String id) {
    return response(search.resolve(id));
  }

  private static AddressMatchResponse response(AddressMatch m) {
    return new AddressMatchResponse(
        m.street(),
        m.streetNumber(),
        m.postalCode(),
        m.locality(),
        m.ineCode(),
        m.latitude(),
        m.longitude());
  }
}
