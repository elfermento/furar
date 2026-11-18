// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { lazyStylesheet } from '../layout/lazy-stylesheet';

/** Nome do paquete de estilos de `angular.json` (`bundleName: preferencias`, `inject: false`). */
export const PREFERENCES_STYLESHEET = 'preferencias.css';

/**
 * Folla do panel de accesibilidade (`styles/preferencias.css`: o panel e o interruptor), que non
 * vai na carga inicial (orzamento, D27). O panel está en todas as rutas, tamén nas que non teñen
 * resolvedor de estilos: cárgaa o compoñente do botón ao crearse (contexto de inxección).
 */
export const loadPreferencesStyles = lazyStylesheet(PREFERENCES_STYLESHEET, 'furar-preferences-css');
