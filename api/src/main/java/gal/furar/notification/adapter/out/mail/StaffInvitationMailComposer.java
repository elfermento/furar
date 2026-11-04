// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.municipality.MunicipalityMailContext;
import gal.furar.notification.StaffInvitationMail;
import gal.furar.notification.application.port.out.InvitationMailComposer;
import gal.furar.notification.application.port.out.OutgoingMail;
import gal.furar.shared.domain.PlaceName;
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
 * Escribe o correo dunha invitación ao persoal (plan do dominio 4, D4-06), co mesmo estilo cós das
 * reservas: banda co nome do concello na súa cor, tarxeta con «Crea a túa conta», quen invita, a
 * que concello e para que rol (asunto e preheader segundo o rol: xestionar ou atender as reservas),
 * o botón «Crear a miña conta» e a ligazón en texto, unha liña co correo que hai que usar e a
 * caducidade exacta (hora de Galicia), «se non o esperabas, ignórao» e o pé co enderezo e o
 * contacto do servizo de deportes, por que chega e unha frase de privacidade (quen trata os datos e
 * para que) coa ligazón á sección do persoal da páxina de privacidade. Sen «darse de baixa»: non é
 * unha lista de correo. Textos en {@code mail/staff-invitation_{gl,es}.properties}, no idioma que
 * escolleu quen invita. Os textos curtos van centrados (maqueta 51).
 */
@Component
class StaffInvitationMailComposer implements InvitationMailComposer {

  /** Ancla da sección «Persoal do concello» na páxina de privacidade (web, furar-legal-html.ts). */
  static final String STAFF_SECTION = "persoal";

  private final MailTemplate html = MailTemplate.load("mail/staff-invitation.html");
  private final MailTemplate text = MailTemplate.load("mail/staff-invitation.txt");
  private final byte[] shield = BookingMailComposer.resource("mail/shield.png");
  private final MessageSource messages = messages();
  private final String publicUrl;

  StaffInvitationMailComposer(@Value("${furar.public-url}") String publicUrl) {
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  @Override
  public OutgoingMail compose(StaffInvitationMail mail, MunicipalityMailContext m) {
    Locale locale = Locale.of(mail.language().code());
    // «de Narón», «da Coruña»: os textos levan o nome do concello xa coa preposición.
    String of = PlaceName.of(m.name(), mail.language());
    String primary =
        BookingMailComposer.color(m.primaryColor(), BookingMailComposer.DEFAULT_PRIMARY);
    String onPrimary =
        BookingMailComposer.color(m.onPrimaryColor(), BookingMailComposer.DEFAULT_ON_PRIMARY);
    String inviter =
        mail.inviterName().isBlank()
            ? text("inviter.fallback", locale, of)
            : mail.inviterName().strip();
    String subject = text("subject." + mail.role(), locale, inviter, of);
    String expires = expires(mail, locale);
    String link = mail.link().toString();

    Map<String, String> v = new HashMap<>();
    v.put("lang", mail.language().code());
    v.put("subject", subject);
    v.put("preheader", text("preheader." + mail.role(), locale, inviter, of));
    v.put("primary", primary);
    v.put("onPrimary", onPrimary);
    v.put("onPrimarySoft", BookingMailComposer.mix(onPrimary, primary, 0.85));
    v.put("separator", BookingMailComposer.mix(onPrimary, primary, 0.45));
    v.put("municipality", m.name());
    v.put("service", text("band.service", locale));
    v.put("title", text("title", locale));
    v.put("lead", text("lead." + mail.role(), locale, inviter, of));
    v.put("button", text("button", locale));
    v.put("link", link);
    v.put("copy", text("copy", locale));
    v.put("use", text("use", locale, mail.recipient(), expires));
    v.put("ignore", text("ignore", locale));
    MunicipalityMailContext.Contact c = m.contact();
    v.put("footerAddress", blank(c.address()));
    v.put("footerPhone", blank(c.phone()));
    v.put("footerEmail", blank(c.email()));
    boolean contact = c.phone() != null || c.email() != null;
    v.put("footerContact", contact ? "yes" : "");
    v.put("footerBlock", contact || c.address() != null ? "yes" : "");
    v.put("footerBreak", contact && c.address() != null ? "yes" : "");
    v.put("footerSeparator", c.phone() != null && c.email() != null ? "yes" : "");
    v.put("why", text("footer.why", locale, of));
    // Información ao persoal na primeira comunicación (RGPD art. 14; ADR-0027): unha frase e a
    // ligazón á sección do persoal da páxina de privacidade, ou á páxina propia do concello
    // (ADR-0017 punto 8).
    v.put("privacyNote", text("footer.privacyNote", locale, of));
    v.put("privacy", text("footer.privacy", locale));
    v.put(
        "privacyUrl",
        m.privacyUrl() == null
            ? publicUrl + "/" + m.slug() + "/privacidade#" + STAFF_SECTION
            : m.privacyUrl());
    v.put("privacyLink", m.privacyPublished() ? "yes" : "");

    return new OutgoingMail(
        text("from", locale, m.name()),
        mail.recipient(),
        subject,
        html.render(v),
        text.render(v),
        List.of(new OutgoingMail.Part("shield", "image/png", shield)),
        List.of());
  }

  /** «venres 6 de novembro ás 18:30 (hora de Galicia)»: a hora exacta, sempre en Galicia. */
  private String expires(StaffInvitationMail mail, Locale locale) {
    ZonedDateTime at = mail.expiresAt().atZone(BookingMailComposer.ZONE);
    return text(
        "expires.at",
        locale,
        BookingMailComposer.day(at, locale),
        DateTimeFormatter.ofPattern("HH:mm").format(at),
        at.getHour());
  }

  private String text(String key, Locale locale, Object... args) {
    return messages.getMessage(key, args.length == 0 ? null : args, locale);
  }

  private static String blank(@Nullable String value) {
    return value == null ? "" : value;
  }

  private static MessageSource messages() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("mail/staff-invitation");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
