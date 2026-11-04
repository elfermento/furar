// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * SEC3-05 (ADR-0006): un concello vive en {@code /<slug>}, así que ningún slug pode coincidir cunha
 * ruta fixa da web (Angular) nin de Caddy. Le os dous ficheiros: se alguén engade unha ruta de
 * primeiro nivel e esquece {@link Slug#RESERVED}, o build falla.
 */
class SlugReservedRoutesTest {

  private static final Path ROUTES = Path.of("../web/src/app/app.routes.ts");
  private static final Path CADDYFILE = Path.of("../deploy/caddy/Caddyfile");

  /** Entradas do array raíz: dous espazos ({@code  { path: 'x'}) ou catro ({@code     path:}). */
  private static final Pattern ROOT_ROUTE =
      Pattern.compile("^(?: {2}\\{ | {4})path: '([a-z0-9-]+)", Pattern.MULTILINE);

  /** Directivas do sitio principal (un tabulador): {@code handle /x}, {@code redir /x}. */
  private static final Pattern CADDY_ROUTE =
      Pattern.compile(
          "^\\t(?:handle|redir|handle_path|route|reverse_proxy) /([a-z0-9-]+)", Pattern.MULTILINE);

  @Test
  void everyFixedTopLevelRouteIsReserved() throws IOException {
    Set<String> routes = new TreeSet<>();
    collect(Files.readString(ROUTES), ROOT_ROUTE, routes);
    collect(Files.readString(CADDYFILE), CADDY_ROUTE, routes);
    assertThat(routes).as("rutas lidas").contains("plataforma", "erro", "api", "codigo-fonte");
    assertThat(Slug.RESERVED).containsAll(routes);
  }

  private static void collect(String text, Pattern pattern, Set<String> into) {
    Matcher m = pattern.matcher(text);
    while (m.find()) {
      into.add(m.group(1));
    }
  }
}
