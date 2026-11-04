// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gal.furar.booking.AbstractBookingIT;
import gal.furar.booking.BookingStatistics;
import gal.furar.facility.FacilityQueries;
import gal.furar.indicators.application.port.in.IndicatorsPanel;
import gal.furar.indicators.application.port.in.IndicatorsQuery;
import gal.furar.indicators.application.port.in.ReadIndicatorsUseCase;
import gal.furar.indicators.domain.Comparison;
import gal.furar.indicators.domain.PeriodKind;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.testsupport.Api;
import gal.furar.testsupport.StatementCounter;
import gal.furar.testsupport.TestTokens;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * D3-04: o panel de indicadores de punta a punta, con {@code facility} de verdade (tempada,
 * festivo, excepción e bloqueo baixan as horas abertas) e as reservas por JDBC (ADR-0026; plan do
 * dominio 3, §1.2.4–§1.2.6 e §2.3). Todo en decembro de 2027: os festivos de Galicia dese ano xa
 * están sementados (6, 8 e 25) e as datas seguen no futuro, onde a xestión admite bloqueos e
 * excepcións.
 *
 * <p>Pista do Alto (A): de 9 a 14 todos os días agás festivos, pechada o 14 (excepción) e bloqueada
 * o 15 de 10 a 12 → 31 × 300 − 3 × 300 − 300 − 120 = 7.980 minutos abertos. Campo da Gándara (B):
 * igual sen excepción nin bloqueo, cun uso só para os sábados.
 */
class IndicatorsIT extends AbstractBookingIT {

  static final LocalDate DECEMBER = LocalDate.parse("2027-12-01");
  static final int OPEN_A_IN_DECEMBER = 7980;

  @Autowired PlatformTransactionManager transactions;
  @Autowired BookingStatistics statistics;
  @Autowired FacilityQueries facilityQueries;
  @Autowired CurrentActor currentActor;

  final AtomicInteger codes = new AtomicInteger();

  String slug;
  UUID municipality;
  UUID courtA;
  UUID pitchB;
  UUID wholeA;
  UUID partsA;
  UUID partA;
  UUID seatsA;
  UUID wholeB;
  UUID saturdaysB;

  @BeforeEach
  void municipality() {
    slug = uniqueSlug("ind");
    municipality = id(createMunicipality(slug));
    Facility a = openFacility("Pista do Alto");
    Facility b = openFacility("Campo da Gándara");
    courtA = a.id();
    pitchB = b.id();
    wholeA = whole(a, Map.of());
    JsonNode parts =
        use(
            a,
            "Media pista",
            "PARTS",
            Map.of("parts", List.of(Map.of("name", "Norte"), Map.of("name", "Sur"))));
    partsA = id(parts);
    partA = UUID.fromString(parts.get("parts").get(0).get("id").asString());
    seatsA = id(use(a, "Clase de pádel", "SEATS", Map.of("capacity", 30)));
    wholeB = whole(b, Map.of());
    saturdaysB = whole(b, "Sábados", Map.of("weekdays", 32));
    created(
        api()
            .post(
                facilities(slug) + "/" + courtA + "/schedule-exceptions",
                Map.of("date", "2027-12-14", "open", false, "reason", "Mantemento"))
            .token(TestTokens.manager(slug))
            .send());
    created(
        api()
            .post(
                facilities(slug) + "/" + courtA + "/blocks",
                Map.of("date", "2027-12-15", "startMinute", 600, "endMinute", 720))
            .token(TestTokens.manager(slug))
            .send());
  }

