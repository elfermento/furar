// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Debuxo de Furar que lle vai a unha instalación (especificación, bloque 1, «Fotos»), por regras e
 * sen IA. Por orde: (1) palabras do nome, en galego ou castelán, sen ter en conta maiúsculas nin
 * acentos («Pavillón de Piñeiros» → pavillón polideportivo); (2) se non, os deportes do catálogo:
 * se todos levan ao mesmo debuxo, ese; se hai varios de pista, o pavillón polideportivo se é
 * cuberta e a pista polideportiva exterior se non; (3) se non, o tipo de instalación; (4) se non, a
 * instalación xenérica. Os deportes creados polo concello non contan. Función pura: o xestor pode
 * escoller despois calquera imaxe.
 */
public final class DrawingSuggestion {

  public static final String ACTIVITY_ROOM = "activity_room";
  public static final String ATHLETICS_TRACK = "athletics_track";
  public static final String BADMINTON_COURT = "badminton_court";
  public static final String BASKETBALL_COURT = "basketball_court";
  public static final String BEACH_VOLLEYBALL = "beach_volleyball";
  public static final String CLIMBING_WALL = "climbing_wall";
  public static final String COMBAT_ROOM = "combat_room";
  public static final String FIELD_HOCKEY = "field_hockey";
  public static final String FOOTBALL_FIELD = "football_field";
  public static final String FRONTON = "fronton";
  public static final String FUTSAL_COURT = "futsal_court";
  public static final String GENERIC = "generic";
  public static final String GYM = "gym";
  public static final String JETTY = "jetty";
  public static final String MULTIPURPOSE_ROOM = "multipurpose_room";
  public static final String OUTDOOR_COURT = "outdoor_court";
  public static final String PADEL_COURT = "padel_court";
  public static final String POOL = "pool";
  public static final String ROLLER_RINK = "roller_rink";
  public static final String RUGBY_FIELD = "rugby_field";
  public static final String SPORTS_HALL = "sports_hall";
  public static final String SQUASH_COURT = "squash_court";
  public static final String TABLE_TENNIS = "table_tennis";
  public static final String TENNIS_COURT = "tennis_court";
  public static final String VELODROME = "velodrome";
  public static final String VOLLEYBALL_COURT = "volleyball_court";

  /** Todos os códigos do catálogo (a táboa {@code drawing} ten que ter exactamente estes). */
  public static final Set<String> CODES =
      Set.of(
          ACTIVITY_ROOM,
          ATHLETICS_TRACK,
          BADMINTON_COURT,
          BASKETBALL_COURT,
          BEACH_VOLLEYBALL,
          CLIMBING_WALL,
          COMBAT_ROOM,
          FIELD_HOCKEY,
          FOOTBALL_FIELD,
          FRONTON,
          FUTSAL_COURT,
          GENERIC,
          GYM,
          JETTY,
          MULTIPURPOSE_ROOM,
          OUTDOOR_COURT,
          PADEL_COURT,
          POOL,
          ROLLER_RINK,
          RUGBY_FIELD,
          SPORTS_HALL,
          SQUASH_COURT,
          TABLE_TENNIS,
          TENNIS_COURT,
          VELODROME,
          VOLLEYBALL_COURT);

  /** Palabra ou expresión do nome (xa en minúsculas e sen acentos) → debuxo. A primeira gaña. */
  private record Rule(Pattern pattern, String drawing) {
    static Rule of(String regex, String drawing) {
      return new Rule(Pattern.compile("\\b(?:" + regex + ")\\b"), drawing);
    }
  }

  private static final List<Rule> NAME_RULES =
      List.of(
          Rule.of("pista polideportiva|pistas polideportivas", OUTDOOR_COURT),
          Rule.of("pavillons?|pabellon(es)?|polideportivos?", SPORTS_HALL),
          Rule.of("piscinas?", POOL),
          Rule.of("padel", PADEL_COURT),
          Rule.of("tenis de mesa|ping ?pong", TABLE_TENNIS),
          Rule.of("tenis", TENNIS_COURT),
          // «Fútbol sala» non é o campo: decídeno os deportes, se é cuberta, e o tipo.
          Rule.of("futbol(?! sala)", FOOTBALL_FIELD),
          Rule.of("rugby", RUGBY_FIELD),
          Rule.of("atletismo", ATHLETICS_TRACK),
          Rule.of("fronton|frontenis", FRONTON),
          Rule.of("squash", SQUASH_COURT),
          Rule.of("badminton", BADMINTON_COURT),
          Rule.of("ximnasio|gimnasio|musculacion|fitness", GYM),
          Rule.of("rocodromo|escalada|bulder|boulder", CLIMBING_WALL),
          Rule.of("velodromo", VELODROME),
          Rule.of("(hockey|hoquei) (herba|hierba)", FIELD_HOCKEY),
          Rule.of("hockey|hoquei|skate|skatepark|patinaxe|patinaje|patinodromo", ROLLER_RINK),
          Rule.of("baloncesto|basquet|basket", BASKETBALL_COURT),
          Rule.of("(voley|volei|voleibol) (praia|playa)", BEACH_VOLLEYBALL),
          Rule.of("voleibol|voley|volei", VOLLEYBALL_COURT),
          Rule.of("tatami|dojo|judo|karate|boxeo|loita|lucha|ring", COMBAT_ROOM),
          Rule.of("multiusos", MULTIPURPOSE_ROOM),
          Rule.of("sala de actividades|actividades|baile|danza|ioga|yoga|pilates", ACTIVITY_ROOM),
          Rule.of("embarcadoiro|embarcadero|piraguismo|remo|nautico", JETTY));

