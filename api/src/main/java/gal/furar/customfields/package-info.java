// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/**
 * Campos personalizados por concello (ADR-0011): definicións e validación dos valores que gardan os
 * módulos donos. Só depende de {@code shared}; o concello sae de {@code TenantContext}.
 */
@ApplicationModule(
    displayName = "Custom fields",
    allowedDependencies = {"shared"})
package gal.furar.customfields;

import org.springframework.modulith.ApplicationModule;
