// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.booking.SeriesNotice;
import gal.furar.booking.SeriesRequestNotice;
import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.notification.application.port.out.SeriesMailComposer;
import gal.furar.shared.domain.Language;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Os correos das tempadas á entidade (ADR-0039, punto 12) coa plantilla das reservas ({@code
 * mail/booking.html} e {@code .txt}, o mesmo renderizador que escapa): banda co nome do concello,
 * título, os días e as horas, a liña da entidade no lugar do saúdo, o texto, a tarxeta coa
 * instalación, o uso e a parte, un parágrafo despois e un botón. Sen QR nin {@code .ics}. Textos en
 * {@code mail/notification_{gl,es}.properties} ({@code season.*}); horas en Europe/Madrid. A
 * mensaxe do concello vai tal cal, en texto plano.
 */
@Component
class SeriesMailTemplates implements SeriesMailComposer {

  private final MailTemplate html = MailTemplate.load("mail/booking.html");
  private final MailTemplate text = MailTemplate.load("mail/booking.txt");
  private final byte[] shield = BookingMailComposer.resource("mail/shield.png");
  private final MessageSource messages = BookingMailComposer.messages();
  private final String publicUrl;

  SeriesMailTemplates(@Value("${furar.public-url}") String publicUrl) {
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  @Override
  public OutgoingMail series(
      SeriesNotice n,
      @Nullable LocalDate endedFrom,
      int cancelled,
      Language language,
      MunicipalityMailContext m) {
    BookingMailComposer.Texts t = new BookingMailComposer.Texts(messages, language);
    Locale locale = t.locale();
    boolean ended = endedFrom != null;
    String kind = ended ? "SEASON_ENDED" : n.fromRequest() ? "SEASON_APPROVED" : "SEASON_CREATED";
    Map<String, String> v = base(t, m, kind, t.get("subject." + kind, n.facilityName()));
    v.put("titleColor", ended ? BookingMailComposer.DANGER : BookingMailComposer.SUCCESS);
    v.put("day", weekdays(n.weekdays(), locale));
    v.put("hours", minutes(n.startMinute()) + "–" + minutes(n.endMinute()));
    v.put("greeting", t.get("organization.season", n.organizationName()));
    String base = publicUrl + "/" + m.slug();
    if (ended) {
      v.put("lead", t.get("lead.SEASON_ENDED", date(endedFrom, locale), cancelled));
      v.put("noShow", "");
    } else {
      v.put(
          "lead",
          t.get(
              "lead.SEASON_CREATED",
              date(n.firstDate(), locale),
              date(n.lastDate(), locale),
              n.sessions()));
      v.put(
          "noShow",
          n.skipped().isEmpty()
              ? ""
              : t.get(
                  "season.skipped",
                  n.skipped().stream()
                      .map(d -> date(d, locale))
                      .collect(Collectors.joining(", "))));
      v.put("button1", t.get("season.button"));
      v.put("link1", base + "/reservas");
    }
    card(v, t, n.facilityName(), n.facilityAddress(), n.useName(), n.partName());
    BookingMailComposer.footer(v, m, t, base);
    v.put("why", t.get("footer.whySeason"));
    return mail(t, m, n.email(), v);
  }

  @Override
  public OutgoingMail rejected(
      SeriesRequestNotice n, Language language, MunicipalityMailContext m) {
    BookingMailComposer.Texts t = new BookingMailComposer.Texts(messages, language);
    Locale locale = t.locale();
    String kind = "SEASON_REQUEST_REJECTED";
    Map<String, String> v = base(t, m, kind, t.get("subject." + kind, n.facilityName()));
    v.put("titleColor", BookingMailComposer.DANGER);
    v.put("day", weekdays(n.weekdays(), locale));
    v.put("hours", minutes(n.startMinute()) + "–" + minutes(n.endMinute()));
    v.put("greeting", t.get("organization.season", n.organizationName()));
    v.put(
        "lead",
        t.get(
            "lead.SEASON_REQUEST_REJECTED",
            date(n.firstDate(), locale),
            date(n.lastDate(), locale)));
    // A mensaxe do concello, tal cal: o renderizador escápaa.
    v.put("noShow", n.message());
    card(v, t, n.facilityName(), null, n.useName(), n.partName());
    String base = publicUrl + "/" + m.slug();
    BookingMailComposer.footer(v, m, t, base);
    v.put("why", t.get("footer.whySeason"));
    return mail(t, m, n.email(), v);
  }

  private static Map<String, String> base(
      BookingMailComposer.Texts t, MunicipalityMailContext m, String kind, String subject) {
    String primary =
        BookingMailComposer.color(m.primaryColor(), BookingMailComposer.DEFAULT_PRIMARY);
    String onPrimary =
        BookingMailComposer.color(m.onPrimaryColor(), BookingMailComposer.DEFAULT_ON_PRIMARY);
    Map<String, String> v = new HashMap<>();
    v.put("lang", t.locale().getLanguage());
    v.put("subject", subject);
    v.put("preheader", subject);
    v.put("primary", primary);
    v.put("onPrimary", onPrimary);
    v.put("onPrimarySoft", BookingMailComposer.mix(onPrimary, primary, 0.85));
    v.put("separator", BookingMailComposer.mix(onPrimary, primary, 0.45));
    v.put("municipality", m.name());
    v.put("service", t.get("band.service"));
    v.put("title", t.get("title." + kind));
    v.put("dataWidth", "472");
    v.put("note", "");
    return v;
  }

  private static void card(
      Map<String, String> v,
      BookingMailComposer.Texts t,
      String facility,
      @Nullable String address,
      String use,
      @Nullable String part) {
    v.put("card", "yes");
    v.put("facility", facility);
    v.put("address", address == null ? "" : address);
    v.put("labelUse", t.get("row.use"));
    v.put("use", use);
    v.put("labelPart", t.get("row.part"));
    v.put("part", part == null ? "" : part);
  }

  private OutgoingMail mail(
      BookingMailComposer.Texts t, MunicipalityMailContext m, String to, Map<String, String> v) {
    return new OutgoingMail(
        t.get("from", m.name()),
        to,
        v.get("subject"),
        html.render(v),
        text.render(v),
        List.of(new OutgoingMail.Part("shield", "image/png", shield)),
        List.of());
  }

  /** «luns e mércores»: os días da semana no idioma do correo. */
  static String weekdays(List<Integer> days, Locale locale) {
    List<String> names =
        days.stream().map(d -> DayOfWeek.of(d).getDisplayName(TextStyle.FULL, locale)).toList();
    if (names.size() == 1) {
      return names.getFirst();
    }
    String and = "es".equals(locale.getLanguage()) ? " y " : " e ";
    return String.join(", ", names.subList(0, names.size() - 1)) + and + names.getLast();
  }

  /** «19:00»; 1440 é «24:00». */
  static String minutes(int minute) {
    return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
  }

  private static String date(LocalDate date, Locale locale) {
    return DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date);
  }
}
