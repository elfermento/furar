// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.portability;

import gal.furar.shared.domain.ErrorCode;

/** Erros da importación dun ficheiro {@code furar-export} (ADR-0018). */
public enum PortabilityError implements ErrorCode {
  /** Formato, versión, sección ou fila que non se pode ler ({@code reason}, {@code section}). */
  INVALID_FILE("portability.invalid-file", 422),
  /** O slug ou algún id do ficheiro xa existe nesta instalación. */
  CONFLICT("portability.conflict", 409);

  private final String code;
  private final int status;

  PortabilityError(String code, int status) {
    this.code = code;
    this.status = status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int status() {
    return status;
  }
}
