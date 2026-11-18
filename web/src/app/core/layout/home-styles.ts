// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { lazyStylesheetResolver } from './lazy-stylesheet';

/** Nome do paquete de estilos de `angular.json` (`bundleName: portada`, `inject: false`). */
export const HOME_STYLESHEET = 'portada.css';

/**
 * Folla das portadas (`styles/portada.css`: cabeceira, collaxe, carril de tarxetas e «Así de
 * fácil»), que non vai na carga inicial (orzamento, D27). Ponse en `resolve` das rutas das
 * portadas: a raíz (a común e a de sempre) e a de cada concello.
 */
export const homeStylesResolver = lazyStylesheetResolver(HOME_STYLESHEET, 'furar-home-css');
