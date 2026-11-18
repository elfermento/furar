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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * As entidades (ADR-0039, punto 7): alta, cambios cos responsables, nome repetido, límite de
 * responsables, eliminar sen historia, non eliminar con reservas futuras e arquivar con historia.
 */
class OrganizationIT extends AbstractSeasonIT {

  private Api.Response get(Facility f, UUID id) {
    return api().get(organizations(f.slug()) + "/" + id).token(TestTokens.manager(f.slug())).send();
  }

  private Api.Response delete(Facility f, UUID id) {
    return api()
        .delete(organizations(f.slug()) + "/" + id)
        .ifMatch(get(f, id).etagVersion())
        .token(TestTokens.manager(f.slug()))
        .send();
  }

  @Test
  void createsChangesAndRefusesWhatDoesNotFit() {
    Facility f = facility("entidade", true);
    String ana = "ana-" + UUID.randomUUID();

    Api.Response created =
        createOrganization(
            f, "Club Demo Patinaxe", List.of(email(ana), "Ana-X@Example.org", "ana-x@example.org"));
    assertThat(created.status()).as(created.toString()).isEqualTo(201);
    assertThat(created.header("Location")).contains("/organizations/");
    assertThat(created.header("ETag")).isNotNull();
    UUID id = id(created.json());
    assertThat(created.json().get("managers")).hasSize(2);

    Api.Response list =
        api().get(organizations(f.slug())).token(TestTokens.manager(f.slug())).send();
    assertThat(list.status()).isEqualTo(200);
    JsonNode row = list.json().get(0);
    assertThat(row.get("name").asString()).isEqualTo("Club Demo Patinaxe");
    assertThat(row.get("managers").asInt()).isEqualTo(2);
    assertThat(row.get("status").asString()).isEqualTo("NONE");

    // O mesmo nome (sen ter en conta maiúsculas) non vale; 11 responsables tampouco.
    assertThat(problem(createOrganization(f, "club demo PATINAXE", List.of())))
        .isEqualTo("409 booking.organization-name-taken");
    List<String> eleven = new ArrayList<>();
    for (int i = 0; i < 11; i++) {
      eleven.add("persoa" + i + "@example.org");
    }
    assertThat(problem(createOrganization(f, "Club Grande", eleven)))
        .isEqualTo("422 booking.organization-managers-limit");
    Map<String, Object> noEmail = new LinkedHashMap<>();
    noEmail.put("name", "Club Sen Correo");
    noEmail.put("managers", List.of());
    assertThat(
            api()
                .post(organizations(f.slug()), noEmail)
                .token(TestTokens.manager(f.slug()))
                .send()
                .status())
        .isEqualTo(400);

    // Cambiar: a lista de responsables substitúe a de antes.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Club Demo Patinaxe Narón");
    body.put("email", "patinaxe@example.org");
    body.put("managers", List.of(email(ana)));
    Api.Response updated =
        api()
            .put(organizations(f.slug()) + "/" + id, body)
            .ifMatch(created.etagVersion())
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(updated.status()).as(updated.toString()).isEqualTo(200);
    assertThat(updated.json().get("managers").toString()).isEqualTo("[\"" + email(ana) + "\"]");
    assertThat(updated.json().get("contactName").isNull()).isTrue();
    assertThat(
            api()
                .put(organizations(f.slug()) + "/" + id, body)
                .ifMatch(created.etagVersion())
                .token(TestTokens.manager(f.slug()))
                .send()
                .status())
        .isEqualTo(412);

    // Recepción non xestiona entidades.
    assertThat(api().get(organizations(f.slug())).token(TestTokens.staff(f.slug())).send().status())
        .isEqualTo(403);
    // Sen historia, bórrase.
    assertThat(delete(f, id).status()).isEqualTo(204);
    assertThat(problem(get(f, id))).isEqualTo("404 booking.organization-not-found");
  }

  @Test
  void withFutureSessionsItStaysAndWithHistoryItIsArchived() throws Exception {
    Facility f = facility("entidade-arquivo", true);
    UUID use = whole(f, Map.of());
    String bea = "bea-" + UUID.randomUUID();
    UUID club = organization(f, "Escola Demo de Fútbol Sala", email(bea));
    LocalDate wednesday = nextWorking(3);
    UUID seriesId =
        created(
            f,
            schedule(club, f, use, null, List.of(3), 600, 690, wednesday, wednesday.plusDays(7)));

    assertThat(problem(delete(f, club))).isEqualTo("409 booking.organization-has-future-bookings");
    // O nome novo vai tamén ás súas reservas.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Escola Demo de Fútbol");
    body.put("email", "escola@example.org");
    body.put("managers", List.of(email(bea)));
    assertThat(
            api()
                .put(organizations(f.slug()) + "/" + club, body)
                .ifMatch(get(f, club).etagVersion())
                .token(TestTokens.manager(f.slug()))
                .send()
                .status())
        .isEqualTo(200);
    assertThat(
            jdbc.sql("select distinct holder_name from booking where series_id = :s")
                .param("s", seriesId)
                .query(String.class)
                .list())
        .containsExactly("Escola Demo de Fútbol");
    assertThat(
            api()
                .get(organizations(f.slug()) + "?status=SEASON")
                .token(TestTokens.manager(f.slug()))
                .send()
                .json())
        .hasSize(1);

    // Rematada a tempada, ten historia pero nada futuro: arquívase e só queda o nome.
    assertThat(end(f, seriesId, wednesday).status()).isEqualTo(200);
    assertThat(delete(f, club).status()).isEqualTo(204);
    JsonNode archived = get(f, club).json();
    assertThat(archived.get("archived").asBoolean()).isTrue();
    assertThat(archived.get("name").asString()).isEqualTo("Escola Demo de Fútbol");
    assertThat(archived.get("email").isNull()).isTrue();
    assertThat(archived.get("managers")).isEmpty();
    assertThat(api().get(organizations(f.slug())).token(TestTokens.manager(f.slug())).send().json())
        .isEmpty();
    assertThat(
            api()
                .get(organizations(f.slug()) + "?archived=true")
                .token(TestTokens.manager(f.slug()))
                .send()
                .json())
        .hasSize(1);
    // A persoa responsable xa non a ve; as reservas pasadas seguen ligadas.
    assertThat(api().get(myOrganizations(f.slug())).token(person(bea)).send().json()).isEmpty();
    assertThat(sessions(seriesId, "CANCELLED_BY_MUNICIPALITY")).isEqualTo(2);
    // Arquivada, non admite tempadas.
    assertThat(
            problem(
                preview(
                    f, schedule(club, f, use, null, List.of(3), 600, 690, wednesday, wednesday))))
        .isEqualTo("404 booking.organization-not-found");
  }
}