  @Test
  void openHoursFollowSeasonHolidaysExceptionsAndBlocksAndUsesWeighTheirShare() {
    confirmed(wholeA, local("2027-12-01T10:00"), 60);
    // Unha parte de dúas: media hora por hora. Unha praza de 30: 1/30.
    confirmed(partsA, local("2027-12-02T10:00"), 60);
    confirmed(seatsA, local("2027-12-03T10:00"), 60);
    insert(wholeA, local("2027-12-03T11:00"), 60, "sub-x", "CANCELLED_BY_PERSON", null);

    JsonNode panel = panel("period=MONTH&date=2027-12-10&facilityId=" + courtA);

    JsonNode kpis = panel.get("kpis");
    assertThat(kpis.get("bookings").get("value").asLong()).isEqualTo(3);
    assertThat(kpis.get("reservedHours").get("value").asDouble()).isEqualTo(92.0);
    assertThat(kpis.get("occupancy").get("value").get("reservedMinutes").asDouble())
        .isEqualTo(92.0);
    assertThat(kpis.get("occupancy").get("value").get("openMinutes").asLong())
        .isEqualTo(OPEN_A_IN_DECEMBER);
    assertThat(panel.get("occupancyByFacility")).hasSize(1);
    assertThat(panel.get("occupancyByFacility").get(0).get("facilityId").asString())
        .isEqualTo(courtA.toString());
    assertThat(panel.get("occupancyByFacility").get(0).get("name").asString())
        .isEqualTo("Pista do Alto");
    assertThat(panel.get("occupancyByFacility").get(0).get("openMinutes").asLong())
        .isEqualTo(OPEN_A_IN_DECEMBER);

    // Mapa de calor: o luns 6 é festivo, o martes 14 pecha e o mércores 15 non abre de 10 a 12.
    Map<String, JsonNode> cells = cells(panel);
    assertThat(cells.get("1-9").get("openMinutes").asLong()).isEqualTo(3 * 60);
    assertThat(cells.get("2-9").get("openMinutes").asLong()).isEqualTo(3 * 60);
    assertThat(cells.get("3-9").get("openMinutes").asLong()).isEqualTo(4 * 60);
    assertThat(cells.get("3-10").get("openMinutes").asLong()).isEqualTo(3 * 60);
    assertThat(cells.get("3-10").get("reservedMinutes").asDouble()).isEqualTo(60.0);
    assertThat(cells.get("4-10").get("reservedMinutes").asDouble()).isEqualTo(30.0);
    assertThat(cells.get("5-10").get("reservedMinutes").asDouble()).isEqualTo(2.0);
    assertThat(cells.get("1-8").get("openMinutes").asLong()).isZero();
    assertThat(panel.get("peakHours")).hasSize(7 * 24);

    // Por semanas, cortadas polo mes: do mércores 1 ao domingo 5, …, do luns 27 ao venres 31.
    JsonNode weeks = panel.get("dailyOccupancy");
    assertThat(weeks).hasSize(5);
    assertThat(weeks.get(0).get("from").asString()).isEqualTo("2027-12-01");
    assertThat(weeks.get(0).get("to").asString()).isEqualTo("2027-12-05");
    assertThat(weeks.get(0).get("openMinutes").asLong()).isEqualTo(5 * 300);
    assertThat(weeks.get(0).get("reservedMinutes").asDouble()).isEqualTo(92.0);
    assertThat(weeks.get(4).get("to").asString()).isEqualTo("2027-12-31");
    long weeksOpen = 0;
    for (JsonNode w : weeks) {
      weeksOpen += w.get("openMinutes").asLong();
    }
    assertThat(weeksOpen).isEqualTo(OPEN_A_IN_DECEMBER);
    assertThat(panel.get("cancellations")).hasSize(5);
    assertThat(panel.get("cancellations").get(0).get("inTime").asLong()).isEqualTo(1);
  }

  @Test
  void aUseOnlyOpensTheDaysItIsValid() {
    confirmed(saturdaysB, local("2027-12-04T10:00"), 60);
    confirmed(wholeB, local("2027-12-04T12:00"), 60);

    JsonNode panel = panel("period=MONTH&date=2027-12-01&useId=" + saturdaysB);

    // Sábados 4, 11 e 18; o 25 é Nadal, festivo e pechado.
    JsonNode occupancy = panel.get("kpis").get("occupancy").get("value");
    assertThat(occupancy.get("openMinutes").asLong()).isEqualTo(3 * 300);
    assertThat(occupancy.get("reservedMinutes").asDouble()).isEqualTo(60.0);
    assertThat(panel.get("kpis").get("bookings").get("value").asLong()).isEqualTo(1);
    assertThat(panel.get("occupancyByFacility")).hasSize(1);
    assertThat(panel.get("occupancyByFacility").get(0).get("facilityId").asString())
        .isEqualTo(pitchB.toString());
    assertThat(cells(panel).get("1-10").get("openMinutes").asLong()).isZero();
  }

  @Test
  void occupancyNeverPassesOneHundredPercent() {
    confirmed(wholeB, local("2027-12-01T09:00"), 240);
    confirmed(wholeB, local("2027-12-01T10:00"), 160);

    JsonNode panel = panel("period=CUSTOM&from=2027-12-01&to=2027-12-01&facilityId=" + pitchB);

    JsonNode occupancy = panel.get("kpis").get("occupancy").get("value");
    assertThat(occupancy.get("openMinutes").asLong()).isEqualTo(300);
    assertThat(occupancy.get("reservedMinutes").asDouble()).isEqualTo(300.0);
    assertThat(panel.get("kpis").get("reservedHours").get("value").asDouble()).isEqualTo(400.0);
    assertThat(panel.get("occupancyByFacility").get(0).get("reservedMinutes").asDouble())
        .isEqualTo(300.0);
    assertThat(panel.get("dailyOccupancy").get(0).get("reservedMinutes").asDouble())
        .isEqualTo(300.0);
    for (JsonNode cell : panel.get("peakHours")) {
      assertThat(cell.get("reservedMinutes").asDouble())
          .isLessThanOrEqualTo(cell.get("openMinutes").asDouble());
    }
  }

