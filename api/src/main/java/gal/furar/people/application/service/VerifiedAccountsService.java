// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import gal.furar.people.application.port.out.AccountEmails;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.security.VerifiedAccounts;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * As contas coñecidas polo seu correo verificado (ADR-0031, punto 4): Furar garda o que xa chega no
 * token para saber se o correo dunha reserva do mostrador ten conta, sen preguntarlle ao sistema de
 * acceso.
 *
 * <ul>
 *   <li><b>Transacción curta propia</b> ({@code REQUIRES_NEW}), antes do controlador.
 *   <li><b>Unha lectura por petición e unha escritura por sesión:</b> só se escribe se a conta é
 *       nova, cambiou de correo ou o {@code auth_time} é posterior ao gardado.
 *   <li><b>Dúas sesións á vez co mesmo correo</b> poden chocar no índice único: reinténtase unha
 *       vez e, se volve fallar, a petición segue sen escribir. O directorio é unha axuda, non
 *       autoriza nada.
 * </ul>
 */
@Service
class VerifiedAccountsService implements VerifiedAccounts {

  private static final Logger LOG = LoggerFactory.getLogger(VerifiedAccountsService.class);

  private static final int MIN_EMAIL = 3;
  private static final int MAX_EMAIL = 254;
  private static final int MAX_SUBJECT = 255;

  private final AccountEmails accounts;
  private final TransactionTemplate tx;

  VerifiedAccountsService(AccountEmails accounts, PlatformTransactionManager transactions) {
    this.accounts = accounts;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void seen(String subject, String verifiedEmail, @Nullable Instant authTime) {
    String key = EmailKey.of(verifiedEmail);
    if (authTime == null
        || key.length() < MIN_EMAIL
        || key.length() > MAX_EMAIL
        || subject.isEmpty()
        || subject.length() > MAX_SUBJECT) {
      return;
    }
    try {
      write(subject, key, authTime);
    } catch (DataAccessException first) {
      try {
        write(subject, key, authTime);
      } catch (DataAccessException second) {
        // Só o tipo do erro: a mensaxe da base podería levar o correo.
        LOG.warn("account e-mail not recorded error={}", second.getClass().getName());
      }
    }
  }

  private void write(String subject, String key, Instant authTime) {
    tx.executeWithoutResult(
        status -> {
          Optional<AccountEmails.Seen> stored = accounts.find(subject);
          if (stored.isPresent()
              && stored.get().emailKey().equals(key)
              && !stored.get().seenAt().isBefore(authTime)) {
            return;
          }
          accounts.write(subject, key, authTime);
        });
  }
}
