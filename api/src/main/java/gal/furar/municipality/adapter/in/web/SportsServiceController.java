// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import gal.furar.municipality.adapter.in.web.dto.NoteRequest;
import gal.furar.municipality.adapter.in.web.dto.NoteResponse;
import gal.furar.municipality.adapter.in.web.dto.PaymentNotesRequest;
import gal.furar.municipality.adapter.in.web.dto.PaymentNotesResponse;
import gal.furar.municipality.adapter.in.web.dto.SportsContactRequest;
import gal.furar.municipality.adapter.in.web.dto.SportsContactResponse;
import gal.furar.municipality.application.port.in.SportsServiceSettingsUseCase;
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
 * Servizo de deportes do concello (plan do dominio 2, §2.2, D2-02): contacto e textos de pago e
 * devolución. Lectura pública (ficha, reservar e correos); escritura do xestor co {@code If-Match}
 * do concello.
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
