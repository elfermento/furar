// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Modo {@code test-mail} da imaxe da API: envía un correo de proba polo SMTP do despregue e sae. O
 * instalador úsao antes do primeiro arranque ({@code docker compose run --rm --no-deps api
 * test-mail correo}): o mesmo camiño ca os correos das reservas ({@code SPRING_MAIL_*}, {@code
 * application-prod.yaml}, remitente {@code FURAR_SMTP_FROM}), sen base nin web.
 *
 * <p>O contexto só ten a autoconfiguración do correo, como {@link FurarMigration} a de Flyway.
 * Rexistra unha liña simple (sen JSON) que empeza por {@value #PREFIX}: aceptado ou o motivo (o
 * tipo do erro e a resposta do servidor, nunca o contrasinal). Código 0 se o servidor o aceptou, 1
 * se non, 2 se falta o destinatario.
 */
public final class FurarTestMail {

  /** Argumento da imaxe: {@code java … JarLauncher test-mail correo}. */
  public static final String COMMAND = "test-mail";

  /** Comezo da liña do resultado, a que le o instalador. */
  static final String PREFIX = COMMAND + ": ";

  static final String SUBJECT = "Reservas deportivas: correo de proba / correo de prueba";

  static final String TEXT =
      """
      Este é o correo de proba da instalación de Reservas deportivas: o servidor SMTP aceptou o \
      envío e o enderezo do remitente vale.

      Este es el correo de prueba de la instalación de Reservas deportivas: el servidor SMTP \
      aceptó el envío y la dirección del remitente es válida.
      """;

  private static final Logger LOG = LoggerFactory.getLogger(FurarTestMail.class);

  /** O resultado: código de saída e a liña que se rexistra. */
  record Result(int code, String line) {}

  private FurarTestMail() {}

  /** Envía o correo de proba e devolve o código de saída do proceso. */
  public static int run(String... args) {
    if (args.length == 0 || !args[0].contains("@")) {
      LOG.error("{}usage: test-mail <address>", PREFIX);
      return 2;
    }
    var application =
        new SpringApplicationBuilder(MailSenderAutoConfiguration.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .build();
    String[] overrides = {
      // Unha liña simple, sen o JSON de produción, e só a deste modo e os avisos.
      "--logging.structured.format.console=",
      "--logging.pattern.console=%m%n",
      "--logging.level.root=warn",
      "--logging.level." + FurarTestMail.class.getName() + "=info"
    };
    try (ConfigurableApplicationContext context =
        application.run(concat(overrides, Arrays.copyOfRange(args, 1, args.length)))) {
      ObjectProvider<JavaMailSender> senders = context.getBeanProvider(JavaMailSender.class);
      String from = context.getEnvironment().getProperty("furar.mail.from", "furar@localhost");
      Result result = send(senders.getIfAvailable(), from, args[0]);
      if (result.code() == 0) {
        LOG.info("{}", result.line());
      } else {
        LOG.error("{}", result.line());
      }
      return result.code();
    }
  }

  /** Envía e devolve o resultado. */
  static Result send(@Nullable JavaMailSender sender, String from, String to) {
    if (sender == null) {
      return new Result(1, PREFIX + "no SMTP server configured (SPRING_MAIL_HOST)");
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(SUBJECT);
    message.setText(TEXT);
    try {
      sender.send(message);
      return new Result(0, PREFIX + "accepted");
    } catch (MailException e) {
      return new Result(1, PREFIX + reason(e));
    }
  }

  /**
   * O erro e a súa causa máis concreta, nunha liña: p. ex. a resposta {@code 535} do servidor ou un
   * certificado que non vale. Sen o contrasinal (o servidor nunca o devolve).
   */
  static String reason(Throwable e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String detail = root.getMessage() == null ? "" : ": " + root.getMessage().strip();
    String text =
        root == e
            ? e.getClass().getSimpleName() + detail
            : e.getClass().getSimpleName() + " (" + root.getClass().getSimpleName() + detail + ")";
    return text.replaceAll("\\s+", " ").strip();
  }

  private static String[] concat(String[] first, String[] second) {
    String[] result = new String[first.length + second.length];
    System.arraycopy(first, 0, result, 0, first.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }
}
