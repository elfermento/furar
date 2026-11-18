// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/** Punto de entrada da API de Furar. */
@SpringBootApplication
@Modulithic(systemName = "Furar", sharedModules = "shared")
public class FurarApplication {

  /**
   * Sen argumentos, a API. Con {@code migrate} como primeiro argumento, só as migracións ({@link
   * FurarMigration}); con {@code test-mail}, un correo de proba ({@link FurarTestMail}). Nos dous
   * casos o proceso sae co seu código.
   */
  public static void main(String[] args) {
    if (args.length > 0 && FurarMigration.COMMAND.equals(args[0])) {
      System.exit(FurarMigration.run(Arrays.copyOfRange(args, 1, args.length)));
    }
    if (args.length > 0 && FurarTestMail.COMMAND.equals(args[0])) {
      System.exit(FurarTestMail.run(Arrays.copyOfRange(args, 1, args.length)));
    }
    SpringApplication.run(FurarApplication.class, args);
  }
}
