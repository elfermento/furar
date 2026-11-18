// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

import gal.furar.shared.domain.Language;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * «¿Es un concello?» (ADR-0042): un concello pide darse de alta e a solicitude vai por correo a
 * quen opera o servizo ({@code FURAR_JOIN_REQUEST_EMAIL}), no idioma no que se encheu o formulario
 * e con {@code Reply-To} ao correo de quen a pide. Se o correo non sae, gárdase e reinténtase
 * (revisión do 2026-11-17). Pública e sen sesión.
 */
public interface SubmitJoinRequestUseCase {

  /**
   * @throws gal.furar.shared.domain.DomainException {@code common.not-found} se o despregue non ten
   *     a función; {@code validation.failed} cos campos que non valen; {@code
   *     notification.join-request-rate-limited} se ese enderezo xa enviou demasiadas hoxe; {@code
   *     notification.join-request-not-sent} se o correo non saíu e tampouco se puido gardar
   */
  void submit(Submission submission);

  /**
   * O que chega do formulario, tal cal.
   *
   * @param trap o campo trampa, escondido ás persoas: se trae algo, enviouno un programa e a
   *     solicitude descártase sen dicilo
   * @param language o idioma da web de quen a envía: o do correo
   * @param client enderezo IP de quen a envía (para o límite; non vai no correo nin se garda)
   */
  record Submission(
      @Nullable String municipality,
      @Nullable String name,
      @Nullable String role,
      @Nullable String email,
      @Nullable String phone,
      @Nullable String message,
      @Nullable String trap,
      Language language,
      String client) {

    public Submission {
      Objects.requireNonNull(language, "language");
      Objects.requireNonNull(client, "client");
    }

    @Override
    public String toString() {
      return "Submission[municipality=" + municipality + "]";
    }
  }
}
