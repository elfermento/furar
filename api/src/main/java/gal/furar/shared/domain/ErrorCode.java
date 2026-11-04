// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

/**
 * Código de erro estable e traducible ({@code modulo.motivo}). Cada módulo ten un {@code enum} que
 * o implementa; o texto vive en {@code messages_gl/es.properties} (ADR-0008).
 */
public interface ErrorCode {

  /** Código estable, en inglés, {@code modulo.motivo-en-kebab}. */
  String code();

  /** Status HTTP que lle corresponde. */
  int status();
}
