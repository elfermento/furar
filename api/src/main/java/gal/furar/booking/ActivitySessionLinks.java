// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * SPI cara a {@code event} (ADR-0041): que sesión dunha actividade ocupa cada reserva, para ir do
 * calendario á actividade. {@code booking} só garda a actividade ({@code activity_id}); a sesión é
 * de {@code event}, que depende de {@code booking}: a dependencia invértese cun porto (ADR-0005).
 * Un só bean; sen ningún (probas dun módulo illado), {@link NoActivitySessionLinks}.
 */
public interface ActivitySessionLinks {

  /** A sesión de cada unha destas reservas no concello actual; as que non o son, non saen. */
  Map<UUID, UUID> sessionsByBooking(Collection<UUID> bookingIds);
}