  @Test
  void previousAndEvolutionMatch() {
    confirmed(wholeA, local("2027-11-10T10:00"), 120);
    insert(wholeA, local("2027-12-01T10:00"), 60, "sub-a", "CONFIRMED", 800);
    confirmed(wholeB, local("2027-12-02T10:00"), 60, "sub-b");
    confirmed(wholeB, local("2027-12-03T10:00"), 60, "sub-a");

    JsonNode panel = panel("period=MONTH&date=2027-12-31");

    assertThat(panel.get("period").get("kind").asString()).isEqualTo("MONTH");
    assertThat(panel.get("period").get("from").asString()).isEqualTo("2027-12-01");
    assertThat(panel.get("period").get("evolutionUnit").asString()).isEqualTo("MONTH");
    assertThat(panel.get("period").get("breakdownUnit").asString()).isEqualTo("WEEK");
    assertThat(panel.get("previous").get("from").asString()).isEqualTo("2027-11-01");
    assertThat(panel.get("previous").get("to").asString()).isEqualTo("2027-11-30");
    JsonNode kpis = panel.get("kpis");
    for (String kpi : List.of("bookings", "occupancy", "reservedHours", "people", "paidCents")) {
      JsonNode evolution = kpis.get(kpi).get("evolution");
      assertThat(evolution).as(kpi).hasSize(12);
      assertThat(evolution.get(0).get("from").asString()).isEqualTo("2027-01-01");
      assertThat(evolution.get(11).get("to").asString()).isEqualTo("2027-12-31");
      assertThat(evolution.get(11).get("value")).as(kpi).isEqualTo(kpis.get(kpi).get("value"));
      assertThat(evolution.get(10).get("value")).as(kpi).isEqualTo(kpis.get(kpi).get("previous"));
    }
    // Decembro de 2027 aínda non empezou: compáranse os dous meses enteiros e nada está en curso.
    JsonNode comparison = panel.get("comparison");
    assertThat(comparison.get("toDate").asBoolean()).isFalse();
    assertThat(comparison.get("from").asString()).isEqualTo("2027-12-01");
    assertThat(comparison.get("to").asString()).isEqualTo("2027-12-31");
    assertThat(comparison.get("comparedFrom").asString()).isEqualTo("2027-11-01");
    assertThat(comparison.get("comparedTo").asString()).isEqualTo("2027-11-30");
    for (String kpi : List.of("bookings", "occupancy", "reservedHours", "people", "paidCents")) {
      assertThat(kpis.get(kpi).get("valueToDate")).as(kpi).isEqualTo(kpis.get(kpi).get("value"));
      assertThat(kpis.get(kpi).get("previousToDate"))
          .as(kpi)
          .isEqualTo(kpis.get(kpi).get("previous"));
    }
    for (String series : List.of("dailyOccupancy", "cancellations", "people")) {
      panel.get(series).forEach(b -> assertThat(b.get("partial").asBoolean()).as(series).isFalse());
    }
    assertThat(kpis.get("bookings").get("value").asLong()).isEqualTo(3);
    assertThat(kpis.get("bookings").get("previous").asLong()).isEqualTo(1);
    assertThat(kpis.get("people").get("value").asLong()).isEqualTo(2);
    assertThat(kpis.get("paidCents").get("value").asLong()).isEqualTo(800);
    assertThat(kpis.get("reservedHours").get("previous").asDouble()).isEqualTo(120.0);
    // Novembro: 30 días menos o 1, Todos os Santos, nas dúas.
    assertThat(kpis.get("occupancy").get("previous").get("openMinutes").asLong())
        .isEqualTo(2 * 29 * 300);
    assertThat(panel.get("revenue")).hasSize(1);
    assertThat(panel.get("revenue").get(0).get("name").asString()).isEqualTo("Pista do Alto");
    assertThat(panel.get("people")).hasSize(6);
    assertThat(panel.get("people").get(0).get("from").asString()).isEqualTo("2027-07-01");
    assertThat(panel.get("people").get(5).get("people").asLong()).isEqualTo(2);
    assertThat(panel.get("people").get(4).get("people").asLong()).isEqualTo(1);

    // Nun rango libre, o anterior son os mesmos días xusto antes, lidos á parte.
    JsonNode custom = panel("period=CUSTOM&from=2027-12-01&to=2027-12-10");
    assertThat(custom.get("previous").get("from").asString()).isEqualTo("2027-11-21");
    assertThat(custom.get("previous").get("to").asString()).isEqualTo("2027-11-30");
    assertThat(custom.get("kpis").get("bookings").get("value").asLong()).isEqualTo(3);
    assertThat(custom.get("kpis").get("bookings").get("previous").asLong()).isZero();
    assertThat(custom.get("kpis").get("bookings").get("evolution")).hasSize(12);
    assertThat(custom.get("dailyOccupancy")).hasSize(10);
  }

