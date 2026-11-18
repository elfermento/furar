// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.security.PermissionKey;
import gal.furar.shared.security.Role;
import java.util.Set;

/**
 * Permisos de xestión das reservas e quen os ten (ADR-0025, punto 4; ADR-0005, ADR-0007). A persoa
 * que reserva non usa ningún: os seus casos de uso levan {@code @PersonUseCase}.
 */
public enum BookingPermission implements PermissionKey {
  /** Ver a lista, o detalle, as contas de cobros e o calendario. */
  VIEW(Code.VIEW, Set.of(Role.MANAGER, Role.STAFF)),
  /** Marcar pagada ou devolta, e desfacelo: traballo do mostrador. */
  PAYMENT(Code.PAYMENT, Set.of(Role.MANAGER, Role.STAFF)),
  /**
   * Reservar no nome dunha persoa (mostrador ou teléfono) e cambiar unha reserva co mesmo
   * formulario (especificación, bloque 7: «Reservar no nome de alguén», activado en Recepción).
   */
  BOOK_FOR(Code.BOOK_FOR, Set.of(Role.MANAGER, Role.STAFF)),
  /** Cancelar unha reserva en nome do concello. */
  CANCEL(Code.CANCEL, Set.of(Role.MANAGER)),
  /** Descargar as reservas dun período en CSV: leva datos persoais, só a xestión. */
  EXPORT(Code.EXPORT, Set.of(Role.MANAGER)),
  /**
   * Validar o QR, marcar «non presentado» e desfacelo (ADR-0032, punto 6; especificación, bloque 7:
   * «Ler o QR e marcar usada ou non presentado»).
   */
  ATTENDANCE(Code.ATTENDANCE, Set.of(Role.MANAGER, Role.STAFF)),
  /**
   * Ver as valoracións cos seus comentarios (tamén os ocultos) e ocultar ou volver mostrar un
   * comentario (ADR-0037, punto 6): os mesmos roles ca os indicadores, onde se ven.
   */
  REVIEWS(Code.REVIEWS, Set.of(Role.MANAGER)),
  /**
   * Ocultar ou volver mostrar unha partida aberta (ADR-0040, punto 8), coma as valoracións: só a
   * xestión. Vela no detalle da reserva é {@link #VIEW}.
   */
  OPEN_MATCHES(Code.OPEN_MATCHES, Set.of(Role.MANAGER)),
  /**
   * As entidades e os seus responsables, as tempadas (crear, rematar, pago) e as solicitudes de
   * tempada (ADR-0039, punto 13): só a xestión. Ver as sesións é {@link #VIEW}; cancelalas, {@link
   * #CANCEL}.
   */
  ORGANIZATIONS(Code.ORGANIZATIONS, Set.of(Role.MANAGER));

  /** Códigos, para {@code @RequiresPermission}. */
  public static final class Code {
    public static final String VIEW = "booking.view";
    public static final String PAYMENT = "booking.payment";
    public static final String BOOK_FOR = "booking.book-for";
    public static final String CANCEL = "booking.cancel";
    public static final String EXPORT = "booking.export";
    public static final String ATTENDANCE = "booking.attendance";
    public static final String REVIEWS = "booking.reviews";
    public static final String OPEN_MATCHES = "booking.open-matches";
    public static final String ORGANIZATIONS = "booking.organizations";

    private Code() {}
  }

  private final String code;
  private final Set<Role> roles;

  BookingPermission(String code, Set<Role> roles) {
    this.code = code;
    this.roles = roles;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public Set<Role> roles() {
    return roles;
  }
}
