// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.booking.BookingNotice;
import gal.furar.booking.CancelledWaitsNotice;
import gal.furar.booking.WaitNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.application.port.in.NoShowFacts;
import gal.furar.notification.application.port.out.CancelledWaitsMailContent;
import gal.furar.notification.application.port.out.MailComposer;
import gal.furar.notification.application.port.out.MailContent;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.application.port.out.WaitMailContent;
import gal.furar.notification.domain.MailKind;
import gal.furar.shared.domain.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
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
 * Escribe o correo dunha reserva: banda co nome do concello na súa cor, tarxeta co título e o día e
 * a hora, saúdo, instalación e enderezo, uso, parte, prezo coa nota de pago do concello, QR co
 * código, prazo exacto para cancelar de balde, botóns (sen os de «as miñas reservas» se a persoa
 * non ten conta) e pé co contacto e «Privacidade». Textos en {@code
 * mail/notification_{gl,es}.properties}, no idioma da reserva; horas en Europe/Madrid. QR e escudo
 * incrustados ({@code cid:}), {@code .ics} adxunto.
 */
@Component
class BookingMailComposer implements MailComposer {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
  static final String DEFAULT_PRIMARY = "#0b4f8a";
  static final String DEFAULT_ON_PRIMARY = "#ffffff";
  static final String SUCCESS = "#2a7d3f";
  static final String DANGER = "#c9372c";

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
    // A falta: «luns 2 ás 19:00», sen o mes.
    boolean reminder = kind == MailKind.REMINDER;
    String subject =
        kind == MailKind.NO_SHOW || reminder
            ? t.get(
                "subject." + kind,
                b.facilityName(),
                DateTimeFormatter.ofPattern("EEEE d", locale).format(start),
                time(b.start()))
            : t.get("subject." + kind, b.facilityName(), day, time(b.start()));

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
    boolean noShow = kind == MailKind.NO_SHOW;
    // Sen QR, sen arquivo para o calendario e sen prazo: a reserva xa pasou.
    boolean plain = kind.cancellation() || noShow;
    v.put("titleColor", plain ? DANGER : SUCCESS);
    v.put("title", t.get("title." + kind));
    v.put("day", day);
    v.put("hours", hours);
    // O recordatorio non saúda: «Tes unha reserva en Narón.» (texto validado, ADR-0035).
    // A dunha entidade (ADR-0039, punto 12): no lugar do saúdo, de quen é.
    v.put(
        "greeting",
        reminder
            ? ""
            : b.organization()
                ? t.get(
                    b.session() ? "organization.session" : "organization.booking", b.holderName())
                : t.get("greeting", firstName(b.holderName())));
    v.put(
        "lead",
        kind == MailKind.NO_SHOW || reminder
            ? t.get("lead." + kind, m.name())
            : t.get("lead." + kind));
    v.put("card", "yes");
    v.put("facility", b.facilityName());
    v.put("address", blank(b.facilityAddress()));
    v.put("labelUse", t.get("row.use"));
    v.put("use", b.useName());
    // «Para quen»: só nos usos por prazas (ADR-0031).
    v.put("labelWho", t.get("row.who"));
    v.put("who", String.join(", ", b.participantNames()));
    v.put("labelPart", t.get("row.part"));
    v.put("part", blank(b.partName()));
    v.put("labelPrice", t.get("row.price"));
    // O recordatorio só lembra onde e cando: sen prezo.
    v.put("price", reminder ? "" : price(b, m, t));
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
    v.put("codeRow", plain ? "yes" : "");
    v.put("qr", plain ? "" : "yes");
    v.put("dataWidth", plain ? "472" : "290");
    v.put("qrAlt", t.get("qr.alt", b.code()));
    v.put("qrHint", t.get("qr.hint"));
    // Unha sesión cancélase ata que empeza: sen prazo (ADR-0039, punto 12).
    boolean open = !plain && !reminder && !b.session() && content.now().isBefore(b.cancelUntil());
    v.put("deadline", open ? "yes" : "");
    v.put("deadlineBefore", t.get("deadline.before"));
    v.put("deadlineAt", deadline(b.cancelUntil(), start, locale, t));
    v.put("deadlineAfter", t.get("deadline.after"));
    v.put(
        "deadlinePassed", plain || open || reminder || b.session() ? "" : t.get("deadline.passed"));
    v.put(
        "noShow",
        reminder ? reminderCancel(b, start, locale, t) : noShow(content.noShow(), locale, t));
    String base = publicUrl + "/" + m.slug();
    String mine = base + "/reservas";
    String reserve = base + "/instalacions/" + b.facilityId() + "/reservar";
    // Sen conta (reservou o persoal no seu nome) non hai «as miñas reservas»: fóra as ligazóns a
    // elas; o código, co QR, é o que lle queda para entrar.
    boolean account = b.holderHasAccount();
    switch (kind) {
      case CONFIRMED, CHANGED -> {
        if (account) {
          v.put("button1", t.get("button.view"));
          v.put("link1", mine);
          v.put("button2", t.get("button.calendar"));
          v.put("link2", mine);
        }
        v.put("note", t.get("note.attached"));
      }
      case CANCELLED_BY_PERSON -> {
        v.put("button1", t.get("button.rebook"));
        v.put("link1", reserve);
        if (account) {
          v.put("button2", t.get("button.mine"));
          v.put("link2", mine);
        }
        v.put("note", t.get("note.cancelled"));
      }
      case CANCELLED_BY_MUNICIPALITY -> {
        v.put("button1", t.get("button.choose"));
        v.put("link1", reserve);
        if (account) {
          v.put("button2", t.get("button.mine"));
          v.put("link2", mine);
        }
        v.put("note", t.get("note.cancelled"));
      }
      case REMINDER -> {
        // Ver, cambiar ou cancelar: desde «As miñas reservas», se ten conta.
        if (account) {
          v.put("button1", t.get("button.view"));
          v.put("link1", mine);
        }
        v.put("note", "");
      }
      case SLOT_RELEASED,
          WAIT_CANCELLED,
          MATCH_CANCELLED,
          MATCH_CHANGED,
          MATCH_REQUESTED,
          MATCH_ACCEPTED,
          MATCH_REJECTED,
          MATCH_HIDDEN,
          SEASON_CREATED,
          SEASON_ENDED,
          SEASON_REQUEST_REJECTED ->
          throw new IllegalArgumentException("Not the mail of a booking");
      case NO_SHOW -> {
        // Só se a reserva ten conta ligada: nunha do mostrador sen conta non sae.
        if (account) {
          v.put("button1", t.get("button.mine"));
          v.put("link1", mine);
        }
        // Se o servizo de deportes ten teléfono.
        String phone = m.contact().phone();
        v.put("note", phone == null ? "" : t.get("noShow.mistake", phone));
      }
    }
    MunicipalityMailContext.Contact c = m.contact();
    // A confirmación dunha reserva que fixo o persoal a nome deste correo: se o correo está mal
    // escrito, quen a recibe sabe a quen avisar (ADR-0031, punto 7). O teléfono; sen el, o correo.
    String service = c.phone() != null ? c.phone() : c.email();
    v.put(
        "notYours",
        kind == MailKind.CONFIRMED && b.bookedByStaff() && service != null
            ? t.get("note.notYours", service)
            : "");
    footer(v, m, t, base);
    // A reserva rexistrouna o persoal no seu nome: non a «fixo» ela (RGPD art. 14.3.b).
    v.put(
        "why",
        t.get(
            b.organization()
                ? "footer.whyOrganization"
                : b.bookedByStaff() ? "footer.whyStaff" : "footer.why"));
    if (reminder && content.optOutToken() != null) {
      // Sen sesión e sen datos persoais no enderezo: só a proba asinada (ADR-0035).
      v.put("optOut", t.get("reminder.optOut"));
      v.put(
          "optOutUrl",
          base
              + "/recordatorios/baixa?t="
              + URLEncoder.encode(content.optOutToken(), StandardCharsets.UTF_8));
    }

