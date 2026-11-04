// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * O {@code JavaMailSender} dos IT (plan do dominio 2, §4: clase propia, sen dependencias de proba):
 * garda cada correo en vez de envialo e pode facer de SMTP caído para os seguintes envíos ({@link
 * #failNext}). Nos IT non hai {@code spring.mail.host}, así que é o único.
 */
@TestComponent
public class RecordingMailSender implements JavaMailSender {

  private final Session session = Session.getInstance(new Properties());
  private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
  private final AtomicInteger failures = new AtomicInteger();

  /** Os correos enviados, na orde de envío. */
  public List<MimeMessage> sent() {
    return List.copyOf(sent);
  }

  /** Os enviados a ese enderezo. */
  public List<MimeMessage> sentTo(String address) {
    return sent.stream()
        .filter(
            m -> {
              try {
                return m.getAllRecipients() != null
                    && m.getAllRecipients().length == 1
                    && m.getAllRecipients()[0].toString().equals(address);
              } catch (MessagingException e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }

  /** Os {@code count} envíos seguintes fallan coma un SMTP caído. */
  public void failNext(int count) {
    failures.set(count);
  }

  @Override
  public MimeMessage createMimeMessage() {
    return new MimeMessage(session);
  }

  @Override
  public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
    try {
      return new MimeMessage(session, contentStream);
    } catch (MessagingException e) {
      throw new MailSendException("Cannot read the message", e);
    }
  }

  @Override
  public void send(MimeMessage... mimeMessages) throws MailException {
    if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
      throw new MailSendException("SMTP down (test)", new java.net.ConnectException("refused"));
    }
    for (MimeMessage message : mimeMessages) {
      try {
        message.saveChanges();
      } catch (MessagingException e) {
        throw new MailSendException("Invalid message", e);
      }
      sent.add(message);
    }
  }

  @Override
  public void send(SimpleMailMessage... simpleMessages) throws MailException {
    throw new UnsupportedOperationException("Furar only sends MIME messages");
  }
}