  @Test
  void everyPeriodAnswers() {
    confirmed(wholeA, local("2027-12-01T10:00"), 60);
    Map<String, Integer> evolution = Map.of("WEEK", 12, "MONTH", 12, "QUARTER", 8, "YEAR", 5);
    evolution.forEach(
        (kind, size) -> {
          JsonNode panel = panel("period=" + kind + "&date=2027-12-01");
          assertThat(panel.get("kpis").get("bookings").get("evolution")).as(kind).hasSize(size);
          assertThat(panel.get("kpis").get("bookings").get("value").asLong()).as(kind).isEqualTo(1);
        });
    JsonNode year = panel("period=YEAR&date=2027-06-01");
    assertThat(year.get("people")).hasSize(12);
    assertThat(year.get("dailyOccupancy")).hasSize(12);
    // Cinco anos de horas abertas (en anacos de 400 días): 2027 enteiro aberto agás os festivos.
    assertThat(
            year.get("kpis")
                .get("occupancy")
                .get("evolution")
                .get(4)
                .get("value")
                .get("openMinutes")
                .asLong())
        .isPositive();
    assertThat(panel("").get("period").get("kind").asString()).isEqualTo("MONTH");
  }

  @Test
  void invalidPeriodsAreA400() {
    for (String query :
        List.of(
            "period=CUSTOM&from=2027-01-01&to=2028-01-02",
            "period=CUSTOM&from=2027-12-10&to=2027-12-01",
            "period=CUSTOM&from=2027-12-10",
            "period=CUSTOM",
            "period=CUSTOM&from=1999-12-31&to=2000-01-01",
            "period=MONTH&date=0000-01-01",
            "period=YEAR&date=9999-12-31",
            "period=WEEK&date=" + LocalDate.now(ZONE).plusYears(2).plusDays(1))) {
      Api.Response response = get(query, TestTokens.manager(slug));
      assertThat(response.status()).as(query).isEqualTo(400);
      assertThat(code(response)).as(query).isEqualTo("indicators.period-invalid");
    }
    assertThat(
            get("period=CUSTOM&from=2027-01-01&to=2027-12-31", TestTokens.manager(slug)).status())
        .isEqualTo(200);
    assertThat(get("period=DAY", TestTokens.manager(slug)).status()).isEqualTo(400);
  }

  @Test
  void aFacilityOrUseFromAnotherMunicipalityLooksTheSameAsOneThatDoesNotExist() {
    String other = uniqueSlug("ind-other");
    UUID otherMunicipality = id(createMunicipality(other));
    Facility foreign = facility(other, otherMunicipality, "Pavillón alleo", true);
    UUID foreignUse = whole(foreign, Map.of());
    UUID missing = UUID.randomUUID();

    Api.Response foreignFacility = get("facilityId=" + foreign.id(), TestTokens.manager(slug));
    Api.Response missingFacility = get("facilityId=" + missing, TestTokens.manager(slug));
    Api.Response foreignUseResponse = get("useId=" + foreignUse, TestTokens.manager(slug));
    Api.Response missingUse = get("useId=" + missing, TestTokens.manager(slug));
    for (Api.Response response :
        List.of(foreignFacility, missingFacility, foreignUseResponse, missingUse)) {
      assertThat(response.status()).as(response.toString()).isEqualTo(400);
      assertThat(code(response)).isEqualTo("indicators.filter-invalid");
      assertThat(response.body())
          .doesNotContain(foreign.id().toString(), missing.toString(), foreignUse.toString());
    }
    assertThat(problem(foreignFacility)).isEqualTo(problem(missingFacility));
    assertThat(problem(foreignUseResponse)).isEqualTo(problem(missingUse));
    assertThat(problem(foreignFacility)).isEqualTo(problem(missingUse));

    // Un uso de B coa instalación A, usos repetidos e máis de 50: tamén 400.
    assertThat(code(get("facilityId=" + courtA + "&useId=" + wholeB, TestTokens.manager(slug))))
        .isEqualTo("indicators.filter-invalid");
    assertThat(code(get("useId=" + wholeA + "&useId=" + wholeA, TestTokens.manager(slug))))
        .isEqualTo("indicators.filter-invalid");
    String many =
        IntStream.range(0, 51)
            .mapToObj(i -> "useId=" + UUID.randomUUID())
            .collect(Collectors.joining("&"));
    try (StatementCounter.Recording recording = StatementCounter.record()) {
      Api.Response tooMany = get(many, TestTokens.manager(slug));
      assertThat(tooMany.status()).isEqualTo(400);
      assertThat(code(tooMany)).isEqualTo("indicators.filter-invalid");
      assertThat(recording.statements())
          .as("rexéitase antes de ler")
          .noneMatch(s -> s.contains("facility_use") || s.contains("booking"));
    }
    assertThat(
            get(
                    "facilityId=" + courtA + "&useId=" + wholeA + "&useId=" + partsA,
                    TestTokens.manager(slug))
                .status())
        .isEqualTo(200);
  }

