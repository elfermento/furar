// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.adapter.in.web.dto.InstallHintRequest;
import gal.furar.municipality.adapter.in.web.dto.InstallHintResponse;
import gal.furar.municipality.adapter.in.web.dto.NoShowPolicyRequest;
import gal.furar.municipality.adapter.in.web.dto.NoShowPolicyResponse;
import gal.furar.municipality.adapter.in.web.dto.NoteRequest;
import gal.furar.municipality.adapter.in.web.dto.NoteResponse;
import gal.furar.municipality.adapter.in.web.dto.OpenDataPublicationRequest;
import gal.furar.municipality.adapter.in.web.dto.OpenDataPublicationResponse;
import gal.furar.municipality.adapter.in.web.dto.OpenMatchPolicyRequest;
import gal.furar.municipality.adapter.in.web.dto.OpenMatchPolicyResponse;
import gal.furar.municipality.adapter.in.web.dto.PaymentNotesRequest;
import gal.furar.municipality.adapter.in.web.dto.PaymentNotesResponse;
import gal.furar.municipality.adapter.in.web.dto.SportsContactRequest;
import gal.furar.municipality.adapter.in.web.dto.SportsContactResponse;
import gal.furar.municipality.application.port.in.SportsServiceSettingsUseCase;
import gal.furar.municipality.application.port.in.UpdateInstallHintCommand;
import gal.furar.municipality.application.port.in.UpdateNoShowPolicyCommand;
import gal.furar.municipality.application.port.in.UpdateOpenDataCommand;
import gal.furar.municipality.application.port.in.UpdateOpenMatchPolicyCommand;
import gal.furar.municipality.application.port.in.UpdatePaymentNotesCommand;
import gal.furar.municipality.application.port.in.UpdateSportsContactCommand;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.PaymentNotes;
import gal.furar.municipality.domain.SportsContact;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.ETags;
import gal.furar.shared.web.VersionedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
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
 * Servizo de deportes do concello: contacto e textos de pago e devolución. Lectura pública (ficha,
 * reservar e correos); escritura do xestor co {@code If-Match} do concello.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}")
@Tag(name = "municipalities", description = "Concellos")
class SportsServiceController {

  private final SportsServiceSettingsUseCase settings;

  SportsServiceController(SportsServiceSettingsUseCase settings) {
    this.settings = settings;
  }

  @GetMapping("/sports-contact")
  @PublicEndpoint
  @Operation(operationId = "getSportsContact", summary = "Contacto do servizo de deportes")
  @VersionedResource
  ResponseEntity<SportsContactResponse> sportsContact(@PathVariable String slug) {
    Municipality m = settings.sportsContact();
    return ResponseEntity.ok().eTag(etag(m)).body(contact(m.sportsContact()));
  }

  @PutMapping("/sports-contact")
  @Operation(
      operationId = "updateSportsContact",
      summary = "Editar o contacto do servizo de deportes (If-Match do concello)")
  @VersionedResource
  ResponseEntity<SportsContactResponse> updateSportsContact(
      @PathVariable String slug,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody SportsContactRequest request) {
    Municipality m =
        settings.updateSportsContact(
            new UpdateSportsContactCommand(
                ETags.parseIfMatch(ifMatch),
                request.name(),
                request.address(),
                request.phone(),
                request.email()));
    return ResponseEntity.ok().eTag(etag(m)).body(contact(m.sportsContact()));
  }

  @GetMapping("/payment-notes")
  @PublicEndpoint
  @Operation(
      operationId = "getPaymentNotes",
      summary = "Como se paga e como se devolve (as do concello ou as de Furar)")
  @VersionedResource
  ResponseEntity<PaymentNotesResponse> paymentNotes(@PathVariable String slug) {
    Municipality m = settings.paymentNotes();
    return ResponseEntity.ok().eTag(etag(m)).body(notes(m.paymentNotes()));
  }

  @PutMapping("/payment-notes")
  @Operation(
      operationId = "updatePaymentNotes",
      summary = "Editar os textos de pago e devolución (If-Match do concello)")
  @VersionedResource
  ResponseEntity<PaymentNotesResponse> updatePaymentNotes(
      @PathVariable String slug,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody PaymentNotesRequest request) {
    NoteRequest payment = request.payment();
    NoteRequest refund = request.refund();
    Municipality m =
        settings.updatePaymentNotes(
            new UpdatePaymentNotesCommand(
                ETags.parseIfMatch(ifMatch),
                request.scope(),
                payment == null ? null : payment.gl(),
                payment == null ? null : payment.es(),
                refund == null ? null : refund.gl(),
                refund == null ? null : refund.es()));
    return ResponseEntity.ok().eTag(etag(m)).body(notes(m.paymentNotes()));
  }

