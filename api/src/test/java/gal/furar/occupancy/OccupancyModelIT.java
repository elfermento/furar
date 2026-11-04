// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.occupancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gal.furar.shared.domain.Ids;
import gal.furar.testsupport.PostgresContainers;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * D19 (ADR-0012 revisada, ADR-0022, ADR-0025): o modelo SQL de ocupación (instalación + uso + parte
 * ou prazas, e unha praza por persoa) sobre as migracións de produción ({@code db/migration}), con
 * concorrencia real en PostgreSQL 18.6 e un pool de 20 conexións. Só SQL e JDBC, nunha base propia
 * migrada por Flyway como en produción: o dono ({@code furar_owner}) migra e a proba conecta como a
 * API.
 *
 * <p>O que garante a base, declarativamente (plan do dominio 1, §1.2.5): <b>un uso por instalación
 * e franxa</b> (exclusión {@code facility_id =, use_id <>, period &&}) e <b>o cupo</b> dentro do
 * uso (enteira: exclusión por uso; partes: exclusión por parte; prazas: contador {@code use_slot}
 * con {@code CHECK (taken <= capacity)}). Bloqueos, peche, horario e «bloquea os demais» non van no
 * modelo: compróbaos {@code booking} baixo o bloqueo de fila da instalación.
 *
 * <p>Como fai booking (ADR-0025, punto 5; ADR-0006): cada reserva toma primeiro {@code select …
 * from facility … for no key update} ({@code FacilityQueries.lockFacility}) e despois escribe a
 * reserva e a ocupación. O pool conecta como {@code furar_app}, sen BYPASSRLS nin propiedade das
 * táboas, e cada transacción fixa {@code furar.municipality_id} con {@code set_config(..., true)}:
 * Row Level Security e as FK compostas actúan durante toda a proba.
 *
 * <p>Repeticións: {@code -Dfurar.occupancy.reps=N}; por defecto 20 en local e 3 na CI (variable
 * {@code CI}). Achado que se mantén de 2026-10-22: só coas exclusións, insercións simultáneas no
 * mesmo oco poden interbloquearse ({@code 40P01}) aínda que nunca hai dúas reservas; o bloqueo da
 * instalación ordena a cola e a exclusión segue sendo a garantía.
 */
class OccupancyModelIT {

  static final int POOL = 20;
  static final String OK = "OK";
  static final String CHECK_VIOLATION = "23514";
  static final String EXCLUSION_VIOLATION = "23P01";
  static final String DEADLOCK = "40P01";
  static final String RLS_VIOLATION = "42501";
  static final String FK_VIOLATION = "23503";
  static final String ONE_USE = EXCLUSION_VIOLATION + ":booking_occupancy_one_use";
  static final String WHOLE_TAKEN = EXCLUSION_VIOLATION + ":booking_occupancy_whole";
  static final String PART_TAKEN = EXCLUSION_VIOLATION + ":booking_occupancy_part";
  static final String NO_SEATS = CHECK_VIOLATION + ":use_slot_taken_ck";
  static final String SEAT_TAKEN_BY_YOU = EXCLUSION_VIOLATION + ":booking_one_seat_per_person";
  static final Duration HOUR = Duration.ofMinutes(60);
  static final UUID MUNICIPALITY = UUID.fromString("019a0b6e-0000-7000-8000-00000000c0de");
  static final UUID OTHER_MUNICIPALITY = UUID.fromString("019a0b6e-0000-7000-8000-00000000beef");

  static HikariDataSource dataSource;
  static ExecutorService pool;

  /** Códigos de reserva distintos en toda a proba (o código é único no concello). */
  static final AtomicInteger CODES = new AtomicInteger();

  /** O que reserva unha petición: instalación, uso, modo do uso e parte (só en PARTS). */
  record Target(UUID facility, UUID use, String mode, UUID part, Integer capacity) {
    Target withPart(UUID otherPart) {
      return new Target(facility, use, mode, otherPart, capacity);
    }
  }

