// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Axudas das probas das entidades e as súas tempadas (ADR-0039): dar de alta unha entidade, ver a
 * vista previa, crear e rematar unha tempada e solicitala como entidade. Pública para os correos
 * ({@code notification}).
 */
public abstract class AbstractSeasonIT extends AbstractWaitIT {

  protected static String organizations(String slug) {
    return "/api/v1/municipalities/" + slug + "/organizations";
  }

  protected static String series(String slug) {
    return "/api/v1/municipalities/" + slug + "/booking-series";
  }

  protected static String requests(String slug) {
    return "/api/v1/municipalities/" + slug + "/booking-series-requests";
  }

  protected static String myOrganizations(String slug) {
    return "/api/v1/municipalities/" + slug + "/me/organizations";
  }

  protected Api.Response createOrganization(Facility f, String name, List<String> managers) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("contactName", "Ana Rey");
    body.put("email", "club-" + UUID.randomUUID() + "@example.org");
    body.put("phone", "981 000 000");
    body.put("managers", managers);
    return api().post(organizations(f.slug()), body).token(TestTokens.manager(f.slug())).send();
  }

  /** Dá de alta unha entidade e devolve o seu id. */
  protected UUID organization(Facility f, String name, String... managers) {
    Api.Response created = createOrganization(f, name, List.of(managers));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return id(created.json());
  }

  /**
   * O horario dunha tempada.
   *
   * @param partId nulo nun uso enteiro
   */
  protected static Map<String, Object> schedule(
      UUID organization,
      Facility f,
      UUID use,
      UUID partId,
      List<Integer> weekdays,
      int start,
      int end,
      LocalDate first,
      LocalDate last) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("organizationId", organization);
    body.put("facilityId", f.id());
    body.put("useId", use);
    if (partId != null) {
      body.put("partId", partId);
    }
    body.put("weekdays", weekdays);
    body.put("startMinute", start);
    body.put("endMinute", end);
    body.put("firstDate", first.toString());
    body.put("lastDate", last.toString());
    return body;
  }

  protected Api.Response preview(Facility f, Map<String, Object> schedule) {
    return api()
        .post(series(f.slug()) + "/preview", schedule)
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  protected JsonNode previewed(Facility f, Map<String, Object> schedule) {
    Api.Response found = preview(f, schedule);
    assertThat(found.status()).as(found.toString()).isEqualTo(200);
    return found.json();
  }

  /** As datas con choque dunha vista previa: o {@code skip} para crear. */
  protected static List<String> conflictDates(JsonNode preview) {
    List<String> dates = new ArrayList<>();
    preview.get("conflicts").forEach(c -> dates.add(c.get("date").asString()));
    return dates.stream().distinct().sorted().toList();
  }

  protected Api.Response create(
      Facility f, Map<String, Object> schedule, List<String> skip, String key) {
    Map<String, Object> body = new LinkedHashMap<>(schedule);
    body.put("skip", skip);
    Api.Call call = api().post(series(f.slug()), body).token(TestTokens.manager(f.slug()));
    if (key != null) {
      call.header("Idempotency-Key", key);
    }
    return call.send();
  }

  /** Crea a tempada coa vista previa de agora e devolve o seu id. */
  protected UUID created(Facility f, Map<String, Object> schedule) {
    Api.Response created =
        create(f, schedule, conflictDates(previewed(f, schedule)), UUID.randomUUID().toString());
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return id(created.json());
  }

  protected Api.Response end(Facility f, UUID seriesId, LocalDate from) {
    return api()
        .post(series(f.slug()) + "/" + seriesId + "/end", Map.of("from", from.toString()))
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  /** As sesións confirmadas dunha serie, na base. */
  protected int sessions(UUID seriesId, String status) {
    return jdbc.sql("select count(*) from booking where series_id = :s and status = :status")
        .param("s", seriesId)
        .param("status", status)
        .query(Integer.class)
        .single();
  }

  /** As reservas da entidade como a súa persoa responsable. */
  protected JsonNode organizationBookings(String token, Facility f, UUID organization) {
    Api.Response found =
        api().get(myOrganizations(f.slug()) + "/" + organization + "/bookings").token(token).send();
    assertThat(found.status()).as(found.toString()).isEqualTo(200);
    return found.json().get("items");
  }

  /** Reserva como entidade, cunha clave nova. */
  protected Api.Response reserveAs(
      String token, Facility f, UUID organization, UUID use, java.time.Instant start) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    return api()
        .post(myOrganizations(f.slug()) + "/" + organization + "/bookings", body)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .token(token)
        .send();
  }

  /** Solicita unha tempada como entidade. */
  protected Api.Response request(
      String token, Facility f, UUID organization, Map<String, Object> schedule) {
    Map<String, Object> body = new LinkedHashMap<>(schedule);
    body.remove("organizationId");
    body.put("note", "Equipos infantís");
    return api()
        .post(myOrganizations(f.slug()) + "/" + organization + "/series-requests", body)
        .token(token)
        .send();
  }

  /** O próximo día da semana ISO desde dentro de dúas semanas. */
  protected static LocalDate next(int isoWeekday) {
    LocalDate d = LocalDate.now(ZONE).plusDays(14);
    while (d.getDayOfWeek().getValue() != isoWeekday) {
      d = d.plusDays(1);
    }
    return d;
  }

  /**
   * Como {@link #next(int)}, pero nunca un festivo (a tempada non crea sesións neles): unha proba
   * dunha soa data non pode depender do día en que corre (o 8 de decembro, por exemplo).
   */
  protected LocalDate nextWorking(int isoWeekday) {
    LocalDate d = next(isoWeekday);
    while (jdbc.sql("select count(*) from public_holiday where date = :d")
            .param("d", d)
            .query(Integer.class)
            .single()
        > 0) {
      d = d.plusWeeks(1);
    }
    return d;
  }
}
