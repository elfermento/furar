// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Concello: slug, nome, idiomas, tema, campos personalizados e, desde o dominio 2, o contacto do
 * servizo de deportes e as notas de pago e devolución. É a rebanda vertical de referencia para os
 * módulos novos (docs/como-engadir-un-modulo.md). API pública: {@link
 * gal.furar.municipality.MunicipalityQueries} e {@link
 * gal.furar.municipality.MunicipalityMailContext}.
 */
@ApplicationModule(
    displayName = "Municipality",
    allowedDependencies = {"shared", "customfields"})
package gal.furar.municipality;

import org.springframework.modulith.ApplicationModule;
