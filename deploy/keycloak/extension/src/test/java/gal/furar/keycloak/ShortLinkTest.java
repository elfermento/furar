// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A ligazón curta dos correos (maqueta 55, opción C): o código, a base tomada do URL da acción, o
 * que se garda e o que se resolve. O correo e a redirección de verdade próbanse en D51.
 */
class ShortLinkTest {

  static final String REALM_ID = "0f6a1c2e-realm";
  static final String ACTION =
      "https://reservas.example.gal/auth/realms/furar/login-actions/action-token"
          + "?key=eyJhbGciOiJIUzUxMiJ9.eyJ0eXAiOiJ2ZXJpZnktZW1haWwifQ.c2ln&client_id=furar-web&tab_id=Xk3p";
  static final Pattern SHAPE = Pattern.compile("[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}");

  /** O almacén de Keycloak, nun mapa (sen caducidade). */
  static final class FakeStore implements ShortLink.Store {
    final Map<String, Map<String, String>> entries = new HashMap<>();
    final Map<String, Long> lifespans = new HashMap<>();

    @Override
    public void put(String key, long lifespanSeconds, Map<String, String> notes) {
      entries.put(key, Map.copyOf(notes));
      lifespans.put(key, lifespanSeconds);
    }
  }

  @Test
  void theAlphabetHasNoLookAlikesAndTheCodeHasMoreThan50Bits() {
    for (char c : "0O1IL".toCharArray()) {
      assertTrue(ShortLink.ALPHABET.indexOf(c) < 0, String.valueOf(c));
    }
    assertEquals(ShortLink.ALPHABET.length(), ShortLink.ALPHABET.chars().distinct().count());
    double bits = ShortLink.LENGTH * Math.log(ShortLink.ALPHABET.length()) / Math.log(2);
    assertTrue(bits >= 50, "bits: " + bits);
  }

  @Test
  void codesAreGroupedAndDoNotRepeat() {
    SecureRandom random = new SecureRandom();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      String code = ShortLink.newCode(random);
      assertTrue(SHAPE.matcher(code).matches(), code);
      assertTrue(seen.add(code), code);
    }
  }

  @Test
  void normalizeIgnoresCaseHyphensAndSpacesAndRejectsTheRest() {
    assertEquals(Optional.of("7KQ2MX9P4HTW"), ShortLink.normalize("7KQ2-MX9P-4HTW"));
    assertEquals(Optional.of("7KQ2MX9P4HTW"), ShortLink.normalize("7kq2 mx9p 4htw"));
    assertEquals(Optional.of("7KQ2MX9P4HTW"), ShortLink.normalize("7KQ2MX9P4HTW"));
    for (String bad :
        new String[] {
          null,
          "",
          "7KQ2-MX9P",
          "7KQ2-MX9P-4HTW-A",
          "7KQ2-MX9P-4HT0",
          "7KQ2-MX9P-4HTI",
          "7KQ2-MX9P-4HT/",
          "../../admin",
          "x".repeat(200)
        }) {
      assertTrue(ShortLink.normalize(bad).isEmpty(), String.valueOf(bad));
    }
  }

  @Test
  void theBaseIsTheOriginAndPathOfTheActionLink() {
    assertEquals(
        Optional.of("https://reservas.example.gal/auth/realms/furar/"),
        ShortLink.base(ACTION, "furar"));
    assertEquals(
        Optional.of("http://localhost:8080/auth/realms/furar/"),
        ShortLink.base("http://localhost:8080/auth/realms/furar/login-actions/x?y=1", "furar"));
    assertTrue(ShortLink.base(ACTION, "outro").isEmpty());
    assertTrue(ShortLink.base("javascript:alert(1)//realms/furar/", "furar").isEmpty());
    assertTrue(ShortLink.base("/auth/realms/furar/login-actions/x", "furar").isEmpty());
    assertTrue(ShortLink.base(null, "furar").isEmpty());
    assertTrue(ShortLink.base(ACTION, null).isEmpty());
  }

  @Test
  void createStoresTheActionLinkWithItsLifespanAndResolveGivesItBack() {
    FakeStore store = new FakeStore();
    String shortUrl =
        ShortLink.create(ACTION, REALM_ID, "furar", 1800, store, new SplittableRandom(1))
            .orElseThrow();

    String prefix = "https://reservas.example.gal/auth/realms/furar/l/";
    assertTrue(shortUrl.startsWith(prefix), shortUrl);
    String code = shortUrl.substring(prefix.length());
    assertTrue(SHAPE.matcher(code).matches(), code);
    assertTrue(shortUrl.length() < 70, shortUrl);
    assertEquals(1, store.entries.size());
    assertEquals(Long.valueOf(1800), store.lifespans.values().iterator().next());
    // A clave non leva o URL, e o valor só o URL.
    String key = store.entries.keySet().iterator().next();
    assertTrue(key.contains(REALM_ID) && !key.contains("action-token"), key);
    assertEquals(Map.of(ShortLink.NOTE_URL, ACTION), store.entries.get(key));

    assertEquals(Optional.of(ACTION), ShortLink.resolve(REALM_ID, code, store.entries::get));
    assertEquals(
        Optional.of(ACTION),
        ShortLink.resolve(REALM_ID, code.toLowerCase().replace("-", ""), store.entries::get));
  }

  @Test
  void unknownCodesOtherRealmsAndOddValuesResolveToNothing() {
    FakeStore store = new FakeStore();
    String shortUrl =
        ShortLink.create(ACTION, REALM_ID, "furar", 1800, store, new SecureRandom()).orElseThrow();
    String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);

    assertTrue(ShortLink.resolve(REALM_ID, "2222-2222-2222", store.entries::get).isEmpty());
    assertTrue(ShortLink.resolve("outro-realm", code, store.entries::get).isEmpty());
    assertTrue(ShortLink.resolve(null, code, store.entries::get).isEmpty());
    assertTrue(ShortLink.resolve(REALM_ID, "nada", store.entries::get).isEmpty());

    // Só se redirixe a http(s), aínda que algo raro chegase ao almacén.
    String key = store.entries.keySet().iterator().next();
    store.entries.put(key, Map.of(ShortLink.NOTE_URL, "javascript:alert(1)"));
    assertTrue(ShortLink.resolve(REALM_ID, code, store.entries::get).isEmpty());
    store.entries.put(key, Map.of());
    assertTrue(ShortLink.resolve(REALM_ID, code, store.entries::get).isEmpty());
  }

  @Test
  void withoutAUsableLinkOrLifespanNothingIsStored() {
    FakeStore store = new FakeStore();
    SecureRandom random = new SecureRandom();
    assertTrue(ShortLink.create("not a url", REALM_ID, "furar", 1800, store, random).isEmpty());
    assertTrue(ShortLink.create(ACTION, REALM_ID, "outro", 1800, store, random).isEmpty());
    assertTrue(ShortLink.create(ACTION, null, "furar", 1800, store, random).isEmpty());
    assertTrue(ShortLink.create(ACTION, REALM_ID, "furar", 0, store, random).isEmpty());
    assertTrue(store.entries.isEmpty());
  }

  @Test
  void theEmailProviderIsTheDefaultOneAndTheResourceLivesUnderL() {
    assertTrue(ShortLinkEmailTemplateProviderFactory.ORDER > 0);
    assertEquals("l", new ShortLinkResourceFactory().getId());
    assertEquals("furarShortLink", ShortLink.MAIL_ATTRIBUTE);
  }
}
