// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.out.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Lista branca do texto propio das páxinas legais (ADR-0017 punto 8): o que deixa e cargas XSS
 * coñecidas, que non poden sobrevivir nin como elemento nin como atributo nin como protocolo.
 */
class JsoupLegalHtmlSanitizerTest {

  private static final Set<String> ALLOWED =
      Set.of("h2", "h3", "p", "strong", "em", "ul", "ol", "li", "a", "br");

  private final JsoupLegalHtmlSanitizer sanitizer = new JsoupLegalHtmlSanitizer();

  @Test
  void keepsTheEditorFormat() {
    String html =
        "<h2>Titular</h2><h3>Datos</h3><p>Texto <strong>forte</strong> e <em>cursiva</em><br>liña"
            + "</p><ul><li>Un</li></ul><ol><li>Dous</li></ol><p><a"
            + " href=\"https://sede.naron.gal\">sede</a> <a href=\"mailto:dpd@naron.gal\">dpd</a>"
            + "</p>";
    assertThat(sanitizer.sanitize(html))
        .isEqualTo(
            "<h2>Titular</h2><h3>Datos</h3><p>Texto <strong>forte</strong> e <em>cursiva</em><br>"
                + "liña</p><ul><li>Un</li></ul><ol><li>Dous</li></ol>"
                + "<p><a href=\"https://sede.naron.gal\" rel=\"noopener noreferrer\">sede</a> "
                + "<a href=\"mailto:dpd@naron.gal\" rel=\"noopener noreferrer\">dpd</a></p>");
  }

  @Test
  void dropsStylesClassesAndOtherElementsButKeepsTheirText() {
    assertThat(
            sanitizer.sanitize(
                "<h1 class=\"x\" style=\"color:red\">Título</h1><div id=\"d\"><p style=\"x\""
                    + " class=\"y\" lang=\"es\">Texto</p></div><b>b</b><i>i</i><u>u</u>"
                    + "<table><tr><td>celda</td></tr></table>"))
        .isEqualTo("Título<p>Texto</p>biucelda");
  }

  @Test
  void linksOnlyKeepHttpsAndMailtoAndNeverOtherAttributes() {
    assertThat(
            sanitizer.sanitize(
                "<p><a href=\"http://naron.gal\" target=\"_blank\" title=\"t\" rel=\"opener\">a</a>"
                    + "<a href=\"/relativa\">b</a></p>"))
        .isEqualTo(
            "<p><a rel=\"noopener noreferrer\">a</a><a rel=\"noopener noreferrer\">b</a></p>");
    assertThat(sanitizer.sanitize("<p><a href=\"HTTPS://naron.gal\">c</a></p>"))
        .containsIgnoringCase("<a href=\"https://naron.gal");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<script>alert(1)</script><p>ok</p>",
        "<p>ok</p><img src=x onerror=alert(1)>",
        "<p onclick=\"alert(1)\" onmouseover=alert(1)>ok</p>",
        "<p>ok<svg onload=alert(1)><script>alert(1)</script></svg></p>",
        "<p><a href=\"javascript:alert(1)\">ok</a></p>",
        "<p><a href=\"JaVaScRiPt:alert(1)\">ok</a></p>",
        "<p><a href=\"java&#x09;script:alert(1)\">ok</a></p>",
        "<p><a"
            + " href=\"&#106;&#97;&#118;&#97;&#115;&#99;&#114;&#105;&#112;&#116;&#58;alert(1)\">ok</a>"
            + "</p>",
        "<p><a href=\"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==\">ok</a></p>",
        "<p><a href=\"vbscript:msgbox(1)\">ok</a></p>",
        "<iframe src=\"https://evil.example\"></iframe><p>ok</p>",
        "<p>ok</p><object data=\"x\"></object><embed src=\"x\">",
        "<style>p{background:url(javascript:alert(1))}</style><p>ok</p>",
        "<p>ok</p><form action=\"https://evil.example\"><input name=x></form>",
        "<math><mtext><table><mglyph><style><img src=x onerror=alert(1)></style></mglyph>"
            + "</table></mtext></math><p>ok</p>",
        "<noscript><p title=\"</noscript><img src=x onerror=alert(1)>\">x</p></noscript><p>ok</p>",
        "<p>ok</p><base href=\"https://evil.example\"><meta http-equiv=\"refresh\" content=\"0\">",
        "<p>ok</p><!--<script>alert(1)</script>-->",
        "<p>ok</p><link rel=\"stylesheet\" href=\"https://evil.example/x.css\">",
        "<p style=\"background-image:url('javascript:alert(1)')\">ok</p>",
      })
  void knownXssPayloadsLeaveNothingExecutable(String payload) {
    String clean = sanitizer.sanitize(payload);
    assertThat(clean).isNotNull().contains("ok");
    // O que queda, analizado coma o faría un navegador: só elementos e atributos da lista branca.
    Document parsed = Jsoup.parseBodyFragment(clean);
    for (Element element : parsed.body().getAllElements()) {
      if (element == parsed.body()) {
        continue;
      }
      assertThat(ALLOWED).as(clean).contains(element.normalName());
      for (Attribute attribute : element.attributes()) {
        assertThat(attribute.getKey()).as(clean).isIn("href", "rel");
        if (attribute.getKey().equals("href")) {
          assertThat(attribute.getValue()).as(clean).matches("^(https://|mailto:).*");
        }
      }
    }
    assertThat(clean.toLowerCase(Locale.ROOT)).doesNotContain("<script", "<img", "<svg", "<iframe");
  }

  @Test
  void emptyOrInvisibleHtmlIsNoText() {
    assertThat(sanitizer.sanitize(null)).isNull();
    assertThat(sanitizer.sanitize("  ")).isNull();
    assertThat(sanitizer.sanitize("<p></p><p> </p><br>")).isNull();
    assertThat(sanitizer.sanitize("<script>alert(1)</script>")).isNull();
  }
}
