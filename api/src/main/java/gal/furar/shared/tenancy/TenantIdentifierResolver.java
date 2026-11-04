// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.tenancy;

import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Resolver de Hibernate que nunca lanza (ADR-0006). Hibernate chámao ao abrir cada sesión:
 *
 * <ul>
 *   <li>con concello → o seu id;
 *   <li>en {@link TenantContext#runAsPlatform} → {@link #PLATFORM}, que é {@code root} (sen
 *       filtro);
 *   <li>sen contexto → {@link #NONE}, un id que non existe: le 0 filas e a FK fai fallar as
 *       escrituras.
 * </ul>
 */
public final class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

  /** Centinela sen concello. Non hai ningún concello con este id. */
  public static final UUID NONE = new UUID(0L, 0L);

  /** Centinela de plataforma: Hibernate non filtra. */
  public static final UUID PLATFORM = new UUID(0L, 1L);

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    if (TenantContext.isPlatform()) {
      return PLATFORM;
    }
    return TenantContext.current().map(TenantRef::id).orElse(NONE);
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return false;
  }

  @Override
  public boolean isRoot(UUID tenantId) {
    return PLATFORM.equals(tenantId);
  }
}
