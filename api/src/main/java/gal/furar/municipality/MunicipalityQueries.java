// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality;

import java.util.Optional;
import java.util.UUID;

/**
 * API pública síncrona do módulo para outros módulos (ADR-0005, A19): lecturas con records propios
 * da raíz. Sen permisos propios (xa os comprobou o caso de uso que chama); nunca se expón por HTTP.
 */
public interface MunicipalityQueries {

  /** Os datos do concello para os correos das reservas; baleiro se non existe. */
  Optional<MunicipalityMailContext> mailContext(UUID municipalityId);

  /** O nome do concello (o menú da persoa con pertenzas en varios); baleiro se non existe. */
  Optional<String> name(UUID municipalityId);
}
