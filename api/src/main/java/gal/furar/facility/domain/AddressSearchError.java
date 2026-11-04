// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros da busca de enderezos (D1-14, ADR-0023). Cada código ten texto en
 * messages/facility_address_*.properties e en gl.json/es.json.
 */
public enum AddressSearchError implements ErrorCode {
  /** CartoCiudad non responde, tarda máis do tempo límite ou responde mal: enderezo a man. */
  GEOCODER_UNAVAILABLE("facility.geocoder-unavailable", 503),
  /** A suxestión xa non existe ou o punto non ten enderezo próximo. */
  ADDRESS_NOT_FOUND("facility.address-not-found", 404);

  private final String code;
  private final int status;

  AddressSearchError(String code, int status) {
    this.code = code;
    this.status = status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int status() {
    return status;
  }
}
