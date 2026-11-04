// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;

/**
 * O correo de proba do instalador: o remitente do despregue, o destinatario e unha liña co
 * resultado que o instalador le; o motivo dun fallo leva a resposta do servidor e nunca o
 * contrasinal.
 */
class FurarTestMailTest {

  /** Garda o que se envía ou falla coma o servidor. */
  static final class FakeSender implements JavaMailSender {
    final List<SimpleMailMessage> sent = new ArrayList<>();
    MailException failure;

    @Override
    public void send(SimpleMailMessage... messages) {
      if (failure != null) {
        throw failure;
      }
      sent.addAll(List.of(messages));
    }

    @Override
    public MimeMessage createMimeMessage() {
      return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void send(MimeMessage... mimeMessages) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) {
      throw new UnsupportedOperationException();
    }
  }

  final FakeSender sender = new FakeSender();

  @Test
  void sendsFromTheDeploymentSenderAndSaysItWasAccepted() {
    FurarTestMail.Result result =
        FurarTestMail.send(sender, "reservas@concello.gal", "informatica@concello.gal");

    assertThat(result.code()).isZero();
    assertThat(result.line()).isEqualTo("test-mail: accepted");
    assertThat(sender.sent)
        .singleElement()
        .satisfies(
            m -> {
              assertThat(m.getFrom()).isEqualTo("reservas@concello.gal");
              assertThat(m.getTo()).containsExactly("informatica@concello.gal");
              assertThat(m.getSubject()).contains("correo de proba");
              assertThat(m.getText()).contains("servidor SMTP aceptou");
            });
  }

  @Test
  void aRejectionGivesTheServerAnswerInOneLine() {
    sender.failure =
        new MailAuthenticationException(
            new AuthenticationFailedException("535 5.7.8 Authentication\n credentials invalid"));

    FurarTestMail.Result result = FurarTestMail.send(sender, "reservas@concello.gal", "a@b.gal");

    assertThat(result.code()).isEqualTo(1);
    assertThat(result.line())
        .isEqualTo(
            "test-mail: MailAuthenticationException (AuthenticationFailedException: 535 5.7.8"
                + " Authentication credentials invalid)");
  }

  @Test
  void withoutAServerItSaysSo() {
    FurarTestMail.Result result = FurarTestMail.send(null, "reservas@concello.gal", "a@b.gal");

    assertThat(result.code()).isEqualTo(1);
    assertThat(result.line()).contains("SPRING_MAIL_HOST");
  }

  @Test
  void withoutARecipientItStopsBeforeStartingAnything() {
    assertThat(FurarTestMail.run()).isEqualTo(2);
    assertThat(FurarTestMail.run("non-é-un-correo")).isEqualTo(2);
  }
}
