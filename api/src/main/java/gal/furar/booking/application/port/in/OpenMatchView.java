// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.OpenMatch;
import gal.furar.facility.FacilityCover;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha partida aberta tal como se ve en público (ADR-0040, punto 9): cos nomes de agora da
 * instalación, o uso e a parte, a franxa da reserva, o prazo do concello e as contas. Sen ningún
 * nome máis ca o de quen organiza.
 *
 * @param facilityAddress enderezo da instalación nunha liña
 * @param cover portada da instalación; nula se xa non existe ou nun correo
 * @param partName nome da parte, só nos usos por partes
 * @param deadline o prazo do concello: o inicio menos as horas de «Cancelar se non hai mínimo»
 * @param confirmed as persoas que se uniron e están dentro
 * @param pending as que o pediron e esperan (só as ve quen organiza)
 */
public record OpenMatchView(
    OpenMatch match,
    UUID facilityId,
    String facilityName,
    @Nullable String facilityAddress,
    @Nullable FacilityCover cover,
    UUID useId,
    String useName,
    @Nullable String partName,
    Instant start,
    Instant end,
    Instant deadline,
    int confirmed,
    int pending) {

  public OpenMatchView {
    Objects.requireNonNull(match, "match");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
  }

  /** As persoas que van: «Xa somos» e as que se uniron. */
  public int total() {
    return match.total(confirmed);
  }
}
