// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.out.recommendation;

import gal.furar.booking.application.port.out.SlotRecommender;
import gal.furar.booking.domain.SimilarSlot;
import gal.furar.booking.domain.SimilarSlot.Reason;
import gal.furar.booking.domain.SlotCandidate;
import gal.furar.booking.domain.SlotTarget;
import java.text.Collator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * «Ocos parecidos» por regras fixas, sen aprendizaxe nin servizos externos (ADR-0033, punto 9).
 * Puro: sen base nin reloxo. Unha fila por regra, nesta orde, e nunca o mesmo oco dúas veces:
 *
 * <ol>
 *   <li>esta instalación e uso, o mesmo día, a franxa anterior ou a seguinte á pedida, dure o que
 *       dure a franxa do uso (decidido o 2026-11-11); se as dúas están libres, a anterior;
 *   <li>outra instalación, libre á mesma hora; a primeira por nome;
 *   <li>o mesmo día, a primeira hora libre despois da pedida que aínda non saíse: nesta instalación
 *       e uso; se non hai, nas outras, pola hora máis temperá e, con empate, por nome;
 *   <li>esta instalación e uso, sete días despois, á mesma hora de reloxo.
 * </ol>
 */
@Component
class RuleBasedSlotRecommender implements SlotRecommender {

  private static final int WEEK_DAYS = 7;

  @Override
  public List<SimilarSlot> recommend(SlotTarget target, List<SlotCandidate> candidates, int max) {
    Collator names = Collator.getInstance(Locale.of("gl"));
    Comparator<SlotCandidate> byName =
        Comparator.comparing(SlotCandidate::facilityName, names)
            .thenComparing(SlotCandidate::facilityId);
    Predicate<SlotCandidate> here =
        c -> c.facilityId().equals(target.facilityId()) && c.useId().equals(target.useId());
    Predicate<SlotCandidate> sameDay = c -> c.date().equals(target.date());
    List<SimilarSlot> result = new ArrayList<>();

    // 1. A franxa anterior ou a seguinte nesta instalación; coas dúas libres, a anterior.
    Duration step = Duration.ofMinutes(target.slotMinutes());
    add(
        result,
        Reason.SAME_FACILITY_NEAR,
        candidates.stream()
            .filter(here.and(sameDay))
            .filter(
                c ->
                    c.start().equals(target.start().minus(step))
                        || c.start().equals(target.start().plus(step)))
            .min(Comparator.comparing(SlotCandidate::start)));
    // 2. Outra instalación á mesma hora.
    add(
        result,
        Reason.OTHER_FACILITY_SAME_TIME,
        candidates.stream()
            .filter(c -> !c.facilityId().equals(target.facilityId()))
            .filter(c -> c.start().equals(target.start()))
            .min(byName));
    // 3. Máis tarde ese día: primeiro aquí; se non hai, noutra.
    Predicate<SlotCandidate> later =
        sameDay.and(c -> c.start().isAfter(target.start())).and(c -> !proposed(result, c));
    Optional<SlotCandidate> laterHere =
        candidates.stream().filter(here.and(later)).min(Comparator.comparing(SlotCandidate::start));
    add(
        result,
        Reason.LATER_SAME_DAY,
        laterHere.isPresent()
            ? laterHere
            : candidates.stream()
                .filter(c -> !c.facilityId().equals(target.facilityId()))
                .filter(later)
                .min(Comparator.comparing(SlotCandidate::start).thenComparing(byName)));
    // 4. A semana seguinte, á mesma hora de reloxo (vale co cambio de hora).
    add(
        result,
        Reason.NEXT_WEEK,
        candidates.stream()
            .filter(here)
            .filter(c -> c.date().equals(target.date().plusDays(WEEK_DAYS)))
            .filter(c -> c.startMinute() == target.startMinute())
            .min(Comparator.comparing(SlotCandidate::start)));
    return result.stream().limit(Math.max(0, max)).toList();
  }

  private static void add(List<SimilarSlot> result, Reason reason, Optional<SlotCandidate> slot) {
    slot.ifPresent(s -> result.add(new SimilarSlot(reason, s)));
  }

  private static boolean proposed(List<SimilarSlot> result, SlotCandidate candidate) {
    return result.stream()
        .map(SimilarSlot::slot)
        .anyMatch(
            s ->
                s.facilityId().equals(candidate.facilityId())
                    && s.useId().equals(candidate.useId())
                    && s.start().equals(candidate.start()));
  }
}
