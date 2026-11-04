// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * Renderizador mínimo das plantillas fixas do correo (plan do dominio 2, §1.2.15: sen motor de
 * plantillas). {@code {{clave}}} pon o valor, escapado en HTML se a plantilla é HTML; {@code
 * {{#clave}}…{{/clave}}} só deixa o bloque se o valor existe e non está baleiro. Unha clave sen
 * valor é un erro: ningún correo sae cun oco nin cun texto sen traducir.
 */
final class MailTemplate {

  private static final Pattern SECTION =
      Pattern.compile("\\{\\{#([a-zA-Z][a-zA-Z0-9]*)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);
  private static final Pattern VALUE = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9]*)}}");

  private final String body;
  private final boolean html;

  private MailTemplate(String body, boolean html) {
    this.body = body;
    this.html = html;
  }

  /**
   * A plantilla do classpath sen a súa cabeceira: en HTML, o comentario de antes de {@code
   * <!doctype}; en texto, as liñas iniciais que empezan por {@code #} e a liña baleira seguinte.
   */
  static MailTemplate load(String resource) {
    String text;
    try (InputStream in = MailTemplate.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing mail template: " + resource);
      }
      text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return resource.endsWith(".html") ? html(text) : text(text);
  }

  static MailTemplate html(String text) {
    int start = text.toLowerCase(java.util.Locale.ROOT).indexOf("<!doctype");
    return new MailTemplate(start < 0 ? text : text.substring(start), true);
  }

  static MailTemplate text(String text) {
    String rest = text;
    while (rest.startsWith("#")) {
      int end = rest.indexOf('\n');
      rest = end < 0 ? "" : rest.substring(end + 1);
    }
    if (rest.startsWith("\n")) {
      rest = rest.substring(1);
    }
    return new MailTemplate(rest, false);
  }

  String render(Map<String, String> values) {
    String out = body;
    Matcher sections = SECTION.matcher(out);
    while (sections.find()) {
      String value = values.get(sections.group(1));
      String kept = value == null || value.isEmpty() ? "" : sections.group(2);
      out = out.substring(0, sections.start()) + kept + out.substring(sections.end());
      sections = SECTION.matcher(out);
    }
    Matcher matcher = VALUE.matcher(out);
    StringBuilder result = new StringBuilder(out.length() + 512);
    while (matcher.find()) {
      String value = values.get(matcher.group(1));
      if (value == null) {
        throw new IllegalStateException("No value for {{" + matcher.group(1) + "}}");
      }
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(html ? HtmlUtils.htmlEscape(value, "UTF-8") : value));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
