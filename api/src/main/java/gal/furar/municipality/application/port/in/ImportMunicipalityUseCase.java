// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.application.port.in;

/**
 * A plataforma crea un concello desde un ficheiro {@code furar-export} doutra instalación, nunha
 * soa transacción (ADR-0018).
 */
public interface ImportMunicipalityUseCase {

  MunicipalityView importMunicipality(ImportMunicipalityCommand command);
}
