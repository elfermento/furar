// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * As notas do concello ou, se non as puxo, as de Furar ({@code paymentDefault}, {@code
 * refundDefault}). Con {@code scope = FACILITY} cada instalación leva as súas (`GET
 * …/facilities/{id}/payment-notes`) e, se non ten, valen as de Furar ({@code furarPayment}, {@code
 * furarRefund}), non as do concello.
 */
public record PaymentNotesResponse(
    NoteResponse payment,
    boolean paymentDefault,
    NoteResponse refund,
    boolean refundDefault,
    @Schema(allowableValues = {"ALL", "FACILITY"}) String scope,
    NoteResponse furarPayment,
    NoteResponse furarRefund) {}
