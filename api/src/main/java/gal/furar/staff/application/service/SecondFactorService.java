// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.shared.security.PublicUseCase;
import gal.furar.staff.application.port.in.SecondFactorUseCase;
import gal.furar.staff.application.port.out.PlatformAdminRepository;
import gal.furar.staff.application.port.out.StaffAccountLookup;
import gal.furar.staff.domain.EmailAddress;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Quen ten que entrar co segundo factor (ADR-0029), lido da base de Furar en cada acceso, como os
 * permisos (ADR-0027, ADR-0028): perder o rol quita a obriga no seguinte acceso.
 *
 * <ul>
 *   <li><b>Sen sesión:</b> quen pregunta é Keycloak, durante o acceso, polo porto interno da API
 *       (9081, que Caddy non publica) e coa clave derivada do segredo compartido ({@code
 *       SecondFactorEndpoint}). Por iso é {@link PublicUseCase}: o caso de uso non mira o actor.
 *   <li><b>Só si ou non:</b> sen o concello, o rol nin o correo de {@code FURAR_ADMIN_EMAIL}.
 *   <li><b>Primeira administración:</b> mentres non hai ningunha, a conta dese correo xa ten que
 *       usalo, porque na súa primeira petición pasa a ser a administración ({@code
 *       PlatformAdminsService}).
 * </ul>
 */
@Service
class SecondFactorService implements SecondFactorUseCase {

  private final PlatformAdminRepository admins;
  private final StaffAccountLookup accounts;
  private final TransactionTemplate tx;
  private final Optional<EmailAddress> firstAdminEmail;

  SecondFactorService(
      PlatformAdminRepository admins,
      StaffAccountLookup accounts,
      PlatformTransactionManager transactions,
      @Value("${furar.platform.admin-email:}") String firstAdminEmail) {
    this.admins = admins;
    this.accounts = accounts;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setReadOnly(true);
    this.firstAdminEmail = PlatformAdminsService.parse(firstAdminEmail);
  }

  @Override
  @PublicUseCase
  public boolean required(String subject, @Nullable String email) {
    return Boolean.TRUE.equals(tx.execute(s -> read(subject, email)));
  }

  private boolean read(String subject, @Nullable String email) {
    if (admins.isAdmin(subject) || accounts.isActiveAnywhere(subject)) {
      return true;
    }
    return firstAdminEmail.isPresent()
        && firstAdminEmail.get().matches(email)
        && !admins.anyAdmin();
  }
}
