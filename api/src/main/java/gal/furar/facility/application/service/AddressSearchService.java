// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.FacilityPermission;
import gal.furar.facility.application.port.in.SearchAddressUseCase;
import gal.furar.facility.application.port.out.AddressSearch;
import gal.furar.facility.domain.AddressMatch;
import gal.furar.facility.domain.AddressSearchError;
import gal.furar.facility.domain.AddressSuggestion;
import gal.furar.facility.domain.SearchArea;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.domain.FieldViolation;
import gal.furar.shared.domain.ValidationError;
import gal.furar.shared.security.RequiresPermission;
import gal.furar.shared.tenancy.MunicipalityPlaces;
import gal.furar.shared.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Busca de enderezos (ADR-0023). Só le: non escribe nada nin deixa rexistro de actividade. A
 * CartoCiudad só chega o texto que escribe o persoal ou un punto, desde a IP do servidor.
 */
@Service
public class AddressSearchService implements SearchAddressUseCase {

  static final int MIN_QUERY = 3;
  static final int MAX_QUERY = 100;
  private static final BigDecimal LAT_MAX = BigDecimal.valueOf(90);
  private static final BigDecimal LON_MAX = BigDecimal.valueOf(180);

  private final AddressSearch search;
  private final MunicipalityPlaces places;

  public AddressSearchService(AddressSearch search, MunicipalityPlaces places) {
    this.search = search;
    this.places = places;
  }

  @Override
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public List<AddressSuggestion> suggest(String query) {
    String text = normalize(query);
    int length = text.codePointCount(0, text.length());
    if (length < MIN_QUERY || length > MAX_QUERY) {
      return List.of();
    }
    List<AddressSuggestion> found = search.suggest(text, area());
    return found.size() > AddressSearch.LIMIT ? found.subList(0, AddressSearch.LIMIT) : found;
  }

  @Override
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public AddressMatch resolve(String id) {
    return search
        .resolve(id)
        .orElseThrow(() -> new DomainException(AddressSearchError.ADDRESS_NOT_FOUND));
  }

  @Override
  @RequiresPermission(FacilityPermission.Code.VIEW)
  public AddressMatch reverse(BigDecimal latitude, BigDecimal longitude) {
    List<FieldViolation> invalid = new ArrayList<>();
    range(invalid, "lat", latitude, LAT_MAX);
    range(invalid, "lon", longitude, LON_MAX);
    if (!invalid.isEmpty()) {
      throw DomainException.validation(invalid);
    }
    return search
        .reverse(latitude, longitude)
        .orElseThrow(() -> new DomainException(AddressSearchError.ADDRESS_NOT_FOUND));
  }

  /** Espazos ao redor fóra e os de dentro reducidos a un: «rúa real » e «rúa real» son iguais. */
  static String normalize(String query) {
    return query == null ? "" : query.strip().replaceAll("\\s+", " ");
  }

  private SearchArea area() {
    return places
        .place(TenantContext.require().id())
        .map(place -> new SearchArea(place.name(), place.ineCode()))
        .orElse(SearchArea.ANYWHERE);
  }

  private static void range(
      List<FieldViolation> out, String field, BigDecimal value, BigDecimal max) {
    if (value == null) {
      out.add(FieldViolation.of(field, ValidationError.REQUIRED));
    } else if (value.abs().compareTo(max) > 0) {
      out.add(
          FieldViolation.of(field, ValidationError.RANGE, Map.of("min", max.negate(), "max", max)));
    }
  }
}
