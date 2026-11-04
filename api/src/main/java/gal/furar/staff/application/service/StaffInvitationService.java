// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.booking.BookingQueries;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.Actor;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.security.Role;
import gal.furar.shared.security.RoleGrant;
import gal.furar.shared.tenancy.MunicipalityPlaces;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.staff.application.port.in.AcceptStaffInvitationCommand;
import gal.furar.staff.application.port.in.StaffInvitationPreview;
import gal.furar.staff.application.port.in.StaffInvitationUseCase;
import gal.furar.staff.application.port.out.StaffAccessRepository;
import gal.furar.staff.application.port.out.StaffInvitationRepository;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.domain.InvitationToken;
import gal.furar.staff.domain.StaffError;
import gal.furar.staff.domain.StaffInvitation;
import gal.furar.staff.domain.StaffMember;
import gal.furar.staff.domain.StaffRole;
import gal.furar.staff.domain.StaffStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ver e aceptar a invitación ao persoal (ADR-0027; plan do dominio 4, §1.2.6 e §2.3).
 *
 * <p><b>Orde das comprobacións ao aceptar</b> (a primeira que falla decide a resposta):
 *
 * <ol>
 *   <li>forma do token, hash no concello do URL, pertenza aínda invitada e invitación aberta (nin
 *       usada nin substituída por un reenvío ou unha desactivación) → se non, {@code 404
 *       staff.invitation-invalid}, a mesma resposta ca un token inventado;
 *   <li>caducidade → {@code 410 staff.invitation-expired};
 *   <li>a conta non é da plataforma ({@code PLATFORM_ADMIN}) nin a de quen invitou ou reenviou →
 *       {@code 403 staff.invitation-platform-account} ou {@code 403 staff.invitation-own}
 *       (segregación de funcións, op.acc.3);
 *   <li>correo verificado da sesión igual ao invitado → {@code 403 staff.invitation-email-mismatch}
 *       (o correo sen verificar xa o parou {@link PersonUseCase} con {@code 403
 *       auth.email-not-verified});
 *   <li>a conta non reservou nunca como veciña, en ningún concello nin estado → {@code 403
 *       staff.invitation-personal-account}: a conta de traballo vai á parte da persoal (ADR-0027,
 *       revisión do 2026-10-30): a persoa ten que ser invitada co correo do traballo;
 *   <li>a persoa non é xa activa no concello (por outra pertenza) → {@code 409
 *       staff.already-member}.
 * </ol>
 *
 * <p><b>Carreiras.</b> A pertenza lese con {@code select … for update}, así que dúas aceptacións do
 * mesmo token van unha detrás doutra e a segunda xa a ve activa (404). Ademais, {@code markUsed} só
 * marca unha invitación aberta e sen caducar: se un reenvío ou unha desactivación chegou antes, non
 * se acepta. O índice único das persoas activas por concello é a última garda (409).
 *
 * <p><b>Sen límite de intentos en {@code preview}.</b> O token son 32 bytes de {@code SecureRandom}
 * (256 bits) e caduca aos 7 días: aínda que alguén probase mil millóns de tokens por segundo
 * durante eses 7 días contra mil invitacións abertas, a probabilidade de acertar unha sería de
 * arredor de 10<sup>−59</sup>. Un límite por IP non engade seguridade e obrigaría a gardar IPs. As
 * respostas son uniformes (mesmo código e corpo para todo token que non vale) e a busca vai polo
 * hash nun índice único, sen comparar textos, así que tampouco hai oráculo de tempo útil.
 *
 * <p>O token nunca sae: non se rexistra, non vai en eventos nin respostas e os detalles do rexistro
 * de actividade ({@code staff.accept}) só levan a pertenza, o rol e o código de erro.
 */
@Service
public class StaffInvitationService implements StaffInvitationUseCase {

  static final String ACCEPT = "staff.accept";

  private final StaffMemberRepository members;
  private final StaffInvitationRepository invitations;
  private final StaffAccessRepository access;
  private final BookingQueries bookings;
  private final MunicipalityPlaces places;
  private final CurrentActor currentActor;
  private final AuditLog audit;
  private final TransactionTemplate readTx;
  private final TransactionTemplate writeTx;
  private final RegistrationProofs proofs;
  private final Clock clock;

  public StaffInvitationService(
      StaffMemberRepository members,
      StaffInvitationRepository invitations,
      StaffAccessRepository access,
      BookingQueries bookings,
      MunicipalityPlaces places,
      CurrentActor currentActor,
      AuditLog audit,
      PlatformTransactionManager transactions,
      RegistrationProofs proofs,
      Clock clock) {
    this.members = members;
    this.invitations = invitations;
    this.access = access;
    this.bookings = bookings;
    this.places = places;
    this.currentActor = currentActor;
    this.audit = audit;
    this.readTx = new TransactionTemplate(transactions);
    this.readTx.setReadOnly(true);
    this.writeTx = new TransactionTemplate(transactions);
    this.proofs = proofs;
    this.clock = clock;
  }

