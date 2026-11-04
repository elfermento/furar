// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.booking.BookingNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.application.port.out.MailComposer;
import gal.furar.notification.application.port.out.MailContent;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.domain.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * Escribe o correo dunha reserva (maqueta 29, «C · en unha liña»): banda co nome do concello na súa
 * cor, tarxeta co título e o día e a hora, saúdo, instalación e enderezo, uso, parte, prezo coa
 * nota de pago do concello, QR co código, prazo exacto para cancelar de balde, botóns e pé co
 * contacto e «Privacidade». Textos en {@code mail/notification_{gl,es}.properties}, no idioma da
 * reserva; horas en Europe/Madrid. QR e escudo incrustados ({@code cid:}), {@code .ics} adxunto.
 */
@Component
class BookingMailComposer implements MailComposer {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
  static final String DEFAULT_PRIMARY = "#0b4f8a";
  static final String DEFAULT_ON_PRIMARY = "#ffffff";
  private static final String SUCCESS = "#2a7d3f";
  private static final String DANGER = "#c9372c";

  private final MailTemplate html = MailTemplate.load("mail/booking.html");
  private final MailTemplate text = MailTemplate.load("mail/booking.txt");
  private final byte[] shield = resource("mail/shield.png");
  private final MessageSource messages = messages();
  private final String publicUrl;

  BookingMailComposer(@Value("${furar.public-url}") String publicUrl) {
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  @Override
  public OutgoingMail compose(MailContent content) {
    BookingNotice b = content.booking();
    MunicipalityMailContext m = content.municipality();
    Texts t = new Texts(messages, content.language());
    Locale locale = t.locale();
    MailKind kind = content.kind();
    ZonedDateTime start = b.start().atZone(ZONE);
    String day = day(start, locale);
    String hours = hours(b.start(), b.end());
    String primary = color(m.primaryColor(), DEFAULT_PRIMARY);
    String onPrimary = color(m.onPrimaryColor(), DEFAULT_ON_PRIMARY);
    String subject = t.get("subject." + kind, b.facilityName(), day, time(b.start()));

    Map<String, String> v = new HashMap<>();
    v.put("lang", content.language().code());
    v.put("subject", subject);
    v.put("preheader", b.facilityName() + " · " + day + " · " + hours);
    v.put("primary", primary);
    v.put("onPrimary", onPrimary);
    v.put("onPrimarySoft", mix(onPrimary, primary, 0.85));
    v.put("separator", mix(onPrimary, primary, 0.45));
    v.put("municipality", m.name());
    v.put("service", t.get("band.service"));
    v.put("titleColor", kind.cancellation() ? DANGER : SUCCESS);
    v.put("title", t.get("title." + kind));
    v.put("day", day);
    v.put("hours", hours);
    v.put("greeting", t.get("greeting", firstName(b.holderName())));
    v.put("lead", t.get("lead." + kind));
    v.put("facility", b.facilityName());
    v.put("address", blank(b.facilityAddress()));
    v.put("labelUse", t.get("row.use"));
    v.put("use", b.useName());
    v.put("labelPart", t.get("row.part"));
    v.put("part", blank(b.partName()));
    v.put("labelPrice", t.get("row.price"));
    v.put("price", price(b, m, t));
    v.put("labelBefore", t.get("row.before"));
    v.put(
        "before",
        content.previousStart() == null || content.previousEnd() == null
            ? ""
            : day(content.previousStart().atZone(ZONE), locale)
                + ", "
                + hours(content.previousStart(), content.previousEnd()));
    v.put("labelCode", t.get("row.code"));
    v.put("code", b.code());
    v.put("codeRow", kind.cancellation() ? "yes" : "");
    v.put("qr", kind.cancellation() ? "" : "yes");
    v.put("dataWidth", kind.cancellation() ? "472" : "290");
    v.put("qrAlt", t.get("qr.alt", b.code()));
    v.put("qrHint", t.get("qr.hint"));
    boolean open = !kind.cancellation() && content.now().isBefore(b.cancelUntil());
    v.put("deadline", open ? "yes" : "");
    v.put("deadlineBefore", t.get("deadline.before"));
    v.put("deadlineAt", deadline(b.cancelUntil(), start, locale, t));
    v.put("deadlineAfter", t.get("deadline.after"));
    v.put("deadlinePassed", kind.cancellation() || open ? "" : t.get("deadline.passed"));
    String base = publicUrl + "/" + m.slug();
    String mine = base + "/reservas";
    String reserve = base + "/instalacions/" + b.facilityId() + "/reservar";
    switch (kind) {
      case CONFIRMED, CHANGED -> {
        v.put("button1", t.get("button.view"));
        v.put("link1", mine);
        v.put("button2", t.get("button.calendar"));
        v.put("link2", mine);
        v.put("note", t.get("note.attached"));
      }
      case CANCELLED_BY_PERSON -> {
        v.put("button1", t.get("button.rebook"));
        v.put("link1", reserve);
        v.put("button2", t.get("button.mine"));
        v.put("link2", mine);
        v.put("note", t.get("note.cancelled"));
      }
      case CANCELLED_BY_MUNICIPALITY -> {
        v.put("button1", t.get("button.choose"));
        v.put("link1", reserve);
        v.put("button2", t.get("button.mine"));
        v.put("link2", mine);
        v.put("note", t.get("note.cancelled"));
      }
    }
    MunicipalityMailContext.Contact c = m.contact();
    // O mesmo pé ca a invitación ao persoal: só enderezo e contacto, sen repetir o nome.
    v.put("footerAddress", blank(c.address()));
    v.put("footerPhone", blank(c.phone()));
    v.put("footerEmail", blank(c.email()));
    boolean contact = c.phone() != null || c.email() != null;
    v.put("footerContact", contact ? "yes" : "");
    v.put("footerBlock", contact || c.address() != null ? "yes" : "");
    v.put("footerBreak", contact && c.address() != null ? "yes" : "");
    v.put("footerSeparator", c.phone() != null && c.email() != null ? "yes" : "");
    v.put("why", t.get("footer.why"));
    v.put("privacy", t.get("footer.privacy"));
    // Páxina propia do concello (ADR-0017 punto 8): a ligazón vai a ela.
    v.put("privacyUrl", m.privacyUrl() == null ? base + "/privacidade" : m.privacyUrl());
    // Sen páxina (modo «Sen páxina»), sen ligazón.
    v.put("privacyLink", m.privacyPublished() ? "yes" : "");

    List<OutgoingMail.Part> inline = new ArrayList<>();
    inline.add(new OutgoingMail.Part("shield", "image/png", shield));
    if (!kind.cancellation()) {
      inline.add(new OutgoingMail.Part("qr", "image/png", b.qrPng()));
    }
    String method = b.calendar().contains("METHOD:CANCEL") ? "CANCEL" : "PUBLISH";
    return new OutgoingMail(
        t.get("from", m.name()),
        b.holderEmail(),
        subject,
        html.render(v),
        text.render(v),
        inline,
        List.of(
            new OutgoingMail.Part(
                "reserva-" + b.code() + ".ics",
                "text/calendar; charset=UTF-8; method=" + method,
                b.calendar().getBytes(StandardCharsets.UTF_8))));
  }

  /** «30,00 € · No mostrador, ao chegar»; gratis; sen prezo, nada. */
  private static String price(BookingNotice b, MunicipalityMailContext m, Texts t) {
    return switch (b.priceKind()) {
      case NONE -> "";
      case FREE -> t.get("price.free");
      case PAID -> {
        String amount =
            NumberFormat.getCurrencyInstance(Locale.of(t.locale().getLanguage(), "ES"))
                .format(b.amountCents() == null ? 0 : b.amountCents() / 100.0);
        String note =
            switch (b.payment()) {
              case PAID -> t.get("price.paid");
              case REFUNDED -> t.get("price.refunded");
              case TO_REFUND -> t.note(m.refundNote());
              case NONE, PENDING -> t.note(m.paymentNote());
            };
        yield amount + " · " + note;
      }
    };
  }

  /** «luns 26 ás 18:00»; co mes se non é o do inicio da reserva. */
  private static String deadline(Instant until, ZonedDateTime start, Locale locale, Texts t) {
    ZonedDateTime at = until.atZone(ZONE);
    String pattern = at.getMonth() == start.getMonth() ? "EEEE d" : "EEEE d 'de' MMMM";
    return t.get(
        "deadline.at", DateTimeFormatter.ofPattern(pattern, locale).format(at), time(until));
  }

  static String day(ZonedDateTime at, Locale locale) {
    return DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale).format(at);
  }

