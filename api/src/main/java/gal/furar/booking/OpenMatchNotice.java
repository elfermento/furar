// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un correo dunha partida aberta pendente (ADR-0040, punto 12; A19: só tipos da raíz e de Java): a
 * partida cos nomes de agora e a quen escribir, co estado de agora.
 *
 * @param title nulo = sen título (o correo pon «uso · día hora»)
 * @param facilityAddress enderezo da instalación nunha liña
 * @param partName a parte, só nos usos por partes
 * @param deadline o prazo do concello
 * @param organizerName o nome de quen organiza
 * @param unfilled cancelouse por non chegar ao mínimo (cancelou tamén a reserva)
 * @param closed xa non busca xente (o cambio do persoal pechouna)
 * @param requesterName quen pediu entrar, só en {@link Kind#REQUESTED}
 */
public record OpenMatchNotice(
    Kind kind,
    UUID matchId,
    @Nullable String title,
    UUID facilityId,
    String facilityName,
    @Nullable String facilityAddress,
    String useName,
    @Nullable String partName,
    Instant start,
    Instant end,
    int minimum,
    Instant deadline,
    String organizerName,
    boolean unfilled,
    boolean closed,
    @Nullable String requesterName,
    List<Recipient> recipients) {

  public OpenMatchNotice {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(matchId, "matchId");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(deadline, "deadline");
    Objects.requireNonNull(organizerName, "organizerName");
    recipients = List.copyOf(recipients);
  }

  /** Que correo. */
  public enum Kind {
    /** A partida cancelouse: ás persoas dentro e, se non chegou ao mínimo, a quen organiza. */
    CANCELLED,
    /** A reserva cambiou de día ou de hora: ás persoas dentro. */
    CHANGED,
    /** Alguén pediu entrar: a quen organiza. */
    REQUESTED,
    /** Quen organiza aceptou a solicitude: a quen a pediu. */
    ACCEPTED,
    /** Quen organiza rexeitou a solicitude: a quen a pediu. */
    REJECTED,
    /** A xestión ocultou a partida: a quen organiza. */
    HIDDEN
  }

  /**
   * A quen escribir: a quen organiza (pola súa reserva) ou a unha persoa da partida. {@link
   * #toString()} non mostra o correo nin o nome.
   *
   * @param bookingId a reserva de quen organiza (o destino do correo), ou nulo
   * @param playerId a fila da persoa (o destino do correo), ou nulo
   * @param language {@code gl} ou {@code es}
   */
  public record Recipient(
      @Nullable UUID bookingId,
      @Nullable UUID playerId,
      String email,
      String language,
      String name) {

    public Recipient {
      if ((bookingId == null) == (playerId == null)) {
        throw new IllegalArgumentException("A recipient is the organizer or a player");
      }
      Objects.requireNonNull(email, "email");
      Objects.requireNonNull(language, "language");
      Objects.requireNonNull(name, "name");
    }

    @Override
    public String toString() {
      return "Recipient[bookingId=" + bookingId + ", playerId=" + playerId + "]";
    }
  }

  @Override
  public String toString() {
    return "OpenMatchNotice[kind="
        + kind
        + ", matchId="
        + matchId
        + ", recipients="
        + recipients.size()
        + "]";
  }
}
