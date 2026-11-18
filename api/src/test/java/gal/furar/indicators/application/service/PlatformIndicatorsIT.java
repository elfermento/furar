// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.booking.AbstractBookingIT;
import gal.furar.indicators.application.port.in.IndicatorsPanel.Occupancy;
import gal.furar.indicators.application.port.in.PlatformIndicators;
import gal.furar.indicators.application.port.in.PlatformIndicators.MunicipalityFigures;
import gal.furar.indicators.domain.PeriodKind;
import gal.furar.municipality.MunicipalityQueries;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.TestTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;

/**
 * Os indicadores da plataforma (ADR-0026, revisión do 2026-11-11): cada fila é o panel do seu
 * concello, os totais son a suma das filas e só os ve a administración da plataforma. Os cálculos
 * próbanse co servizo e un directorio cos dous concellos da proba (a base dos IT leva os concellos
 * de todas as clases); o permiso e a resposta, pola API.
 */
class PlatformIndicatorsIT extends AbstractBookingIT {

  private static final AtomicInteger CODES = new AtomicInteger();

  @Autowired IndicatorsService panels;
  @Autowired MunicipalityQueries municipalityQueries;
  @Autowired PlatformTransactionManager transactions;
  @Autowired Clock clock;

  Facility alto;
  Facility gandara;
  UUID useAlto;
  UUID useGandara;

  @BeforeEach
  void municipalities() {
    String a = uniqueSlug("pind-a");
    String b = uniqueSlug("pind-b");
    alto = facility(a, id(createMunicipality(a)), "Pista do Alto", true);
    gandara = facility(b, id(createMunicipality(b)), "Campo da Gándara", true);
    useAlto = whole(alto, java.util.Map.of());
    useGandara = whole(gandara, java.util.Map.of());
  }

  @Test
  void totalsAreTheSumOfTheRowsAndEachRowIsTheMunicipalityPanel() {
    confirmed(alto, useAlto, "2027-12-01T10:00", 60, "sub-1", 500);
    confirmed(alto, useAlto, "2027-12-02T10:00", 120, "sub-1", null);
    confirmed(gandara, useGandara, "2027-12-03T10:00", 60, "sub-2", 300);
    // Noutro mes: non conta no período, si na evolución.
    confirmed(gandara, useGandara, "2027-11-03T10:00", 60, "sub-3", null);

    PlatformIndicators result =
        service(alto, gandara).read(PeriodKind.MONTH, LocalDate.parse("2027-12-10"), null, null);

    MunicipalityFigures a = row(result, alto);
    MunicipalityFigures b = row(result, gandara);
    assertThat(a.bookings()).isEqualTo(2);
    assertThat(a.people()).isEqualTo(1);
    assertThat(a.paidCents()).isEqualTo(500);
    assertThat(a.reservedMinutes()).isEqualTo(180.0);
    assertThat(b.bookings()).isEqualTo(1);
    assertThat(b.paidCents()).isEqualTo(300);

    PlatformIndicators.Totals totals = result.totals();
    assertThat(totals.bookings().value()).isEqualTo(a.bookings() + b.bookings());
    assertThat(totals.paidCents().value()).isEqualTo(a.paidCents() + b.paidCents());
    assertThat(totals.reservedMinutes().value())
        .isEqualTo(a.reservedMinutes() + b.reservedMinutes());
    Occupancy occupancy = totals.occupancy().value();
    assertThat(occupancy.openMinutes())
        .isEqualTo(a.occupancy().openMinutes() + b.occupancy().openMinutes())
        .isPositive();
    assertThat(occupancy.reservedMinutes())
        .isEqualTo(a.occupancy().reservedMinutes() + b.occupancy().reservedMinutes());
    // A evolución tamén se suma: decembro é o último punto e o anterior leva a de novembro.
    assertThat(totals.bookings().evolution()).hasSize(12);
    assertThat(totals.bookings().evolution().getLast().value()).isEqualTo(3L);
    assertThat(totals.bookings().previous()).isEqualTo(1L);

    // Cada fila é o panel que ve o xestor do seu concello.
    JsonNode panel =
        api()
            .get(
                "/api/v1/municipalities/"
                    + alto.slug()
                    + "/indicators?period=MONTH&date=2027-12-10")
            .token(TestTokens.manager(alto.slug()))
            .send()
            .json()
            .get("kpis");
    assertThat(panel.get("bookings").get("value").asLong()).isEqualTo(a.bookings());
    assertThat(panel.get("people").get("value").asLong()).isEqualTo(a.people());
    assertThat(panel.get("occupancy").get("value").get("openMinutes").asLong())
        .isEqualTo(a.occupancy().openMinutes());
  }

  @Test
  void eachMunicipalityOnlyCountsItsOwnBookings() {
    confirmed(alto, useAlto, "2027-12-01T10:00", 60, "sub-1", null);

    PlatformIndicators result =
        service(alto, gandara).read(PeriodKind.MONTH, LocalDate.parse("2027-12-10"), null, null);

    assertThat(row(result, alto).bookings()).isEqualTo(1);
    assertThat(row(result, gandara).bookings()).isZero();
    assertThat(row(result, gandara).people()).isZero();
    assertThat(result.municipalities())
        .extracting(MunicipalityFigures::name)
        .containsExactly("Concello " + alto.slug(), "Concello " + gandara.slug());
  }

