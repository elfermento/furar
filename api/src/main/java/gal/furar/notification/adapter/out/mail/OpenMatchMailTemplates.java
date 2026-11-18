// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.booking.OpenMatchNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.application.port.out.OpenMatchMailComposer;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.shared.domain.Language;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Os correos das partidas abertas (ADR-0040, punto 12) coa plantilla das reservas ({@code
 * mail/booking.html} e {@code .txt}, o mesmo renderizador que escapa): banda co nome do concello,
 * título co día e a hora, saúdo, o texto, a tarxeta da partida (instalación, enderezo, uso e parte)
 * onde o di a ADR, un parágrafo despois e un botón. Sen QR, sen {@code .ics} e sen prazo de
 * cancelación. Textos en {@code mail/notification_{gl,es}.properties} ({@code match.*}), no idioma
 * de cada persoa; horas en Europe/Madrid. Ningún nome máis ca o de quen organiza e, para quen
 * organiza, o de quen pide entrar.
 */
@Component
class OpenMatchMailTemplates implements OpenMatchMailComposer {

  private final MailTemplate html = MailTemplate.load("mail/booking.html");
  private final MailTemplate text = MailTemplate.load("mail/booking.txt");
  private final byte[] shield = BookingMailComposer.resource("mail/shield.png");
  private final MessageSource messages = BookingMailComposer.messages();
  private final String publicUrl;

  OpenMatchMailTemplates(@Value("${furar.public-url}") String publicUrl) {
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  @Override
  public OutgoingMail compose(
      OpenMatchNotice n,
      OpenMatchNotice.Recipient recipient,
      Language language,
      MunicipalityMailContext m) {
    BookingMailComposer.Texts t = new BookingMailComposer.Texts(messages, language);
    Locale locale = t.locale();
    ZonedDateTime start = n.start().atZone(BookingMailComposer.ZONE);
    String day = BookingMailComposer.day(start, locale);
    String hours = BookingMailComposer.hours(n.start(), n.end());
    String name =
        n.title() != null
            ? n.title()
            : t.get(
                "match.untitled",
                n.useName(),
                DateTimeFormatter.ofPattern("EEEE d", locale).format(start),
                BookingMailComposer.time(n.start()));
    String kind = "MATCH_" + n.kind().name();
    String subject = t.get("subject." + kind, name);
    String base = publicUrl + "/" + m.slug();
    String matchPage = base + "/partidas/" + n.matchId();
    String primary =
        BookingMailComposer.color(m.primaryColor(), BookingMailComposer.DEFAULT_PRIMARY);
    String onPrimary =
        BookingMailComposer.color(m.onPrimaryColor(), BookingMailComposer.DEFAULT_ON_PRIMARY);
    String deadlineDay =
        BookingMailComposer.day(n.deadline().atZone(BookingMailComposer.ZONE), locale);
    String deadlineTime = BookingMailComposer.time(n.deadline());

    Map<String, String> v = new HashMap<>();
    v.put("lang", language.code());
    v.put("subject", subject);
    v.put("preheader", n.facilityName() + " · " + day + " · " + hours);
    v.put("primary", primary);
    v.put("onPrimary", onPrimary);
    v.put("onPrimarySoft", BookingMailComposer.mix(onPrimary, primary, 0.85));
    v.put("separator", BookingMailComposer.mix(onPrimary, primary, 0.45));
    v.put("municipality", m.name());
    v.put("service", t.get("band.service"));
    v.put("title", t.get("title." + kind));
    v.put("day", day);
    v.put("hours", hours);
    v.put("greeting", t.get("greeting", BookingMailComposer.firstName(recipient.name())));
    v.put("dataWidth", "472");
    v.put("note", "");
    boolean card = false;
    String after = "";
    @Nullable String button = null;
    @Nullable String link = null;
    switch (n.kind()) {
      case CANCELLED -> {
        v.put("titleColor", BookingMailComposer.DANGER);
        v.put(
            "lead",
            n.unfilled()
                ? t.get("match.cancelled.unfilled", n.minimum(), deadlineDay, deadlineTime)
                : t.get("lead." + kind));
        card = true;
        after = t.get("match.otherDay", m.name());
        button = t.get("match.button.list");
        link = base;
      }
      case CHANGED -> {
        v.put("titleColor", BookingMailComposer.SUCCESS);
        String lead = t.get("lead." + kind, day, hours);
        v.put("lead", n.closed() ? lead + " " + t.get("match.changed.closed") : lead);
        card = true;
        after = t.get("match.changed.after");
        button = t.get("match.button.match");
        link = matchPage;
      }
      case REQUESTED -> {
        v.put("titleColor", BookingMailComposer.SUCCESS);
        v.put("lead", t.get("lead." + kind, blank(n.requesterName())));
        button = t.get("match.button.requests");
        link = matchPage;
      }
      case ACCEPTED -> {
        v.put("titleColor", BookingMailComposer.SUCCESS);
        v.put("lead", t.get("lead." + kind, n.organizerName()));
        card = true;
        after = t.get("match.accepted.after", n.minimum(), deadlineDay, deadlineTime);
        button = t.get("match.button.match");
        link = matchPage;
      }
      case REJECTED -> {
        v.put("titleColor", BookingMailComposer.DANGER);
        v.put("lead", t.get("lead." + kind, m.name()));
        button = t.get("match.button.list");
        link = base;
      }
      case HIDDEN -> {
        v.put("titleColor", BookingMailComposer.DANGER);
        String phone = m.contact().phone();
        v.put(
            "lead",
            phone == null
                ? t.get("lead." + kind)
                : t.get("lead." + kind) + " " + t.get("match.hidden.call", phone));
      }
    }
    if (card) {
      v.put("card", "yes");
      v.put("facility", n.facilityName());
      v.put("address", blank(n.facilityAddress()));
      v.put("labelUse", t.get("row.use"));
      v.put("use", n.useName());
      v.put("labelPart", t.get("row.part"));
      v.put("part", blank(n.partName()));
    }
    v.put("noShow", after);
    if (button != null && link != null) {
      v.put("button1", button);
      v.put("link1", link);
    }
    BookingMailComposer.footer(v, m, t, base);
    v.put(
        "why",
        t.get(recipient.bookingId() != null ? "footer.whyMatchOrganizer" : "footer.whyMatch"));
    return new OutgoingMail(
        t.get("from", m.name()),
        recipient.email(),
        subject,
        html.render(v),
        text.render(v),
        List.of(new OutgoingMail.Part("shield", "image/png", shield)),
        List.of());
  }

  private static String blank(@Nullable String value) {
    return value == null ? "" : value;
  }
}
