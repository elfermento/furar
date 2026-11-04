// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.FacilityPaymentNotes;

/**
 * Ficha pública, sen sesión: só das instalacións que ve a veciñanza (nin borrador nin de baixa); as
 * demais, coma se non existisen ({@code facility.not-found}, 404).
 */
public interface GetPublicFacilityUseCase {

  PublicFacilityDetail getPublic(FacilityId id);

  /**
   * Pago e devolución da instalación (D2-11): de calquera instalación do concello, tamén de baixa
   * (quen ten unha reserva nela segue precisando o texto da devolución); 404 se non existe.
   */
  FacilityPaymentNotes paymentNotes(FacilityId id);
}
