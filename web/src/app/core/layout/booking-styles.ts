// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { lazyStylesheetResolver } from './lazy-stylesheet';

/** Nome do paquete de estilos de `angular.json` (`bundleName: reservas`, `inject: false`). */
export const BOOKING_STYLESHEET = 'reservas.css';

/**
 * Folla das pezas das reservas (`styles/reservas.css`: chip de filtro, campo con panel e QR), que
 * non van na carga inicial (orzamento, D27). Ponse en `resolve` de toda ruta que as use: reservar,
 * as miñas reservas, o listado, a ficha pública, a xestión de reservas e a ficha de xestión (modal
 * do uso).
 */
export const bookingStylesResolver = lazyStylesheetResolver(BOOKING_STYLESHEET, 'furar-booking-css');
