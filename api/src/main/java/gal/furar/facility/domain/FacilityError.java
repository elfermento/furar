// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import gal.furar.shared.domain.ErrorCode;

/**
 * Erros do módulo. Cada código ten texto en messages/facility_*.properties e en gl.json/es.json.
 */
public enum FacilityError implements ErrorCode {
  NOT_FOUND("facility.not-found", 404),
  NAME_INVALID("facility.name-invalid", 422),
  NAME_TAKEN("facility.name-taken", 409),
  ADDRESS_REQUIRED("facility.address-required", 422),
  ADDRESS_INVALID("facility.address-invalid", 422),
  /** Hai reservas afectadas sen decisión (ou cambiaron): o problema leva {@code affected[]}. */
  AFFECTED_BOOKINGS("facility.affected-bookings", 409),
  /** Unha decisión do modal non é coherente: cancelar sen canle ou manter con canles. */
  DECISION_INVALID("facility.decision-invalid", 422),
  /** Tarxeta Datos: algún campo non vale ({@code errors[]} por campo). */
  DETAILS_INVALID("facility.details-invalid", 422),
  /** Reserva por internet e mostrar os ocos libres á vez. */
  BOOKING_MODE_INVALID("facility.booking-mode-invalid", 422),
  /** Descrición nun só idioma ou demasiado longa. */
  DESCRIPTION_INVALID("facility.description-invalid", 422),
  /** Normas da instalación nun só idioma ou demasiado longas. */
  HOUSE_RULES_INVALID("facility.house-rules-invalid", 422),
  /** Un deporte que non está no catálogo nin entre os do concello. */
  SPORT_UNKNOWN("facility.sport-unknown", 422),
  /** O mesmo deporte dúas veces na instalación. */
  SPORT_DUPLICATED("facility.sport-duplicated", 409),
  /** Xa hai un deporte do concello con ese nome (sen maiúsculas). */
  SPORT_NAME_TAKEN("facility.sport-name-taken", 409),
  /** Nome do deporte novo baleiro ou longo. */
  SPORT_NAME_INVALID("facility.sport-name-invalid", 422),
  /** A foto non existe nesa instalación (ou a instalación está de baixa, na lectura pública). */
  PHOTO_NOT_FOUND("facility.photo-not-found", 404),
  /** Ficheiro de máis de 8 MB (o límite de Spring, antes de lelo). */
  PHOTO_TOO_LARGE("facility.photo-too-large", 413),
  /** Máis de 40 megapíxeles: rexéitase antes de decodificar. */
  PHOTO_TOO_MANY_PIXELS("facility.photo-too-many-pixels", 422),
  /** Nin JPEG nin PNG (polo contido, non polo nome nin polo tipo que di o navegador). */
  PHOTO_UNSUPPORTED_TYPE("facility.photo-unsupported-type", 415),
  /** JPEG ou PNG que non se pode ler (cortado, estragado ou en CMYK). */
  PHOTO_UNREADABLE("facility.photo-unreadable", 422),
  /** Máis de 12 imaxes nunha instalación. */
  PHOTO_LIMIT("facility.photo-limit", 422),
  /** Texto alternativo baleiro nalgún idioma ou longo ({@code errors[]} por campo). */
  PHOTO_ALT_INVALID("facility.photo-alt-invalid", 422),
  /**
   * Ao gardar as imaxes: unha repetida, unha foto que non está na biblioteca do concello ou un
   * debuxo que non existe.
   */
  IMAGES_INVALID("facility.images-invalid", 422),
  /** Só se pode descartar unha subida pendente, non unha foto da biblioteca. */
  PHOTO_NOT_PENDING("facility.photo-not-pending", 409),
  /** Tivo reservas: non se borra, dáse de baixa para conservar o historial. */
  HAS_BOOKINGS("facility.has-bookings", 409),
  /** «Publicar» sen enderezo, deportes, usos ou horario. */
  NOT_PUBLISHABLE("facility.not-publishable", 409);

  private final String code;
  private final int status;

  FacilityError(String code, int status) {
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
