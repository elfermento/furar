// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.CustomSport;
import gal.furar.facility.domain.Sport;
import gal.furar.facility.domain.SportRef;
import java.util.Collection;
import java.util.List;

/**
 * Deportes: o catálogo de Furar (plataforma, só lectura) e os do concello actual. Os deportes de
 * cada instalación van co agregado ({@link FacilityRepository}).
 */
public interface SportRepository {

  /** Catálogo e deportes do concello, sen orde. */
  List<Sport> findAll();

  /** Os pedidos que existen, na orde pedida; os que non existen non saen. */
  List<Sport> findAll(Collection<SportRef> refs);

  /** Deportes do concello, por data de alta (portabilidade). */
  List<CustomSport> findAllCustom();

  /** Nome repetido sen maiúsculas → {@code facility.sport-name-taken}. */
  CustomSport insert(CustomSport sport);
}
