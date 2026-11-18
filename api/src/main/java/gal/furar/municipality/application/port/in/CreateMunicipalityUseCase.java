// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/** A plataforma dá de alta un concello. */
public interface CreateMunicipalityUseCase {

  MunicipalityView create(CreateMunicipalityCommand command);
}
