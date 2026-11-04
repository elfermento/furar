// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ResolveFn } from '@angular/router';
import { lazyStylesheet } from './lazy-stylesheet';

/** Nome do paquete de estilos de `angular.json` (`bundleName: xestion`, `inject: false`). */
export const MANAGEMENT_STYLESHEET = 'xestion.css';

/**
 * Folla dos compoñentes que só usa a xestión (`styles/xestion.css`): non vai na carga inicial.
 * Tamén a pide fóra da xestión o que usa algún deles (p. ex. «Para abrir as reservas» na portada).
 */
export const loadManagementStyles = lazyStylesheet(MANAGEMENT_STYLESHEET, 'furar-management-css');

/** A ruta de xestión agarda por ela. */
export const managementStylesResolver: ResolveFn<true> = () => loadManagementStyles();
