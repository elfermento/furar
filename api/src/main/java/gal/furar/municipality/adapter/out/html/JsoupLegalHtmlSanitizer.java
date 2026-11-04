// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.html;

import gal.furar.municipality.application.port.out.LegalHtmlSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Lista branca do texto propio das páxinas legais (ADR-0017 punto 8) con jsoup (MIT): analiza o
 * HTML coma un navegador e copia só os elementos e atributos permitidos. As ligazóns sen {@code
 * https:} nin {@code mailto:} (tamén as relativas, {@code javascript:} ou {@code data:}) perden o
 * {@code href}; todas levan {@code rel="noopener noreferrer"}. Nada de {@code style}, clases,
 * imaxes nin scripts: a web pinta o resultado co estilo de Furar e coa CSP {@code style-src
 * 'self'}.
 */
@Component
class JsoupLegalHtmlSanitizer implements LegalHtmlSanitizer {

  private static final Safelist SAFELIST =
      new Safelist()
          .addTags("h2", "h3", "p", "strong", "em", "ul", "ol", "li", "a", "br")
          .addAttributes("a", "href")
          .addProtocols("a", "href", "https", "mailto")
          .addEnforcedAttribute("a", "rel", "noopener noreferrer");

  private static final Document.OutputSettings OUTPUT =
      new Document.OutputSettings()
          .prettyPrint(false)
          .charset("UTF-8")
          .escapeMode(Entities.EscapeMode.base);

  @Override
  public @Nullable String sanitize(@Nullable String html) {
    if (html == null || html.isBlank()) {
      return null;
    }
    Document dirty = Jsoup.parseBodyFragment(html, "");
    Document clean = new Cleaner(SAFELIST).clean(dirty);
    clean.outputSettings(OUTPUT);
    if (clean.body().text().isBlank()) {
      return null;
    }
    return clean.body().html().strip();
  }
}
