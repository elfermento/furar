// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.application.port.out.SportRepository;
import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.Sport;
import gal.furar.facility.domain.SportRef;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Os deportes co seu nome para pintar moitas instalacións á vez (listados): unha soa lectura do
 * catálogo e dos do concello, nunca unha por instalación.
 */
final class SportNames {

  private SportNames() {}

  static Map<SportRef, Sport> of(SportRepository sports) {
    return sports.findAll().stream()
        .collect(Collectors.toMap(Sport::ref, Function.identity(), (a, b) -> a));
  }

  /** Os da instalación, na orde da tarxeta Datos; os que xa non existen non saen. */
  static List<Sport> resolve(Map<SportRef, Sport> named, Facility facility) {
    return facility.details().sports().stream().map(named::get).filter(Objects::nonNull).toList();
  }
}