  @Test
  void onlyTheManagerOfTheMunicipalitySeesIt() {
    String other = uniqueSlug("ind-b");
    createMunicipality(other);
    for (String path : List.of(base(slug), base(slug) + "/filters")) {
      assertThat(api().get(path).send().status()).isEqualTo(401);
      for (String token :
          List.of(
              TestTokens.staff(slug),
              TestTokens.resident(slug),
              TestTokens.platformAdmin(),
              TestTokens.manager(other))) {
        Api.Response response = api().get(path).token(token).send();
        assertThat(response.status()).as(path + " " + response).isEqualTo(403);
        assertThat(code(response)).isEqualTo("auth.forbidden");
      }
      assertThat(api().get(path).token(TestTokens.manager(slug)).send().status()).isEqualTo(200);
    }
    String unknown = uniqueSlug("ind-none");
    assertThat(api().get(base(unknown)).token(TestTokens.manager(unknown)).send().status())
        .isEqualTo(404);
  }

  /**
   * Un número fixo de sentenzas por petición (ADR-0026, §14), co tope de cada unidade: non crece
   * coas instalacións e un cambio que engada lecturas ten que subir o tope a conciencia.
   */
  @Test
  void readsAFixedNumberOfStatementsWhateverTheNumberOfFacilities() {
    confirmed(wholeA, local("2027-12-01T10:00"), 60);
    Map<String, Integer> caps = new LinkedHashMap<>();
    // 5 da petición (concello, administración da plataforma, ADR-0028, e persoal), 3 das
    // instalacións, 3 dos usos, 8 das horas abertas (unha lectura para os cinco anos) e as de
    // reservas: 6 no mes, 7 na semana, no trimestre e
    // no ano (as persoas mes a mes), 9 no rango libre (o anterior e as persoas do período).
    caps.put("period=MONTH&date=2027-12-01", 25);
    caps.put("period=WEEK&date=2027-12-01", 26);
    caps.put("period=QUARTER&date=2027-12-01", 26);
    caps.put("period=YEAR&date=2027-12-01", 26);
    caps.put("period=CUSTOM&from=2027-01-01&to=2027-12-31", 28);
    // No período en curso (o de hoxe), catro máis: as cifras e as persoas ata hoxe e do mesmo tramo
    // do anterior (ADR-0026, revisión do 2026-10-29).
    caps.put("period=MONTH", 29);
    caps.put("period=WEEK", 30);
    int added = 0;
    for (Map.Entry<String, Integer> cap : caps.entrySet()) {
      String query = cap.getKey();
      List<String> before = statements(query);
      for (int i = 0; i < 3; i++, added++) {
        Facility more = facility(slug, municipality, "Pavillón " + added, true);
        UUID use = whole(more, Map.of());
        confirmed(use, local("2027-12-" + (10 + added) + "T10:00"), 60);
      }
      List<String> after = statements(query);
      System.err.printf("IndicatorsIT %s: %d statements%n", query, after.size());
      assertThat(after).as(query).hasSameSizeAs(before).hasSizeLessThanOrEqualTo(cap.getValue());
    }
  }

