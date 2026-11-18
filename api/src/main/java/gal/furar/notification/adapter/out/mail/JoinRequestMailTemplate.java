// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.notification.application.port.out.JoinRequestMailComposer;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.JoinRequest;
import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * O correo dunha solicitude de alta (ADR-0042) para quen opera o servizo: un correo de traballo,
 * sen a cor de ningún concello. Asunto «Solicitude de alta: ‹concello›», todos os campos (os
 * opcionais, só se os hai), a data na hora de Galicia, o seguinte paso e {@code Reply-To} ao correo
 * de quen a pide. No idioma no que se encheu o formulario (ADR-0042, revisión do 2026-11-17);
 * textos en {@code mail/join-request_{gl,es}.properties}.
 */
@Component
class JoinRequestMailTemplate implements JoinRequestMailComposer {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final MessageSource messages = messages();

  @Override
  public OutgoingMail compose(
      JoinRequest request, Language language, String recipient, Instant receivedAt) {
    Locale locale = Locale.of(language.code());
    Texts text = (key, args) -> messages.getMessage(key, args.length == 0 ? null : args, locale);
    String when = when(text, receivedAt);
    List<Map.Entry<String, String>> rows = new ArrayList<>();
    rows.add(Map.entry(text.text("municipality"), request.municipality()));
    rows.add(Map.entry(text.text("name"), request.name()));
    add(rows, text.text("role"), request.role());
    rows.add(Map.entry(text.text("email"), request.email()));
    rows.add(Map.entry(text.text("phone"), request.phone()));
    add(rows, text.text("message"), request.message());
    rows.add(Map.entry(text.text("receivedAt"), when));
    String subject = text.text("subject", request.municipality());
    String lead = text.text("lead", when, request.email());
    return new OutgoingMail(
        text.text("from"),
        recipient,
        subject,
        html(text, language, subject, lead, rows),
        plain(text, lead, rows),
        List.of(),
        List.of(),
        request.email());
  }

  private static String html(
      Texts text,
      Language language,
      String subject,
      String lead,
      List<Map.Entry<String, String>> rows) {
    StringBuilder table = new StringBuilder();
    for (Map.Entry<String, String> row : rows) {
      table
          .append("<tr><th scope=\"row\" style=\"text-align:left;vertical-align:top;")
          .append("padding:6px 16px 6px 0;color:#4a5560;font-weight:600\">")
          .append(escape(row.getKey()))
          .append("</th><td style=\"padding:6px 0;white-space:pre-line\">")
          .append(escape(row.getValue()))
          .append("</td></tr>");
    }
    return "<!doctype html><html lang=\""
        + language.code()
        + "\"><head><meta charset=\"utf-8\"><title>"
        + escape(subject)
        + "</title></head><body style=\"margin:0;padding:24px;font-family:system-ui,sans-serif;"
        + "font-size:16px;line-height:1.5;color:#1d252c;background:#ffffff\">"
        + "<h1 style=\"font-size:20px;margin:0 0 12px\">"
        + escape(text.text("title"))
        + "</h1><p style=\"margin:0 0 16px\">"
        + escape(lead)
        + "</p><table role=\"presentation\" style=\"border-collapse:collapse\">"
        + table
        + "</table><p style=\"margin:24px 0 8px\">"
        + escape(text.text("next"))
        + "</p><p style=\"margin:0;color:#4a5560;font-size:14px\">"
        + escape(text.text("privacy"))
        + "</p></body></html>";
  }

  private static String plain(Texts text, String lead, List<Map.Entry<String, String>> rows) {
    StringBuilder out =
        new StringBuilder(text.text("title")).append("\n\n").append(lead).append("\n\n");
    for (Map.Entry<String, String> row : rows) {
      out.append(row.getKey()).append(": ").append(row.getValue()).append('\n');
    }
    return out.append('\n')
        .append(text.text("next"))
        .append("\n\n")
        .append(text.text("privacy"))
        .append('\n')
        .toString();
  }

  /** «17/11/2026 ás 18:05» («a las 18:05»), sempre na hora de Galicia. */
  private static String when(Texts text, Instant at) {
    ZonedDateTime zoned = at.atZone(BookingMailComposer.ZONE);
    return text.text("when", DATE.format(zoned), TIME.format(zoned), zoned.getHour());
  }

  private static void add(
      List<Map.Entry<String, String>> rows, String label, @Nullable String value) {
    if (value != null && !value.isBlank()) {
      rows.add(Map.entry(label, value));
    }
  }

  /** Un texto do idioma do correo. */
  @FunctionalInterface
  private interface Texts {
    String text(String key, Object... args);
  }

  private static String escape(String value) {
    return HtmlUtils.htmlEscape(value, "UTF-8");
  }

  private static MessageSource messages() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("mail/join-request");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
