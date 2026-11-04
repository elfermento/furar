// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.keycloak;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * A ligazón curta dos correos de Keycloak (maqueta 55, opción C): baixo o botón, «Non funciona o
 * botón? Abre esta ligazón:» e {@code <orixe>/auth/realms/<realm>/l/XXXX-XXXX-XXXX} no canto do
 * URL longo da acción. O botón segue levando o URL orixinal.
 *
 * <ul>
 *   <li><b>Código:</b> 12 caracteres ao chou ({@code SecureRandom}) dun alfabeto de 31 sen os que se
 *       confunden (sen 0, O, 1, I nin L): ~59 bits. Móstrase en tres grupos de catro; ao abrilo
 *       dan igual as maiúsculas e os guións.
 *   <li><b>Onde se garda:</b> no almacén de obxectos dun só uso de Keycloak ({@code
 *       singleUseObjects}, o mesmo das accións), co URL orixinal e a mesma vida ca a ligazón da
 *       acción. Non se borra ao abrila: a acción xa controla se se pode usar dúas veces.
 *   <li><b>Orixe:</b> a mesma ca a do URL da acción (que Keycloak fai co hostname configurado), así
 *       que en produción sae {@code https://<dominio>/auth/realms/furar/l/…}, polo Caddy.
 *   <li>Nunca se rexistra o código nin o URL.
 * </ul>
 *
 * <p>Sen dependencias de Keycloak, para probala soa.
 */
final class ShortLink {

  /** Camiño do recurso do realm ({@code /realms/<realm>/l/<código>}). */
  static final String PATH = "l";

  /** Atributo dos modelos de correo co URL curto (se non o hai, os modelos usan {@code link}). */
  static final String MAIL_ATTRIBUTE = "furarShortLink";

  /** Sen 0/O, 1/I/L: lese e dítase sen dúbidas. */
  static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

  static final int LENGTH = 12;
  static final int GROUP = 4;
  static final String NOTE_URL = "url";
  private static final String KEY_PREFIX = "furar-short-link:";
  private static final int MAX_INPUT = 32;

  /** Onde se garda (en Keycloak, {@code session.singleUseObjects()::put}). */
  @FunctionalInterface
  interface Store {
    void put(String key, long lifespanSeconds, Map<String, String> notes);
  }

  private ShortLink() {}

  /** Un código novo, en grupos ({@code 7KQ2-MX9P-4HTW}). */
  static String newCode(RandomGenerator random) {
    StringBuilder code = new StringBuilder(LENGTH + LENGTH / GROUP);
    for (int i = 0; i < LENGTH; i++) {
      if (i > 0 && i % GROUP == 0) {
        code.append('-');
      }
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }

  /** O código sen guións nin espazos e en maiúsculas, ou baleiro se non pode ser un código. */
  static Optional<String> normalize(String input) {
    if (input == null || input.length() > MAX_INPUT) {
      return Optional.empty();
    }
    String code = input.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    if (code.length() != LENGTH || !code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
      return Optional.empty();
    }
    return Optional.of(code);
  }

  /** Clave no almacén: o realm e o código normalizado. */
  static String key(String realmId, String normalizedCode) {
    return KEY_PREFIX + realmId + ":" + normalizedCode;
  }

  /**
   * A base das ligazóns curtas, tomada do URL da acción: {@code <orixe e camiño>/realms/<realm>/}.
   * Baleiro se o URL non é http(s) ou non vai a ese realm.
   */
  static Optional<String> base(String link, String realmName) {
    if (link == null || realmName == null || realmName.isEmpty()) {
      return Optional.empty();
    }
    try {
      URI uri = new URI(link);
      String scheme = uri.getScheme();
      if (scheme == null
          || uri.getRawAuthority() == null
          || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
        return Optional.empty();
      }
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    String marker = "/realms/" + realmName + "/";
    int at = link.indexOf(marker);
    if (at < 0) {
      return Optional.empty();
    }
    return Optional.of(link.substring(0, at + marker.length()));
  }

  /**
   * Garda o URL da acción baixo un código novo e devolve a ligazón curta, ou baleiro se o URL non
   * vale para iso (daquela o correo mostra o URL longo).
   */
  static Optional<String> create(
      String link,
      String realmId,
      String realmName,
      long lifespanSeconds,
      Store store,
      RandomGenerator random) {
    Optional<String> base = base(link, realmName);
    if (base.isEmpty() || realmId == null || lifespanSeconds <= 0) {
      return Optional.empty();
    }
    String code = newCode(random);
    store.put(
        key(realmId, normalize(code).orElseThrow()), lifespanSeconds, Map.of(NOTE_URL, link));
    return Optional.of(base.get() + PATH + "/" + code);
  }

  /** O URL da acción gardado para ese código, se existe e non caducou. */
  static Optional<String> resolve(
      String realmId, String code, Function<String, Map<String, String>> lookup) {
    if (realmId == null) {
      return Optional.empty();
    }
    return normalize(code)
        .map(c -> lookup.apply(key(realmId, c)))
        .map(notes -> notes.get(NOTE_URL))
        .filter(url -> url.startsWith("https://") || url.startsWith("http://"));
  }
}
