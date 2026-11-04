// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.adapter.out.mail;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.MessagingException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

/** O motivo dun envío fallido vai aos rexistros: o tipo e o código SMTP, nunca o enderezo. */
class SmtpMailTransportTest {

  /** Como as excepcións SMTP de Jakarta Mail: un código e o enderezo na mensaxe. */
  static final class RefusedRecipient extends MessagingException {
    private static final long serialVersionUID = 1L;

    RefusedRecipient() {
      super("550 5.1.1 <persoa@example.org>: Recipient address rejected");
    }

    public int getReturnCode() {
      return 550;
    }
  }

  @Test
  void theReasonKeepsTheTypeAndTheSmtpCodeButNeverTheAddress() {
    MailSendException failed = new MailSendException(Map.of(new Object(), new RefusedRecipient()));
    String reason = SmtpMailTransport.reason(failed);
    assertThat(reason)
        .isEqualTo("MailSendException: RefusedRecipient (SMTP 550)")
        .doesNotContain("@")
        .doesNotContain("persoa");
  }

  @Test
  void withoutACodeOnlyTheTypes() {
    String reason =
        SmtpMailTransport.reason(
            new MailSendException(
                "Mail server connection failed to persoa@example.org",
                new MessagingException("Could not connect to persoa@example.org")));
    assertThat(reason).isEqualTo("MailSendException: MessagingException");
  }
}
