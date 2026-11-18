// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha reserva tal como a conta o correo (ADR-0025, puntos 13 a 15; A19: só tipos da raíz e de
 * Java): os nomes de agora da instalación, o uso e a parte, o prezo conxelado, o prazo para
 * cancelar, quen a fixo e en que idioma, o QR en PNG e o {@code .ics}.
 *
 * @param version versión da reserva (o {@code SEQUENCE} do {@code .ics})
 * @param facilityAddress enderezo da instalación nunha liña
 * @param partName nome da parte, só nos usos por partes
 * @param amountCents total en céntimos, só se é de pago
 * @param holderLanguage idioma dos correos, {@code gl} ou {@code es}
 * @param holderHasAccount se a reserva ten conta (a de quen reservou ou, no mostrador, a do seu
 *     correo); sen ela non ten «as miñas reservas» onde vela
 * @param seats prazas da reserva (1 fóra dos usos por prazas)
 * @param participantNames «Para quen», nos usos por prazas: os nomes, na súa orde (ADR-0031)
 * @param bookedByStaff se a fixo o persoal a nome do correo: o correo leva «Se esta reserva non é
 *     túa…»
 * @param qrPng o QR do código en PNG, coa súa zona tranquila
 * @param calendar o {@code .ics} da reserva ({@code METHOD:CANCEL} se está cancelada)
 * @param organization se é dunha entidade (ADR-0039): o correo vai á entidade, sen saúdo nin «Se
 *     esta reserva non é túa…»
 * @param session se é unha sesión dunha tempada: sen prezo nin prazo para cancelar
 */
public record BookingNotice(
    UUID id,
    String code,
    long version,
    UUID facilityId,
    String facilityName,
    @Nullable String facilityAddress,
    String useName,
    @Nullable String partName,
    Instant start,
    Instant end,
    BookingSummary.Status status,
    Payment payment,
    PriceKind priceKind,
    @Nullable Integer amountCents,
    Instant cancelUntil,
    String holderName,
    String holderEmail,
    String holderLanguage,
    boolean holderHasAccount,
    byte[] qrPng,
    String calendar,
    int seats,
    List<String> participantNames,
    boolean bookedByStaff,
    boolean organization,
    boolean session) {

  /** Dunha persoa (non dunha entidade). */
  public BookingNotice(
      UUID id,
      String code,
      long version,
      UUID facilityId,
      String facilityName,
      @Nullable String facilityAddress,
      String useName,
      @Nullable String partName,
      Instant start,
      Instant end,
      BookingSummary.Status status,
      Payment payment,
      PriceKind priceKind,
      @Nullable Integer amountCents,
      Instant cancelUntil,
      String holderName,
      String holderEmail,
      String holderLanguage,
      boolean holderHasAccount,
      byte[] qrPng,
      String calendar,
      int seats,
      List<String> participantNames,
      boolean bookedByStaff) {
    this(
        id,
        code,
        version,
        facilityId,
        facilityName,
        facilityAddress,
        useName,
        partName,
        start,
        end,
        status,
        payment,
        priceKind,
        amountCents,
        cancelUntil,
        holderName,
        holderEmail,
        holderLanguage,
        holderHasAccount,
        qrPng,
        calendar,
        seats,
        participantNames,
        bookedByStaff,
        false,
        false);
  }

  public BookingNotice {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(payment, "payment");
    Objects.requireNonNull(priceKind, "priceKind");
    Objects.requireNonNull(holderEmail, "holderEmail");
    Objects.requireNonNull(qrPng, "qrPng");
    Objects.requireNonNull(calendar, "calendar");
    qrPng = qrPng.clone();
    participantNames = List.copyOf(participantNames);
  }

  @Override
  public byte[] qrPng() {
    return qrPng.clone();
  }

  /** Tipo de prezo, como no dominio. */
  public enum PriceKind {
    NONE,
    FREE,
    PAID
  }

  /** Estado do cobro no mostrador, como no dominio. */
  public enum Payment {
    NONE,
    PENDING,
    PAID,
    TO_REFUND,
    REFUNDED
  }
}