  @Test
  void withoutMunicipalitiesTheTotalsAreZeroWithTheEvolutionBuckets() {
    PlatformIndicators result =
        new PlatformIndicatorsService(panels, List::of, municipalityQueries, transactions, clock)
            .read(PeriodKind.QUARTER, LocalDate.parse("2027-12-10"), null, null);

    assertThat(result.municipalities()).isEmpty();
    assertThat(result.totals().bookings().value()).isZero();
    assertThat(result.totals().occupancy().value().openMinutes()).isZero();
    assertThat(result.totals().bookings().evolution()).hasSize(8);
  }

  @Test
  void onlyThePlatformSeesItAndItCarriesNoPersonalData() {
    confirmed(alto, useAlto, "2027-12-01T10:00", 60, "sub-persoa", 500);
    String path = "/api/v1/indicators?period=MONTH&date=2027-12-10";

    assertThat(api().get(path).send().status()).isEqualTo(401);
    for (String token :
        List.of(
            TestTokens.manager(alto.slug()),
            TestTokens.staff(alto.slug()),
            TestTokens.resident(alto.slug()))) {
      Api.Response response = api().get(path).token(token).send();
      assertThat(response.status()).as(response.toString()).isEqualTo(403);
      assertThat(code(response)).isEqualTo("auth.forbidden");
    }

    Api.Response response = api().get(path).token(TestTokens.platformAdmin()).send();
    long municipalities = jdbc.sql("select count(*) from municipality").query(Long.class).single();
    if (municipalities > PlatformIndicatorsService.MAX_MUNICIPALITIES) {
      // Na base compartida dos IT pode haber máis concellos dos que se calculan nunha petición.
      assertThat(response.status()).as(response.toString()).isEqualTo(409);
      assertThat(code(response)).isEqualTo("indicators.too-many-municipalities");
      return;
    }
    assertThat(response.status()).as(response.toString()).isEqualTo(200);
    JsonNode mine = null;
    for (JsonNode row : response.json().get("municipalities")) {
      if (row.get("slug").asString().equals(alto.slug())) {
        mine = row;
      }
    }
    assertThat(mine).isNotNull();
    assertThat(mine.get("bookings").asLong()).isEqualTo(1);
    assertThat(mine.get("paidCents").asLong()).isEqualTo(500);
    assertThat(response.json().get("kpis").has("people")).isFalse();
    assertThat(response.body())
        .doesNotContain("sub-persoa", "proba@example.org", "Persoa de proba");
  }

  @Test
  void anInvalidPeriodIsRejectedBeforeReadingAnything() {
    Api.Response response =
        api()
            .get("/api/v1/indicators?period=CUSTOM&from=2027-12-10")
            .token(TestTokens.platformAdmin())
            .send();
    assertThat(response.status()).as(response.toString()).isEqualTo(400);
    assertThat(code(response)).isEqualTo("indicators.period-invalid");
  }

  // --- Axudas ---

  private PlatformIndicatorsService service(Facility... facilities) {
    MunicipalityDirectory directory =
        () ->
            java.util.Arrays.stream(facilities)
                .map(f -> new TenantRef(f.municipality(), f.slug()))
                .toList();
    return new PlatformIndicatorsService(
        panels, directory, municipalityQueries, transactions, clock);
  }

  private static MunicipalityFigures row(PlatformIndicators result, Facility f) {
    return result.municipalities().stream()
        .filter(m -> m.id().equals(f.municipality()))
        .findFirst()
        .orElseThrow();
  }

  /** Unha reserva confirmada por JDBC, pagada se leva céntimos. */
  private void confirmed(
      Facility f, UUID use, String at, int minutes, String subject, @Nullable Integer cents) {
    Instant start = LocalDateTime.parse(at).atZone(ZONE).toInstant();
    Instant cancelUntil = start.minus(Duration.ofHours(24));
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, part_id, rental_mode, period,
               slots, status, payment_status, price_kind, amount_cents, cancel_until,
               holder_subject, holder_name, holder_email, holder_email_key, holder_language,
               created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, null, 'WHOLE', tstzrange(:start, :end, '[)'), 1,
                    'CONFIRMED', :payment, :priceKind, :cents, :cancelUntil, :subject,
                    'Persoa de proba', 'proba@example.org', 'proba@example.org', 'gl',
                    now(), now(), 0)
            """)
        .param("id", UUID.randomUUID())
        .param("m", f.municipality())
        .param("code", code())
        .param("f", f.id())
        .param("u", use)
        .param("start", utc(start))
        .param("end", utc(start.plus(Duration.ofMinutes(minutes))))
        .param("payment", cents == null ? "NONE" : "PAID")
        .param("priceKind", cents == null ? "FREE" : "PAID")
        .param("cents", cents, java.sql.Types.INTEGER)
        .param("cancelUntil", utc(cancelUntil))
        .param("subject", subject)
        .update();
  }

  /** Un código distinto por reserva: {@code FUR-} e catro letras do alfabeto do código. */
  private static String code() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    int n = CODES.getAndIncrement();
    StringBuilder code = new StringBuilder("FUR-");
    for (int i = 0; i < 4; i++) {
      code.append(alphabet.charAt(n % alphabet.length()));
      n /= alphabet.length();
    }
    return code.toString();
  }

  private static OffsetDateTime utc(Instant at) {
    return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
  }
}
