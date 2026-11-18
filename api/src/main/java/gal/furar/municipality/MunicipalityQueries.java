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

  /**
   * A penalización das faltas do concello (ADR-0032, punto 7); baleiro se o concello non existe.
   */
  Optional<MunicipalityNoShowPolicy> noShowPolicy(UUID municipalityId);

  /** «Buscar xente para xogar» do concello (ADR-0040); baleiro se o concello non existe. */
  Optional<MunicipalityOpenMatchPolicy> openMatchPolicy(UUID municipalityId);

  /** A publicación dos datos abertos do concello (ADR-0034); baleiro se o concello non existe. */
  Optional<MunicipalityOpenData> openData(UUID municipalityId);

  /** O nome do concello (o menú da persoa con pertenzas en varios); baleiro se non existe. */
  Optional<String> name(UUID municipalityId);
}
