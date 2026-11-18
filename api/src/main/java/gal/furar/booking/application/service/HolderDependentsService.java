// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.BookingPermission;
import gal.furar.booking.NoStaffAccounts;
import gal.furar.booking.StaffAccounts;
import gal.furar.booking.application.port.in.DependentsRateLimitedException;
import gal.furar.booking.application.port.in.HolderDependentsUseCase;
import gal.furar.booking.application.port.out.BookingRepository;
import gal.furar.booking.domain.Booking;
import gal.furar.booking.domain.BookingError;
import gal.furar.booking.domain.BookingId;
import gal.furar.booking.domain.Holder;
import gal.furar.people.DependentSummary;
import gal.furar.people.PeopleQueries;
import gal.furar.shared.audit.AuditEvent;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.audit.AuditFilter;
import gal.furar.shared.audit.AuditLog;
import gal.furar.shared.audit.AuditRecord;
import gal.furar.shared.audit.AuditTrail;
import gal.furar.shared.domain.CommonError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.EmailKey;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As persoas a cargo da conta dun correo, para o «Para quen» do mostrador (ADR-0031, punto 7): a
 * única lectura que o persoal fai das fichas dunha conta. Mínima exposición:
 *
 * <ul>
 *   <li>só as fichas activas da conta do correo que o propio persoal escribe (ou o da reserva que
 *       cambia); sen conta, sen fichas ou cunha conta de traballo, a mesma lista baleira, e o
 *       {@code sub} nunca sae;
 *   <li><b>sen rexistro non hai lista:</b> cada lectura queda no rexistro de actividade ({@value
 *       #READ}) antes de devolver nada, con cantas e {@code emailHash}, a pegada con clave do
 *       correo (HMAC-SHA256 co segredo do despregue {@code FURAR_AUDIT_HASH_SECRET}): permite saber
 *       despois se alguén consultou un correo concreto sen gardar o correo;
 *   <li>como moito {@value #MAX_PER_HOUR} lecturas por persoa do persoal e hora, contadas no propio
 *       rexistro; pasado, 429 con {@code Retry-After}.
 * </ul>
 */
@Service
public class HolderDependentsService implements HolderDependentsUseCase {

  static final String READ = "booking.dependents-read";

  /** Lecturas por persoa do persoal e hora. */
  static final int MAX_PER_HOUR = 30;

  /** Lonxitude mínima do segredo da pegada. */
  static final int MIN_SECRET_LENGTH = 32;

  private static final Duration WINDOW = Duration.ofHours(1);
  private static final String HMAC = "HmacSHA256";

  private final PeopleQueries people;
  private final BookingRepository bookings;
  private final StaffAccounts staffAccounts;
  private final AuditLog audit;
  private final AuditTrail trail;
  private final CurrentActor currentActor;
  private final Clock clock;
  private final SecretKeySpec hashKey;

  public HolderDependentsService(
      PeopleQueries people,
      BookingRepository bookings,
      ObjectProvider<StaffAccounts> staffAccounts,
      AuditLog audit,
      AuditTrail trail,
      CurrentActor currentActor,
      Clock clock,
      @Value("${furar.audit.hash-secret}") String hashSecret) {
    this.people = people;
    this.bookings = bookings;
    this.staffAccounts = staffAccounts.getIfAvailable(NoStaffAccounts::new);
    this.audit = audit;
    this.trail = trail;
    this.currentActor = currentActor;
    this.clock = clock;
    if (hashSecret == null || hashSecret.strip().length() < MIN_SECRET_LENGTH) {
      throw new IllegalStateException(
          "furar.audit.hash-secret (FURAR_AUDIT_HASH_SECRET) must have at least "
              + MIN_SECRET_LENGTH
              + " characters (ADR-0031)");
    }
    this.hashKey = new SecretKeySpec(hashSecret.strip().getBytes(StandardCharsets.UTF_8), HMAC);
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(BookingPermission.Code.BOOK_FOR)
  public List<DependentSummary> of(@Nullable String email, @Nullable BookingId bookingId) {
    TenantRef tenant = TenantContext.require();
    String actor =
        currentActor
            .current()
            .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
            .subject();
    Map<String, Object> details = new LinkedHashMap<>();
    try {
      String asked = email(email, bookingId, details);
      if (asked != null) {
        details.put("emailHash", hash(EmailKey.of(asked)));
      }
      checkRate(tenant, actor);
      List<DependentSummary> found =
          asked == null
              ? List.of()
              : people
                  .accountByEmail(asked)
                  .filter(account -> !staffAccounts.isStaffAccount(account))
                  .map(people::dependents)
                  .orElse(List.of());
      details.put("count", found.size());
      // Antes de devolver nada: se o rexistro non se pode escribir, non hai lista.
      audit.record(new AuditEvent(READ, Outcome.SUCCESS, tenant.id(), tenant.slug(), details));
      return found;
    } catch (DomainException e) {
      audit.recordFailure(e, READ, tenant.id(), tenant.slug(), details);
      throw e;
    }
  }

  /** O correo consultado: o escrito ou o da reserva que se cambia. */
  private @Nullable String email(
      @Nullable String email, @Nullable BookingId bookingId, Map<String, Object> details) {
    if (bookingId != null) {
      details.put("bookingId", bookingId.value().toString());
      Booking booking =
          bookings
              .findById(bookingId)
              .orElseThrow(() -> new DomainException(BookingError.NOT_FOUND));
      Holder holder = booking.holder();
      return holder == null ? null : holder.email();
    }
    if (email == null || email.isBlank()) {
      throw new DomainException(CommonError.INVALID_PARAMETER, Map.of("name", "email"));
    }
    return email;
  }

  /** As lecturas boas da última hora desta persoa do persoal, contadas no rexistro. */
  private void checkRate(TenantRef tenant, String actor) {
    Instant now = clock.instant();
    List<AuditRecord> recent =
        trail
            .page(
                tenant.id(),
                new AuditFilter(
                    now.minus(WINDOW),
                    now.plusSeconds(1),
                    null,
                    Set.of(actor),
                    Outcome.SUCCESS,
                    Set.of(READ),
                    Set.of(),
                    Set.of()),
                null)
            .records();
    if (recent.size() >= MAX_PER_HOUR) {
      // Do máis novo ao máis vello: cando caduque a trixésima, cabe outra.
      Instant oldest = recent.get(MAX_PER_HOUR - 1).occurredAt();
      throw new DependentsRateLimitedException(
          Duration.between(now, oldest.plus(WINDOW)).toSeconds() + 1);
    }
  }

  /** HMAC-SHA256 do correo normalizado, en hexadecimal. */
  String hash(String emailKey) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(hashKey);
      return HexFormat.of().formatHex(mac.doFinal(emailKey.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA256 is mandatory in every JDK", e);
    }
  }
}
