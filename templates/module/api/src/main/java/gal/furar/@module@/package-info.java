// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * @Name@ (docs/glosario.md). Xerado con scripts/novo-modulo.sh: docs/como-engadir-un-modulo.md di
 * como enchelo. API pública: @Name@Queries, @Name@Summary e @Name@Created.
 */
@ApplicationModule(displayName = "@Name@", allowedDependencies = {"shared"}) // @nocf
@ApplicationModule(displayName = "@Name@", allowedDependencies = {"shared", "customfields"}) // @cf
package gal.furar.@module@;

import org.springframework.modulith.ApplicationModule;
