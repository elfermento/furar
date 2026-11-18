// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Preparación común das probas da asistencia (ADR-0032): ler un código, marcar e desfacer unha
 * falta, mover unha reserva no tempo (a API só reserva cara a adiante), acender o control de
 * entrada dunha instalación e a penalización do concello. Pública para os correos ({@code
 * notification}).
 */
public abstract class AbstractAttendanceIT extends AbstractBookingIT {

  protected static String bookings(String slug) {
    return "/api/v1/municipalities/" + slug + "/bookings";
  }

  protected Api.Response validate(String token, String slug, String code) {
    return validate(token, slug, code, null, null);
  }

  protected Api.Response validate(
      String token, String slug, String code, Boolean force, String reason) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", code);
    if (force != null) {
      body.put("force", force);
    }
    if (reason != null) {
      body.put("reason", reason);
    }
    Api.Call call = api().post(bookings(slug) + "/validations", body);
    return token == null ? call.send() : call.token(token).send();
  }

  protected Api.Response noShow(String token, String slug, UUID booking) {
    return api()
        .put(bookings(slug) + "/" + booking + "/attendance", Map.of("attendance", "NO_SHOW"))
        .ifMatch(version(slug, booking, token))
        .token(token)
        .send();
  }

  protected Api.Response undo(String token, String slug, UUID booking) {
    return api()
        .delete(bookings(slug) + "/" + booking + "/attendance")
        .ifMatch(version(slug, booking, token))
        .token(token)
        .send();
  }

  protected JsonNode detail(String token, String slug, UUID booking) {
    Api.Response found = api().get(bookings(slug) + "/" + booking).token(token).send();
    assertThat(found.status()).as(found.toString()).isEqualTo(200);
    return found.json();
  }

  private long version(String slug, UUID booking, String token) {
    return api().get(bookings(slug) + "/" + booking).token(token).send().etagVersion();
  }

  /** O persoal reserva no mostrador a nome dese correo (ou sen correo, con {@code null}). */
  protected Api.Response bookFor(Facility f, UUID use, Instant start, String name, String email) {
    Map<String, Object> holder = new LinkedHashMap<>();
    holder.put("name", name);
    if (email != null) {
      holder.put("email", email);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    body.put("holder", holder);
    return api()
        .post(bookings(f.slug()), body)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .token(TestTokens.staff(f.slug()))
        .send();
  }

  /**
   * A reserva pasa a empezar en {@code start} (unha hora): a API só reserva cara a adiante, e a
   * asistencia mira reservas que están en marcha ou xa remataron. A ocupación queda na hora na que
   * se reservou (cada proba reserva horas distintas).
   */
  protected void move(UUID booking, Instant start) {
    OffsetDateTime from = start.atOffset(ZoneOffset.UTC);
    jdbc.sql(
            """
            update booking
            set period = tstzrange(:from, :to, '[)'), cancel_until = :from
            where id = :id
            """)
        .param("from", from)
        .param("to", from.plusHours(1))
        .param("id", booking)
        .update();
  }

  /** Acende o control de entrada da instalación pola API e ponlle a data de acendido. */
  protected void entryControl(Facility f, Instant since) {
    String url = facilities(f.slug()) + "/" + f.id();
    String manager = TestTokens.manager(f.slug());
    JsonNode details = api().get(url).token(manager).send().json().get("details");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sports", List.of(Map.of("code", "padel")));
    body.put("type", "FIELD");
    body.put("onlineBooking", details.get("onlineBooking").asBoolean());
    body.put("showFreeSlots", details.get("showFreeSlots").asBoolean());
    body.put("covered", false);
    body.put("entryControl", true);
    Api.Response saved =
        api().put(url + "/details", body).ifMatch(version(f.slug(), f.id())).token(manager).send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    assertThat(saved.json().get("details").get("entryControl").asBoolean()).isTrue();
    jdbc.sql("update facility set entry_control_since = :since where id = :id")
        .param("since", since.atOffset(ZoneOffset.UTC))
        .param("id", f.id())
        .update();
  }

  /** Acende a penalización do concello pola API e ponlle a data de acendido. */
  protected void penalty(Facility f, int threshold, int windowDays, int blockDays, Instant since) {
    String url = "/api/v1/municipalities/" + f.slug();
    String manager = TestTokens.manager(f.slug());
    Api.Response saved =
        api()
            .put(
                url + "/no-show-policy",
                Map.of(
                    "enabled",
                    true,
                    "threshold",
                    threshold,
                    "windowDays",
                    windowDays,
                    "blockDays",
                    blockDays))
            .ifMatch(api().get(url).token(manager).send().etagVersion())
            .token(manager)
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    jdbc.sql("update municipality set no_show_penalty_since = :since where id = :id")
        .param("since", since.atOffset(ZoneOffset.UTC))
        .param("id", f.municipality())
        .update();
  }

  /** A marca gardada: «ASISTENCIA quen». */
  protected String mark(UUID booking) {
    return jdbc.sql(
            "select attendance || ' ' || coalesce(attendance_by, '-') from booking where id = :id")
        .param("id", booking)
        .query(String.class)
        .single();
  }

  /** Os tipos das filas da historia da reserva, en orde. */
  protected List<String> history(UUID booking) {
    return jdbc.sql("select kind from booking_event where booking_id = :id order by at, id")
        .param("id", booking)
        .query(String.class)
        .list();
  }

  /** «acción RESULTADO» do rexistro de actividade do concello para esas accións, en orde. */
  protected List<String> audit(UUID municipality, String action) {
    return jdbc.sql(
            """
            select outcome || ' ' || coalesce(details ->> 'code', details ->> 'forced', '')
            from audit_event where municipality_id = :m and action = :action
            order by occurred_at, id
            """)
        .param("m", municipality)
        .param("action", action)
        .query(String.class)
        .list();
  }
}
