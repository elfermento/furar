// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ficheiro {@code furar-export} v3 (ADR-0018). {@code sections} leva unha clave por módulo; o
 * esquema de cada sección está no OpenAPI ({@code MunicipalityExportSections}).
 */
public record MunicipalityExportResponse(
    @Schema(allowableValues = {"furar-export"}) String format,
    @Schema(minimum = "3", maximum = "3") int version,
    Instant exportedAt,
    MunicipalityDataResponse municipality,
    Map<String, List<Object>> sections) {

  public static final String FORMAT = "furar-export";

  /**
   * Versión que se escribe. A 3 (2026-11-04) leva os campos personalizados desactivados ({@code
   * INACTIVE}, {@code inactive}) no canto de arquivados.
   */
  public static final int VERSION = 3;

  /** Versión máis antiga que o importador aínda le. */
  public static final int OLDEST_READABLE_VERSION = 1;
}