    List<OutgoingMail.Part> inline = new ArrayList<>();
    inline.add(new OutgoingMail.Part("shield", "image/png", shield));
    if (!plain) {
      inline.add(new OutgoingMail.Part("qr", "image/png", b.qrPng()));
    }
    if (noShow || reminder) {
      return new OutgoingMail(
          t.get("from", m.name()),
          b.holderEmail(),
          subject,
          html.render(v),
          text.render(v),
          inline,
          List.of());
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

  /**
   * «Quedou libre» (ADR-0033, punto 11): a mesma plantilla, sen saúdo, sen QR, sen {@code .ics} e
   * sen prazo de cancelación. A tarxeta coa instalación, o uso, o día e a hora, e o botón «Reservar
   * esta hora», que abre a páxina de reservar co uso, o día e a hora postos.
   */
  @Override
  public OutgoingMail compose(WaitMailContent content) {
    WaitNotice w = content.notice();
    MunicipalityMailContext m = content.municipality();
    Texts t = new Texts(messages, content.language());
    Locale locale = t.locale();
    MailKind kind = MailKind.SLOT_RELEASED;
    ZonedDateTime start = w.start().atZone(ZONE);
    String day = day(start, locale);
    String hours = hours(w.start(), w.end());
    String primary = color(m.primaryColor(), DEFAULT_PRIMARY);
    String onPrimary = color(m.onPrimaryColor(), DEFAULT_ON_PRIMARY);
    // «mércores 11 ás 19:00», sen o mes, coma o asunto da falta.
    String subject =
        t.get(
            "subject." + kind,
            w.facilityName(),
            DateTimeFormatter.ofPattern("EEEE d", locale).format(start),
            time(w.start()));

    Map<String, String> v = new HashMap<>();
    v.put("lang", content.language().code());
    v.put("subject", subject);
    v.put("preheader", w.facilityName() + " · " + day + " · " + hours);
    v.put("primary", primary);
    v.put("onPrimary", onPrimary);
    v.put("onPrimarySoft", mix(onPrimary, primary, 0.85));
    v.put("separator", mix(onPrimary, primary, 0.45));
    v.put("municipality", m.name());
    v.put("service", t.get("band.service"));
    v.put("titleColor", SUCCESS);
    v.put("title", t.get("title." + kind));
    v.put("day", day);
    v.put("hours", hours);
    v.put("lead", t.get("lead." + kind, m.name()));
    v.put("card", "yes");
    v.put("facility", w.facilityName());
    v.put("address", blank(w.facilityAddress()));
    v.put("labelUse", t.get("row.use"));
    v.put("use", w.useName());
    v.put("dataWidth", "472");
    // O parágrafo de despois da tarxeta: avisouse a todas, reserva a primeira que entre.
    v.put("noShow", t.get("wait.first"));
    String base = publicUrl + "/" + m.slug();
    v.put("button1", t.get("button.reserveSlot"));
    v.put(
        "link1",
        base
            + "/instalacions/"
            + w.facilityId()
            + "/reservar?uso="
            + w.useId()
            + "&data="
            + start.toLocalDate()
            + "&hora="
            + URLEncoder.encode(time(w.start()), StandardCharsets.UTF_8));
    v.put("note", "");
    footer(v, m, t, base);
    v.put("why", t.get("footer.whyWait"));
    return new OutgoingMail(
        t.get("from", m.name()),
        w.email(),
        subject,
        html.render(v),
        text.render(v),
        List.of(new OutgoingMail.Part("shield", "image/png", shield)),
        List.of());
  }

  /**
   * «Lista de espera cancelada» (ADR-0033, revisión do 2026-11-11): a mesma plantilla, sen tarxeta
   * de datos, QR, botóns nin adxunto. O título, a instalación e o texto con todas as horas polas
   * que esperaba a persoa; despois, buscar outra instalación ou chamar ao servizo de deportes (se
   * ten teléfono).
   */
  @Override
  public OutgoingMail compose(CancelledWaitsMailContent content) {
    CancelledWaitsNotice n = content.notice();
    MunicipalityMailContext m = content.municipality();
    Texts t = new Texts(messages, content.language());
    Locale locale = t.locale();
    MailKind kind = MailKind.WAIT_CANCELLED;
    String primary = color(m.primaryColor(), DEFAULT_PRIMARY);
    String onPrimary = color(m.onPrimaryColor(), DEFAULT_ON_PRIMARY);
    String subject = t.get("subject." + kind, n.facilityName());
    List<String> slots =
        n.slots().stream()
            .map(
                s ->
                    t.get(
                        "waitCancelled.slot", day(s.start().atZone(ZONE), locale), time(s.start())))
            .toList();
    boolean one = slots.size() == 1;

    Map<String, String> v = new HashMap<>();
    v.put("lang", content.language().code());
    v.put("subject", subject);
    v.put("preheader", n.facilityName());
    v.put("primary", primary);
    v.put("onPrimary", onPrimary);
    v.put("onPrimarySoft", mix(onPrimary, primary, 0.85));
    v.put("separator", mix(onPrimary, primary, 0.45));
    v.put("municipality", m.name());
    v.put("service", t.get("band.service"));
    v.put("titleColor", DANGER);
    v.put("title", t.get("title." + kind));
    v.put("day", n.facilityName());
    v.put("hours", "");
    v.put(
        "lead",
        t.get("lead." + kind, n.facilityName())
            + " "
            + t.get(
                one ? "waitCancelled.one" : "waitCancelled.many",
                join(slots, t.get("waitCancelled.and"))));
    // Sen teléfono do servizo, a frase remata en «Podes buscar outra instalación.».
    String phone = m.contact().phone();
    v.put(
        "noShow",
        phone == null ? t.get("waitCancelled.search") : t.get("waitCancelled.call", phone));
    v.put("note", "");
    String base = publicUrl + "/" + m.slug();
    footer(v, m, t, base);
    v.put("why", t.get("footer.whyWaitCancelled"));
    return new OutgoingMail(
        t.get("from", m.name()),
        n.email(),
        subject,
        html.render(v),
        text.render(v),
        List.of(new OutgoingMail.Part("shield", "image/png", shield)),
        List.of());
  }

  /** «a, b e c». */
  static String join(List<String> items, String and) {
    if (items.size() == 1) {
      return items.getFirst();
    }
    return String.join(", ", items.subList(0, items.size() - 1))
        + " "
        + and
        + " "
        + items.getLast();
  }

  /**
   * A frase do recordatorio (texto validado, ADR-0035): cancelar antes do prazo para cancelar de
   * balde, ou, sen prazo (o prazo é o propio inicio), cancelar sen máis.
   */
  private static String reminderCancel(
      BookingNotice b, ZonedDateTime start, Locale locale, Texts t) {
    if (!b.cancelUntil().isBefore(b.start())) {
      return t.get("reminder.cancel");
    }
    return t.get("reminder.cancelBefore", deadline(b.cancelUntil(), start, locale, t));
  }

  /** O pé común: enderezo e contacto do servizo e a ligazón a «Privacidade». */
  static void footer(Map<String, String> v, MunicipalityMailContext m, Texts t, String base) {
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
    v.put("privacy", t.get("footer.privacy"));
    // Páxina propia do concello (ADR-0017 punto 8): a ligazón vai a ela.
    v.put("privacyUrl", m.privacyUrl() == null ? base + "/privacidade" : m.privacyUrl());
    // Sen páxina (modo «Sen páxina»), sen ligazón.
    v.put("privacyLink", m.privacyPublished() ? "yes" : "");
  }

  /**
   * Cantas faltas leva a persoa e cando se bloquea, ou ata cando o está; baleiro coa penalización
   * apagada (ADR-0032, punto 10). Cunha soa falta, en singular.
   */
  private static String noShow(@Nullable NoShowFacts facts, Locale locale, Texts t) {
    if (facts == null || facts.faults() < 1) {
      return "";
    }
    String one = facts.faults() == 1 ? ".one" : "";
    LocalDate until = facts.blockedUntil();
    if (until != null) {
      return t.get(
          "noShow.blocked" + one,
          facts.faults(),
          facts.windowDays(),
          DateTimeFormatter.ofPattern("d 'de' MMMM", locale).format(until));
    }
    return t.get(
        "noShow.count" + one,
        facts.faults(),
        facts.windowDays(),
        facts.threshold(),
        facts.blockDays());
  }

  /** «30,00 € · No mostrador, ao chegar»; coas prazas se son varias; gratis; sen prezo, nada. */
  private static String price(BookingNotice b, MunicipalityMailContext m, Texts t) {
    return switch (b.priceKind()) {
      case NONE -> "";
      case FREE -> withSeats(t.get("price.free"), b, t);
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
        yield withSeats(amount, b, t) + " · " + note;
      }
    };
  }

  /** «6,00 € · 2 prazas» cando a reserva leva varias (ADR-0031). */
  private static String withSeats(String amount, BookingNotice b, Texts t) {
    return b.seats() < 2 ? amount : t.get("price.seats", amount, b.seats());
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

  /** «Ana» de «Ana Rey»: o saúdo leva só o nome. */
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

  static MessageSource messages() {
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
  record Texts(MessageSource messages, Language language) {

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
