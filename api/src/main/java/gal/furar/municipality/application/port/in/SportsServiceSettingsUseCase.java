// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

import gal.furar.municipality.domain.Municipality;

/**
 * Axustes do servizo de deportes do concello actual (plan do dominio 2, P8): contacto e textos de
 * pago e devolución. A lectura é pública (ficha e reservar); a escritura, do xestor, coa versión do
 * concello ({@code If-Match}). Devolven o concello enteiro para a versión.
 */
public interface SportsServiceSettingsUseCase {

  Municipality sportsContact();

  Municipality updateSportsContact(UpdateSportsContactCommand command);

  Municipality paymentNotes();

  Municipality updatePaymentNotes(UpdatePaymentNotesCommand command);
}
