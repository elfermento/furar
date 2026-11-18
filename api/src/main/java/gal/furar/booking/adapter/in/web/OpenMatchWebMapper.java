// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web;

import gal.furar.booking.adapter.in.web.dto.BookingOpenMatchResponse;
import gal.furar.booking.adapter.in.web.dto.MyOpenMatchResponse;
import gal.furar.booking.adapter.in.web.dto.OpenMatchCardResponse;
import gal.furar.booking.adapter.in.web.dto.OpenMatchJoinRequestResponse;
import gal.furar.booking.adapter.in.web.dto.OpenMatchLevelValue;
import gal.furar.booking.adapter.in.web.dto.OpenMatchMeResponse;
import gal.furar.booking.adapter.in.web.dto.OpenMatchPersonResponse;
import gal.furar.booking.adapter.in.web.dto.OpenMatchRequest;
import gal.furar.booking.adapter.in.web.dto.OpenMatchResponse;
import gal.furar.booking.adapter.in.web.dto.OpenMatchRoleValue;
import gal.furar.booking.adapter.in.web.dto.OpenMatchStatusValue;
import gal.furar.booking.application.port.in.MyOpenMatch;
import gal.furar.booking.application.port.in.OpenMatchSummary;
import gal.furar.booking.application.port.in.OpenMatchUseCase.OpenMatchCommand;
import gal.furar.booking.application.port.in.OpenMatchView;
import gal.furar.booking.domain.MatchLevel;
import gal.furar.booking.domain.OpenMatch;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

/** Partidas abertas (ADR-0040) ↔ DTO. */
final class OpenMatchWebMapper {

  /** Os buscadores non indexan as partidas (ADR-0040, punto 9). */
  static final String ROBOTS = "X-Robots-Tag";

  static final String NOINDEX = "noindex";

  private OpenMatchWebMapper() {}

  static HttpHeaders noindex() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(ROBOTS, NOINDEX);
    return headers;
  }

  static OpenMatchCommand command(OpenMatchRequest r) {
    return new OpenMatchCommand(
        r.knownCount(),
        r.minimum(),
        r.maximum(),
        r.level() == null ? null : MatchLevel.valueOf(r.level().name()),
        r.title(),
        r.description(),
        r.approval());
  }

  static OpenMatchCardResponse card(OpenMatchView v) {
    OpenMatch m = v.match();
    return new OpenMatchCardResponse(
        m.id(),
        v.facilityId(),
        v.facilityName(),
        v.facilityAddress(),
        BookingWebMapper.cover(v.cover()),
        v.useName(),
        v.partName(),
        v.start(),
        v.end(),
        level(m),
        m.title(),
        m.organizerName(),
        v.total(),
        m.minimum(),
        m.maximum(),
        m.approval());
  }

  static OpenMatchResponse page(OpenMatchView v) {
    OpenMatch m = v.match();
    return new OpenMatchResponse(
        m.id(),
        v.facilityId(),
        v.facilityName(),
        v.facilityAddress(),
        BookingWebMapper.cover(v.cover()),
        v.useName(),
        v.partName(),
        v.start(),
        v.end(),
        level(m),
        m.title(),
        m.organizerName(),
        v.total(),
        m.minimum(),
        m.maximum(),
        m.approval(),
        m.description(),
        v.deadline(),
        OpenMatchStatusValue.valueOf(m.status().name()));
  }

  static MyOpenMatchResponse mine(MyOpenMatch my) {
    OpenMatchView v = my.view();
    OpenMatch m = v.match();
    boolean organizer = my.role() == MyOpenMatch.Role.ORGANIZER;
    return new MyOpenMatchResponse(
        m.id(),
        v.facilityId(),
        v.facilityName(),
        v.facilityAddress(),
        BookingWebMapper.cover(v.cover()),
        v.useName(),
        v.partName(),
        v.start(),
        v.end(),
        level(m),
        m.title(),
        m.organizerName(),
        v.total(),
        m.minimum(),
        m.maximum(),
        m.approval(),
        m.description(),
        v.deadline(),
        OpenMatchStatusValue.valueOf(m.status().name()),
        new OpenMatchMeResponse(OpenMatchRoleValue.valueOf(my.role().name()), my.playerId()),
        m.hidden(),
        m.knownCount(),
        organizer ? m.bookingId() : null,
        my.players().stream().map(p -> new OpenMatchPersonResponse(p.id(), p.name())).toList(),
        my.requests().stream()
            .map(r -> new OpenMatchJoinRequestResponse(r.id(), r.name(), r.requestedAt()))
            .toList(),
        m.version());
  }

  static @Nullable BookingOpenMatchResponse booking(@Nullable OpenMatchSummary s) {
    return s == null
        ? null
        : new BookingOpenMatchResponse(
            s.id(),
            OpenMatchStatusValue.valueOf(s.status().name()),
            s.hidden(),
            s.total(),
            s.minimum(),
            s.maximum(),
            s.confirmed(),
            s.pending());
  }

  private static @Nullable OpenMatchLevelValue level(OpenMatch m) {
    return m.level() == null ? null : OpenMatchLevelValue.valueOf(m.level().name());
  }
}
