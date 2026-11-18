// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.furar.booking.application.port.out.BookingCustomFields;
import gal.furar.booking.application.port.out.BookingRetention;
import gal.furar.booking.application.port.out.OpenMatches;
import gal.furar.booking.application.port.out.ResidentBookings;
import gal.furar.booking.domain.BookingId;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * ADR-0043: ao eliminar a conta, as súas reservas sen pago anonimízanse xa, en cada concello e en
 * lotes, coa mesma anonimización ca a conservación; se a conta non reservou nunca, non se percorre
 * ningún concello.
 */
class AccountClosureBookingServiceTest {

  static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
  static final TenantRef NARON = new TenantRef(UUID.randomUUID(), "naron");
  static final TenantRef FERROL = new TenantRef(UUID.randomUUID(), "ferrol");

  static final class NoDatabaseTransactions extends AbstractPlatformTransactionManager {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }

  final ResidentBookings residents = mock(ResidentBookings.class);
  final MunicipalityDirectory directory = () -> List.of(NARON, FERROL);
  final BookingRetention bookings = mock(BookingRetention.class);
  final BookingCustomFields customFields = mock(BookingCustomFields.class);
  final OpenMatches openMatches = mock(OpenMatches.class);
  final List<UUID> tenants = new ArrayList<>();

  AccountClosureBookingService service() {
    return new AccountClosureBookingService(
        residents,
        directory,
        bookings,
        customFields,
        openMatches,
        new NoDatabaseTransactions(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  static List<BookingId> ids(int n) {
    return IntStream.range(0, n).mapToObj(i -> new BookingId(UUID.randomUUID())).toList();
  }

  @Test
  void anAccountThatNeverBookedTouchesNoMunicipality() {
    when(residents.existAnywhere("sub")).thenReturn(false);
    assertThat(service().close("sub")).isEmpty();
    verifyNoInteractions(bookings, customFields, openMatches);
  }

  @Test
  void anonymisesTheUnpaidOnesInBatchesInEachMunicipality() {
    when(residents.existAnywhere("sub")).thenReturn(true);
    List<BookingId> full = ids(BookingRetentionService.BATCH);
    List<BookingId> rest = ids(3);
    when(bookings.unpaidOf(eq("sub"), anyInt()))
        .thenAnswer(
            a -> {
              UUID tenant = TenantContext.require().id();
              tenants.add(tenant);
              long seen = tenants.stream().filter(tenant::equals).count();
              if (!tenant.equals(NARON.id())) {
                return List.of();
              }
              return seen == 1 ? full : seen == 2 ? rest : List.of();
            });

    Map<UUID, Long> done = service().close("sub");

    assertThat(done).isEqualTo(Map.of(NARON.id(), (long) BookingRetentionService.BATCH + 3));
    verify(bookings).anonymise(full, NOW);
    verify(bookings).anonymise(rest, NOW);
    verify(customFields).erasePersonalData(rest.get(0));
    verify(openMatches).purgeOfBookings(rest.stream().map(BookingId::value).toList());
    assertThat(tenants).contains(NARON.id(), FERROL.id());
  }

  @Test
  void nothingLeftNothingDone() {
    when(residents.existAnywhere("sub")).thenReturn(true);
    when(bookings.unpaidOf(eq("sub"), anyInt())).thenReturn(List.of());
    assertThat(service().close("sub")).isEmpty();
    verify(bookings, never()).anonymise(any(), any());
  }
}
