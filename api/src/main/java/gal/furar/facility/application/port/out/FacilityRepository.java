// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.Facility;
import gal.furar.facility.domain.FacilityId;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PageResult;
import java.util.List;
import java.util.Optional;

/** Porto de saída. Sempre dentro do concello actual: o adaptador non recibe o concello. */
public interface FacilityRepository {

  Optional<Facility> findById(FacilityId id);

  List<Facility> findAllById(List<FacilityId> ids);

  /** Páxina en orde alfabética coa colación do idioma (ADR-0022, punto 10). */
  PageResult<Facility> findPage(int page, int size, Language language);

  /**
   * Páxina das que poden verse no público, filtradas na base (ADR-0022, puntos 10 e 17): non de
   * baixa, publicadas e, con {@code onlineBookingOnly}, coa reserva por internet. Mesma orde ca
   * {@link #findPage}. O que lle falta a cada unha (borrador) compróbao quen chama, por lote.
   */
  List<Facility> findPublishedPage(
      int page, int size, Language language, boolean onlineBookingOnly);

  /** Todas as do concello, por data de alta (portabilidade, ADR-0018). */
  List<Facility> findAll();

  /** Nome repetido sen distinguir maiúsculas → {@code facility.name-taken}. */
  Facility insert(Facility facility);

  /**
   * Garda os cambios se a versión coincide coa gardada; se non, {@code common.stale-version}. Nome
   * repetido → {@code facility.name-taken}.
   */
  Facility update(Facility facility);
}
