// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

/**
 * Gardar unha sección da propia instalación (ADR-0022, punto 7): cada método substitúe a sección
 * enteira se a versión coincide coa gardada; se non, {@code common.stale-version} (412). Devolve a
 * ficha enteira coa versión nova.
 */
public interface UpdateFacilitySectionUseCase {

  /** Tarxeta Datos: deportes, tipo, RIDEGA, contacto, reserva por internet/ocos e cuberta. */
  FacilityView updateDetails(UpdateFacilityDetailsCommand command);

  FacilityView updateDescription(UpdateFacilityDescriptionCommand command);

  /** Normas da instalación en texto libre (maqueta 06). */
  FacilityView updateHouseRules(UpdateHouseRulesCommand command);

  /** Pago e devolución desta instalación (D2-11, «Distinto por instalación»). */
  FacilityView updatePaymentNotes(UpdateFacilityPaymentNotesCommand command);

  FacilityView updateAddress(UpdateFacilityAddressCommand command);

  FacilityView updateAmenities(UpdateFacilityAmenitiesCommand command);
}
