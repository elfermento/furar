// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web;

import gal.furar.facility.adapter.in.web.dto.ArrangeImagesRequest;
import gal.furar.facility.adapter.in.web.dto.FacilityResponse;
import gal.furar.facility.application.port.in.FacilityView;
import gal.furar.facility.application.port.in.ManageFacilityImagesUseCase;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sección Fotos (especificación, bloque 1, «Fotos»): a lista de imaxes da instalación, gardada nun
 * só paso co {@code If-Match} da instalación. Devolve a ficha enteira.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/facilities/{id}/images")
@Tag(name = "facilities", description = "Instalacións")
class FacilityImageController {

  private final ManageFacilityImagesUseCase images;

  FacilityImageController(ManageFacilityImagesUseCase images) {
    this.images = images;
  }

  @PutMapping
  @Operation(
      operationId = "arrangeFacilityImages",
      summary =
          "Gardar as imaxes (If-Match): fotos da biblioteca e debuxos na orde nova (a primeira é a"
              + " portada; máximo 12) e o texto alternativo de cada foto. Baleira = o debuxo"
              + " suxerido")
  @VersionedResource
  ResponseEntity<FacilityResponse> arrange(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody ArrangeImagesRequest request) {
    FacilityView updated =
        images.arrange(
            FacilityWebMapper.toCommand(new FacilityId(id), ETags.parseIfMatch(ifMatch), request));
    Long version = updated.value().version();
    return ResponseEntity.ok()
        .eTag(ETags.of(version == null ? 0 : version))
        .body(FacilityWebMapper.toResponse(updated));
  }
}
