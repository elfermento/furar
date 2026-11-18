// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Axudas das probas das partidas abertas (ADR-0040): acender a función, abrir unha partida sobre
 * unha reserva propia, unirse, decidir e ver. Pública para os correos ({@code notification}).
 */
public abstract class AbstractOpenMatchIT extends AbstractWaitIT {

  protected static String me(String slug) {
    return "/api/v1/municipalities/" + slug + "/me";
  }

  protected static String publicMatches(String slug) {
    return "/api/v1/municipalities/" + slug + "/public/open-matches";
  }

  /** Dentro de tres días: abonda para calquera prazo de 12 horas. */
  protected static LocalDate later() {
    return tomorrow().plusDays(2);
  }

  /**
   * Acende (ou apaga) «Buscar xente para xogar» pola API e leva o último cambio 30 días atrás: así
   * o prazo de calquera partida da proba cae despois.
   */
  protected void openMatches(Facility f, boolean enabled, int hours) {
    String url = "/api/v1/municipalities/" + f.slug();
    String manager = TestTokens.manager(f.slug());
    Api.Response saved =
        api()
            .put(url + "/open-match-policy", Map.of("enabled", enabled, "cancelHours", hours))
            .ifMatch(api().get(url).token(manager).send().etagVersion())
            .token(manager)
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    jdbc.sql(
            """
            update municipality
               set open_match_since = case when open_match_since is null then null
                                           else now() - interval '30 days' end,
                   open_match_policy_changed_at = now() - interval '30 days'
             where id = :id
            """)
        .param("id", f.municipality())
        .update();
  }

  protected static Map<String, Object> terms(
      int known, int minimum, int maximum, boolean approval) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("knownCount", known);
    body.put("minimum", minimum);
    body.put("maximum", maximum);
    body.put("level", "INTERMEDIATE");
    body.put("title", "Pachanga");
    body.put("description", "Ven xogar <b>con nós</b>");
    body.put("approval", approval);
    return body;
  }

  protected Api.Response open(String token, Facility f, UUID booking, Map<String, Object> body) {
    return api()
        .put(me(f.slug()) + "/bookings/" + booking + "/open-match", body)
        .token(token)
        .send();
  }

  /** Abre a partida e devolve o seu id. */
  protected UUID opened(
      String token,
      Facility f,
      UUID booking,
      int known,
      int minimum,
      int maximum,
      boolean approval) {
    Api.Response created = open(token, f, booking, terms(known, minimum, maximum, approval));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    return id(created.json());
  }

  protected Api.Response status(String token, Facility f, UUID booking, String status) {
    return api()
        .put(me(f.slug()) + "/bookings/" + booking + "/open-match/status", Map.of("status", status))
        .token(token)
        .send();
  }

  protected Api.Response enter(String token, Facility f, UUID match) {
    return api()
        .post(me(f.slug()) + "/open-matches/" + match + "/players", null)
        .token(token)
        .send();
  }

  protected Api.Response exit(String token, Facility f, UUID match) {
    return api()
        .delete(me(f.slug()) + "/open-matches/" + match + "/players/me")
        .token(token)
        .send();
  }

  protected Api.Response decide(String token, Facility f, UUID match, UUID player, boolean accept) {
    return api()
        .post(
            me(f.slug())
                + "/open-matches/"
                + match
                + "/players/"
                + player
                + (accept ? "/accept" : "/reject"),
            null)
        .token(token)
        .send();
  }

  protected Api.Response mine(String token, Facility f, UUID match) {
    return api().get(me(f.slug()) + "/open-matches/" + match).token(token).send();
  }

  protected Api.Response publicPage(Facility f, UUID match) {
    return api().get(publicMatches(f.slug()) + "/" + match).send();
  }

  protected JsonNode publicList(Facility f) {
    Api.Response found = api().get(publicMatches(f.slug())).send();
    assertThat(found.status()).as(found.toString()).isEqualTo(200);
    return found.json().get("items");
  }

  protected Api.Response hide(Facility f, UUID booking, boolean hidden) {
    return api()
        .put(bookings(f.slug()) + "/" + booking + "/open-match/hidden", Map.of("hidden", hidden))
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  /** «ESTADO MOTIVO» da partida na base. */
  protected String matchState(UUID match) {
    return jdbc.sql(
            "select status || ' ' || coalesce(cancel_reason, '-') from booking_open_match"
                + " where id = :id")
        .param("id", match)
        .query(String.class)
        .single();
  }

  protected int players(UUID match, String status) {
    return jdbc.sql(
            "select count(*) from booking_open_match_player where match_id = :m and status = :s")
        .param("m", match)
        .param("s", status)
        .query(Integer.class)
        .single();
  }

  protected String bookingStatus(UUID booking) {
    return jdbc.sql("select status from booking where id = :id")
        .param("id", booking)
        .query(String.class)
        .single();
  }
}
