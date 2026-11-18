// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.IncidentReportedMail;
import gal.furar.notification.IncidentResolvedMail;
import gal.furar.notification.application.port.out.IncidentMailComposer;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PlaceName;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Escribe os correos das incidencias (ADR-0036) co estilo dos das reservas: banda co nome do
 * concello na súa cor e unha tarxeta. «Nova incidencia» (á xestión): a instalación, o tipo e cando,
 * e o botón «Ver as incidencias» (a lista da xestión coa incidencia aberta); sen a descrición nin
 * quen avisou. «Resolvemos o teu aviso» (á persoa): a instalación, o tipo e cando avisou e, se a
 * hai, a mensaxe do concello. Pé co contacto do servizo de deportes e por que chega. Textos en
 * {@code mail/incident_{gl,es}}.
 */
@Component
class IncidentMailTemplates implements IncidentMailComposer {

  private final MailTemplate html = MailTemplate.load("mail/incident.html");
  private final MailTemplate text = MailTemplate.load("mail/incident.txt");
  private final byte[] shield = BookingMailComposer.resource("mail/shield.png");
  private final MessageSource messages = messages();
  private final String publicUrl;

  IncidentMailTemplates(@Value("${furar.public-url}") String publicUrl) {
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  @Override
  public OutgoingMail reported(
      IncidentReportedMail mail, String recipient, Language language, MunicipalityMailContext m) {
    Locale locale = Locale.of(language.code());
    String type = text("type." + mail.type(), locale);
    Map<String, String> v = base(m, language, locale);
    String subject = text("reported.subject", locale, mail.facilityName());
    v.put("subject", subject);
    v.put("preheader", text("reported.preheader", locale, type, mail.facilityName()));
    v.put("title", text("reported.title", locale));
    v.put(
        "lead",
        text("reported.lead", locale, mail.facilityName(), type, when(mail.reportedAt(), locale)));
    v.put("messageLabel", "");
    v.put("message", "");
    v.put("button", text("reported.button", locale));
    v.put(
        "link",
        publicUrl + "/" + m.slug() + "/xestion/incidencias?incidencia=" + mail.incidentId());
    v.put("why", text("reported.why", locale, PlaceName.of(m.name(), language)));
    return outgoing(m, recipient, subject, v, locale);
  }

  @Override
  public OutgoingMail resolved(IncidentResolvedMail mail, MunicipalityMailContext m) {
    Locale locale = Locale.of(mail.language().code());
    String type = text("type." + mail.type(), locale);
    Map<String, String> v = base(m, mail.language(), locale);
    String subject = text("resolved.subject", locale, mail.facilityName());
    v.put("subject", subject);
    v.put("preheader", text("resolved.preheader", locale, mail.facilityName()));
    v.put("title", text("resolved.title", locale));
    v.put(
        "greeting",
        mail.name() == null || mail.name().isBlank()
            ? text("resolved.hello", locale)
            : text("resolved.helloName", locale, BookingMailComposer.firstName(mail.name())));
    v.put(
        "lead",
        text("resolved.lead", locale, mail.facilityName(), type, when(mail.reportedAt(), locale)));
    String message = mail.message() == null ? "" : mail.message().strip();
    v.put("messageLabel", message.isEmpty() ? "" : text("resolved.messageLabel", locale));
    v.put("message", message);
    v.put("button", "");
    v.put("link", "");
    v.put("why", text("resolved.why", locale, PlaceName.of(m.name(), mail.language())));
    return outgoing(m, mail.recipient(), subject, v, locale);
  }

  private Map<String, String> base(MunicipalityMailContext m, Language language, Locale locale) {
    String primary =
        BookingMailComposer.color(m.primaryColor(), BookingMailComposer.DEFAULT_PRIMARY);
    String onPrimary =
        BookingMailComposer.color(m.onPrimaryColor(), BookingMailComposer.DEFAULT_ON_PRIMARY);
    Map<String, String> v = new HashMap<>();
    v.put("lang", language.code());
    v.put("primary", primary);
    v.put("onPrimary", onPrimary);
    v.put("onPrimarySoft", BookingMailComposer.mix(onPrimary, primary, 0.85));
    v.put("separator", BookingMailComposer.mix(onPrimary, primary, 0.45));
    v.put("municipality", m.name());
    v.put("service", text("band.service", locale));
    v.put("greeting", "");
    MunicipalityMailContext.Contact c = m.contact();
    v.put("footerAddress", blank(c.address()));
    v.put("footerPhone", blank(c.phone()));
    v.put("footerEmail", blank(c.email()));
    boolean contact = c.phone() != null || c.email() != null;
    v.put("footerContact", contact ? "yes" : "");
    v.put("footerBlock", contact || c.address() != null ? "yes" : "");
    v.put("footerBreak", contact && c.address() != null ? "yes" : "");
    v.put("footerSeparator", c.phone() != null && c.email() != null ? "yes" : "");
    return v;
  }

  private OutgoingMail outgoing(
      MunicipalityMailContext m,
      String recipient,
      String subject,
      Map<String, String> v,
      Locale locale) {
    return new OutgoingMail(
        text("from", locale, m.name()),
        recipient,
        subject,
        html.render(v),
        text.render(v),
        List.of(new OutgoingMail.Part("shield", "image/png", shield)),
        List.of());
  }

  /** «mércores 11 de novembro ás 10:42», sempre na hora de Galicia. */
  private String when(Instant at, Locale locale) {
    ZonedDateTime zoned = at.atZone(BookingMailComposer.ZONE);
    return text(
        "when",
        locale,
        BookingMailComposer.day(zoned, locale),
        DateTimeFormatter.ofPattern("HH:mm").format(zoned),
        zoned.getHour());
  }

  private String text(String key, Locale locale, Object... args) {
    return messages.getMessage(key, args.length == 0 ? null : args, locale);
  }

  private static String blank(@Nullable String value) {
    return value == null ? "" : value;
  }

  private static MessageSource messages() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("mail/incident");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
