// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.booking.BookingQueries;
import gal.furar.notification.DeliveryResult;
import gal.furar.notification.StaffInvitationMail;
import gal.furar.notification.StaffInvitationMailer;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.StaffPermission;
import gal.furar.staff.application.port.in.ChangeStaffRoleCommand;
import gal.furar.staff.application.port.in.DeactivateStaffMemberCommand;
import gal.furar.staff.application.port.in.InviteStaffMemberCommand;
import gal.furar.staff.application.port.in.InviteStaffUseCase;
import gal.furar.staff.application.port.in.ListStaffUseCase;
import gal.furar.staff.application.port.in.ManageStaffMemberUseCase;
import gal.furar.staff.application.port.in.ReactivateStaffMemberCommand;
import gal.furar.staff.application.port.in.ResendStaffInvitationCommand;
import gal.furar.staff.application.port.in.StaffMemberView;
import gal.furar.staff.application.port.in.StaffMemberView.Inviter;
import gal.furar.staff.application.port.out.StaffInvitationRepository;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.domain.EmailAddress;
import gal.furar.staff.domain.InvitationDelivery;
import gal.furar.staff.domain.InvitationToken;
import gal.furar.staff.domain.SendLimits;
import gal.furar.staff.domain.StaffError;
import gal.furar.staff.domain.StaffInvitation;
import gal.furar.staff.domain.StaffInvitationId;
import gal.furar.staff.domain.StaffMember;
import gal.furar.staff.domain.StaffMemberId;
import gal.furar.staff.domain.StaffStatus;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Xestión do persoal do concello (ADR-0027; plan do dominio 4, §1.2.6–§1.2.8 e §2.3). Cada método
 * público declara o seu permiso (A15) e cada escritura queda no rexistro de actividade, boa ou
 * fallida, sen correo nin token nos detalles (ADR-0020).
 *
 * <p><b>Invitar e reenviar</b> non son {@code @Transactional}: o correo sae despois do commit e
 * fóra de toda transacción (§1.2.6), así que van en tres pasos:
 *
 * <ol>
 *   <li>Transacción 1: límites de envío, pertenza e invitación {@code PENDING}, e o evento de éxito
 *       (queda só se se confirma).
 *   <li>Sen transacción, no concello da petición: {@link StaffInvitationMailer#send} co token en
 *       memoria. Un SMTP caído non lanza: devolve {@code FAILED}. Unha excepción inesperada do
 *       envío cóntase tamén como {@code FAILED}: a invitación xa está gardada e «Reenviar» é a
 *       saída.
 *   <li>Transacción 2, curta: {@code PENDING → SENT|FAILED}. Se non se pode gardar (a base caeu
 *       despois de enviar), a invitación queda {@code PENDING} e a resposta dío: o xestor ve que
 *       non se sabe se saíu e pode reenviala pasados os 2 minutos. Non se reintenta: o token non se
 *       garda.
 * </ol>
 *
 * <p><b>Cambiar o rol e desactivar</b> bloquean primeiro as pertenzas xestoras activas e despois a
 * pertenza (sempre na mesma orde, sen interbloqueos): dúas desactivacións á vez dos dous últimos
 * xestores pásanse unha detrás doutra e a segunda ve un só xestor (409 {@code staff.last-manager}).
 *
 * <p><b>Reactivar</b> (ADR-0027, revisión do 2026-10-30) devolve a pertenza desactivada a activa co
 * mesmo rol e sen invitación nova; vale na seguinte petición, porque o rol sae da táboa en cada
 * petición. Non se reactiva unha pertenza anonimizada, nin se xa hai outra viva co mesmo correo ou
 * a mesma persoa (os índices únicos), nin se a conta reservou como veciña mentres estaba
 * desactivada: a conta de traballo vai á parte.
 *
 * <p>Límite do concello: dúas altas simultáneas poden ler os mesmos 29 envíos e pasar as dúas. Non
 * se bloquea por iso: o límite é contra o abuso, non unha cota exacta.
 */
@Service
public class StaffManagementService
    implements ListStaffUseCase, InviteStaffUseCase, ManageStaffMemberUseCase {

  static final String INVITE = "staff.invite";
  static final String RESEND = "staff.invite-resend";
  static final String ROLE_CHANGE = "staff.role-change";
  static final String DEACTIVATE = "staff.deactivate";
  static final String REACTIVATE = "staff.reactivate";

  private static final Logger LOG = LoggerFactory.getLogger(StaffManagementService.class);

  private final StaffMemberRepository members;
  private final StaffInvitationRepository invitations;
  private final StaffInvitationMailer mailer;
  private final BookingQueries bookings;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final AuditRetention retention;
  private final SecureRandom random = new SecureRandom();
  private final String publicUrl;

  public StaffManagementService(
      StaffMemberRepository members,
      StaffInvitationRepository invitations,
      StaffInvitationMailer mailer,
      BookingQueries bookings,
      CurrentActor currentActor,
      AuditLog audit,
      PlatformTransactionManager transactions,
      Clock clock,
      AuditRetention retention,
      @Value("${furar.public-url}") String publicUrl) {
    this.members = members;
    this.invitations = invitations;
    this.mailer = mailer;
    this.bookings = bookings;
    this.currentActor = currentActor;
    this.audit = audit;
    this.tx = new TransactionTemplate(transactions);
    this.clock = clock;
    this.retention = retention;
    this.publicUrl =
        publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
  }

  // --- ver ---

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(StaffPermission.Code.VIEW)
  public List<StaffMemberView> list(@Nullable StaffStatus status) {
    List<StaffMember> all = members.list(status);
    Map<StaffMemberId, StaffInvitation> latest =
        invitations.latestPerMember().stream()
            .collect(Collectors.toMap(StaffInvitation::memberId, Function.identity()));
    Map<StaffMemberId, Integer> sends = invitations.sendCountPerMember();
    Map<String, Inviter> inviters = inviters(all.stream().map(StaffMember::invitedBy).toList());
    Instant now = clock.instant();
    Instant visibleSince = retention.visibleSince(now);
    return all.stream()
        .map(
            m ->
                view(
                    m,
                    inviters.get(m.invitedBy()),
                    latest.get(m.id()),
                    sends.getOrDefault(m.id(), 0),
                    now,
                    visibleSince))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(StaffPermission.Code.VIEW)
  public StaffMemberView get(StaffMemberId id) {
    return view(load(id));
  }

  // --- invitar ---

  @Override
  @RequiresPermission(StaffPermission.Code.INVITE)
  public StaffMemberView invite(InviteStaffMemberCommand command) {
    Actor actor = actor();
    Issued issued =
        inTransaction(
            () -> {
              Map<String, Object> details = new LinkedHashMap<>();
              details.put("role", command.role().name());
              return audited(
                  INVITE,
                  details,
                  () -> {
                    EmailAddress email = EmailAddress.of(command.email());
                    Instant now = clock.instant();
                    SendLimits.check(municipalitySends(now), List.of(), now);
                    StaffMember member =
                        StaffMember.invite(
                            StaffMemberId.newId(),
                            email,
                            command.role(),
                            command.language(),
                            actor.subject(),
                            now);
                    if (!members.insert(member)) {
                      throw new DomainException(StaffError.ALREADY_MEMBER);
                    }
                    details.put("memberId", member.id().value().toString());
                    return issue(load(member.id()), actor, now, 1);
                  });
            });
    return deliver(issued, actor);
  }

  @Override
  @RequiresPermission(StaffPermission.Code.INVITE)
  public StaffMemberView resend(ResendStaffInvitationCommand command) {
    Actor actor = actor();
    Issued issued =
        inTransaction(
            () -> {
              Map<String, Object> details = new LinkedHashMap<>();
              details.put("memberId", command.id().value().toString());
              return audited(
                  RESEND,
                  details,
                  () -> {
                    StaffMember current = current(command.id(), command.expectedVersion());
                    details.put("role", current.role().name());
                    if (current.status() != StaffStatus.INVITED) {
                      throw new DomainException(StaffError.TRANSITION_INVALID);
                    }
                    Instant now = clock.instant();
                    List<Instant> memberSends = invitations.sendsOf(current.id());
                    SendLimits.check(municipalitySends(now), memberSends, now);
                    invitations.supersedeOpen(current.id(), now);
                    // A pertenza non cambia, pero a súa invitación si: a versión sobe para que o
                    // ETag cambie e un segundo reenvío co mesmo If-Match dea 412.
                    save(current, command.expectedVersion());
                    return issue(load(current.id()), actor, now, memberSends.size() + 1);
                  });
            });
    return deliver(issued, actor);
  }

  // --- cambiar o rol, desactivar e reactivar ---

  @Override
  @Transactional
  @RequiresPermission(StaffPermission.Code.MANAGE)
  public StaffMemberView changeRole(ChangeStaffRoleCommand command) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("memberId", command.id().value().toString());
    details.put("toRole", command.role().name());
    return audited(
        ROLE_CHANGE,
        details,
        () -> {
          int managers = members.lockActiveManagers();
          StaffMember current = current(command.id(), command.expectedVersion());
          details.put("fromRole", current.role().name());
          StaffMember changed = current.changeRole(command.role(), managers, clock.instant());
          if (changed != current) {
            save(changed, command.expectedVersion());
          }
          return view(load(command.id()));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(StaffPermission.Code.DEACTIVATE)
  public StaffMemberView deactivate(DeactivateStaffMemberCommand command) {
    Actor actor = actor();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("memberId", command.id().value().toString());
    return audited(
        DEACTIVATE,
        details,
        () -> {
          int managers = members.lockActiveManagers();
          StaffMember current = current(command.id(), command.expectedVersion());
          details.put("role", current.role().name());
          Instant now = clock.instant();
          save(current.deactivate(actor.subject(), managers, now), command.expectedVersion());
          invitations.supersedeOpen(current.id(), now);
          return view(load(command.id()));
        });
  }

  @Override
  @Transactional
  @RequiresPermission(StaffPermission.Code.DEACTIVATE)
  public StaffMemberView reactivate(ReactivateStaffMemberCommand command) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("memberId", command.id().value().toString());
    return audited(
        REACTIVATE,
        details,
        () -> {
          StaffMember current = current(command.id(), command.expectedVersion());
          details.put("role", current.role().name());
          StaffMember reactivated = current.reactivate(clock.instant());
          if (bookings.hasResidentBookings(Objects.requireNonNull(reactivated.subject()))) {
            throw new DomainException(StaffError.PERSONAL_ACCOUNT);
          }
          try {
            save(reactivated, command.expectedVersion());
          } catch (DuplicateKeyException e) {
            // Outra pertenza viva co mesmo correo (volveuse invitar) ou a mesma persoa activa.
            throw new DomainException(StaffError.ALREADY_MEMBER);
          }
          return view(load(command.id()));
        });
  }

  // --- axudas ---

  /**
   * O que deixa a transacción 1 dunha invitación: o token só vive aquí, en memoria; {@code sends}
   * conta xa este envío.
   */
  private record Issued(
      StaffMember member,
      StaffInvitation invitation,
      InvitationToken token,
      Inviter inviter,
      int sends) {}

  /** Emite e garda unha invitación nova da pertenza (dentro da transacción 1). */
  private Issued issue(StaffMember member, Actor actor, Instant now, int sends) {
    InvitationToken token = InvitationToken.generate(random);
    StaffInvitation invitation =
        StaffInvitation.issue(
            StaffInvitationId.newId(), member.id(), token.hash(), actor.subject(), now);
    invitations.insert(invitation);
    return new Issued(member, invitation, token, inviter(member), sends);
  }

  /** Pasos 2 e 3: envía o correo fóra de toda transacción e garda o resultado. */
  private StaffMemberView deliver(Issued issued, Actor actor) {
    StaffInvitation invitation = issued.invitation();
    DeliveryResult result = send(issued, actor);
    InvitationDelivery delivery =
        result == DeliveryResult.SENT ? InvitationDelivery.SENT : InvitationDelivery.FAILED;
    StaffInvitation recorded = invitation;
    try {
      tx.executeWithoutResult(s -> invitations.recordDelivery(invitation.id(), delivery));
      recorded = invitation.delivered(delivery == InvitationDelivery.SENT);
    } catch (RuntimeException e) {
      LOG.warn(
          "Staff invitation {} mail {} but the result could not be stored: left PENDING ({})",
          invitation.id().value(),
          delivery,
          e.getClass().getName());
    }
    return new StaffMemberView(
        issued.member(),
        issued.inviter(),
        recorded,
        SendLimits.resendAvailability(issued.sends(), recorded.createdAt(), clock.instant()),
        false);
  }

  private DeliveryResult send(Issued issued, Actor actor) {
    StaffMember member = issued.member();
    TenantRef tenant = TenantContext.require();
    URI link =
        URI.create(
            publicUrl
                + "/"
                + Objects.requireNonNull(tenant.slug(), "slug")
                + "/convite#"
                + issued.token().value());
    String inviter = actor.profile().name();
    try {
      return mailer.send(
          new StaffInvitationMail(
              issued.invitation().id().value(),
              member.email().value(),
              member.language(),
              member.role().name(),
              inviter == null ? "" : inviter.strip(),
              link,
              issued.invitation().expiresAt()));
    } catch (RuntimeException e) {
      // Só a clase: a mensaxe dunha excepción do envío podería levar a ligazón co token.
      LOG.error(
          "Staff invitation {} mail failed unexpectedly: {}",
          issued.invitation().id().value(),
          e.getClass().getName());
      return DeliveryResult.FAILED;
    }
  }

  /** Os envíos do concello nas últimas 24 h que contan para o límite. */
  private List<Instant> municipalitySends(Instant now) {
    return invitations.sendsSince(
        now.minus(SendLimits.MUNICIPALITY_WINDOW), SendLimits.PER_MUNICIPALITY);
  }

  /** A pertenza bloqueada, se a versión é a esperada. */
  private StaffMember current(StaffMemberId id, long expectedVersion) {
    StaffMember current =
        members.findForUpdate(id).orElseThrow(() -> new DomainException(StaffError.NOT_FOUND));
    if (current.version() == null || current.version() != expectedVersion) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
    return current;
  }

  private void save(StaffMember member, long expectedVersion) {
    if (!members.update(member, expectedVersion)) {
      throw new DomainException(CommonError.STALE_VERSION);
    }
  }

  private StaffMember load(StaffMemberId id) {
    return members.find(id).orElseThrow(() -> new DomainException(StaffError.NOT_FOUND));
  }

  private StaffMemberView view(StaffMember member) {
    boolean invited = member.status() == StaffStatus.INVITED;
    Instant now = clock.instant();
    return view(
        member,
        inviter(member),
        invited ? invitations.latestOf(member.id()).orElse(null) : null,
        invited ? invitations.sendsOf(member.id()).size() : 0,
        now,
        retention.visibleSince(now));
  }

  /**
   * A invitación e se se pode reenviar saen só mentres a pertenza está invitada.
   *
   * @param sends cantos envíos leva a pertenza
   * @param visibleSince o primeiro instante que se ve no rexistro: unha desactivación anterior
   *     bloquea o correo e o nome
   */
  private static StaffMemberView view(
      StaffMember member,
      Inviter inviter,
      @Nullable StaffInvitation latest,
      int sends,
      Instant now,
      Instant visibleSince) {
    if (member.status() != StaffStatus.INVITED) {
      return new StaffMemberView(
          member, inviter, null, null, member.isIdentityBlocked(visibleSince));
    }
    return new StaffMemberView(
        member,
        inviter,
        latest,
        SendLimits.resendAvailability(sends, latest == null ? null : latest.createdAt(), now),
        false);
  }

  /**
   * Quen invitou, para cada {@code sub}: as súas pertenzas no concello, tamén desactivadas ou
   * anonimizadas, deciden se é persoal ou a plataforma ({@link Inviter#of}); o nome dunha
   * desactivada bloqueada non sae.
   */
  private Map<String, Inviter> inviters(Collection<String> subjects) {
    Map<String, List<StaffMember>> bySubject = new HashMap<>();
    for (StaffMember m : members.findBySubjects(Set.copyOf(subjects))) {
      if (m.subject() != null) {
        bySubject.computeIfAbsent(m.subject(), s -> new ArrayList<>()).add(m);
      }
    }
    Instant visibleSince = retention.visibleSince(clock.instant());
    Map<String, Inviter> inviters = new HashMap<>();
    for (String subject : subjects) {
      inviters.put(subject, Inviter.of(bySubject.getOrDefault(subject, List.of()), visibleSince));
    }
    return inviters;
  }

  private Inviter inviter(StaffMember member) {
    return inviters(Set.of(member.invitedBy())).get(member.invitedBy());
  }

  private Actor actor() {
    return currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
  }

  private <T> T inTransaction(Supplier<T> work) {
    return Objects.requireNonNull(tx.execute(s -> work.get()));
  }

  private <T> T audited(String action, Map<String, Object> details, Supplier<T> work) {
    return StaffAudit.audited(audit, action, details, work);
  }
}
