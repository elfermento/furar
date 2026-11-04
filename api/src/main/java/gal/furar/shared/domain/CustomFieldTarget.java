// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.domain;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Entidade que admite campos personalizados (ADR-0011), como código (ADR-0005 ARQ3-2). Non é un
 * {@code enum} cos módulos futuros: cada módulo rexistra o seu cun {@link
 * CustomFieldTargetProvider} e {@code customfields} rexeita os non rexistrados. Os códigos van en
 * maiúsculas, como os gardados desde a v0 e os do contrato ({@code MUNICIPALITY}, {@code FACILITY},
 * {@code RESIDENT_PROFILE}).
 */
public record CustomFieldTarget(String code) {

  public static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");

  /** O concello, raíz da plataforma. */
  public static final CustomFieldTarget MUNICIPALITY = new CustomFieldTarget("MUNICIPALITY");

  /** A instalación (módulo {@code facility}, tarxeta Datos). */
  public static final CustomFieldTarget FACILITY = new CustomFieldTarget("FACILITY");

  /** A reserva (módulo {@code booking}): o que se pide antes de «Confirmar reserva». */
  public static final CustomFieldTarget BOOKING = new CustomFieldTarget("BOOKING");

  public CustomFieldTarget {
    if (code == null || !FORMAT.matcher(code).matches()) {
      throw DomainException.validation(
          List.of(FieldViolation.of("target", ValidationError.PATTERN)));
    }
  }

  @Override
  public String toString() {
    return code;
  }
}
