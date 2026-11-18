// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.opendata.application.service.OpenDataRefreshService;
import gal.furar.testsupport.AbstractIT;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base dos IT dos datos abertos (ADR-0034): concellos con instalacións publicadas de verdade (pola
 * API), reservas confirmadas na base e a tarefa nocturna a man.
 */
public abstract class AbstractOpenDataIT extends AbstractIT {

  protected static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  protected static final List<String> DAYS =
      List.of(
          "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY", "HOLIDAYS");

  @Autowired protected OpenDataRefreshService refresh;
  @Autowired protected TransactionTemplate tx;

  /** Unha instalación publicada e o seu uso enteiro de 60 minutos. */
  protected record Published(
      String slug, UUID municipality, UUID id, UUID use, Map<String, Object> details) {}

  protected static String facilities(String slug) {
    return "/api/v1/municipalities/" + slug + "/facilities";
  }

  protected static String openData(String slug) {
    return "/api/v1/municipalities/" + slug + "/open-data";
  }

  protected static LocalDate today() {
    return LocalDate.now(ZONE);
  }

  /** Un concello cos datos legais completos (sae na portada común). */
  protected UUID municipalityReady(String slug) {
    UUID id = id(createMunicipality(slug));
    jdbc.sql(
            """
            update municipality set legal_entity_name = 'Concello de Proba', nif = 'P1505400J',
              address = 'Praza do Concello, 1', general_email = 'concello@example.org',
              dpo_email = 'dpd@example.org', dir3_code = null
            where id = :id
            """)
        .param("id", id)
        .update();
    return id;
  }

  /**
   * Instalación publicada: un uso enteiro de 60 minutos, aberta todos os días de 09:00 a 22:00 ata
   * 2030; reserva por internet ou só «ocos visibles» ou ningún dos dous.
   */
  protected Published publish(
      String slug, UUID municipality, String name, boolean online, boolean showFreeSlots) {
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("street", "Rúa do Alto");
    address.put("streetNumber", "12");
    address.put("postalCode", "15570");
    address.put("locality", "Narón");
    address.put("latitude", 43.5167);
    address.put("longitude", -8.1833);
    Api.Response created =
        api()
            .post(facilities(slug), Map.of("name", name, "address", address))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    UUID facility = id(created.json());
    Map<String, Object> details = new HashMap<>();
    details.put("sports", List.of(Map.of("code", "padel")));
    details.put("type", "FIELD");
    details.put("ridegaCode", "15-054-0012");
    details.put("onlineBooking", online);
    details.put("showFreeSlots", showFreeSlots);
    details.put("covered", false);
    Api.Response saved =
        api()
            .put(facilities(slug) + "/" + facility + "/details", details)
            .ifMatch(version(slug, facility))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    Map<String, Object> use = new LinkedHashMap<>();
    use.put("name", "Pista enteira");
    use.put("rentalMode", "WHOLE");
    use.put("slotMinutes", 60);
    use.put("availability", "ALWAYS");
    use.put("price", Map.of("kind", "PAID", "amountCents", 600));
    Api.Response useCreated =
        api()
            .post(facilities(slug) + "/" + facility + "/uses", use)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(useCreated.status()).as(useCreated.toString()).isEqualTo(201);
    Map<String, Object> season = new LinkedHashMap<>();
    season.put("name", "Todo o ano");
    season.put("startDate", "2025-01-01");
    season.put("endDate", "2030-12-31");
    Api.Response seasonCreated =
        api()
            .post(facilities(slug) + "/" + facility + "/seasons", season)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(seasonCreated.status()).as(seasonCreated.toString()).isEqualTo(201);
    List<Map<String, Object>> week = new ArrayList<>();
    DAYS.forEach(
        day ->
            week.add(
                Map.of(
                    "day",
                    day,
                    "intervals",
                    List.of(Map.of("startMinute", 540, "endMinute", 1320)))));
    Map<String, Object> withWeek = new LinkedHashMap<>(season);
    withWeek.put("week", week);
    Api.Response hours =
        api()
            .put(
                facilities(slug)
                    + "/"
                    + facility
                    + "/seasons/"
                    + seasonCreated.json().get("id").asString(),
                withWeek)
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(hours.status()).as(hours.toString()).isEqualTo(200);
    Api.Response published =
        api()
            .put(facilities(slug) + "/" + facility + "/publication", Map.of())
            .ifMatch(version(slug, facility))
            .token(TestTokens.manager(slug))
            .send();
    assertThat(published.status()).as(published.toString()).isEqualTo(200);
    return new Published(slug, municipality, facility, id(useCreated.json()), details);
  }

  protected long version(String slug, UUID facility) {
    return api()
        .get(facilities(slug) + "/" + facility)
        .token(TestTokens.manager(slug))
        .send()
        .etagVersion();
  }

  /** Unha reserva confirmada do uso enteiro, directamente na base. */
  protected void occupy(Published f, LocalDate date, int startMinute, int minutes) {
    UUID booking = UUID.randomUUID();
    OffsetDateTime start = date.atStartOfDay(ZONE).plusMinutes(startMinute).toOffsetDateTime();
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, rental_mode, period, slots, status,
               payment_status, price_kind, cancel_until, holder_subject, holder_name,
               holder_email, holder_email_key, holder_language, created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, 'WHOLE', tstzrange(:start, :end, '[)'), 1,
                    'CONFIRMED', 'NONE', 'NONE', :start, 'sub-ana', 'Ana Rey', 'ana@example.org',
                    'ana@example.org', 'gl', now(), now(), 0)
            """)
        .param("id", booking)
        .param("m", f.municipality())
        .param("code", "FUR-" + code())
        .param("f", f.id())
        .param("u", f.use())
        .param("start", start)
        .param("end", start.plusMinutes(minutes))
        .update();
  }

  private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  private static final java.util.Random RANDOM = new java.util.Random();

  /** Un código de reserva válido ao chou (as colisións son moi improbables nun IT). */
  private static String code() {
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }

  /** Garda valores de campos propios da instalación pola API, coma o xestor. */
  protected void fieldValues(Published f, Map<String, ?> values) {
    Map<String, Object> body = new HashMap<>(f.details());
    body.put("customFields", values);
    Api.Response saved =
        api()
            .put(facilities(f.slug()) + "/" + f.id() + "/details", body)
            .ifMatch(version(f.slug(), f.id()))
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
  }

  /** Un campo propio da instalación. */
  protected static Map<String, Object> facilityField(String code, String type, String visibility) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("code", code);
    field.put("type", type);
    field.put("label", Map.of("gl", "Etiqueta " + code, "es", "Etiqueta " + code));
    field.put(
        "usages",
        List.of(Map.of("target", "FACILITY", "visibility", visibility, "required", false)));
    return field;
  }
}
