// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

/**
 * Quen le ou escribe un recurso, para decidir que campos personalizados ve (ADR-0011): calquera
 * persoa, o titular do recurso (e o persoal) ou só o persoal.
 */
public enum Audience {
  PUBLIC,
  OWNER,
  STAFF
}