  /** Quen mira o panel queda só en DEBUG (ADR-0026): nunca no rexistro INFO de cada día. */
  @Test
  void theReadIsTracedOnlyAtDebug() {
    Logger logger =
        (Logger)
            LoggerFactory.getLogger("gal.furar.indicators.application.service.IndicatorsService");
    Level level = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      panel("period=MONTH&date=2027-12-01");
      assertThat(appender.list).isEmpty();
      logger.setLevel(Level.DEBUG);
      panel("period=MONTH&date=2027-12-01");
      assertThat(appender.list)
          .singleElement()
          .satisfies(e -> assertThat(e.getLevel()).isEqualTo(Level.DEBUG))
          .satisfies(e -> assertThat(e.getFormattedMessage()).contains("indicators read"));
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(level);
    }
  }

  @Test
  void filtersListWithdrawnFacilitiesAndArchivedUsesOnlyWithBookings() {
    Facility gone = facility(slug, municipality, "Pavillón vello", true);
    UUID goneUse = whole(gone, Map.of());
    Facility empty = facility(slug, municipality, "Frontón baleiro", true);
    whole(empty, Map.of());
    confirmed(goneUse, local("2027-12-01T10:00"), 60);
    jdbc.sql("update facility set status = 'WITHDRAWN' where id in (:a, :b)")
        .param("a", gone.id())
        .param("b", empty.id())
        .update();
    Api.Response archived =
        api()
            .post(facilities(slug) + "/" + courtA + "/uses/" + seatsA + "/archive", Map.of())
            .ifMatch(0)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(archived.status()).as(archived.toString()).isEqualTo(200);

    JsonNode filters =
        api().get(base(slug) + "/filters").token(TestTokens.manager(slug)).send().json();

    List<String> names = new ArrayList<>();
    filters.get("facilities").forEach(f -> names.add(f.get("name").asString()));
    assertThat(names).containsExactly("Campo da Gándara", "Pavillón vello", "Pista do Alto");
    assertThat(filters.get("facilities").get(1).get("status").asString()).isEqualTo("WITHDRAWN");
    List<String> uses = new ArrayList<>();
    filters.get("uses").forEach(u -> uses.add(u.get("id").asString()));
    assertThat(uses)
        .contains(wholeA.toString(), partsA.toString(), saturdaysB.toString(), goneUse.toString())
        .doesNotContain(seatsA.toString());

    confirmed(seatsA, local("2027-12-01T12:00"), 60);
    JsonNode again =
        api().get(base(slug) + "/filters").token(TestTokens.manager(slug)).send().json();
    JsonNode seats = null;
    for (JsonNode u : again.get("uses")) {
      if (u.get("id").asString().equals(seatsA.toString())) {
        seats = u;
      }
    }
    assertThat(seats).isNotNull();
    assertThat(seats.get("archived").asBoolean()).isTrue();
  }

  /**
   * O período en curso (ADR-0026, revisión do 2026-10-29), co SQL de verdade e o reloxo parado o 15
   * de decembro de 2027: a variación compara do 1 ao 15 co 1 ao 15 de novembro, o valor segue sendo
   * o mes enteiro coas reservas xa feitas e só o último cubo de cada serie está en curso. O servizo
   * constrúese aquí co reloxo parado (sen proxy: a transacción e o concello póñense á man).
   */
  @Test
  void theMonthInProgressComparesUpToTodayWithTheSameDaysOfThePreviousMonth()
      throws ReflectiveOperationException {
    confirmed(wholeA, local("2027-12-01T10:00"), 60, "sub-a");
    confirmed(wholeA, local("2027-12-15T12:00"), 60, "sub-b");
    confirmed(wholeA, local("2027-12-20T10:00"), 120, "sub-c");
    confirmed(wholeA, local("2027-11-10T10:00"), 60, "sub-a");
    confirmed(wholeA, local("2027-11-20T10:00"), 60, "sub-d");
    insert(wholeA, local("2027-12-02T10:00"), 60, "sub-e", "CONFIRMED", 500);
    insert(wholeA, local("2027-12-21T10:00"), 60, "sub-e", "CONFIRMED", 700);

    Class<?> type = Class.forName("gal.furar.indicators.application.service.IndicatorsService");
    Constructor<?> constructor =
        type.getDeclaredConstructor(
            BookingStatistics.class, FacilityQueries.class, CurrentActor.class, Clock.class);
    constructor.setAccessible(true);
    ReadIndicatorsUseCase service =
        (ReadIndicatorsUseCase)
            constructor.newInstance(
                statistics,
                facilityQueries,
                currentActor,
                Clock.fixed(Instant.parse("2027-12-15T11:00:00Z"), ZoneOffset.UTC));
    TransactionTemplate template = new TransactionTemplate(transactions);
    template.setReadOnly(true);
    IndicatorsQuery month =
        new IndicatorsQuery(PeriodKind.MONTH, null, null, null, null, List.of());
    IndicatorsPanel panel;
    List<String> statements;
    try (StatementCounter.Recording recording = StatementCounter.record()) {
      panel =
          TenantContext.runAs(
              TenantRef.of(municipality), () -> template.execute(s -> service.read(month)));
      statements = recording.statements();
    }

    assertThat(panel.comparison())
        .isEqualTo(
            new Comparison(
                LocalDate.parse("2027-12-01"),
                LocalDate.parse("2027-12-15"),
                LocalDate.parse("2027-11-01"),
                LocalDate.parse("2027-11-15"),
                true));
    IndicatorsPanel.Kpis kpis = panel.kpis();
    assertThat(kpis.bookings().value()).isEqualTo(5);
    assertThat(kpis.bookings().previous()).isEqualTo(2);
    assertThat(kpis.bookings().valueToDate()).isEqualTo(3);
    assertThat(kpis.bookings().previousToDate()).isEqualTo(1);
    assertThat(kpis.reservedMinutes().valueToDate()).isEqualTo(180.0);
    assertThat(kpis.paidCents().value()).isEqualTo(1200);
    assertThat(kpis.paidCents().valueToDate()).isEqualTo(500);
    assertThat(kpis.paidCents().previousToDate()).isZero();
    assertThat(kpis.people().value()).isEqualTo(4);
    assertThat(kpis.people().valueToDate()).isEqualTo(3);
    assertThat(kpis.people().previousToDate()).isEqualTo(1);
    // Horas abertas ata hoxe: A, 15 días menos os festivos 6 e 8, a excepción do 14 e o bloqueo
    // do 15 (10–12); B, 15 días menos os dous festivos. Novembro do 1 ao 15: o 1 é festivo.
    assertThat(kpis.occupancy().valueToDate().openMinutes())
        .isEqualTo((15 - 3) * 300L - 120 + (15 - 2) * 300L);
    assertThat(kpis.occupancy().previousToDate().openMinutes()).isEqualTo(2 * 14 * 300L);
    assertThat(kpis.bookings().evolution().getLast().partial()).isTrue();
    assertThat(kpis.bookings().evolution().subList(0, 11))
        .noneMatch(IndicatorsPanel.Point::partial);
    assertThat(panel.dailyOccupancy())
        .filteredOn(IndicatorsPanel.BucketOccupancy::partial)
        .singleElement()
        .satisfies(b -> assertThat(b.from()).isEqualTo(LocalDate.parse("2027-12-13")));
    assertThat(panel.people().getLast().partial()).isTrue();
    // As catro lecturas de máis do período en curso (o tope do mes pechado é 23).
    assertThat(statements).hasSizeLessThanOrEqualTo(27);
  }

  /**
   * O tope de tempo: o caso de uso declara {@code timeout} e, coa mesma transacción de Spring, unha
   * sentenza que se pasa cancélaa PostgreSQL (non queda colgada).
   */
  @Test
  void aSlowQueryIsCancelled() throws ReflectiveOperationException {
    Method read =
        Class.forName("gal.furar.indicators.application.service.IndicatorsService")
            .getMethod("read", IndicatorsQuery.class);
    Transactional transactional = read.getAnnotation(Transactional.class);
    assertThat(transactional.readOnly()).isTrue();
    assertThat(transactional.timeout()).isEqualTo(10);

    TransactionTemplate template = new TransactionTemplate(transactions);
    template.setReadOnly(true);
    template.setTimeout(1);
    long started = System.nanoTime();
    assertThatThrownBy(
            () ->
                TenantContext.runAs(
                    TenantRef.of(municipality),
                    () ->
                        template.execute(
                            s -> appJdbc.sql("select pg_sleep(5)").query().singleValue())))
        .isInstanceOf(DataAccessException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
  }

  // --- Axudas ---

  static String base(String slug) {
    return "/api/v1/municipalities/" + slug + "/indicators";
  }

  Api.Response get(String query, String token) {
    return api().get(base(slug) + (query.isEmpty() ? "" : "?" + query)).token(token).send();
  }

  JsonNode panel(String query) {
    Api.Response response = get(query, TestTokens.manager(slug));
    assertThat(response.status()).as(query + " → " + response).isEqualTo(200);
    return response.json();
  }

  List<String> statements(String query) {
    try (StatementCounter.Recording recording = StatementCounter.record()) {
      panel(query);
      return recording.statements();
    }
  }

  static Map<String, JsonNode> cells(JsonNode panel) {
    Map<String, JsonNode> cells = new LinkedHashMap<>();
    panel
        .get("peakHours")
        .forEach(c -> cells.put(c.get("isoWeekday").asInt() + "-" + c.get("hour").asInt(), c));
    return cells;
  }

  /** O problema sen o que cambia en cada petición. */
  static Map<String, String> problem(Api.Response response) {
    JsonNode json = response.json();
    return Map.of(
        "status", String.valueOf(response.status()),
        "code", json.get("code").asString(),
        "title", json.get("title").asString(),
        "detail", json.get("detail").asString());
  }

  static void created(Api.Response response) {
    assertThat(response.status()).as(response.toString()).isEqualTo(201);
  }

  /** Instalación publicada, aberta de 9:00 a 14:00 todos os días de 2026 a 2030 agás festivos. */
  Facility openFacility(String name) {
    Facility created = facility(slug, municipality, name, true);
    UUID facility = created.id();
    List<Map<String, Object>> week = new ArrayList<>();
    for (String day : DAYS) {
      week.add(
          Map.of(
              "day",
              day,
              "intervals",
              day.equals("HOLIDAYS")
                  ? List.of()
                  : List.of(Map.of("startMinute", OPENS, "endMinute", CLOSES))));
    }
    JsonNode seasons =
        api()
            .get(facilities(slug) + "/" + facility + "/schedule")
            .token(TestTokens.manager(slug))
            .send()
            .json()
            .get("seasons");
    String seasonId = seasons.get(0).get("id").asString();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Todo o ano");
    body.put("startDate", "2026-01-01");
    body.put("endDate", "2030-12-31");
    body.put("week", week);
    Api.Response saved =
        api()
            .put(facilities(slug) + "/" + facility + "/seasons/" + seasonId, body)
            .ifMatch(1)
            .token(TestTokens.manager(slug))
            .send();
    assertThat(saved.status()).as(saved.toString()).isEqualTo(200);
    return created;
  }

  static Instant local(String at) {
    return LocalDateTime.parse(at).atZone(ZONE).toInstant();
  }

  void confirmed(UUID use, Instant start, int minutes) {
    confirmed(use, start, minutes, "sub-" + UUID.randomUUID());
  }

  void confirmed(UUID use, Instant start, int minutes, String subject) {
    insert(use, start, minutes, subject, "CONFIRMED", null);
  }

  /** Unha reserva por JDBC (pode ser pasada), co modo e a parte do seu uso. */
  void insert(
      UUID use,
      Instant start,
      int minutes,
      String subject,
      String status,
      @Nullable Integer cents) {
    Map<String, Object> row =
        jdbc.sql("select facility_id, rental_mode from facility_use where id = :u")
            .param("u", use)
            .query()
            .singleRow();
    String mode = (String) row.get("rental_mode");
    Instant cancelUntil = start.minus(Duration.ofHours(24));
    boolean cancelled = !status.equals("CONFIRMED");
    jdbc.sql(
            """
            insert into booking
              (id, municipality_id, code, facility_id, use_id, part_id, rental_mode, period,
               slots, status, payment_status, price_kind, amount_cents, cancel_until,
               holder_subject, holder_name, holder_email, holder_language, cancelled_at,
               cancelled_by, created_at, updated_at, version)
            values (:id, :m, :code, :f, :u, :part, :mode, tstzrange(:start, :end, '[)'), 1,
                    :status, :payment, :priceKind, :cents, :cancelUntil, :subject,
                    'Persoa de proba', 'proba@example.org', 'gl', :cancelledAt, :cancelledBy,
                    now(), now(), 0)
            """)
        .param("id", UUID.randomUUID())
        .param("m", municipality)
        .param("code", code())
        .param("f", row.get("facility_id"))
        .param("u", use)
        .param("part", mode.equals("PARTS") ? partA : null, java.sql.Types.OTHER)
        .param("mode", mode)
        .param("start", utc(start))
        .param("end", utc(start.plus(Duration.ofMinutes(minutes))))
        .param("status", status)
        .param("payment", cents == null ? "NONE" : "PAID")
        .param("priceKind", cents == null ? "FREE" : "PAID")
        .param("cents", cents, java.sql.Types.INTEGER)
        .param("cancelUntil", utc(cancelUntil))
        .param("subject", subject)
        .param(
            "cancelledAt",
            cancelled ? utc(cancelUntil.minus(Duration.ofDays(1))) : null,
            java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .param("cancelledBy", cancelled ? "sub-desk" : null, java.sql.Types.VARCHAR)
        .update();
  }

  /** Un código distinto por reserva: {@code FUR-} e catro letras do alfabeto do código. */
  String code() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    int n = codes.getAndIncrement();
    StringBuilder code = new StringBuilder("FUR-");
    for (int i = 0; i < 4; i++) {
      code.append(alphabet.charAt(n % alphabet.length()));
      n /= alphabet.length();
    }
    return code.toString();
  }

  static OffsetDateTime utc(Instant at) {
    return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
  }
}