  static String hours(Instant start, Instant end) {
    return time(start) + "–" + time(end);
  }

  static String time(Instant at) {
    return DateTimeFormatter.ofPattern("HH:mm").format(at.atZone(ZONE));
  }

  /** «Ana» de «Ana Rey»: o saúdo da maqueta. */
  static String firstName(String name) {
    String trimmed = name.strip();
    int space = trimmed.indexOf(' ');
    return space < 0 ? trimmed : trimmed.substring(0, space);
  }

  private static String blank(@Nullable String value) {
    return value == null ? "" : value;
  }

  static String color(String value, String fallback) {
    return value != null && HEX.matcher(value).matches() ? value : fallback;
  }

  /** {@code a} sobre {@code b} con opacidade {@code alpha}: as cores suaves da banda. */
  static String mix(String a, String b, double alpha) {
    int ca = Integer.parseInt(a.substring(1), 16);
    int cb = Integer.parseInt(b.substring(1), 16);
    int r = (int) Math.round(((ca >> 16) & 0xFF) * alpha + ((cb >> 16) & 0xFF) * (1 - alpha));
    int g = (int) Math.round(((ca >> 8) & 0xFF) * alpha + ((cb >> 8) & 0xFF) * (1 - alpha));
    int bl = (int) Math.round((ca & 0xFF) * alpha + (cb & 0xFF) * (1 - alpha));
    return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, bl);
  }

  private static MessageSource messages() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("mail/notification");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  static byte[] resource(String name) {
    try (InputStream in = BookingMailComposer.class.getClassLoader().getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("Missing mail resource: " + name);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Os textos dun idioma. */
  private record Texts(MessageSource messages, Language language) {

    Locale locale() {
      return Locale.of(language.code());
    }

    String get(String key, Object... args) {
      return messages.getMessage(key, args.length == 0 ? null : args, locale());
    }

    String note(MunicipalityMailContext.Text note) {
      return language == Language.ES ? note.es() : note.gl();
    }
  }
}
