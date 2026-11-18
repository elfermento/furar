// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.port.in;

import gal.furar.@module@.domain.@Name@;
import gal.furar.shared.domain.CustomFieldValues; // @cf

/**
 * O que devolven os casos de uso: o agregado e, con campos personalizados, só os valores que
 * pode ver quen pregunta (CustomFieldProjector). Nunca se garda unha View.
 */
public record @Name@View(
    CustomFieldValues visibleCustomFields, // @cf
    @Name@ value) {}
