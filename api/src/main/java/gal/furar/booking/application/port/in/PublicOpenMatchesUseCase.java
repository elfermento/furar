// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.shared.domain.PageResult;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As partidas abertas en público, sen sesión (ADR-0040, punto 9): só as abertas, sen ocultar, sen
 * empezar e co concello coa función acesa. Co nome de quen organiza e ningún outro.
 */
public interface PublicOpenMatchesUseCase {

  /** Filas como moito por páxina. */
  int MAX_PAGE = 50;

  /** As que teñen sitio, da que empeza antes; dunha instalación ou de todas. Apagado: ningunha. */
  PageResult<OpenMatchView> list(@Nullable UUID facilityId, int page, int size);

  /**
   * A páxina dunha partida que se ve en público (tamén completa); o resto, 404 {@code
   * booking.match-not-found}.
   */
  OpenMatchView find(UUID matchId);
}
