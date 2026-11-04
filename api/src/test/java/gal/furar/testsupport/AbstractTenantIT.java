// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contrato do {@code <Clase>TenantIT} dunha clase {@code @NativeSql(scope = MUNICIPALITY)} (A8b,
 * ADR-0006). A proba de illamento é fixa e non se pode baleirar: crea dous concellos, sementa unha
 * fila en cada un e esixe que, como o primeiro, a clase vexa (ou cambie) a súa e nunca a do outro.
 * A subclase só di como sementar e como ler co código de produción. A8b comproba que {@link
 * #visibleTo} chama á clase probada.
 */
public abstract class AbstractTenantIT extends AbstractIT {

  /** Garda unha fila do concello (por JDBC, sen filtro) e devolve o que a identifica. */
  protected abstract Object seed(UUID municipality);

  /**
   * Chama á clase {@code @NativeSql} como o concello indicado e devolve os identificadores das
   * filas que viu ou cambiou.
   */
  protected abstract Collection<Object> visibleTo(UUID municipality);

  @Test
  final void seesOnlyItsOwnMunicipality() {
    UUID own = id(createMunicipality(uniqueSlug("tenant-a")));
    UUID other = id(createMunicipality(uniqueSlug("tenant-b")));
    Object mine = seed(own);
    Object theirs = seed(other);

    assertThat(visibleTo(own)).contains(mine).doesNotContain(theirs);
    assertThat(visibleTo(other)).contains(theirs).doesNotContain(mine);
  }
}
