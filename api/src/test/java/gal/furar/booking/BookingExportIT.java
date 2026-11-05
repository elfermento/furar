// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * «Descargar CSV» de punta a punta (maqueta 60): o ficheiro (BOM, «;», cabeceiras no idioma da
 * petición, unha columna por campo propio), os filtros de instalación e estado, o período de como
 * moito 12 meses, o permiso (só a xestión), outro concello e o rexistro de actividade sen datos
 * persoais.
 */
class BookingExportIT extends AbstractBookingIT {

  static String export(String slug) {
    return "/api/v1/municipalities/" + slug + "/bookings/export";
  }

  @Test
  void theManagerDownloadsTheBookingsOfAPeriodAsCsv() {
    Facility alto = facility("csv-reservas", true);
    Facility pineiros = facility(alto.slug(), alto.municipality(), "Pista de Piñeiros", true);
    UUID paid = whole(alto, Map.of("price", Map.of("kind", "PAID", "amountCents", 1250)));
    UUID free = whole(pineiros, Map.of("price", Map.of("kind", "FREE")));
    createField(
        alto.slug(),
        Map.of(
            "code",
            "level",
            "type",
            "SELECT",
            "label",
            Map.of("gl", "Nivel", "es", "Nivel"),
            "options",
            List.of(Map.of("code", "mid", "label", Map.of("gl", "Medio", "es", "Medio"))),
            "usages",
            List.of(Map.of("target", "BOOKING", "visibility", "OWNER", "required", false))));
    LocalDate day = tomorrow();
    UUID ana = booked(reserve(person(), alto, paid, at(day, OPENS), Map.of("level", "mid")));
    jdbc.sql("update booking set holder_name = '=Ánxela' where id = :k").param("k", ana).update();
    booked(reserve(person(), pineiros, free, at(day, OPENS + 60), Map.of()));
    String dora = person();
    UUID doraId = booked(reserve(dora, alto, paid, at(day, OPENS + 120), Map.of()));
    Api.Response cancelled =
        api()
            .post(mine(alto.slug()) + "/" + doraId + "/cancel", null)
            .ifMatch(0)
            .token(dora)
            .send();
    assertThat(cancelled.status()).as(cancelled.toString()).isEqualTo(200);
    // Fóra do período: o día seguinte.
    booked(reserve(person(), alto, paid, at(day.plusDays(1), OPENS), Map.of()));
    // Outro concello, o mesmo día: non sae.
    Facility other = facility("csv-outro", true);
    UUID otherUse = whole(other, Map.of());
    booked(reserve(person(), other, otherUse, at(day, OPENS), Map.of()));

    String manager = TestTokens.manager(alto.slug());
    String period = "?from=" + day + "&to=" + day;
    Api.Response all = api().get(export(alto.slug()) + period).token(manager).send();
    assertThat(all.status()).as(all.toString()).isEqualTo(200);
    assertThat(all.contentType()).startsWith("text/csv");
    assertThat(all.header("Content-Disposition"))
        .contains("furar-" + alto.slug() + "-reservas-" + day + "-" + day + ".csv");
    assertThat(all.body()).startsWith("﻿");
    List<String> lines = List.of(all.body().substring(1).split("\r\n"));
    assertThat(lines.getFirst())
        .isEqualTo(
            "Data;Inicio;Fin;Instalación;Uso;Persoa;Correo;Teléfono;Estado;Pago;Importe (€);"
                + "Código;Reservada o;Reservada por;Nivel");
    assertThat(lines).hasSize(4);
    assertThat(lines.get(1))
        .as("por día e hora; a fórmula neutralizada, o importe con coma e a opción co seu nome")
        .startsWith(dayText(day) + ";09:00;")
        .contains(";'=Ánxela;", ";Confirmada;Pendente;12,50;")
        .endsWith(";Medio");
    assertThat(lines.get(2)).contains(";Pista de Piñeiros;", ";Gratis;;");
    assertThat(lines.get(3)).contains(";Cancelada pola persoa;");

    Api.Response filtered =
        api()
            .get(export(alto.slug()) + period + "&facilityId=" + alto.id() + "&status=CONFIRMED")
            .token(manager)
            .language("es")
            .send();
    assertThat(filtered.status()).as(filtered.toString()).isEqualTo(200);
    List<String> only = List.of(filtered.body().substring(1).split("\r\n"));
    assertThat(only.getFirst()).startsWith("Fecha;Inicio;Fin;Instalación;Uso;Persona;");
    assertThat(only).hasSize(2);
    assertThat(only.get(1)).contains(";Confirmada;Pendiente;12,50;");

    assertThat(
            jdbc.sql(
                    """
                    select outcome || ' ' || (details ->> 'rows') || ' '
                      || (details ->> 'facilities') || ' ' || (details ->> 'statuses')
                    from audit_event
                    where municipality_id = :k and action = 'booking.export'
                    order by occurred_at
                    """)
                .param("k", alto.municipality())
                .query(String.class)
                .list())
        .as("co número de filas e os filtros, sen datos persoais")
        .containsExactly("SUCCESS 3 0 []", "SUCCESS 1 1 [\"CONFIRMED\"]");
    assertThat(
            jdbc.sql(
                    "select count(*) from audit_event where action = 'booking.export'"
                        + " and (details::text like '%Ánxela%' or details::text like '%@%')")
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void aBookingMadeByTheStaffCarriesThePhoneAndWhoMadeIt() {
    Facility f = facility("csv-por-outra", true);
    UUID free = whole(f, Map.of());
    LocalDate day = tomorrow();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", free);
    body.put("start", at(day, OPENS).toString());
    body.put("slots", 1);
    body.put("holder", Map.of("name", "Carme Souto", "phone", "600 111 222"));
    body.put("language", "gl");
    booked(
        api()
            .post("/api/v1/municipalities/" + f.slug() + "/bookings", body)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .token(TestTokens.staff(f.slug()))
            .send());

    Api.Response csv =
        api()
            .get(export(f.slug()) + "?from=" + day + "&to=" + day)
            .token(TestTokens.manager(f.slug()))
            .send();
    assertThat(csv.status()).as(csv.toString()).isEqualTo(200);
    List<String> lines = List.of(csv.body().substring(1).split("\r\n"));
    assertThat(lines).hasSize(2);
    assertThat(lines.get(1))
        .contains(";Carme Souto;;600 111 222;Confirmada;")
        .contains(";Persoa de proba");
  }

  @Test
  void onlyTheManagerAndAtMostTwelveMonths() {
    Facility f = facility("csv-permisos", true);
    LocalDate day = tomorrow();
    String period = "?from=" + day + "&to=" + day;

    Api.Response staff =
        api().get(export(f.slug()) + period).token(TestTokens.staff(f.slug())).send();
    assertThat(staff.status()).as("o mostrador non descarga").isEqualTo(403);

    String manager = TestTokens.manager(f.slug());
    Api.Response tooLong =
        api()
            .get(export(f.slug()) + "?from=" + day + "&to=" + day.plusMonths(12))
            .token(manager)
            .header("Accept", "text/csv")
            .send();
    assertThat(tooLong.status()).as(tooLong.toString()).isEqualTo(422);
    assertThat(tooLong.contentType())
        .as("tamén pedindo CSV")
        .startsWith("application/problem+json");
    assertThat(tooLong.json().get("code").asString()).isEqualTo("booking.export-range-too-long");
    assertThat(tooLong.json().get("params").get("months").asInt()).isEqualTo(12);

    Api.Response year =
        api()
            .get(export(f.slug()) + "?from=" + day + "&to=" + day.plusMonths(12).minusDays(1))
            .token(manager)
            .send();
    assertThat(year.status()).as("doce meses xustos, si").isEqualTo(200);
    assertThat(year.body().substring(1).split("\r\n")).hasSize(1);

    Api.Response reversed =
        api()
            .get(export(f.slug()) + "?from=" + day + "&to=" + day.minusDays(1))
            .token(manager)
            .send();
    assertThat(reversed.status()).isEqualTo(400);

    Api.Response withoutTo = api().get(export(f.slug()) + "?from=" + day).token(manager).send();
    assertThat(withoutTo.status()).as("o período é obrigatorio").isEqualTo(400);

    Api.Response otherMunicipality =
        api().get(export(f.slug()) + period).token(TestTokens.manager("outro-concello")).send();
    assertThat(otherMunicipality.status()).isEqualTo(403);

    assertThat(
            jdbc.sql(
                    """
                    select outcome || coalesce(' ' || (details ->> 'code'), '')
                    from audit_event
                    where municipality_id = :k and action = 'booking.export'
                    order by occurred_at
                    """)
                .param("k", f.municipality())
                .query(String.class)
                .list())
        .as("o 403 é do permiso, antes do caso de uso")
        .containsExactly(
            "FAILURE booking.export-range-too-long", "SUCCESS", "FAILURE common.invalid-parameter");
  }

  private static String dayText(LocalDate day) {
    return String.format("%02d/%02d/%d", day.getDayOfMonth(), day.getMonthValue(), day.getYear());
  }

  private static UUID booked(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
    return id(response.json());
  }

  private Api.Response reserve(
      String token, Facility f, UUID use, Instant start, Map<String, Object> fields) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("facilityId", f.id());
    body.put("useId", use);
    body.put("start", start.toString());
    body.put("slots", 1);
    body.put("customFields", fields);
    return api()
        .post(mine(f.slug()), body)
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .token(token)
        .send();
  }
}
