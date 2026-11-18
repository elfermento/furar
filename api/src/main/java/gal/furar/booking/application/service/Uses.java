// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.domain.BookableUse;
import gal.furar.booking.domain.BookingRules;
import gal.furar.booking.domain.CapPeriod;
import gal.furar.booking.domain.Price;
import gal.furar.booking.domain.PriceKind;
import gal.furar.booking.domain.RentalMode;
import gal.furar.facility.UseSummary;
import gal.furar.facility.UseTerms;

/** Un uso de {@code facility} ({@link UseSummary}) no dominio das reservas. */
final class Uses {

  private Uses() {}

  static BookableUse bookable(UseSummary use) {
    UseTerms.Rules rules = use.terms().rules();
    return new BookableUse(
        use.id(),
        RentalMode.valueOf(use.rentalMode()),
        use.slotMinutes(),
        "SEATS".equals(use.rentalMode()) ? use.capacity() : null,
        use.parts().stream().map(UseSummary.Part::id).toList(),
        use.periods().stream().map(p -> new BookableUse.Period(p.from(), p.to())).toList(),
        use.weekdays(),
        use.archived(),
        use.blocksOthers(),
        new BookingRules(
            rules.minAdvanceHours(),
            rules.maxAdvanceDays(),
            rules.maxSlots(),
            rules.capCount(),
            rules.capPeriod() == null ? null : CapPeriod.valueOf(rules.capPeriod()),
            rules.cancelHours()));
  }

  /** O prezo do uso por franxa (nas prazas, por persoa e franxa). */
  static Price unitPrice(UseSummary use) {
    UseTerms.Price price = use.terms().price();
    return new Price(PriceKind.valueOf(price.kind()), price.amountCents());
  }
}
