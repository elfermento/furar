// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.Ids;
import gal.furar.shared.security.PlatformAdmins;
import gal.furar.shared.security.Role;
import gal.furar.staff.application.port.out.PlatformAdminRepository;
import gal.furar.staff.domain.EmailAddress;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A administración da plataforma desde a base de Furar, en cada petición (ADR-0028).
 *
 * <ul>
 *   <li><b>Transacción curta propia</b> ({@code REQUIRES_NEW}), como {@code StaffRolesService}: a
 *       autorización corre antes da transacción do caso de uso.
 *   <li><b>Primeiro acceso:</b> se a conta non é administración, o seu correo verificado é o de
 *       {@code FURAR_ADMIN_EMAIL} (normalizado, como o do persoal) e non hai ningunha
 *       administración, pasa a selo na mesma transacción ca o evento {@value #FIRST_ADMIN} do
 *       rexistro de actividade. Unha vez que a hai, cambiar a variable non fai nada. Se dúas
 *       peticións da mesma conta chegan á vez, a que non insire volve ler: as dúas din que si.
 *   <li><b>O correo nunca sae:</b> nin no rexistro de actividade, nin nos rexistros da API, nin en
 *       ningunha resposta.
 * </ul>
 */
@Service
class PlatformAdminsService implements PlatformAdmins {

  static final String FIRST_ADMIN = "platform.first-admin";

  private static final Logger LOG = LoggerFactory.getLogger(PlatformAdminsService.class);

  private final PlatformAdminRepository admins;
  private final AuditLog audit;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final Optional<EmailAddress> firstAdminEmail;

  PlatformAdminsService(
      PlatformAdminRepository admins,
      AuditLog audit,
      Clock clock,
      PlatformTransactionManager transactions,
      @Value("${furar.platform.admin-email:}") String firstAdminEmail) {
    this.admins = admins;
    this.audit = audit;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.firstAdminEmail = parse(firstAdminEmail);
  }

  /**
   * Baleiro: sen primeiro acceso. Mal escrito: a API non arrinca (mellor ca non entrar ninguén).
   */
  static Optional<EmailAddress> parse(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(EmailAddress.of(configured));
    } catch (DomainException e) {
      throw new IllegalStateException(
          "FURAR_ADMIN_EMAIL is not a valid e-mail address (ADR-0028)", e);
    }
  }

  @Override
  public boolean isPlatformAdmin(String subject, @Nullable String verifiedEmail) {
    return Boolean.TRUE.equals(tx.execute(s -> read(subject, verifiedEmail)));
  }

  private boolean read(String subject, @Nullable String verifiedEmail) {
    if (admins.isAdmin(subject)) {
      return true;
    }
    if (firstAdminEmail.isEmpty() || !firstAdminEmail.get().matches(verifiedEmail)) {
      return false;
    }
    if (!admins.grantFirst(Ids.newV7(clock), subject, clock.instant())) {
      // Outra petición da mesma conta deullo xusto antes (a web pide á vez o acceso á plataforma e
      // as pertenzas ao persoal): esta esperou ao seu commit no índice único e non inseriu nada.
      // Unha lectura nova xa o ve; sen ela, a primeira resposta diría «non» e a web quedaría na
      // portada en vez de na alta do concello.
      return admins.isAdmin(subject);
    }
    audit.record(
        new AuditEvent(
                FIRST_ADMIN,
                AuditEvent.Outcome.SUCCESS,
                null,
                null,
                Map.of("source", "FURAR_ADMIN_EMAIL"))
            // A petición aínda non ve o rol (estase lendo agora): vai no evento.
            .withActorRole(Role.PLATFORM_ADMIN));
    LOG.info("First platform administrator granted from FURAR_ADMIN_EMAIL (ADR-0028)");
    return true;
  }
}
