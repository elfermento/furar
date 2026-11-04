// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Límites de envío das invitacións (plan do dominio 4, §1.2.7 e P12), no dominio e sen Caddy:
 *
 * <ul>
 *   <li>como moito {@value #PER_MUNICIPALITY} envíos (altas e reenvíos) por concello en {@link
 *       #MUNICIPALITY_WINDOW};
 *   <li>{@link #MEMBER_GAP} entre dous envíos á mesma pertenza;
 *   <li>{@value #PER_MEMBER} envíos por pertenza.
 * </ul>
 *
 * Cóntanse as filas de {@code staff_invitation}: o caso de uso le as horas de creación e chama a
 * {@link #check} antes de gardar outra.
 */
public final class SendLimits {

  public static final int PER_MUNICIPALITY = 30;
  public static final Duration MUNICIPALITY_WINDOW = Duration.ofHours(24);
  public static final Duration MEMBER_GAP = Duration.ofMinutes(2);
  public static final int PER_MEMBER = 5;

  private SendLimits() {}

  /**
   * Comproba se se pode enviar outra invitación agora.
   *
   * @param municipalitySends horas dos envíos do concello desde {@code now - 24 h} (abondan os
   *     {@value #PER_MUNICIPALITY} máis recentes; a orde non importa)
   * @param memberSends horas de todos os envíos da pertenza (para unha nova, ningún)
   * @throws InvitationRateLimitedException co tempo que falta, ou sen tempo se a pertenza xa gastou
   *     os seus {@value #PER_MEMBER} envíos
   */
  public static void check(
      List<Instant> municipalitySends, List<Instant> memberSends, Instant now) {
    if (isExhausted(memberSends.size())) {
      throw new InvitationRateLimitedException(null);
    }
    Duration wait = Duration.ZERO;
    List<Instant> recent =
        municipalitySends.stream()
            .filter(t -> t.isAfter(now.minus(MUNICIPALITY_WINDOW)))
            .sorted(Comparator.reverseOrder())
            .toList();
    if (recent.size() >= PER_MUNICIPALITY) {
      // Hai sitio cando o envío número 30 (contando desde o máis novo) saia da xanela.
      Instant freesAt = recent.get(PER_MUNICIPALITY - 1).plus(MUNICIPALITY_WINDOW);
      wait = max(wait, Duration.between(now, freesAt));
    }
    Instant last = memberSends.stream().max(Comparator.naturalOrder()).orElse(null);
    if (last != null && now.isBefore(nextMemberSend(last))) {
      wait = max(wait, Duration.between(now, nextMemberSend(last)));
    }
    if (wait.isPositive()) {
      throw new InvitationRateLimitedException(wait);
    }
  }

  /**
   * O que a web precisa para desactivar «Reenviar» con antelación: os mesmos límites da pertenza ca
   * {@link #check}, sen o do concello.
   *
   * @param memberSends cantos envíos leva a pertenza
   * @param lastSend a hora do último envío, se o hai
   */
  public static ResendAvailability resendAvailability(
      int memberSends, @Nullable Instant lastSend, Instant now) {
    if (isExhausted(memberSends)) {
      return new ResendAvailability(null, true);
    }
    if (lastSend != null && now.isBefore(nextMemberSend(lastSend))) {
      return new ResendAvailability(nextMemberSend(lastSend), false);
    }
    return new ResendAvailability(null, false);
  }

  private static boolean isExhausted(int memberSends) {
    return memberSends >= PER_MEMBER;
  }

  private static Instant nextMemberSend(Instant lastSend) {
    return lastSend.plus(MEMBER_GAP);
  }

  private static Duration max(Duration a, Duration b) {
    return a.compareTo(b) >= 0 ? a : b;
  }
}