  @PutMapping("/no-show-policy")
  @Operation(
      operationId = "updateNoShowPolicy",
      summary =
          "Editar a penalización das faltas (If-Match do concello): acesa ou apagada, cantas"
              + " faltas, en cantos días e cantos días sen reservar por internet")
  @ApiResponse(responseCode = "200", description = "Gardada", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "422",
      description = "municipality.no-show-policy-invalid",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(ref = "#/components/schemas/Problem")))
  @VersionedResource
  ResponseEntity<NoShowPolicyResponse> updateNoShowPolicy(
      @PathVariable String slug,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody NoShowPolicyRequest request) {
    Municipality m =
        settings.updateNoShowPolicy(
            new UpdateNoShowPolicyCommand(
                ETags.parseIfMatch(ifMatch),
                request.enabled(),
                request.threshold(),
                request.windowDays(),
                request.blockDays()));
    return ResponseEntity.ok()
        .eTag(etag(m))
        .body(MunicipalityWebMapper.noShowPolicy(m.noShowPolicy()));
  }

  @PutMapping("/open-match-policy")
  @Operation(
      operationId = "updateOpenMatchPolicy",
      summary =
          "«Buscar xente para xogar» (If-Match do concello): aceso ou apagado e «Cancelar se non"
              + " hai mínimo», horas antes do inicio (de 1 a 72). Apagado, as partidas deixan de"
              + " verse e de admitir xente sen cancelar nada")
  @ApiResponse(responseCode = "200", description = "Gardado", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "422",
      description = "municipality.open-match-policy-invalid",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(ref = "#/components/schemas/Problem")))
  @VersionedResource
  ResponseEntity<OpenMatchPolicyResponse> updateOpenMatchPolicy(
      @PathVariable String slug,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody OpenMatchPolicyRequest request) {
    Municipality m =
        settings.updateOpenMatchPolicy(
            new UpdateOpenMatchPolicyCommand(
                ETags.parseIfMatch(ifMatch), request.enabled(), request.cancelHours()));
    return ResponseEntity.ok()
        .eTag(etag(m))
        .body(MunicipalityWebMapper.openMatchPolicy(m.openMatchPolicy()));
  }

  @PutMapping("/open-data-publication")
  @Operation(
      operationId = "updateOpenDataPublication",
      summary =
          "Acender ou apagar os datos abertos do concello (If-Match do concello): apagados, a"
              + " páxina e os ficheiros responden 404")
  @ApiResponse(responseCode = "200", description = "Gardado", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<OpenDataPublicationResponse> updateOpenDataPublication(
      @PathVariable String slug,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody OpenDataPublicationRequest request) {
    Municipality m =
        settings.updateOpenData(
            new UpdateOpenDataCommand(ETags.parseIfMatch(ifMatch), request.enabled()));
    return ResponseEntity.ok().eTag(etag(m)).body(new OpenDataPublicationResponse(m.openData()));
  }

  @PutMapping("/install-hint")
  @Operation(
      operationId = "updateInstallHint",
      summary =
          "Acender ou apagar o aviso para instalar Furar no móbil, na portada do concello"
              + " (If-Match do concello)")
  @ApiResponse(responseCode = "200", description = "Gardado", useReturnTypeSchema = true)
  @VersionedResource
  ResponseEntity<InstallHintResponse> updateInstallHint(
      @PathVariable String slug,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody InstallHintRequest request) {
    Municipality m =
        settings.updateInstallHint(
            new UpdateInstallHintCommand(ETags.parseIfMatch(ifMatch), request.enabled()));
    return ResponseEntity.ok().eTag(etag(m)).body(new InstallHintResponse(m.installHint()));
  }

  private static String etag(Municipality m) {
    Long version = m.version();
    return ETags.of(version == null ? 0 : version);
  }

  private static SportsContactResponse contact(SportsContact c) {
    return new SportsContactResponse(c.name(), c.address(), c.phone(), c.email());
  }

  private static PaymentNotesResponse notes(PaymentNotes n) {
    return new PaymentNotesResponse(
        note(n.effectivePayment()),
        n.payment() == null,
        note(n.effectiveRefund()),
        n.refund() == null,
        n.scope().name(),
        note(PaymentNotes.DEFAULT_PAYMENT),
        note(PaymentNotes.DEFAULT_REFUND));
  }

  private static NoteResponse note(@Nullable LocalizedText t) {
    return t == null ? new NoteResponse("", "") : new NoteResponse(t.gl(), t.es());
  }
}
