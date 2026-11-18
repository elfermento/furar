// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.out;

import gal.furar.booking.domain.CancelledWait;
import gal.furar.booking.domain.Wait;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * As esperas do concello actual e o seu reconto anónimo (ADR-0033, puntos 3, 4, 5 e 8). Sempre
 * dentro dunha transacción (ADR-0006). «Activa» = a súa franxa aínda non empezou: as lecturas nunca
 * devolven unha vencida, aínda que a tarefa tarde en borrala.
 */
public interface BookingWaits {

  /**
   * Garda a espera con ese ordinal (1 a 3), sen abortar a transacción se o ordinal ou a franxa xa
   * están collidos pola conta.
   *
   * @return falso se non entrou
   */
  boolean insert(Wait wait, int ordinal);

  /** A espera activa da conta por ese uso e franxa, se a hai. */
  Optional<Wait> findOfAccount(String subject, UUID useId, Instant start, Instant now);

  /** As esperas activas da conta, por inicio. */
  List<Wait> activeOfAccount(String subject, Instant now);

  /**
   * Borra a espera da conta.
   *
   * @return falso se non existe ou é doutra conta
   */
  boolean delete(UUID id, String subject);

  /**
   * Pon o correo verificado de agora nas esperas da conta que teñan outro.
   *
   * @return cantas cambiou
   */
  int refreshEmail(String subject, String emailKey);

  /**
   * Borra as esperas dese correo e uso que pisan {@code [start, end)}: xa ten reserva nesa hora.
   *
   * @return cantas borrou
   */
  int deleteServed(String emailKey, UUID useId, Instant start, Instant end);

  /** As esperas activas da instalación que pisan {@code [from, to)}, por inicio. */
  List<Wait> activeOfFacility(UUID facilityId, Instant from, Instant to, Instant now);

  /**
   * Borra esas esperas, sen reconto: a súa hora deixou de existir.
   *
   * @return cantas borrou
   */
  int deleteAll(Collection<UUID> ids);

  /**
   * Cancela esas esperas porque a instalación deixou de reservarse por internet, se pechou ou se
   * deu de baixa (ADR-0033, revisión do 2026-11-11): bórraas, sen reconto, e garda das que borrou
   * de verdade o mínimo para avisar despois do commit, baixo {@code cancellationId}.
   *
   * @return cantas cancelou
   */
  int cancel(Collection<UUID> ids, UUID cancellationId, Instant now);

  /** As esperas desa cancelación pendentes de avisar, por correo e inicio. */
  List<CancelledWait> cancelled(UUID cancellationId);

  /**
   * Conservación das esperas canceladas: borra as de antes de {@code cutoff}.
   *
   * @return cantas borrou
   */
  int purgeCancelledBefore(Instant cutoff);

  /**
   * As esperas activas por ese uso e franxa creadas como moito en {@code createdUntil}: a quen
   * avisar dunha liberación.
   */
  List<Wait> ofSlot(UUID useId, Instant start, Instant createdUntil, Instant now);

  /**
   * Vence as esperas do concello cuxa hora xa empezou: bórraas e suma cantas eran ao reconto
   * anónimo, na mesma transacción (cada espera cóntase unha vez, tamén se dúas pasadas se cruzan).
   *
   * @return cantas venceu
   */
  int expire(Instant now);

  /** Como {@link #expire}, só as dunha conta: antes de crearlle outra. */
  int expireOfAccount(String subject, Instant now);

  /**
   * Conservación do reconto: borra as filas de franxas anteriores a {@code cutoff}.
   *
   * @return cantas borrou
   */
  int purgeTallyBefore(Instant cutoff);
}
