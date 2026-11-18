// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.domain;

/**
 * Activo ou desactivado (ADR-0011, revisión do 2026-11-04). Desactivado: non sae nos formularios
 * nin admite valores novos, pero os gardados consérvanse e vense; vólvese activar cando se queira.
 * Un campo sen valores pódese ademais eliminar.
 */
public enum DefinitionStatus {
  ACTIVE,
  INACTIVE
}