  /** Deporte do catálogo → o seu debuxo. Os de pista dependen de se a instalación é cuberta. */
  private static final Map<String, String> SPORT_DRAWINGS =
      Map.ofEntries(
          Map.entry("athletics", ATHLETICS_TRACK),
          Map.entry("badminton", BADMINTON_COURT),
          Map.entry("boxing", COMBAT_ROOM),
          Map.entry("canoeing", JETTY),
          Map.entry("climbing", CLIMBING_WALL),
          Map.entry("cycling", VELODROME),
          Map.entry("dance", ACTIVITY_ROOM),
          Map.entry("fitness", GYM),
          Map.entry("football", FOOTBALL_FIELD),
          Map.entry("football_7", FOOTBALL_FIELD),
          Map.entry("fronton", FRONTON),
          Map.entry("gymnastics", ACTIVITY_ROOM),
          Map.entry("field_hockey", FIELD_HOCKEY),
          Map.entry("roller_hockey", ROLLER_RINK),
          Map.entry("judo", COMBAT_ROOM),
          Map.entry("karate", COMBAT_ROOM),
          Map.entry("martial_arts", COMBAT_ROOM),
          Map.entry("padel", PADEL_COURT),
          Map.entry("pilates", ACTIVITY_ROOM),
          Map.entry("rowing", JETTY),
          Map.entry("rugby", RUGBY_FIELD),
          Map.entry("skating", ROLLER_RINK),
          Map.entry("squash", SQUASH_COURT),
          Map.entry("swimming", POOL),
          Map.entry("table_tennis", TABLE_TENNIS),
          Map.entry("tennis", TENNIS_COURT),
          Map.entry("volleyball", VOLLEYBALL_COURT),
          Map.entry("beach_volleyball", BEACH_VOLLEYBALL),
          Map.entry("water_polo", POOL),
          Map.entry("wrestling", COMBAT_ROOM),
          Map.entry("yoga", ACTIVITY_ROOM));

  /** Deportes de pista: cun só, o seu debuxo; con varios, pavillón ou pista exterior. */
  private static final Set<String> COURT_SPORTS =
      Set.of("basketball", "futsal", "handball", "volleyball", "badminton");

  private static final Map<FacilityType, String> TYPE_DRAWINGS =
      Map.of(
          FacilityType.FIELD, FOOTBALL_FIELD,
          FacilityType.SPORTS_HALL, SPORTS_HALL,
          FacilityType.PADEL_COURT, PADEL_COURT,
          FacilityType.POOL, POOL,
          FacilityType.OUTDOOR_COURT, OUTDOOR_COURT);

  private static final Pattern MARKS = Pattern.compile("\\p{M}+");

  private DrawingSuggestion() {}

  public static String suggest(Facility facility) {
    FacilityDetails details = facility.details();
    return suggest(facility.name(), details.sports(), details.type(), details.covered());
  }

  public static String suggest(
      @Nullable String name,
      List<SportRef> sports,
      @Nullable FacilityType type,
      @Nullable Boolean covered) {
    String byName = byName(name);
    if (byName != null) {
      return byName;
    }
    String bySports = bySports(sports, Boolean.TRUE.equals(covered));
    if (bySports != null) {
      return bySports;
    }
    return type == null ? GENERIC : TYPE_DRAWINGS.getOrDefault(type, GENERIC);
  }

  private static @Nullable String byName(@Nullable String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    String text = normalize(name);
    for (Rule rule : NAME_RULES) {
      if (rule.pattern().matcher(text).find()) {
        return rule.drawing();
      }
    }
    return null;
  }

  private static @Nullable String bySports(List<SportRef> sports, boolean covered) {
    List<String> codes =
        sports.stream()
            .filter(s -> !s.isCustom())
            .map(SportRef::code)
            .filter(Objects::nonNull)
            .toList();
    if (codes.isEmpty()) {
      return null;
    }
    long courts = codes.stream().filter(COURT_SPORTS::contains).count();
    if (courts >= 2) {
      return covered ? SPORTS_HALL : OUTDOOR_COURT;
    }
    Set<String> drawings = new LinkedHashSet<>();
    for (String code : codes) {
      drawings.add(sportDrawing(code, covered));
    }
    drawings.remove(null);
    return drawings.size() == 1 ? drawings.iterator().next() : null;
  }

  private static @Nullable String sportDrawing(String code, boolean covered) {
    return switch (code) {
      case "basketball" -> covered ? SPORTS_HALL : BASKETBALL_COURT;
      case "futsal", "handball" -> covered ? SPORTS_HALL : OUTDOOR_COURT;
      default -> SPORT_DRAWINGS.get(code);
    };
  }

  /** Minúsculas, sen acentos e cun só espazo entre palabras. */
  static String normalize(String text) {
    String plain = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
    return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
  }
}
