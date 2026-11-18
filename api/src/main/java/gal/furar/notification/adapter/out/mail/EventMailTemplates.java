// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.EventDecisionMail;
import gal.furar.notification.EventMailFacts;
import gal.furar.notification.EventNoticeMail;
import gal.furar.notification.EventSessionMail;
import gal.furar.notification.EventWaitlistMail;
import gal.furar.notification.application.port.out.EventMailComposer;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PlaceName;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
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
 * Escribe os correos dos eventos do concello (ADR-0038) co mesmo deseño ca os das incidencias
 * (banda co nome do concello na súa cor e unha tarxeta: {@code mail/incident.html} e {@code .txt},
 * que son xenéricos): título, saúdo, o evento (nome, cando e onde) e o botón «Ver o evento» (a súa
 * ficha). Pé co contacto do servizo de deportes e por que chega. Textos en {@code
 * mail/event_{gl,es}}. Nunha actividade con horario (ADR-0041), no canto da data, o horario («luns
 * e mércores, de 18:00 a 19:00, do 1 de decembro ao 30 de xuño»); e tres correos máis: o aviso da
 * lista de espera, a sesión cancelada e a actividade que remata antes.
 */
@Component
class EventMailTemplates implements EventMailComposer {

  private final MailTemplate html = MailTemplate.load("mail/incident.html");
  private final MailTemplate text = MailTemplate.load("mail/incident.txt");
  private final byte[] shield = BookingMailComposer.resource("mail/shield.png");
  private final MessageSource messages = messages();
  private final String publicUrl;

  EventMailTemplates(@Value("${furar.public-url}") String publicUrl) {
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  @Override
  public OutgoingMail decision(EventDecisionMail mail, MunicipalityMailContext m) {
    String kind = mail.accepted() ? "accepted" : "rejected";
    return compose(
        m, mail.event(), kind, mail.recipient(), mail.language(), mail.name(), mail.accepted());
  }

  @Override
  public OutgoingMail waitlist(EventWaitlistMail mail, MunicipalityMailContext m) {
    Locale locale = Locale.of(mail.language().code());
    EventMailFacts event = mail.event();
    String lead =
        text(
            "waitlist.lead",
            locale,
            event.name(mail.language()),
            when(event, locale),
            event.place(mail.language()),
            when(mail.exclusiveUntil(), locale));
    String forWhom = mail.dependentName();
    return compose(
        m,
        event,
        "waitlist",
        mail.recipient(),
        mail.language(),
        mail.name(),
        lead,
        forWhom == null ? "" : text("waitlist.for", locale),
        forWhom == null ? "" : forWhom,
        text("waitlist.button", locale));
  }

  @Override
  public OutgoingMail session(
      EventSessionMail mail, EventNoticeMail.Recipient recipient, MunicipalityMailContext m) {
    Locale locale = Locale.of(recipient.language().code());
    EventMailFacts event = mail.event();
    boolean cancelled = mail.kind() == EventSessionMail.Kind.SESSION_CANCELLED;
    String kind = cancelled ? "session" : "ended";
    String lead =
        cancelled
            ? text(
                "session.lead",
                locale,
                event.name(recipient.language()),
                when(mail.startsAt(), locale),
                event.place(recipient.language()))
            : text(
                "ended.lead", locale, event.name(recipient.language()), date(mail.date(), locale));
    return compose(
        m,
        event,
        kind,
        recipient.email(),
        recipient.language(),
        recipient.name(),
        lead,
        "",
        "",
        text("button", locale));
  }

  @Override
  public OutgoingMail notice(
      EventNoticeMail mail, EventNoticeMail.Recipient recipient, MunicipalityMailContext m) {
    String kind = mail.kind() == EventNoticeMail.Kind.MOVED ? "moved" : "cancelled";
    return compose(
        m,
        mail.event(),
        kind,
        recipient.email(),
        recipient.language(),
        recipient.name(),
        mail.kind() == EventNoticeMail.Kind.MOVED);
  }

  private OutgoingMail compose(
      MunicipalityMailContext m,
      EventMailFacts event,
      String kind,
      String recipient,
      Language language,
      @Nullable String name,
      boolean link) {
    Locale locale = Locale.of(language.code());
    String leadKey = event.schedule() == null ? kind + ".lead" : kind + ".scheduledLead";
    String lead =
        text(leadKey, locale, event.name(language), when(event, locale), event.place(language));
    return compose(
        m,
        event,
        kind,
        recipient,
        language,
        name,
        lead,
        "",
        "",
        link ? text("button", locale) : "");
  }

  private OutgoingMail compose(
      MunicipalityMailContext m,
      EventMailFacts event,
      String kind,
      String recipient,
      Language language,
      @Nullable String name,
      String lead,
      String messageLabel,
      String message,
      String button) {
    Locale locale = Locale.of(language.code());
    String eventName = event.name(language);
    Map<String, String> v = base(m, language, locale);
    String subject = text(kind + ".subject", locale, eventName);
    v.put("subject", subject);
    v.put("preheader", text(kind + ".preheader", locale, eventName));
    v.put("title", text(kind + ".title", locale));
    v.put(
        "greeting",
        name == null || name.isBlank()
            ? text("hello", locale)
            : text("helloName", locale, BookingMailComposer.firstName(name)));
    v.put("lead", lead);
    v.put("messageLabel", messageLabel);
    v.put("message", message);
    v.put("button", button);
    v.put(
        "link", button.isEmpty() ? "" : publicUrl + "/" + m.slug() + "/eventos/" + event.eventId());
    v.put("why", text("why", locale, PlaceName.of(m.name(), language)));
    return new OutgoingMail(
        text("from", locale, m.name()),
        recipient,
        subject,
        html.render(v),
        text.render(v),
        List.of(new OutgoingMail.Part("shield", "image/png", shield)),
        List.of());
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

  /**
   * Cando: nun evento dun día, a data e a hora; nunha actividade, o horario («luns e mércores, de
   * 18:00 a 19:00, do 1 de decembro ao 30 de xuño»).
   */
  private String when(EventMailFacts event, Locale locale) {
    EventMailFacts.Schedule s = event.schedule();
    if (s == null) {
      return when(event.startsAt(), locale);
    }
    List<String> days =
        s.weekdays().stream().map(d -> text("day." + DayOfWeek.of(d).getValue(), locale)).toList();
    String joined =
        days.size() == 1
            ? days.getFirst()
            : String.join(", ", days.subList(0, days.size() - 1))
                + text("and", locale)
                + days.getLast();
    return text(
        "schedule",
        locale,
        joined,
        minutes(s.startMinute()),
        minutes(s.endMinute()),
        date(s.firstDate(), locale),
        date(s.lastDate(), locale));
  }

  private static String minutes(int minute) {
    return String.format(Locale.ROOT, "%02d:%02d", (minute / 60) % 24, minute % 60);
  }

  /** «1 de decembro». */
  private String date(LocalDate date, Locale locale) {
    return text(
        "date", locale, date.getDayOfMonth(), text("month." + date.getMonthValue(), locale));
  }

  /** «domingo 20 de decembro ás 11:00», sempre na hora de Galicia. */
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
    source.setBasename("mail/event");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
