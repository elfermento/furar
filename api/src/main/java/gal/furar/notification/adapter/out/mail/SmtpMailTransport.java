// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import gal.furar.notification.application.port.out.MailTransport;
import gal.furar.notification.application.port.out.MailTransportException;
import gal.furar.notification.application.port.out.OutgoingMail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Envía polo SMTP do despregue ({@code SPRING_MAIL_*}; en desenvolvemento, Mailpit). Remitente:
 * {@code FURAR_SMTP_FROM} co nome visible do concello. Mensaxe {@code multipart}: texto e HTML,
 * imaxes incrustadas ({@code cid:}) e o {@code .ics} adxunto. Sen servidor configurado, cada envío
 * falla e queda rexistrado.
 */
@Component
class SmtpMailTransport implements MailTransport {

  private final ObjectProvider<JavaMailSender> senders;
  private final String from;

  SmtpMailTransport(
      ObjectProvider<JavaMailSender> senders,
      @Value("${furar.mail.from:furar@localhost}") String from) {
    this.senders = senders;
    this.from = from;
  }

  @Override
  public void send(OutgoingMail mail) {
    JavaMailSender sender = senders.getIfAvailable();
    if (sender == null) {
      throw new MailTransportException("No SMTP server configured (SPRING_MAIL_HOST)", null);
    }
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
      helper.setFrom(from, mail.fromName());
      helper.setTo(mail.to());
      helper.setSubject(mail.subject());
      helper.setText(mail.text(), mail.html());
      for (OutgoingMail.Part part : mail.inline()) {
        helper.addInline(part.name(), new ByteArrayResource(part.bytes()), part.contentType());
      }
      for (OutgoingMail.Part part : mail.attachments()) {
        helper.addAttachment(part.name(), new ByteArrayResource(part.bytes()), part.contentType());
      }
      sender.send(message);
    } catch (MailException | MessagingException | UnsupportedEncodingException e) {
      throw new MailTransportException(reason(e), e);
    }
  }

  /**
   * O tipo do fallo, o da súa causa e o código de resposta do SMTP se o hai ({@code SMTP 550}):
   * nunca as mensaxes, que poden levar o enderezo de quen recibe ou a ligazón co token.
   */
  static String reason(Exception e) {
    Throwable cause = e.getCause();
    if (cause == null && e instanceof MailSendException send) {
      cause = send.getMessageExceptions().length > 0 ? send.getMessageExceptions()[0] : null;
    }
    String text =
        cause == null
            ? e.getClass().getSimpleName()
            : e.getClass().getSimpleName() + ": " + cause.getClass().getSimpleName();
    Integer code = smtpCode(e);
    return code == null ? text : text + " (SMTP " + code + ")";
  }

  /**
   * O código de resposta do servidor, se algunha excepción da cadea o trae ({@code getReturnCode()}
   * das excepcións SMTP de Jakarta Mail, lido sen depender da implementación).
   */
  private static @Nullable Integer smtpCode(Throwable e) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<Throwable> pending = new ArrayDeque<>(List.of(e));
    while (!pending.isEmpty()) {
      Throwable t = pending.pop();
      if (!seen.add(t)) {
        continue;
      }
      try {
        if (t.getClass().getMethod("getReturnCode").invoke(t) instanceof Integer code && code > 0) {
          return code;
        }
      } catch (ReflectiveOperationException | RuntimeException ignored) {
        // Sen código: outro tipo de excepción.
      }
      if (t instanceof MailSendException send) {
        pending.addAll(Arrays.asList(send.getMessageExceptions()));
      }
      if (t instanceof MessagingException m && m.getNextException() != null) {
        pending.add(m.getNextException());
      }
      if (t.getCause() != null) {
        pending.add(t.getCause());
      }
    }
    return null;
  }
}
