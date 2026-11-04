// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityUse;
import gal.furar.facility.domain.schedule.Closure;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Ficha pública dunha instalación visible: o agregado (só se expón o público), as imaxes que se
 * ven, os usos que se poden reservar agora ou máis adiante e o horario dos próximos sete días desde
 * hoxe (tempada, festivos e excepcións xa resoltos; os bloqueos non, que son do persoal), e os
 * peches dos próximos sesenta días (excepcións pechadas, festivos que pechan e bloqueos).
 */
public record PublicFacilityDetail(
    Facility value,
    List<ImageView> images,
    List<FacilityUse> uses,
    List<OpeningDay> week,
    List<Closure> closures) {

  public PublicFacilityDetail {
    Objects.requireNonNull(value, "value");
    images = List.copyOf(images);
    uses = List.copyOf(uses);
    week = List.copyOf(week);
    closures = List.copyOf(closures);
  }

  /** Un día co seus tramos abertos, en orde (baleiro = pechado). */
  public record OpeningDay(LocalDate date, List<OpeningSlot> slots) {

    public OpeningDay {
      Objects.requireNonNull(date, "date");
      slots = List.copyOf(slots);
    }
  }

  /** Un tramo en minutos de reloxo (0–1440) e os instantes reais, co cambio de hora aplicado. */
  public record OpeningSlot(int startMinute, int endMinute, Instant start, Instant end) {}
}
