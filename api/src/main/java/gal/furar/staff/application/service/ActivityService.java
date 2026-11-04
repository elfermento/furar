// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.application.service;

import gal.furar.shared.audit.AuditError;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditFilter;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.audit.AuditPage;
import gal.furar.shared.audit.AuditPermission;
import gal.furar.shared.audit.AuditRecord;
import gal.furar.shared.audit.AuditRetention;
import gal.furar.shared.audit.AuditTrail;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.security.Role;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import gal.furar.staff.StaffMemberSummary;
import gal.furar.staff.StaffQueries;
import gal.furar.staff.application.port.in.ActivityEntry;
import gal.furar.staff.application.port.in.ActivityEntry.ActorKind;
import gal.furar.staff.application.port.in.ActivityEntry.Target;
import gal.furar.staff.application.port.in.ActivityEntry.TargetKind;
import gal.furar.staff.application.port.in.ActivityPage;
import gal.furar.staff.application.port.in.ActivityQuery;
import gal.furar.staff.application.port.in.BlockedActivityExport;
import gal.furar.staff.application.port.in.ExportBlockedActivityCommand;
import gal.furar.staff.application.port.in.ExportBlockedActivityUseCase;
import gal.furar.staff.application.port.in.ReadActivityUseCase;
import gal.furar.staff.application.port.in.ReadPlatformActivityUseCase;
import gal.furar.staff.application.port.out.StaffMemberRepository;
import gal.furar.staff.domain.StaffMember;
import gal.furar.staff.domain.StaffMemberId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rexistro de actividade (ADR-0020, ADR-0027; plan do dominio 4, §1.2.12). Le os eventos con {@link
 * AuditTrail} e pon a cada {@code sub} o nome e o correo da súa pertenza no concello do evento,
 * tamén se está desactivada ({@link StaffQueries}). Unha pertenza anonimizada (§1.2.14) segue sendo
 * persoal ({@code MEMBER}), pero sen nome nin correo: a web di «Persoa anonimizada». Sen pertenza,
 * a conta sae como da plataforma (se actuou con {@code PLATFORM_ADMIN}), como unha tarefa de Furar
 * (un nome {@code furar:…}, sen roles) ou como outra conta.
 *
 * <ul>
 *   <li><b>Concello:</b> todo o que pasou nel, tamén o que fixo a plataforma; o rol queda en {@code
 *       actor.roles} e a conta da plataforma, en {@code actor.kind}.
 *   <li><b>Plataforma:</b> as súas accións en todos os concellos e as que non teñen concello. Os
 *       nomes lense en cada concello por separado, cunha transacción curta de lectura en cada un
 *       (RLS).
 * </ul>
 *
 * <p>O período por defecto é o máis longo que se admite ({@value AuditFilter#MAX_DAYS} días ata
 * agora). <b>Bloqueo</b> (LOPDGDD art. 32; ADR-0020, revisión do 2026-10-29): as dúas listas
 * recortan o período ao tramo visible ({@link AuditRetention#visibleSince}, un ano por defecto),
 * así que ningunha devolve un evento bloqueado; e o persoal desactivado antes dese tramo sae sen
 * nome nin correo ({@code blocked}). Os bloqueados só saen pola exportación do xestor ({@link
 * #export}), cun motivo e unha referencia que quedan no rexistro ({@value #EXPORT_BLOCKED}), e nela
 * a identidade do persoal vese sempre que aínda non estea anonimizada.
 */
@Service
public class ActivityService
    implements ReadActivityUseCase, ReadPlatformActivityUseCase, ExportBlockedActivityUseCase {

  /** Acción do rexistro ao exportar os bloqueados. */
  public static final String EXPORT_BLOCKED = "audit.export-blocked";

  /** Eventos como moito nunha exportación: se hai máis, pídese un período máis curto. */
  public static final int EXPORT_MAX_ROWS = 10_000;

  /** Detalle → tipo, por orde: a reserva antes ca a súa instalación. */
  private static final List<Map.Entry<String, TargetKind>> KEYS =
      List.of(
          Map.entry("bookingId", TargetKind.BOOKING),
          Map.entry("facilityId", TargetKind.FACILITY),
          Map.entry("photoId", TargetKind.PHOTO));

  private final AuditTrail trail;
  private final StaffQueries staff;
  private final StaffMemberRepository members;
  private final TransactionTemplate readOnly;
  private final AuditRetention retention;
  private final AuditLog audit;
  private final Clock clock;

  public ActivityService(
      AuditTrail trail,
      StaffQueries staff,
      StaffMemberRepository members,
      PlatformTransactionManager transactions,
      AuditRetention retention,
      AuditLog audit,
      Clock clock) {
    this.retention = retention;
    this.audit = audit;
    this.trail = trail;
    this.staff = staff;
    this.members = members;
    this.readOnly = new TransactionTemplate(transactions);
    this.readOnly.setReadOnly(true);
    this.readOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(AuditPermission.Code.VIEW)
  public ActivityPage read(ActivityQuery query) {
    TenantRef tenant = TenantContext.require();
    Instant now = clock.instant();
    AuditFilter period = period(query, now);
    if (period == null) {
      return new ActivityPage(List.of(), null);
    }
    String actor = null;
    if (query.memberId() != null) {
      Optional<String> subject = subjectOf(query.memberId());
      if (subject.isEmpty()) {
        // Unha pertenza doutro concello, inexistente ou que nunca aceptou: non fixo nada aquí.
        return new ActivityPage(List.of(), null);
      }
      actor = subject.get();
    }
    AuditFilter filter =
        new AuditFilter(period.from(), period.to(), query.area(), actor, query.outcome());
    AuditPage page = trail.page(tenant.id(), filter, query.after());
    Directory directory = directory(page.records(), retention.visibleSince(now));
    return new ActivityPage(
        page.records().stream().map(r -> entry(r, directory)).toList(), page.next());
  }

  @Override
  @RequiresPermission(AuditPermission.Code.PLATFORM_VIEW)
  public ActivityPage readPlatform(ActivityQuery query) {
    Instant now = clock.instant();
    AuditFilter period = period(query, now);
    if (period == null) {
      return new ActivityPage(List.of(), null);
    }
    Instant visibleSince = retention.visibleSince(now);
    AuditPage page = trail.platformPage(period, query.after());
    Map<UUID, Directory> directories = new HashMap<>();
    page.records().stream()
        .filter(r -> r.municipalityId() != null)
        .collect(
            Collectors.groupingBy(
                r -> Objects.requireNonNull(r.municipalityId()),
                LinkedHashMap::new,
                Collectors.toList()))
        .forEach(
            (municipality, records) ->
                directories.put(
                    municipality,
                    TenantContext.runAs(
                        new TenantRef(municipality, records.get(0).municipalitySlug()),
                        () ->
                            Objects.requireNonNull(
                                readOnly.execute(s -> directory(records, visibleSince))))));
    return new ActivityPage(
        page.records().stream()
            .map(
                r ->
                    entry(
                        r,
                        r.municipalityId() == null
                            ? Directory.EMPTY
                            : directories.get(r.municipalityId())))
            .toList(),
        page.next());
  }

  /**
   * O período pedido (sen comezo, o máis longo; sen fin, ata agora), validado e recortado ao tramo
   * visible: nulo se enteiro está bloqueado ou borrado.
   */
  private @Nullable AuditFilter period(ActivityQuery query, Instant now) {
    Instant to = query.to() != null ? query.to() : now;
    Instant from =
        query.from() != null ? query.from() : to.minus(Duration.ofDays(AuditFilter.MAX_DAYS));
    AuditFilter asked = AuditFilter.between(from, to);
    Instant visibleSince = retention.visibleSince(now);
    if (!asked.to().isAfter(visibleSince)) {
      return null;
    }
    return asked.from().isBefore(visibleSince)
        ? AuditFilter.between(visibleSince, asked.to())
        : asked;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(AuditPermission.Code.EXPORT_BLOCKED)
  public BlockedActivityExport export(ExportBlockedActivityCommand command) {
    TenantRef tenant = TenantContext.require();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("reason", command.reason().name());
    return StaffAudit.audited(
        audit,
        EXPORT_BLOCKED,
        details,
        () -> {
          details.put("reference", command.checkedReference());
          // A duración pedida, coma no rexistro; despois, só o tramo bloqueado.
          AuditFilter asked = AuditFilter.between(command.from(), command.to());
          Instant now = clock.instant();
          Instant from = latest(asked.from(), retention.keptSince(now));
          Instant to = earliest(asked.to(), retention.visibleSince(now));
          if (!from.isBefore(to)) {
            throw new DomainException(AuditError.EXPORT_NOT_BLOCKED);
          }
          details.put("from", from.toString());
          details.put("to", to.toString());
          List<AuditRecord> records = trail.all(tenant.id(), from, to, EXPORT_MAX_ROWS);
          if (records.size() > EXPORT_MAX_ROWS) {
            throw new DomainException(
                AuditError.EXPORT_TOO_LARGE, Map.of("maxRows", EXPORT_MAX_ROWS));
          }
          details.put("count", records.size());
          // Sen bloqueo: quen exporta ten que ver a quen corresponde cada acción.
          Directory directory = directory(records, Instant.MIN);
          return new BlockedActivityExport(
              from, to, records.stream().map(r -> entry(r, directory)).toList());
        });
  }

  private static Instant latest(Instant a, Instant b) {
    return a.isAfter(b) ? a : b;
  }

  private static Instant earliest(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }

  private Optional<String> subjectOf(StaffMemberId id) {
    return members.find(id).map(StaffMember::subject);
  }

  /**
   * Nomes e correos do concello actual para os eventos dunha páxina.
   *
   * @param visibleSince o primeiro instante visible: o persoal desactivado antes queda sen nome nin
   *     correo ({@link Instant#MIN} na exportación, onde se ve todo o que non se anonimizou)
   */
  private Directory directory(List<AuditRecord> records, Instant visibleSince) {
    Set<String> subjects =
        records.stream().map(AuditRecord::actorSubject).collect(Collectors.toSet());
    Map<String, StaffMemberSummary> bySubject = new HashMap<>();
    for (StaffMemberSummary m : staff.members(subjects)) {
      // A máis nova primeiro: unha persoa desactivada e volta a invitar sae co seu último nome.
      if (m.subject() != null) {
        bySubject.putIfAbsent(m.subject(), m);
      }
    }
    Set<StaffMemberId> targets =
        records.stream()
            .map(r -> uuid(r.details().get("memberId")))
            .filter(Objects::nonNull)
            .map(StaffMemberId::new)
            .collect(Collectors.toSet());
    Map<UUID, String> emails = new HashMap<>();
    for (StaffMember m : members.findByIds(targets)) {
      // A anonimizada queda sen correo: a web di «Persoa anonimizada» (§1.2.14); a bloqueada,
      // tamén.
      if (!m.email().isAnonymised() && !m.isIdentityBlocked(visibleSince)) {
        emails.put(m.id().value(), m.email().value());
      }
    }
    return new Directory(bySubject, emails, visibleSince);
  }

  private static ActivityEntry entry(AuditRecord record, Directory directory) {
    return new ActivityEntry(
        record.id(),
        record.occurredAt(),
        record.action(),
        record.outcome(),
        actor(record, directory),
        target(record, directory),
        record.municipalitySlug(),
        record.requestId());
  }

  private static ActivityEntry.Actor actor(AuditRecord record, Directory directory) {
    StaffMemberSummary member = directory.bySubject().get(record.actorSubject());
    if (member != null) {
      if (directory.blocked(member)) {
        return new ActivityEntry.Actor(ActorKind.MEMBER, null, null, record.actorRoles(), true);
      }
      return new ActivityEntry.Actor(
          ActorKind.MEMBER, member.name(), member.email(), record.actorRoles(), false);
    }
    if (record.actorSubject().startsWith(AuditEvent.SYSTEM_PREFIX)) {
      return new ActivityEntry.Actor(ActorKind.SYSTEM, null, null, Set.of(), false);
    }
    ActorKind kind =
        record.actorRoles().contains(Role.PLATFORM_ADMIN) ? ActorKind.PLATFORM : ActorKind.OTHER;
    return new ActivityEntry.Actor(kind, null, null, record.actorRoles(), false);
  }

  /**
   * Sobre que foi a acción, polos identificadores dos detalles (ADR-0020): a pertenza, a reserva, a
   * instalación ou a foto; nas accións do concello ({@code municipality.*}), o concello.
   */
  private static @Nullable Target target(AuditRecord record, Directory directory) {
    Map<String, Object> details = record.details();
    UUID member = uuid(details.get("memberId"));
    if (member != null) {
      return new Target(TargetKind.STAFF_MEMBER, member, directory.emails().get(member));
    }
    for (Map.Entry<String, TargetKind> key : KEYS) {
      UUID id = uuid(details.get(key.getKey()));
      if (id != null) {
        return new Target(key.getValue(), id, null);
      }
    }
    if (record.action().startsWith("municipality.") && record.municipalityId() != null) {
      return new Target(TargetKind.MUNICIPALITY, record.municipalityId(), null);
    }
    return null;
  }

  private static @Nullable UUID uuid(@Nullable Object value) {
    if (!(value instanceof String text)) {
      return null;
    }
    try {
      return UUID.fromString(text);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Nomes por {@code sub} e correos por pertenza dun concello, e o primeiro instante visible: unha
   * desactivación anterior bloquea a identidade (o correo dunha anonimizada xa vén nulo).
   */
  private record Directory(
      Map<String, StaffMemberSummary> bySubject, Map<UUID, String> emails, Instant visibleSince) {

    static final Directory EMPTY = new Directory(Map.of(), Map.of(), Instant.MIN);

    boolean blocked(StaffMemberSummary member) {
      Instant deactivatedAt = member.deactivatedAt();
      return member.status() == StaffMemberSummary.Status.DEACTIVATED
          && member.email() != null
          && deactivatedAt != null
          && deactivatedAt.isBefore(visibleSince);
    }
  }
}
