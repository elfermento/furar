// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un aviso «Quedou libre» pendente (ADR-0033, puntos 7 e 11; A19: só tipos da raíz e de Java): a
 * espera, a quen escribir e en que idioma, e os nomes de agora da instalación e do uso. Sen nome da
 * persoa: o correo non saúda.
 *
 * @param email o correo co que a conta espera
 * @param language {@code gl} ou {@code es}
 * @param facilityAddress enderezo da instalación nunha liña
 */
public record WaitNotice(
    UUID waitId,
    String email,
    String language,
    UUID facilityId,
    String facilityName,
    @Nullable String facilityAddress,
    UUID useId,
    String useName,
    Instant start,
    Instant end) {

  public WaitNotice {
    Objects.requireNonNull(waitId, "waitId");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(useName, "useName");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
  }
}
