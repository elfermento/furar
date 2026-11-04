// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.security;

import java.util.Optional;

/** Porto para saber quen fai a petición. Baleiro = anónimo. */
public interface CurrentActor {

  Optional<Actor> current();
}
