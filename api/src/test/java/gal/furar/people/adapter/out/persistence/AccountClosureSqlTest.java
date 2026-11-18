// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.people.domain.AccountDeletionBlocker;
import gal.furar.people.domain.AccountDeletionBlocker.Kind;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-0043: o JSON das funcións da base, tal como o escribe PostgreSQL. */
class AccountClosureSqlTest {

  static final String NARON = "00000000-0000-7000-8000-000000000001";
  static final String EVENT = "00000000-0000-7000-8000-0000000000e1";

  @Test
  void readsTheBlockers() {
    String json =
        "[{\"kind\": \"BOOKING\", \"count\": 2, \"eventId\": null, \"nameEs\": null, \"nameGl\":"
            + " null, \"municipalityId\": \""
            + NARON
            + "\"}, {\"kind\": \"EVENT\", \"count\": 1, \"eventId\": \""
            + EVENT
            + "\", \"nameEs\": \"Carrera\", \"nameGl\": \"Carreira\", \"municipalityId\": \""
            + NARON
            + "\"}, {\"kind\": \"PLATFORM_ADMIN\", \"count\": 1, \"eventId\": null, \"nameEs\":"
            + " null, \"nameGl\": null, \"municipalityId\": null}]";

    assertThat(AccountClosureSql.parseBlockers(json))
        .containsExactly(
            new AccountDeletionBlocker(Kind.BOOKING, UUID.fromString(NARON), null, null, null, 2),
            new AccountDeletionBlocker(
                Kind.EVENT,
                UUID.fromString(NARON),
                UUID.fromString(EVENT),
                "Carreira",
                "Carrera",
                1),
            new AccountDeletionBlocker(Kind.PLATFORM_ADMIN, null, null, null, null, 1));
    assertThat(AccountClosureSql.parseBlockers("[]")).isEmpty();
    assertThat(AccountClosureSql.parseBlockers(null)).isEmpty();
  }

  @Test
  void readsTheRowsPerMunicipality() {
    assertThat(AccountClosureSql.parseCounts("{\"" + NARON + "\": 7}"))
        .isEqualTo(Map.of(UUID.fromString(NARON), 7L));
    assertThat(AccountClosureSql.parseCounts("{}")).isEmpty();
  }
}
