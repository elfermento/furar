// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.notification.StaffInvitationMailer;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.PlatformTask;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.application.port.out.RetainedMember;
import gal.furar.staff.application.port.out.StaffInvitationRepository;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.domain.InvitationToken;
import gal.furar.staff.domain.StaffInvitationId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Conservación dos datos do persoal (ADR-0027, punto 14; plan do dominio 4, §1.2.14 e P8; RGPD art.
 * 5.1.e). Unha vez ao día, en cada concello e coa súa RLS (unha transacción curta por concello):
 *
 * <ul>
 *   <li>as pertenzas desactivadas hai máis de {@code FURAR_AUDIT_RETENTION} (o mesmo prazo ca o
 *       rexistro de actividade, P3Y por defecto: cando xa non queda ningún evento seu) quedan sen
 *       nome e cun correo anonimizado; a fila, o {@code sub}, o rol e as datas quedan, así o
 *       rexistro aínda di que actuou unha persoa do persoal («Persoa anonimizada»). Antes, desde a
 *       desactivación máis {@code FURAR_AUDIT_VISIBLE}, a identidade xa está bloqueada: a xestión
 *       non a ve ({@link gal.furar.staff.domain.StaffMember#isIdentityBlocked}), só a exportación
 *       dos eventos bloqueados (ADR-0020, revisión do 2026-10-29);
 *   <li>as invitacións non aceptadas que caducaron hai máis de {@value #INVITATION_DAYS} días
 *       bórranse;
 *   <li>as pertenzas invitadas que nunca aceptaron e quedan sen ningunha invitación (a última acaba
 *       de borrarse, ou borrouse noutra pasada) pasan a desactivadas por {@value #SUBJECT} e
 *       anonimízanse igual (RGPD art. 5.1.c): o correo dunha persoa que non chegou a aceptar non se
 *       garda para sempre;
 *   <li>os envíos das invitacións borradas e das pertenzas anonimizadas esquécense na mesma
 *       transacción ({@link StaffInvitationMailer#forget}): o rexistro de correos levaba o correo
 *       de destino.
 * </ul>
 *
 * <p><b>Rastro</b> (ENS op.exp.8): cada pertenza tocada deixa un evento en {@code audit_event}, na
 * mesma transacción e coa tarefa como quen actúa ({@value #SUBJECT}, sen roles): {@code
 * staff.deactivate} coa razón {@value #REASON} para as invitadas que nunca aceptaron e {@code
 * staff.anonymize} para cada anonimizada (tamén esas). Os detalles só levan a pertenza, o rol e a
 * razón, nunca correo nin nome.
 *
 * <p>Idempotente: unha segunda pasada (outra réplica, ou o día seguinte) non atopa nada novo. O
 * rexistro técnico só leva o concello e as contas, nunca correos nin nomes. Un fallo nun concello
 * non para os demais: queda no rexistro e repítese ao día seguinte.
 */
@Service
@PlatformTask
public class StaffRetentionService {

  /** Días que se gardan as invitacións non aceptadas despois de caducar. */
  public static final int INVITATION_DAYS = 30;

  /**
   * Quen desactiva as invitadas que nunca aceptaron ({@code staff_member.deactivated_by}): a propia
   * tarefa, non unha persoa. Non pode ser un {@code sub} de Keycloak, que é un UUID.
   */
  public static final String SUBJECT = AuditEvent.SYSTEM_PREFIX + "staff-retention";

  /** Acción do rexistro ao anonimizar unha pertenza. */
  public static final String ANONYMIZE = "staff.anonymize";

  /** Razón nos detalles do rexistro: a conservación, non unha persoa. */
  public static final String REASON = "retention";

  private static final Logger LOG = LoggerFactory.getLogger(StaffRetentionService.class);

  private final MunicipalityDirectory municipalities;
  private final StaffMemberRepository members;
  private final StaffInvitationRepository invitations;
  private final StaffInvitationMailer mailer;
  private final AuditLog audit;
  private final TransactionTemplate tx;
  private final AuditRetention retention;
  private final Clock clock;

  public StaffRetentionService(
      MunicipalityDirectory municipalities,
      StaffMemberRepository members,
      StaffInvitationRepository invitations,
      StaffInvitationMailer mailer,
      AuditLog audit,
      PlatformTransactionManager transactions,
      AuditRetention retention,
      Clock clock) {
    this.municipalities = municipalities;
    this.members = members;
    this.invitations = invitations;
    this.mailer = mailer;
    this.audit = audit;
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.retention = retention;
    this.clock = clock;
  }

  /**
   * O que fixo unha pasada.
   *
   * @param anonymised desactivadas vellas anonimizadas
   * @param invitationsDeleted invitacións non aceptadas borradas
   * @param unacceptedDeactivated invitadas sen ningunha invitación, desactivadas e anonimizadas
   * @param deliveriesForgotten envíos de invitacións borrados de {@code notification_delivery}
   */
  public record Result(
      int anonymised, int invitationsDeleted, int unacceptedDeactivated, int deliveriesForgotten) {

    Result plus(Result other) {
      return new Result(
          anonymised + other.anonymised,
          invitationsDeleted + other.invitationsDeleted,
          unacceptedDeactivated + other.unacceptedDeactivated,
          deliveriesForgotten + other.deliveriesForgotten);
    }

    boolean isEmpty() {
      return anonymised == 0
          && invitationsDeleted == 0
          && unacceptedDeactivated == 0
          && deliveriesForgotten == 0;
    }
  }

  @Scheduled(cron = "${furar.staff.retention-cron:0 45 3 * * *}")
  void scheduled() {
    apply();
  }

  /** Aplica a conservación en todos os concellos e devolve o total. */
  public Result apply() {
    Instant now = clock.instant();
    // Cando xa non queda ningún evento seu no rexistro.
    Instant deactivatedCutoff = retention.keptSince(now);
    Instant invitationCutoff = now.minus(Duration.ofDays(INVITATION_DAYS));
    // Quen tiña aínda unha invitación vixente non pode estar invitado despois disto.
    Instant invitedCutoff = invitationCutoff.minus(InvitationToken.VALIDITY);
    List<TenantRef> all = TenantContext.runAsPlatform(municipalities::all);
    Result total = new Result(0, 0, 0, 0);
    int failed = 0;
    for (TenantRef municipality : all) {
      try {
        Result done =
            TenantContext.runAs(
                municipality,
                () ->
                    Objects.requireNonNull(
                        tx.execute(
                            s ->
                                pass(
                                    municipality,
                                    deactivatedCutoff,
                                    invitationCutoff,
                                    invitedCutoff,
                                    now))));
        if (!done.isEmpty()) {
          LOG.info(
              "staff retention municipality={} anonymised={} invitationsDeleted={}"
                  + " unacceptedDeactivated={} deliveriesForgotten={}",
              municipality.id(),
              done.anonymised(),
              done.invitationsDeleted(),
              done.unacceptedDeactivated(),
              done.deliveriesForgotten());
        }
        total = total.plus(done);
      } catch (RuntimeException e) {
        failed++;
        // Só o tipo do erro: a mensaxe da base podería levar valores das filas.
        LOG.warn(
            "staff retention failed municipality={} error={}",
            municipality.id(),
            e.getClass().getName());
      }
    }
    LOG.info(
        "staff retention retention={} deactivatedCutoff={} invitationCutoff={} municipalities={}"
            + " failed={} anonymised={} invitationsDeleted={} unacceptedDeactivated={}"
            + " deliveriesForgotten={}",
        retention.retention(),
        deactivatedCutoff,
        invitationCutoff,
        all.size(),
        failed,
        total.anonymised(),
        total.invitationsDeleted(),
        total.unacceptedDeactivated(),
        total.deliveriesForgotten());
    return total;
  }

  /**
   * Unha pasada no concello do contexto: as desactivadas vellas, as invitacións e, ao final, as
   * invitadas que quedaron sen ningunha, para pechar na mesma pasada as que perden agora a última.
   * Despois, os envíos das invitacións que xa non se gardan e das pertenzas anonimizadas, e o
   * rexistro de actividade: todo na mesma transacción, así un fallo non deixa nada a medias.
   */
  private Result pass(
      TenantRef municipality,
      Instant deactivatedCutoff,
      Instant invitationCutoff,
      Instant invitedCutoff,
      Instant now) {
    List<RetainedMember> anonymised = members.anonymiseDeactivatedBefore(deactivatedCutoff, now);
    List<StaffInvitationId> deleted = invitations.deleteUnusedExpiredBefore(invitationCutoff);
    List<RetainedMember> closed =
        members.deactivateUnacceptedWithoutInvitation(invitedCutoff, SUBJECT, now);
    List<UUID> forget = new ArrayList<>();
    deleted.forEach(i -> forget.add(i.value()));
    // As aceptadas quedan coa pertenza; ao anonimizala, o seu envío tamén se esquece.
    invitations
        .idsOf(anonymised.stream().map(RetainedMember::id).toList())
        .forEach(i -> forget.add(i.value()));
    int forgotten = forget.isEmpty() ? 0 : mailer.forget(forget);
    for (RetainedMember m : closed) {
      record(municipality, StaffManagementService.DEACTIVATE, m);
    }
    for (RetainedMember m : anonymised) {
      record(municipality, ANONYMIZE, m);
    }
    for (RetainedMember m : closed) {
      record(municipality, ANONYMIZE, m);
    }
    return new Result(anonymised.size(), deleted.size(), closed.size(), forgotten);
  }

  /** Un evento da tarefa sobre unha pertenza: só o identificador, o rol e a razón. */
  private void record(TenantRef municipality, String action, RetainedMember member) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("memberId", member.id().value().toString());
    details.put("role", member.role().name());
    details.put("reason", REASON);
    audit.record(
        AuditEvent.bySystem(
            SUBJECT, action, Outcome.SUCCESS, municipality.id(), municipality.slug(), details));
  }
}
