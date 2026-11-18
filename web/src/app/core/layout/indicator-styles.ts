// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { lazyStylesheetResolver } from './lazy-stylesheet';

/** Nome do paquete de estilos de `angular.json` (`bundleName: indicadores`, `inject: false`). */
export const INDICATOR_STYLESHEET = 'indicadores.css';

/**
 * Folla das pezas dos indicadores (`styles/indicadores.css`: gráfico, cifra, tarxeta de gráfico,
 * Exportar, táboa de datos, transicións e impresión), que non van na carga inicial (D27). Ponse
 * en `resolve` de toda ruta con gráficos: o panel de indicadores e o catálogo.
 */
export const indicatorStylesResolver = lazyStylesheetResolver(INDICATOR_STYLESHEET, 'furar-indicators-css');
