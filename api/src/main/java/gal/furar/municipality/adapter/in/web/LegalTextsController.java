// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.adapter.in.web.dto.LegalTextsRequest;
import gal.furar.municipality.adapter.in.web.dto.LegalTextsResponse;
import gal.furar.municipality.application.port.in.LegalTextsData;
import gal.furar.municipality.application.port.in.LegalTextsUseCase;
import gal.furar.municipality.application.port.in.UpdateLegalTextsCommand;
import gal.furar.municipality.domain.Municipality;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Textos propios das páxinas legais do concello (ADR-0017 punto 8). Lectura pública (a web pinta a
 * páxina no modo «Texto propio»); escritura do xestor co {@code If-Match} do concello. O HTML
 * sanéase no servidor antes de gardalo.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}")
@Tag(name = "municipalities", description = "Concellos")
class LegalTextsController {

  private final LegalTextsUseCase texts;

  LegalTextsController(LegalTextsUseCase texts) {
    this.texts = texts;
  }

  @GetMapping("/legal-texts")
  @PublicEndpoint
  @Operation(operationId = "getLegalTexts", summary = "Textos propios das páxinas legais")
  @VersionedResource
  ResponseEntity<LegalTextsResponse> legalTexts(@PathVariable String slug) {
    Municipality m = texts.legalTexts();
    return ResponseEntity.ok().eTag(etag(m)).body(MunicipalityWebMapper.legalTexts(m.legalTexts()));
  }

  @PutMapping("/legal-texts")
  @Operation(
      operationId = "updateLegalTexts",
      summary = "Editar os textos propios das páxinas legais (If-Match do concello)")
  @VersionedResource
  ResponseEntity<LegalTextsResponse> updateLegalTexts(
      @PathVariable String slug,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody LegalTextsRequest request) {
    LegalTextsData data =
        Objects.requireNonNull(MunicipalityWebMapper.legalTexts(request), "request");
    Municipality m =
        texts.updateLegalTexts(new UpdateLegalTextsCommand(ETags.parseIfMatch(ifMatch), data));
    return ResponseEntity.ok().eTag(etag(m)).body(MunicipalityWebMapper.legalTexts(m.legalTexts()));
  }

  private static String etag(Municipality m) {
    Long version = m.version();
    return ETags.of(version == null ? 0 : version);
  }
}
