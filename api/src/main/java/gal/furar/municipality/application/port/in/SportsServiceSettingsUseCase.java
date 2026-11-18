// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.municipality.domain.Municipality;

/**
 * Axustes do servizo de deportes do concello actual: contacto e textos de pago e devolución. A
 * lectura é pública (ficha e reservar); a escritura, do xestor, coa versión do concello ({@code
 * If-Match}). Devolven o concello enteiro para a versión.
 */
public interface SportsServiceSettingsUseCase {

  Municipality sportsContact();

  Municipality updateSportsContact(UpdateSportsContactCommand command);

  Municipality paymentNotes();

  Municipality updatePaymentNotes(UpdatePaymentNotesCommand command);

  /**
   * Penalización das faltas (tarxeta «Non presentados», ADR-0032): 422 {@code
   * municipality.no-show-policy-invalid} se os números saen de rango.
   */
  Municipality updateNoShowPolicy(UpdateNoShowPolicyCommand command);

  /**
   * «Buscar xente para xogar» e as súas horas (Concello › Xeral, ADR-0040): 422 {@code
   * municipality.open-match-policy-invalid} se as horas saen de 1 a 72.
   */
  Municipality updateOpenMatchPolicy(UpdateOpenMatchPolicyCommand command);

  /** Datos abertos acesos ou apagados (Concello › Xeral, ADR-0034, punto 12). */
  Municipality updateOpenData(UpdateOpenDataCommand command);

  /** Aviso para instalar no móbil na portada, aceso ou apagado (Concello › Xeral). */
  Municipality updateInstallHint(UpdateInstallHintCommand command);
}
