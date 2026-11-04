// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.Sport;
import gal.furar.shared.domain.Language;
import java.util.List;

/** Deportes que se poden pór nunha instalación: o catálogo de Furar e os do concello. */
public interface SportsUseCase {

  /** Catálogo e deportes do concello, en orde alfabética no idioma de quen mira. */
  List<Sport> list(Language language);

  /** «+ crear»: nome repetido (en gl, sen maiúsculas) → {@code facility.sport-name-taken}. */
  Sport create(CreateSportCommand command);
}
