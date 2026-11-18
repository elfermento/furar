// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

/** Alta no concello actual (o do URL): queda activa e en borrador. */
public interface CreateFacilityUseCase {

  FacilityView create(CreateFacilityCommand command);
}