  @BeforeAll
  static void setUp() throws SQLException {
    PostgreSQLContainer pg = PostgresContainers.shared();
    try (Connection c =
            java.sql.DriverManager.getConnection(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        Statement s = c.createStatement()) {
      s.execute("drop database if exists occupancy_model");
      s.execute("create database occupancy_model owner " + PostgresContainers.OWNER_USER);
    }
    String url = pg.getJdbcUrl().replaceFirst("/furar(\\?|$)", "/occupancy_model$1");
    // Os mesmos permisos ca na base da API (deploy/postgres/init/05-furar.sh), nesta base.
    try (Connection c =
            java.sql.DriverManager.getConnection(url, pg.getUsername(), pg.getPassword());
        Statement s = c.createStatement()) {
      s.execute("revoke all on database occupancy_model from public");
      s.execute("grant connect on database occupancy_model to " + PostgresContainers.APP_USER);
      s.execute("revoke all on schema public from public");
      s.execute("grant usage on schema public to " + PostgresContainers.APP_USER);
      s.execute(
          "alter default privileges for role "
              + PostgresContainers.OWNER_USER
              + " in schema public grant select, insert, update, delete on tables to "
              + PostgresContainers.APP_USER);
    }
    // As migracións de produción, como dono; a proba, como a API (furar_app): RLS aplícase.
    Flyway.configure()
        .dataSource(url, PostgresContainers.OWNER_USER, PostgresContainers.OWNER_PASSWORD)
        .locations("classpath:db/migration")
        .placeholders(PostgresContainers.flywayPlaceholders())
        .load()
        .migrate();
    try (Connection c =
            java.sql.DriverManager.getConnection(
                url, PostgresContainers.OWNER_USER, PostgresContainers.OWNER_PASSWORD);
        PreparedStatement insert =
            c.prepareStatement(
                "insert into municipality (id, slug, name, languages, default_language, theme,"
                    + " created_at, updated_at, version)"
                    + " values (?, ?, ?, array['gl', 'es'], 'gl', '{}'::jsonb, now(), now(), 0)")) {
      for (UUID municipality : List.of(MUNICIPALITY, OTHER_MUNICIPALITY)) {
        insert.setObject(1, municipality);
        insert.setString(2, "concello-" + municipality.toString().substring(32));
        insert.setString(3, "Concello " + municipality);
        insert.executeUpdate();
      }
    }
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(url);
    config.setUsername(PostgresContainers.APP_USER);
    config.setPassword(PostgresContainers.APP_PASSWORD);
    config.setMaximumPoolSize(POOL);
    config.setMinimumIdle(POOL);
    config.setAutoCommit(false);
    config.setConnectionTimeout(10_000);
    dataSource = new HikariDataSource(config);
    pool = Executors.newFixedThreadPool(50);
  }

  @AfterAll
  static void tearDown() {
    pool.shutdownNow();
    dataSource.close();
  }

  static Stream<Integer> repetitions() {
    int fallback = System.getenv("CI") == null ? 20 : 3;
    return IntStream.rangeClosed(1, Integer.getInteger("furar.occupancy.reps", fallback)).boxed();
  }

  /**
   * {@code uuid WITH <>} funciona con btree_gist en PG 18: a exclusión existe tal como se pediu.
   */
  @Test
  void btreeGistSupportsUuidNotEqualInTheOneUseExclusion() throws SQLException {
    try (Connection c = dataSource.getConnection();
        PreparedStatement q =
            c.prepareStatement(
                "select contype, pg_get_constraintdef(oid) from pg_constraint"
                    + " where conname = 'booking_occupancy_one_use'");
        ResultSet rs = q.executeQuery()) {
      assertThat(rs.next()).isTrue();
      assertThat(rs.getString(1)).isEqualTo("x");
      assertThat(rs.getString(2))
          .contains("facility_id WITH =", "use_id WITH <>", "period WITH &&", "WHERE (active)");
      c.rollback();
    }
  }

  /**
   * Dous usos distintos da mesma instalación na mesma franxa, 20 peticións á vez (10 por uso) → 1
   * éxito; as do outro uso chocan coa exclusión por instalación e as do mesmo, coa de enteira.
   */
  @ParameterizedTest(name = "repetición {0}")
  @MethodSource("repetitions")
  void twoDifferentUsesNeverShareASlot(int repetition) throws Exception {
    UUID facility = newFacility();
    Target football11 = newUse(facility, "WHOLE", null);
    Target football7 = newUse(facility, "WHOLE", null);
    Instant start = slot(0);
    List<String> results =
        concurrently(
            20, i -> book(i % 2 == 0 ? football11 : football7, List.of(start), HOUR, true));
    assertThat(results.stream().filter(OK::equals).count()).as(results.toString()).isEqualTo(1);
    assertThat(results.stream().filter(ONE_USE::equals).count())
        .as(results.toString())
        .isEqualTo(10);
    assertThat(results.stream().filter(WHOLE_TAKEN::equals).count())
        .as(results.toString())
        .isEqualTo(9);
    assertThat(activeInFacility(facility)).isEqualTo(1);
  }

  /** Enteira: 20 peticións solapadas do mesmo uso (en punto e ás :15) → 1 éxito e 19 rexeitadas. */
  @ParameterizedTest(name = "repetición {0}")
  @MethodSource("repetitions")
  void wholeAllowsOnlyOne(int repetition) throws Exception {
    Target court = newUse(newFacility(), "WHOLE", null);
    Instant start = slot(0);
    List<String> results =
        concurrently(
            20,
            i ->
                book(
                    court,
                    List.of(start.plus(i % 2 == 0 ? 0 : 15, ChronoUnit.MINUTES)),
                    HOUR,
                    true));
    assertThat(results.stream().filter(OK::equals).count()).as(results.toString()).isEqualTo(1);
    assertThat(results.stream().filter(WHOLE_TAKEN::equals).count())
        .as(results.toString())
        .isEqualTo(19);
    assertThat(activeInUse(court.use())).isEqualTo(1);
  }

  /**
   * Partes: uso con 2 partes e 20 peticións á vez (10 por parte) → 2 éxitos, un por parte; despois,
   * unha terceira reserva nunha parte collida falla e outro uso da instalación tampouco entra.
   */
  @ParameterizedTest(name = "repetición {0}")
  @MethodSource("repetitions")
  void partsAllowOnePerPart(int repetition) throws Exception {
    UUID facility = newFacility();
    Target football7 = newUse(facility, "PARTS", null);
    UUID fieldA = newPart(football7.use(), "Campo A", 1);
    UUID fieldB = newPart(football7.use(), "Campo B", 2);
    Instant start = slot(0);
    List<String> results =
        concurrently(
            20,
            i ->
                book(football7.withPart(i % 2 == 0 ? fieldA : fieldB), List.of(start), HOUR, true));
    assertThat(results.stream().filter(OK::equals).count()).as(results.toString()).isEqualTo(2);
    assertThat(results.stream().filter(PART_TAKEN::equals).count())
        .as(results.toString())
        .isEqualTo(18);
    assertThat(activeInUse(football7.use())).isEqualTo(2);

    assertThat(book(football7.withPart(fieldA), List.of(start), HOUR, true)).isEqualTo(PART_TAKEN);
    Target football11 = newUse(facility, "WHOLE", null);
    assertThat(book(football11, List.of(start), HOUR, true)).isEqualTo(ONE_USE);
  }

  /**
   * Prazas: uso de 30 prazas e 50 peticións de 1 praza á vez → exactamente 30 éxitos. Sen o bloqueo
   * da instalación, para que a garantía probada sexa a da base (fila {@code use_slot} + CHECK).
   */
  @ParameterizedTest(name = "repetición {0}")
  @MethodSource("repetitions")
  void seatsNeverExceedCapacity(int repetition) throws Exception {
    Target gym = newUse(newFacility(), "SEATS", 30);
    Instant start = slot(0);
    List<String> results = concurrently(50, i -> book(gym, List.of(start), HOUR, false));
    assertThat(results.stream().filter(OK::equals).count()).as(results.toString()).isEqualTo(30);
    assertThat(results.stream().filter(NO_SEATS::equals).count())
        .as(results.toString())
        .isEqualTo(20);
    assertThat(taken(gym.use(), start)).isEqualTo(30);
    assertThat(activeInUse(gym.use())).isEqualTo(30);
  }

  /** A segunda franxa está chea e alguén pide as dúas → todo ou nada (a primeira segue libre). */
  @Test
  void multiSlotSeatsAreAllOrNothing() throws Exception {
    Target swimming = newUse(newFacility(), "SEATS", 2);
    Instant first = slot(0);
    Instant second = slot(1);
    assertThat(book(swimming, List.of(second), HOUR, true)).isEqualTo(OK);
    assertThat(book(swimming, List.of(second), HOUR, true)).isEqualTo(OK);
    assertThat(book(swimming, List.of(first, second), HOUR, true)).isEqualTo(NO_SEATS);
    assertThat(taken(swimming.use(), first)).isZero();
    assertThat(taken(swimming.use(), second)).isEqualTo(2);
    assertThat(book(swimming, List.of(first), HOUR, true)).isEqualTo(OK);
    assertThat(taken(swimming.use(), first)).isEqualTo(1);
  }

  /**
   * Unha persoa = unha praza (ADR-0012, 2026-10-26): 20 peticións á vez da mesma persoa á mesma
   * franxa dun uso por prazas → 1. Outra persoa, a franxa seguinte, outra parte e unha reserva
   * cancelada si entran; unha franxa que se pisa, non.
   */
  @ParameterizedTest(name = "repetición {0}")
  @MethodSource("repetitions")
  void samePersonNeverHoldsTwoSeats(int repetition) throws Exception {
    UUID facility = newFacility();
    Target gym = newUse(facility, "SEATS", 30);
    String ana = "ana-" + Ids.newV7();
    Instant start = slot(0);
    List<String> results = concurrently(20, i -> book(gym, gym, List.of(start), HOUR, ana, true));
    assertThat(results.stream().filter(OK::equals).count()).as(results.toString()).isEqualTo(1);
    assertThat(results.stream().filter(SEAT_TAKEN_BY_YOU::equals).count())
        .as(results.toString())
        .isEqualTo(19);
    assertThat(taken(gym.use(), start)).isEqualTo(1);

    String ben = "ben-" + Ids.newV7();
    assertThat(book(gym, gym, List.of(start), HOUR, ben, true)).isEqualTo(OK);
    assertThat(book(gym, gym, List.of(slot(1)), HOUR, ana, true)).isEqualTo(OK);
    Instant overlapping = start.plus(30, ChronoUnit.MINUTES);
    assertThat(book(gym, gym, List.of(overlapping), HOUR, ana, true)).isEqualTo(SEAT_TAKEN_BY_YOU);

    // Cancelada (non CONFIRMED): xa non lle conta.
    assertThat(cancelBookingsOf(ana, gym.use(), start)).isEqualTo(1);
    assertThat(book(gym, gym, List.of(start), HOUR, ana, true)).isEqualTo(OK);

    // Só nas prazas: a mesma persoa pode ter dúas partes á mesma hora (P6).
    Target football7 = newUse(newFacility(), "PARTS", null);
    UUID fieldA = newPart(football7.use(), "Campo A", 1);
    UUID fieldB = newPart(football7.use(), "Campo B", 2);
    assertThat(
            book(
                football7.withPart(fieldA),
                football7.withPart(fieldA),
                List.of(start),
                HOUR,
                ana,
                true))
        .isEqualTo(OK);
    assertThat(
            book(
                football7.withPart(fieldB),
                football7.withPart(fieldB),
                List.of(start),
                HOUR,
                ana,
                true))
        .isEqualTo(OK);
  }

  /**
   * Multifranxa en orde inversa, á vez, sen interbloqueo (≤ 5 s): prazas só coa orde de {@code
   * use_slot} (sen bloqueo da instalación) e partes co fluxo de booking (bloqueo da instalación).
   */
  @ParameterizedTest(name = "repetición {0}")
  @MethodSource("repetitions")
  void noDeadlockWithReversedSlots(int repetition) throws Exception {
    UUID facility = newFacility();
    Target lanes = newUse(facility, "SEATS", 30);
    List<Instant> forward = List.of(slot(0), slot(1), slot(2));
    List<Instant> backward = List.of(slot(2), slot(1), slot(0));
    long started = System.nanoTime();
    List<String> seats =
        concurrently(20, i -> book(lanes, i % 2 == 0 ? forward : backward, HOUR, false));
    assertThat(seats).as(seats.toString()).allMatch(OK::equals);
    for (Instant s : forward) {
      assertThat(taken(lanes.use(), s)).isEqualTo(20);
    }

    Target athletics = newUse(newFacility(), "PARTS", null);
    UUID track = newPart(athletics.use(), "Pista", 1);
    newPart(athletics.use(), "Lonxitude", 2);
    List<String> parts =
        concurrently(
            20, i -> book(athletics.withPart(track), i % 2 == 0 ? forward : backward, HOUR, true));
    long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    assertThat(parts).doesNotContain(DEADLOCK);
    assertThat(parts.stream().filter(OK::equals).count()).as(parts.toString()).isEqualTo(1);
    assertThat(parts.stream().filter(PART_TAKEN::equals).count()).isEqualTo(19);
    assertThat(millis).isLessThan(5_000);
  }

  /**
   * Sen o bloqueo da instalación, a base segue sen permitir dúas reservas incompatibles; só cambia
   * que algunhas peticións se interbloquean ({@code 40P01}) en vez de recibir {@code 23P01}.
   */
  @RepeatedTest(3)
  void withoutTheFacilityLockTheBaseStillNeverDoubleBooks() throws Exception {
    UUID facility = newFacility();
    Target football11 = newUse(facility, "WHOLE", null);
    Target football7 = newUse(facility, "WHOLE", null);
    Instant start = slot(5);
    List<String> results =
        concurrently(
            10, i -> book(i % 2 == 0 ? football11 : football7, List.of(start), HOUR, false));
    assertThat(results.stream().filter(OK::equals).count()).as(results.toString()).isEqualTo(1);
    assertThat(results)
        .allMatch(r -> r.equals(OK) || r.startsWith(EXCLUSION_VIOLATION) || r.equals(DEADLOCK));
    assertThat(activeInFacility(facility)).isEqualTo(1);
  }

  /**
   * Franxas contiguas ({@code [)}) de usos distintos conviven; cancelar ({@code active = false})
   * libera a franxa para outro uso.
   */
  @Test
  void consecutiveSlotsAndCancelledOccupanciesFreeTheFacility() throws Exception {
    UUID facility = newFacility();
    Target football11 = newUse(facility, "WHOLE", null);
    Target gym = newUse(facility, "SEATS", 20);
    assertThat(book(football11, List.of(slot(0)), HOUR, true)).isEqualTo(OK);
    assertThat(book(gym, List.of(slot(1)), HOUR, true)).isEqualTo(OK);
    assertThat(book(gym, List.of(slot(0)), HOUR, true)).isEqualTo(ONE_USE);
    String cancel =
        inTransaction(
            c -> {
              try (PreparedStatement u =
                  c.prepareStatement(
                      "update booking_occupancy set active = false where use_id = ?"
                          + " and municipality_id = ?")) {
                u.setObject(1, football11.use());
                u.setObject(2, MUNICIPALITY);
                assertThat(u.executeUpdate()).isEqualTo(1);
              }
            });
    assertThat(cancel).isEqualTo(OK);
    assertThat(book(gym, List.of(slot(0)), HOUR, true)).isEqualTo(OK);
  }

  /**
   * Unha reserva ou unha ocupación co uso doutra instalación → FK composta (instalación, uso),
   * {@code 23503}.
   */
  @Test
  void occupancyWithTheUseOfAnotherFacilityIsRejected() {
    Target naronPadel = newUse(newFacility(), "WHOLE", null);
    UUID otherFacility = newFacility();
    Target forged =
        new Target(otherFacility, naronPadel.use(), naronPadel.mode(), null, naronPadel.capacity());
    assertThat(book(naronPadel, forged, List.of(slot(0)), HOUR, newHolder(), true))
        .isEqualTo(FK_VIOLATION + ":booking_occupancy_use_fk");
    assertThat(book(forged, List.of(slot(0)), HOUR, true))
        .isEqualTo(FK_VIOLATION + ":booking_use_fk");
  }

  /**
   * O modo da ocupación e da reserva ten que ser o do uso ({@code 23503}): enteira sobre un uso por
   * partes, parte doutro uso e contador de prazas nun uso que non é por prazas.
   */
  @Test
  void occupancyWhoseModeDoesNotMatchTheUseIsRejected() {
    UUID facility = newFacility();
    Target football7 = newUse(facility, "PARTS", null);
    UUID fieldA = newPart(football7.use(), "Campo A", 1);
    newPart(football7.use(), "Campo B", 2);
    Target asWhole = new Target(facility, football7.use(), "WHOLE", null, null);
    assertThat(book(football7.withPart(fieldA), asWhole, List.of(slot(0)), HOUR, newHolder(), true))
        .isEqualTo(FK_VIOLATION + ":booking_occupancy_mode_fk");
    assertThat(book(asWhole, List.of(slot(0)), HOUR, true))
        .isEqualTo(FK_VIOLATION + ":booking_mode_fk");

    Target otherParts = newUse(facility, "PARTS", null);
    UUID fieldC = newPart(otherParts.use(), "Campo C", 1);
    assertThat(
            book(
                otherParts.withPart(fieldC),
                otherParts.withPart(fieldA),
                List.of(slot(0)),
                HOUR,
                newHolder(),
                true))
        .isEqualTo(FK_VIOLATION + ":booking_occupancy_part_fk");
    assertThat(book(otherParts.withPart(fieldA), List.of(slot(0)), HOUR, true))
        .isEqualTo(FK_VIOLATION + ":booking_part_fk");

    Target whole = newUse(facility, "WHOLE", null);
    Target asSeats = new Target(facility, whole.use(), "SEATS", null, 10);
    assertThat(book(whole, asSeats, List.of(slot(0)), HOUR, newHolder(), true))
        .isEqualTo(FK_VIOLATION + ":use_slot_use_fk");
  }

  /**
   * Illamento na base (ADR-0006): outro concello non ve nin cambia as reservas nin as ocupacións,
   * non pode escribir filas co concello alleo (RLS, {@code 42501}) nin apuntar a un uso alleo co
   * seu propio concello (FK composta, {@code 23503}); sen concello na transacción non se ve nada.
   */
  @Test
  void rowLevelSecurityAndCompositeKeysIsolateMunicipalities() throws Exception {
    UUID facility = newFacility();
    Target gym = newUse(facility, "SEATS", 30);
    Instant slot = slot(8);
    assertThat(book(gym, List.of(slot), HOUR, true)).isEqualTo(OK);
    assertThat(book(gym, List.of(slot), HOUR, true)).isEqualTo(OK);
    UUID booking = bookingIdsOf(gym.use()).getFirst();
    assertThat(
            inTransaction(
                c -> {
                  try (PreparedStatement insert =
                      c.prepareStatement(
                          "insert into booking_event (id, municipality_id, booking_id, kind, at,"
                              + " actor_subject) values (?, ?, ?, 'CREATED', now(), 'test')")) {
                    insert.setObject(1, Ids.newV7());
                    insert.setObject(2, MUNICIPALITY);
                    insert.setObject(3, booking);
                    insert.executeUpdate();
                  }
                }))
        .isEqualTo(OK);

    for (String table :
        List.of(
            "facility",
            "facility_use",
            "facility_use_part",
            "booking",
            "booking_occupancy",
            "use_slot",
            "booking_event")) {
      assertThat(count(OTHER_MUNICIPALITY, "select count(*) from " + table)).as(table).isZero();
      assertThat(count(null, "select count(*) from " + table)).as(table).isZero();
    }
    assertThat(
            count(
                MUNICIPALITY,
                "select count(*) from booking_occupancy where use_id = '" + gym.use() + "'"))
        .isEqualTo(2);

    String update =
        inTransaction(
            OTHER_MUNICIPALITY,
            c -> {
              try (PreparedStatement u =
                  c.prepareStatement("update use_slot set taken = 0 where use_id = ?")) {
                u.setObject(1, gym.use());
                assertThat(u.executeUpdate()).isZero();
              }
              try (PreparedStatement u =
                  c.prepareStatement("update booking set status = 'CANCELLED_BY_PERSON'")) {
                assertThat(u.executeUpdate()).isZero();
              }
            });
    assertThat(update).isEqualTo(OK);
    assertThat(taken(gym.use(), slot)).isEqualTo(2);

    String foreignRow =
        inTransaction(
            OTHER_MUNICIPALITY, c -> insertOccupancy(c, MUNICIPALITY, booking, gym, slot, HOUR));
    assertThat(foreignRow).isEqualTo(RLS_VIOLATION);

    String foreignUse =
        inTransaction(
            OTHER_MUNICIPALITY,
            c -> insertOccupancy(c, OTHER_MUNICIPALITY, Ids.newV7(), gym, slot, HOUR));
    assertThat(foreignUse).startsWith(FK_VIOLATION);
  }

  /** {@code booking_event} só se engade: o rol da API non o cambia nin o borra (ADR-0025). */
  @Test
  void bookingEventsAreAppendOnlyForTheApi() throws Exception {
    Target court = newUse(newFacility(), "WHOLE", null);
    assertThat(book(court, List.of(slot(3)), HOUR, true)).isEqualTo(OK);
    UUID booking = bookingIdsOf(court.use()).getFirst();
    SqlWork insertEvent =
        c -> {
          try (PreparedStatement insert =
              c.prepareStatement(
                  "insert into booking_event (id, municipality_id, booking_id, kind, at,"
                      + " actor_subject) values (?, ?, ?, 'CREATED', now(), 'test')")) {
            insert.setObject(1, Ids.newV7());
            insert.setObject(2, MUNICIPALITY);
            insert.setObject(3, booking);
            insert.executeUpdate();
          }
        };
    assertThat(inTransaction(insertEvent)).isEqualTo(OK);
    String insufficientPrivilege = "42501";
    for (String sql :
        List.of(
            "update booking_event set kind = 'PAID' where municipality_id = ?",
            "delete from booking_event where municipality_id = ?")) {
      assertThat(
              inTransaction(
                  c -> {
                    try (PreparedStatement change = c.prepareStatement(sql)) {
                      change.setObject(1, MUNICIPALITY);
                      change.executeUpdate();
                    }
                  }))
          .as(sql)
          .isEqualTo(insufficientPrivilege);
    }
    // Quen actuou só cambia ao anonimizar a reserva, e só a 'furar:anonymized' (V202611041300).
    String raised = "P0001";
    assertThat(
            inTransaction(
                c -> {
                  try (PreparedStatement change =
                      c.prepareStatement(
                          "update booking_event set actor_subject = 'other'"
                              + " where municipality_id = ?")) {
                    change.setObject(1, MUNICIPALITY);
                    change.executeUpdate();
                  }
                }))
        .isEqualTo(raised);
  }

  // --- Procedemento do modelo (o que fai booking) ---

  /** Reserva dunha persoa nova, coa mesma reserva e ocupación. */
  static String book(Target target, List<Instant> starts, Duration duration, boolean lockFacility) {
    return book(target, target, starts, duration, newHolder(), lockFacility);
  }

  /**
   * Reserva: (1) se {@code lockFacility}, bloquea a fila da instalación ({@code for no key update},
   * ADR-0025 punto 5); (2) insire a reserva (a exclusión «unha praza por persoa» actúa aquí); (3)
   * en prazas, para cada franxa en orde de inicio crea a fila {@code use_slot} se falta, bloquéaa e
   * suma unha praza (o CHECK impide pasarse); (4) insire unha ocupación por franxa, e as exclusións
   * impiden outro uso ou pasar o cupo. Todo nunha transacción: todo ou nada. {@code occupancy}
   * distinto de {@code booking} só para probar as FK da ocupación cunha reserva válida.
   */
  static String book(
      Target booking,
      Target occupancy,
      List<Instant> starts,
      Duration duration,
      String holder,
      boolean lockFacility) {
    List<Instant> ordered = starts.stream().sorted(Comparator.naturalOrder()).toList();
    UUID id = Ids.newV7();
    return inTransaction(
        c -> {
          if (lockFacility) {
            try (PreparedStatement lock =
                c.prepareStatement(
                    "select id from facility where id = ? and municipality_id = ?"
                        + " for no key update")) {
              lock.setObject(1, booking.facility());
              lock.setObject(2, MUNICIPALITY);
              lock.executeQuery().close();
            }
          }
          insertBooking(c, id, booking, ordered, duration, holder);
          if (occupancy.mode().equals("SEATS")) {
            takeSeats(c, occupancy, ordered);
          }
          for (Instant start : ordered) {
            insertOccupancy(c, MUNICIPALITY, id, occupancy, start, duration);
          }
        });
  }

  private static void insertBooking(
      Connection c, UUID id, Target target, List<Instant> ordered, Duration duration, String holder)
      throws SQLException {
    Instant first = ordered.getFirst();
    Instant end = ordered.getLast().plus(duration);
    try (PreparedStatement insert =
        c.prepareStatement(
            "insert into booking (id, municipality_id, code, facility_id, use_id, part_id,"
                + " rental_mode, period, slots, status, payment_status, price_kind, cancel_until,"
                + " holder_subject, holder_name, holder_email, holder_language, created_at,"
                + " updated_at, version)"
                + " values (?, ?, ?, ?, ?, ?, ?, tstzrange(?, ?, '[)'), ?, 'CONFIRMED', 'NONE',"
                + " 'NONE', ?, ?, 'Persoa de proba', 'proba@example.org', 'gl', now(), now(), 0)")) {
      insert.setObject(1, id);
      insert.setObject(2, MUNICIPALITY);
      insert.setString(3, nextCode());
      insert.setObject(4, target.facility());
      insert.setObject(5, target.use());
      insert.setObject(6, target.part());
      insert.setString(7, target.mode());
      insert.setTimestamp(8, Timestamp.from(first));
      insert.setTimestamp(9, Timestamp.from(end));
      insert.setInt(10, ordered.size());
      insert.setTimestamp(11, Timestamp.from(first));
      insert.setString(12, holder);
      insert.executeUpdate();
    }
  }

  /** {@code FUR-XXXX} en orde, sen repetir: 32⁴ códigos (ADR-0025, punto 13). */
  static String nextCode() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    int n = CODES.getAndIncrement();
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      code.insert(0, alphabet.charAt(n % 32));
      n /= 32;
    }
    return "FUR-" + code;
  }

  static String newHolder() {
    return "persoa-" + Ids.newV7();
  }

  private static void takeSeats(Connection c, Target target, List<Instant> ordered)
      throws SQLException {
    for (Instant start : ordered) {
      try (PreparedStatement insert =
          c.prepareStatement(
              "insert into use_slot (use_id, slot_start, municipality_id, capacity)"
                  + " values (?, ?, ?, ?) on conflict do nothing")) {
        insert.setObject(1, target.use());
        insert.setTimestamp(2, Timestamp.from(start));
        insert.setObject(3, MUNICIPALITY);
        insert.setInt(4, target.capacity());
        insert.executeUpdate();
      }
    }
    for (Instant start : ordered) {
      try (PreparedStatement lock =
          c.prepareStatement(
              "select taken from use_slot where use_id = ? and slot_start = ?"
                  + " and municipality_id = ? for update")) {
        lock.setObject(1, target.use());
        lock.setTimestamp(2, Timestamp.from(start));
        lock.setObject(3, MUNICIPALITY);
        lock.executeQuery().close();
      }
      try (PreparedStatement take =
          c.prepareStatement(
              "update use_slot set taken = taken + 1 where use_id = ? and slot_start = ?"
                  + " and municipality_id = ?")) {
        take.setObject(1, target.use());
        take.setTimestamp(2, Timestamp.from(start));
        take.setObject(3, MUNICIPALITY);
        take.executeUpdate();
      }
    }
  }

  /** Cancelar como fará booking: reserva cancelada, ocupación inactiva e praza devolta. */
  static int cancelBookingsOf(String holder, UUID use, Instant slotStart) throws SQLException {
    int[] cancelled = new int[1];
    String result =
        inTransaction(
            c -> {
              try (PreparedStatement u =
                  c.prepareStatement(
                      "update booking set status = 'CANCELLED_BY_PERSON', cancelled_at = now(),"
                          + " cancelled_by = holder_subject, version = version + 1"
                          + " where holder_subject = ? and use_id = ? and municipality_id = ?"
                          + " and status = 'CONFIRMED' and lower(period) = ?")) {
                u.setString(1, holder);
                u.setObject(2, use);
                u.setObject(3, MUNICIPALITY);
                u.setTimestamp(4, Timestamp.from(slotStart));
                cancelled[0] = u.executeUpdate();
              }
              try (PreparedStatement u =
                  c.prepareStatement(
                      "update booking_occupancy o set active = false from booking b"
                          + " where b.id = o.booking_id and b.holder_subject = ? and b.use_id = ?"
                          + " and o.municipality_id = ? and b.status <> 'CONFIRMED'")) {
                u.setString(1, holder);
                u.setObject(2, use);
                u.setObject(3, MUNICIPALITY);
                u.executeUpdate();
              }
              try (PreparedStatement u =
                  c.prepareStatement(
                      "update use_slot set taken = taken - ? where use_id = ? and slot_start = ?"
                          + " and municipality_id = ?")) {
                u.setInt(1, cancelled[0]);
                u.setObject(2, use);
                u.setTimestamp(3, Timestamp.from(slotStart));
                u.setObject(4, MUNICIPALITY);
                u.executeUpdate();
              }
            });
    assertThat(result).isEqualTo(OK);
    return cancelled[0];
  }

  static void insertOccupancy(
      Connection c,
      UUID municipality,
      UUID booking,
      Target target,
      Instant start,
      Duration duration)
      throws SQLException {
    try (PreparedStatement insert =
        c.prepareStatement(
            "insert into booking_occupancy (id, municipality_id, booking_id, facility_id, use_id,"
                + " part_id, rental_mode, period)"
                + " values (?, ?, ?, ?, ?, ?, ?, tstzrange(?, ?, '[)'))")) {
      insert.setObject(1, Ids.newV7());
      insert.setObject(2, municipality);
      insert.setObject(3, booking);
      insert.setObject(4, target.facility());
      insert.setObject(5, target.use());
      insert.setObject(6, target.part());
      insert.setString(7, target.mode());
      insert.setTimestamp(8, Timestamp.from(start));
      insert.setTimestamp(9, Timestamp.from(start.plus(duration)));
      insert.executeUpdate();
    }
  }

  // --- Datos de partida (as táboas de facility, como as deixa o seu módulo) ---

  static UUID newFacility() {
    UUID id = Ids.newV7();
    insert(
        "insert into facility (id, municipality_id, name, street, postal_code, locality, ine_code,"
            + " latitude, longitude, created_at, updated_at, version)"
            + " values (?, ?, ?, 'Rúa do Alto', '15570', 'Narón', '15054', 43.5, -8.2, now(), now(),"
            + " 0)",
        id,
        MUNICIPALITY,
        "Instalación " + id);
    return id;
  }

  /** Uso da instalación; {@code capacity} só en SEATS. */
  static Target newUse(UUID facility, String mode, Integer capacity) {
    UUID id = Ids.newV7();
    insert(
        "insert into facility_use (id, municipality_id, facility_id, name, rental_mode, capacity,"
            + " slot_minutes, availability, created_at, updated_at, version)"
            + " values (?, ?, ?, ?, ?, ?, 60, 'ALWAYS', now(), now(), 0)",
        id,
        MUNICIPALITY,
        facility,
        "Uso " + id,
        mode,
        capacity);
    return new Target(facility, id, mode, null, capacity);
  }

  static UUID newPart(UUID use, String name, int position) {
    UUID id = Ids.newV7();
    insert(
        "insert into facility_use_part (id, municipality_id, use_id, name, position)"
            + " values (?, ?, ?, ?, ?)",
        id,
        MUNICIPALITY,
        use,
        name,
        position);
    return id;
  }

  private static void insert(String sql, Object... values) {
    String result =
        inTransaction(
            c -> {
              try (PreparedStatement insert = c.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) {
                  insert.setObject(i + 1, values[i]);
                }
                insert.executeUpdate();
              }
            });
    assertThat(result).isEqualTo(OK);
  }

  // --- Utilidades ---

  @FunctionalInterface
  interface SqlWork {
    void run(Connection c) throws SQLException;
  }

  @FunctionalInterface
  interface Attempt {
    String run(int index) throws Exception;
  }

  static String inTransaction(SqlWork work) {
    return inTransaction(MUNICIPALITY, work);
  }

  /**
   * Transacción no concello indicado (null = sen concello), como fará TenantContext en booking.
   * Devolve {@code OK}, o SQLSTATE ou {@code SQLSTATE:restrición} cando PostgreSQL a nomea.
   */
  static String inTransaction(UUID municipality, SqlWork work) {
    try (Connection c = dataSource.getConnection()) {
      try (Statement s = c.createStatement()) {
        s.execute("set local lock_timeout = '5s'");
      }
      setTenant(c, municipality);
      try {
        work.run(c);
        c.commit();
        return OK;
      } catch (SQLException e) {
        c.rollback();
        return outcome(e);
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String outcome(SQLException e) {
    if (e instanceof PSQLException p
        && p.getServerErrorMessage() != null
        && p.getServerErrorMessage().getConstraint() != null) {
      return e.getSQLState() + ":" + p.getServerErrorMessage().getConstraint();
    }
    return e.getSQLState();
  }

  static List<String> concurrently(int n, Attempt attempt) throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(n);
    List<Future<String>> futures = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      int index = i;
      futures.add(
          pool.submit(
              () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return attempt.run(index);
              }));
    }
    List<String> results = new ArrayList<>();
    for (Future<String> f : futures) {
      results.add(f.get(30, TimeUnit.SECONDS));
    }
    return results;
  }

  static void setTenant(Connection c, UUID municipality) throws SQLException {
    try (PreparedStatement set =
        c.prepareStatement("select set_config('furar.municipality_id', ?, true)")) {
      set.setString(1, municipality == null ? "" : municipality.toString());
      set.executeQuery().close();
    }
  }

  static int count(UUID municipality, String sql) throws SQLException {
    try (Connection c = dataSource.getConnection()) {
      setTenant(c, municipality);
      try (PreparedStatement q = c.prepareStatement(sql);
          ResultSet rs = q.executeQuery()) {
        rs.next();
        int value = rs.getInt(1);
        c.rollback();
        return value;
      }
    }
  }

  static Instant slot(int hour) {
    return Instant.parse("2026-11-02T09:00:00Z").plus(hour, ChronoUnit.HOURS);
  }

  static int taken(UUID use, Instant start) throws SQLException {
    return count(
        MUNICIPALITY,
        "select coalesce((select taken from use_slot where use_id = '"
            + use
            + "' and slot_start = '"
            + start
            + "'), 0)");
  }

  static int activeInUse(UUID use) throws SQLException {
    return count(
        MUNICIPALITY,
        "select count(*) from booking_occupancy where use_id = '" + use + "' and active");
  }

  static List<UUID> bookingIdsOf(UUID use) throws SQLException {
    try (Connection c = dataSource.getConnection()) {
      setTenant(c, MUNICIPALITY);
      try (PreparedStatement q =
          c.prepareStatement(
              "select id from booking where use_id = ? and municipality_id = ? order by id")) {
        q.setObject(1, use);
        q.setObject(2, MUNICIPALITY);
        List<UUID> ids = new ArrayList<>();
        try (ResultSet rs = q.executeQuery()) {
          while (rs.next()) {
            ids.add(rs.getObject(1, UUID.class));
          }
        }
        c.rollback();
        return ids;
      }
    }
  }

  static int activeInFacility(UUID facility) throws SQLException {
    return count(
        MUNICIPALITY,
        "select count(*) from booking_occupancy where facility_id = '" + facility + "' and active");
  }
}