  @Override
  @PublicUseCase
  public StaffInvitationPreview preview(@Nullable String token) {
    return Objects.requireNonNull(
        readTx.execute(
            s -> {
              Instant now = clock.instant();
              StaffInvitation invitation = open(token);
              StaffMember member = invitedMember(invitation, false);
              invitation.requireUsable(now);
              String name =
                  places
                      .place(TenantContext.require().id())
                      .map(MunicipalityPlaces.Place::name)
                      .orElseThrow(StaffInvitationService::invalid);
              return new StaffInvitationPreview(
                  name,
                  member.role(),
                  member.email().value(),
                  invitation.expiresAt(),
                  proofs.issue(invitation.id(), member.email(), now, invitation.expiresAt()));
            }));
  }

  @Override
  @PersonUseCase
  public StaffRole accept(AcceptStaffInvitationCommand command) {
    Actor actor =
        currentActor.current().orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED));
    if (!actor.emailVerified()) {
      throw new DomainException(AuthError.EMAIL_NOT_VERIFIED);
    }
    return Objects.requireNonNull(
        writeTx.execute(
            s -> {
              Map<String, Object> details = new LinkedHashMap<>();
              return StaffAudit.audited(
                  audit, ACCEPT, details, () -> accept(command, actor, details));
            }));
  }

  private StaffRole accept(
      AcceptStaffInvitationCommand command, Actor actor, Map<String, Object> details) {
    Instant now = clock.instant();
    StaffInvitation invitation = open(command.token());
    details.put("memberId", invitation.memberId().value().toString());
    StaffMember member = invitedMember(invitation, true);
    details.put("role", member.role().name());
    invitation.requireUsable(now);
    if (isPlatformAccount(actor)) {
      throw new DomainException(StaffError.INVITATION_PLATFORM_ACCOUNT);
    }
    if (actor.subject().equals(member.invitedBy())
        || actor.subject().equals(invitation.requestedBy())) {
      throw new DomainException(StaffError.INVITATION_OWN);
    }
    if (!member.email().matches(actor.profile().email())) {
      throw new DomainException(StaffError.INVITATION_EMAIL_MISMATCH);
    }
    if (bookings.hasResidentBookings(actor.subject())) {
      throw new DomainException(StaffError.INVITATION_PERSONAL_ACCOUNT);
    }
    if (access.active(actor.subject()).isPresent()) {
      throw new DomainException(StaffError.ALREADY_MEMBER);
    }
    StaffMember accepted =
        member.accept(actor.profile().email(), actor.subject(), actor.profile().name(), now);
    if (!invitations.markUsed(invitation.id(), now)) {
      // Un reenvío ou unha desactivación chegaron antes, ou caducou xusto agora.
      throw new DomainException(
          invitation.isExpired(now)
              ? StaffError.INVITATION_EXPIRED
              : StaffError.INVITATION_INVALID);
    }
    try {
      if (!members.update(accepted, Objects.requireNonNull(member.version(), "version"))) {
        throw invalid();
      }
    } catch (DuplicateKeyException e) {
      // A mesma persoa aceptou á vez outra pertenza do concello (índice único das activas).
      throw new DomainException(StaffError.ALREADY_MEMBER);
    }
    return accepted.role();
  }

  /** Se a conta ten o rol da plataforma. */
  private static boolean isPlatformAccount(Actor actor) {
    return actor.grants().stream().map(RoleGrant::role).anyMatch(Role.PLATFORM_ADMIN::equals);
  }

  /** A invitación do token no concello actual, se ten forma de token e existe. */
  private StaffInvitation open(@Nullable String token) {
    return InvitationToken.parse(token)
        .flatMap(t -> invitations.findByHash(t.hash()))
        .orElseThrow(StaffInvitationService::invalid);
  }

  /**
   * A pertenza da invitación, se segue invitada; unha desactivada ou xa activa dá o mesmo 404 ca un
   * token inventado. Para aceptar, bloqueada ata o fin da transacción.
   */
  private StaffMember invitedMember(StaffInvitation invitation, boolean forUpdate) {
    StaffMember member =
        (forUpdate
                ? members.findForUpdate(invitation.memberId())
                : members.find(invitation.memberId()))
            .orElseThrow(StaffInvitationService::invalid);
    if (member.status() != StaffStatus.INVITED) {
      throw invalid();
    }
    return member;
  }

  private static DomainException invalid() {
    return new DomainException(StaffError.INVITATION_INVALID);
  }
}
