// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * API pública para que unha actividade do concello con horario ocupe unha instalación (ADR-0041): o
 * mesmo planificador e as mesmas regras ca as tempadas das entidades (ADR-0039, punto 8). Cada
 * sesión é unha reserva sen persoa nin entidade, co nome da actividade e a actividade en {@code
 * activity_id}: a dispoñibilidade, as esperas, o calendario e os indicadores xa a ven, e a base
 * impide que pise nada. Non comproba permisos (xa os comprobou o caso de uso que chama); escribir e
 * soltar piden a conta de quen actúa e van dentro da transacción de quen chama, que toma o bloqueo
 * da instalación aquí, despois dos seus.
 */
public interface ActivityBookings {

  /**
   * As sesións e os días que non se crean, sen escribir nin bloquear.
   *
   * @throws gal.furar.shared.domain.DomainException {@code booking.facility-not-bookable}, {@code
   *     booking.use-not-available}, {@code booking.activity-seats-use} ou {@code
   *     booking.slot-invalid}
   */
  ActivityPlan plan(ActivitySlot slot);

  /**
   * Baixo o bloqueo da instalación volve calcular e, se os días con choque son exactamente {@code
   * skip}, escribe unha reserva por sesión libre a nome da actividade. Se non, non escribe nada e
   * devolve o plan novo con {@code changed}.
   */
  ActivityOccupation occupy(UUID activityId, String name, ActivitySlot slot, Set<LocalDate> skip);

  /**
   * Cancela polo concello, baixo o bloqueo da instalación, as reservas desa actividade que estean
   * confirmadas, sen marca e sen empezar; solta a súa ocupación e avisa ás esperas.
   *
   * @return as reservas que cancelou
   */
  Set<UUID> release(UUID activityId, UUID facilityId, Collection<UUID> bookingIds);

  /** O nome novo da actividade nas súas reservas. */
  void rename(UUID activityId, String name);
}
